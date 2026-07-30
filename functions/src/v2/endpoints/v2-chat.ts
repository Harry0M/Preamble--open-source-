/**
 * v2Chat — Main chat endpoint for AI V2 Ecosystem with SSE streaming.
 *
 * Cloud Functions v2 `onRequest` handler that:
 * 1. Verifies Firebase Auth token
 * 2. Validates input (message, conversationId, optional mode/modelId)
 * 3. Starts trace, checks token budget
 * 4. Gets conversation history via ConversationManager
 * 5. Retrieves memories via MemoryEngine
 * 6. Builds chat prompt via PromptFactory
 * 7. Resolves model via ModelRouter
 * 8. Streams response via SSE (delta, thinking, tool_calls, done events)
 * 9. Stores assistant message, records usage, extracts memory updates
 * 10. Ends trace
 *
 * Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 13.1, 14.2, 14.3
 */

import { onRequest } from "firebase-functions/v2/https";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, FieldValue } from "firebase-admin/firestore";

import {
  StreamChunk,
  TokenUsage,
  MemoryEntry,
} from "../models/types";

import {
  getHistory,
} from "../services/conversation-manager";

import {
  search,
  store as storeMemory,
} from "../services/memory-engine";

import {
  buildChatPrompt,
} from "../services/prompt-factory";

import {
  resolveModel,
  streamGenerate,
  estimateTokens,
} from "../services/model-router";

import {
  checkBudget,
  recordUsage,
} from "../services/token-economy-guard";

import {
  classifyMemory,
} from "../services/memory-categorizer";

import {
  startTrace,
  logMemoryRetrieval,
  logModelCall,
  logFailure,
  endTrace,
} from "../services/trace-logger";
import { enforceRateLimit } from "../utils/rate-limiter";

// ─── Auth Helper ───────────────────────────────────────────────────────────────

/**
 * Extracts and verifies Firebase Auth token from the Authorization header.
 * Returns the UID on success or null on failure.
 */
async function verifyAuth(authHeader: string | undefined): Promise<string | null> {
  if (!authHeader?.startsWith("Bearer ")) return null;
  try {
    const decoded = await getAuth().verifyIdToken(authHeader.slice(7));
    return decoded.uid;
  } catch {
    return null;
  }
}

// ─── Input Validation ──────────────────────────────────────────────────────────

interface ChatRequestBody {
  message: string;
  conversationId: string;
  mode?: "concise" | "detailed";
  modelId?: string;
}

function validateInput(body: unknown): { valid: true; data: ChatRequestBody } | { valid: false; error: string } {
  if (!body || typeof body !== "object") {
    return { valid: false, error: "Request body must be a JSON object" };
  }

  const { message, conversationId, mode, modelId } = body as Record<string, unknown>;

  if (typeof message !== "string" || message.trim().length === 0) {
    return { valid: false, error: "\"message\" is required and must be a non-empty string" };
  }

  if (typeof conversationId !== "string" || conversationId.trim().length === 0) {
    return { valid: false, error: "\"conversationId\" is required and must be a non-empty string" };
  }

  if (mode !== undefined && mode !== "concise" && mode !== "detailed") {
    return { valid: false, error: "\"mode\" must be \"concise\" or \"detailed\"" };
  }

  if (modelId !== undefined && typeof modelId !== "string") {
    return { valid: false, error: "\"modelId\" must be a string" };
  }

  return {
    valid: true,
    data: {
      message: message as string,
      conversationId: conversationId as string,
      mode: (mode as "concise" | "detailed") ?? "concise",
      modelId: modelId as string | undefined,
    },
  };
}

// ─── SSE Helper ────────────────────────────────────────────────────────────────

/**
 * Writes an SSE event to the response stream.
 */
function writeSSE(res: import("express").Response, chunk: StreamChunk & { traceId?: string }): void {
  res.write(`data: ${JSON.stringify(chunk)}\n\n`);
}

// ─── Firestore References ──────────────────────────────────────────────────────

function getDb() {
  return getFirestore("preamble");
}

function getMessagesCollection(uid: string, conversationId: string) {
  return getDb()
    .collection("v2_conversations")
    .doc(uid)
    .collection("threads")
    .doc(conversationId)
    .collection("messages");
}

function getThreadDoc(uid: string, conversationId: string) {
  return getDb()
    .collection("v2_conversations")
    .doc(uid)
    .collection("threads")
    .doc(conversationId);
}

// ─── Cloud Function Export ─────────────────────────────────────────────────────

export const v2Chat = onRequest(
  {
    cors: true,
    timeoutSeconds: 120,
    memory: "1GiB",
  },
  async (req, res) => {
    // ─── 1. Verify Firebase Auth ───────────────────────────────────────
    const uid = await verifyAuth(req.headers.authorization);
    if (!uid) {
      res.status(401).json({ error: "Unauthorized", code: "AUTH_REQUIRED" });
      return;
    }

    // ─── 1b. Rate Limiting (max 10 req/min per user, Req 26.3) ────────
    if (await enforceRateLimit(uid, res)) return;

    // ─── 2. Validate Input ─────────────────────────────────────────────
    const validation = validateInput(req.body);
    if (!validation.valid) {
      res.status(400).json({ error: validation.error, code: "INVALID_INPUT" });
      return;
    }

    const { message, conversationId, mode, modelId } = validation.data;

    // ─── 3. Start Trace ────────────────────────────────────────────────
    const traceCtx = startTrace(uid, "chat");

    try {
      // ─── 4. Resolve Model ──────────────────────────────────────────────
      const modelConfig = await resolveModel(modelId);

      // ─── 5. Estimate Cost & Check Budget ───────────────────────────────
      const estimatedCost = estimateTokens(message, modelConfig) * modelConfig.costPerMillionTokens / 1_000_000;
      const budgetCheck = await checkBudget(uid, estimatedCost);

      if (!budgetCheck.allowed) {
        logFailure(traceCtx, "BUDGET_EXCEEDED", Date.now() - traceCtx.startTime);
        endTrace(traceCtx, "error");
        res.status(429).json({
          error: "Daily token budget exceeded",
          code: "BUDGET_EXCEEDED",
          resetTime: budgetCheck.resetTime,
          dailyBudget: budgetCheck.dailyBudget,
          remainingBudget: budgetCheck.remainingBudget,
        });
        return;
      }

      // ─── 6. Get Conversation History ───────────────────────────────────
      const history = await getHistory(uid, conversationId);

      // ─── 7. Retrieve Memories ──────────────────────────────────────────
      let memories: MemoryEntry[] = [];
      try {
        memories = await search(uid, message, { limit: 10 });
      } catch {
        // Req 9.5: Memory retrieval failure is non-blocking
        memories = [];
      }

      logMemoryRetrieval(
        traceCtx,
        memories.length,
        memories.length > 0 ? memories[0].confidence : 0
      );

      // ─── 8. Build Chat Prompt ──────────────────────────────────────────
      const promptPayload = buildChatPrompt({
        message,
        conversationId,
        history,
        memories,
        mode: mode!,
        activeModel: modelConfig,
      });

      // ─── 9. Set SSE Headers ────────────────────────────────────────────
      res.setHeader("Content-Type", "text/event-stream");
      res.setHeader("Cache-Control", "no-cache");
      res.setHeader("Connection", "keep-alive");
      res.setHeader("X-Trace-Id", traceCtx.traceId);
      res.flushHeaders();

      // ─── 10. Stream via ModelRouter ────────────────────────────────────
      const streamStartTime = Date.now();
      let fullText = "";
      let thinkingText = "";
      let inputTokens = 0;
      let outputTokens = 0;
      let detectedToolCalls: StreamChunk["toolCalls"] | undefined;
      let finishReason: StreamChunk["finishReason"] = "stop";

      const stream = streamGenerate(
        {
          systemPrompt: promptPayload.systemPrompt,
          messages: promptPayload.messages,
          tools: promptPayload.tools,
          forceToolCall: promptPayload.forceToolCall,
          temperature: promptPayload.temperature,
          maxOutputTokens: promptPayload.maxOutputTokens,
        },
        modelConfig
      );

      for await (const chunk of stream) {
        // Write each chunk as an SSE event with trace ID
        writeSSE(res, { ...chunk, traceId: traceCtx.traceId });

        // Accumulate content for storage
        switch (chunk.type) {
          case "delta":
            fullText += chunk.text ?? "";
            break;
          case "thinking":
            thinkingText += chunk.text ?? "";
            break;
          case "tool_calls":
            detectedToolCalls = chunk.toolCalls;
            break;
          case "done":
            inputTokens = chunk.inputTokens ?? 0;
            outputTokens = chunk.outputTokens ?? 0;
            finishReason = chunk.finishReason ?? "stop";
            break;
          case "error":
            logFailure(traceCtx, chunk.error ?? "STREAM_ERROR", Date.now() - traceCtx.startTime);
            break;
        }
      }

      const streamLatencyMs = Date.now() - streamStartTime;

      // Log model call metrics
      logModelCall(traceCtx, modelConfig.modelId, inputTokens, outputTokens, streamLatencyMs);

      // ─── 11. Store Assistant Message in Conversation ───────────────────
      const assistantContent = fullText || (detectedToolCalls ? "[tool_calls]" : "");

      await getMessagesCollection(uid, conversationId).add({
        role: "assistant",
        content: assistantContent,
        toolCalls: detectedToolCalls ?? null,
        toolResults: null,
        renderBlocks: null,
        inputTokens,
        outputTokens,
        model: modelConfig.modelId,
        traceId: traceCtx.traceId,
        createdAt: Date.now(),
      });

      // Also store the user message
      await getMessagesCollection(uid, conversationId).add({
        role: "user",
        content: message,
        toolCalls: null,
        toolResults: null,
        renderBlocks: null,
        inputTokens: 0,
        outputTokens: 0,
        model: null,
        traceId: traceCtx.traceId,
        createdAt: Date.now() - 1, // Slightly before assistant to maintain order
      });

      // Update thread metadata
      const threadRef = getThreadDoc(uid, conversationId);
      const threadSnap = await threadRef.get();
      if (threadSnap.exists) {
        await threadRef.update({
          messageCount: FieldValue.increment(2),
          updatedAt: Date.now(),
        });
      } else {
        // Create thread if it doesn't exist yet
        await threadRef.set({
          createdAt: Date.now(),
          updatedAt: Date.now(),
          messageCount: 2,
          summarizedUpTo: 0,
          summaryText: "",
          isProcessing: false,
          lastBriefingDate: null,
        });
      }

      // ─── 12. Record Token Usage ────────────────────────────────────────
      const usage: TokenUsage = {
        inputTokens,
        outputTokens,
        model: modelConfig.modelId,
        costPerMillionTokens: modelConfig.costPerMillionTokens,
        normalizedCost: ((inputTokens + outputTokens) * modelConfig.costPerMillionTokens) / 1_000_000,
        traceId: traceCtx.traceId,
      };

      await recordUsage(uid, usage);

      // ─── 13. Extract & Store Memory Updates (Req 13.1, 14.2, 14.3) ────
      // Non-blocking: attempt to extract memory entries from the conversation
      extractAndStoreMemories(uid, message, fullText, conversationId).catch(() => {
        // Memory extraction failure is non-critical (Req 13.2 — retry handled internally)
      });

      // ─── 14. End Trace ─────────────────────────────────────────────────
      endTrace(traceCtx, "success");
      res.end();
    } catch (error) {
      const elapsed = Date.now() - traceCtx.startTime;
      const errorMessage = error instanceof Error ? error.message : "Unknown error";
      logFailure(traceCtx, errorMessage, elapsed);
      endTrace(traceCtx, "error");

      // If headers haven't been sent yet, send error JSON
      if (!res.headersSent) {
        res.status(500).json({
          error: "Internal server error",
          code: "INTERNAL_ERROR",
          traceId: traceCtx.traceId,
        });
      } else {
        // Headers already sent (SSE started) — write error event and end
        writeSSE(res, {
          type: "error",
          error: "Stream interrupted: " + errorMessage,
          traceId: traceCtx.traceId,
        });
        res.end();
      }
    }
  }
);

// ─── Memory Extraction (Background) ───────────────────────────────────────────

/**
 * Extracts new memory entries from the conversation and stores them.
 *
 * Analyzes both the user message and assistant response for memory-worthy
 * information (identity facts, preferences, goals, relationships, schedules).
 *
 * Requirements: 13.1, 14.2, 14.3
 */
async function extractAndStoreMemories(
  uid: string,
  userMessage: string,
  assistantResponse: string,
  conversationId: string
): Promise<void> {
  // Simple heuristic extraction — look for statements that indicate
  // personal facts, preferences, or plans in the user message.
  // A more sophisticated approach would use the AI model, but for now
  // we do keyword-based detection to avoid additional model calls.

  const memoryWorthy = detectMemoryWorthyContent(userMessage);

  for (const text of memoryWorthy) {
    const category = classifyMemory(text);

    await storeMemory(uid, {
      text,
      category,
      memoryType: "short_term",
      confidence: 0.7,
      source: "chat",
      conversationId,
    });
  }
}

/**
 * Detects memory-worthy content from a user message.
 *
 * Looks for patterns indicating personal information:
 * - "I am..." / "My name is..." (identity)
 * - "I like..." / "I prefer..." (preferences)
 * - "I want to..." / "My goal is..." (goals)
 * - "I work at..." / "I study at..." (identity/context)
 * - Schedule/time patterns (habits)
 */
function detectMemoryWorthyContent(message: string): string[] {
  const results: string[] = [];

  // Patterns that indicate memory-worthy statements
  const memoryPatterns = [
    /\b(?:I am|I'm|my name is|mera naam)\b.{3,100}/gi,
    /\b(?:I like|I love|I prefer|I enjoy|mujhe pasand)\b.{3,100}/gi,
    /\b(?:I want to|I need to|my goal|I plan to|mujhe karna hai)\b.{3,100}/gi,
    /\b(?:I work at|I study at|I live in|I'm from)\b.{3,100}/gi,
    /\b(?:every day|every morning|every evening|daily routine|har din)\b.{3,100}/gi,
    /\b(?:my (?:friend|brother|sister|mother|father|wife|husband))\b.{3,100}/gi,
  ];

  for (const pattern of memoryPatterns) {
    const matches = message.match(pattern);
    if (matches) {
      for (const match of matches) {
        // Trim and limit length
        const trimmed = match.trim().substring(0, 200);
        if (trimmed.length >= 5 && !results.includes(trimmed)) {
          results.push(trimmed);
        }
      }
    }
  }

  return results;
}
