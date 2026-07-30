/**
 * v2ChatContinue — Cloud Function endpoint for processing tool results and generating final response.
 *
 * After the client presents tool permission prompts and collects user decisions,
 * this endpoint receives the tool results (granted/denied) and re-invokes the model
 * to generate a final response incorporating those results.
 *
 * Flow:
 * 1. Verify Firebase Auth token
 * 2. Validate input (conversationId, toolResults array)
 * 3. Start trace, check budget
 * 4. Process each tool result (granted → pass result, denied → inform model)
 * 5. Get conversation history, build chat prompt with tool results injected
 * 6. Generate response via ModelRouter.generate() (non-streaming)
 * 7. Validate response, store assistant message
 * 8. Record usage, end trace
 * 9. Return response JSON
 *
 * Requirements: 7.2, 7.3, 7.7, 7.8
 */

import { onRequest } from "firebase-functions/v2/https";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import {
  ConversationMessage,
  ModelConfig,
  ToolResult,
} from "../models/types";
import {
  checkBudget,
  recordUsage,
  resolveModel,
  generate,
  estimateTokens,
  buildChatPrompt,
  getHistory,
  search,
  validateChatResponse,
} from "../services";
import {
  startTrace,
  logModelCall,
  logFailure,
  endTrace,
} from "../services/trace-logger";
import { enforceRateLimit } from "../utils/rate-limiter";

// ─── Types ─────────────────────────────────────────────────────────────────────

/**
 * A single tool result submitted by the client after user grants/denies permission.
 */
interface ClientToolResult {
  toolName: string;
  granted: boolean;
  result?: unknown;
  error?: string;
}

/**
 * Request body shape for v2ChatContinue.
 */
interface ChatContinueRequestBody {
  conversationId: string;
  toolResults: ClientToolResult[];
}

// ─── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Returns the Firestore instance for the "preamble" named database.
 */
function getDb() {
  return getFirestore("preamble");
}

/**
 * Extracts and verifies the Firebase Auth token from the Authorization header.
 * Returns the authenticated user's UID or null if invalid.
 */
async function verifyAuth(authHeader: string | undefined): Promise<string | null> {
  if (!authHeader || !authHeader.startsWith("Bearer ")) {
    return null;
  }
  const token = authHeader.substring(7);
  try {
    const decoded = await getAuth().verifyIdToken(token);
    return decoded.uid;
  } catch {
    return null;
  }
}

/**
 * Validates the request body against expected shape.
 * Returns an error message if invalid, null if valid.
 */
function validateRequestBody(body: unknown): string | null {
  if (!body || typeof body !== "object") {
    return "Request body must be a JSON object";
  }

  const { conversationId, toolResults } = body as Record<string, unknown>;

  if (!conversationId || typeof conversationId !== "string") {
    return "conversationId must be a non-empty string";
  }

  if (!Array.isArray(toolResults)) {
    return "toolResults must be an array";
  }

  if (toolResults.length === 0) {
    return "toolResults must contain at least one tool result";
  }

  for (let i = 0; i < toolResults.length; i++) {
    const tr = toolResults[i];
    if (!tr || typeof tr !== "object") {
      return `toolResults[${i}] must be an object`;
    }
    const { toolName, granted } = tr as Record<string, unknown>;
    if (!toolName || typeof toolName !== "string") {
      return `toolResults[${i}].toolName must be a non-empty string`;
    }
    if (typeof granted !== "boolean") {
      return `toolResults[${i}].granted must be a boolean`;
    }
  }

  return null;
}

/**
 * Builds messages representing tool results to inject into the conversation.
 * - Granted tools: include the execution result from the client
 * - Denied tools: inject a system message indicating user denied the action (Req 7.3)
 */
function buildToolResultMessages(toolResults: ClientToolResult[], traceId: string): ConversationMessage[] {
  const messages: ConversationMessage[] = [];

  const processedResults: ToolResult[] = [];

  for (const tr of toolResults) {
    if (tr.granted) {
      // Req 7.2: User granted — pass tool result to model
      processedResults.push({
        toolName: tr.toolName,
        success: !tr.error,
        data: tr.result,
        error: tr.error,
        traceId,
        executedAt: Date.now(),
      });
    } else {
      // Req 7.3: User denied — inform model the action was not performed
      processedResults.push({
        toolName: tr.toolName,
        success: false,
        error: `User denied the ${tr.toolName} action`,
        traceId,
        executedAt: Date.now(),
      });
    }
  }

  // Build a single assistant-role message containing tool results
  // This follows the conversation pattern where tool results come back
  const toolSummaryParts: string[] = [];
  for (const result of processedResults) {
    if (result.success) {
      const dataStr = result.data !== undefined
        ? JSON.stringify(result.data)
        : "completed successfully";
      toolSummaryParts.push(`[Tool: ${result.toolName}] Result: ${dataStr}`);
    } else {
      toolSummaryParts.push(`[Tool: ${result.toolName}] ${result.error}`);
    }
  }

  messages.push({
    role: "system",
    content: `Tool execution results:\n${toolSummaryParts.join("\n")}`,
    toolResults: processedResults,
    createdAt: Date.now(),
  });

  return messages;
}

/**
 * Stores an assistant message in the conversation's messages subcollection.
 */
async function storeAssistantMessage(
  uid: string,
  conversationId: string,
  content: string,
  model: string,
  traceId: string,
  inputTokens: number,
  outputTokens: number
): Promise<void> {
  const threadRef = getDb()
    .collection("v2_conversations")
    .doc(uid)
    .collection("threads")
    .doc(conversationId);

  const messagesCol = threadRef.collection("messages");

  await messagesCol.add({
    role: "assistant",
    content,
    toolCalls: null,
    toolResults: null,
    renderBlocks: null,
    inputTokens,
    outputTokens,
    model,
    traceId,
    createdAt: Date.now(),
  });

  // Update thread metadata
  await threadRef.update({
    messageCount: FieldValue.increment(1),
    updatedAt: Date.now(),
  });
}

/**
 * Stores tool result messages in the conversation history for continuity.
 */
async function storeToolResultMessage(
  uid: string,
  conversationId: string,
  toolResults: ClientToolResult[],
  traceId: string
): Promise<void> {
  const threadRef = getDb()
    .collection("v2_conversations")
    .doc(uid)
    .collection("threads")
    .doc(conversationId);

  const messagesCol = threadRef.collection("messages");

  // Build summary of tool results for the stored message
  const summaryParts: string[] = [];
  for (const tr of toolResults) {
    if (tr.granted) {
      const resultStr = tr.error
        ? `Failed: ${tr.error}`
        : tr.result !== undefined
          ? JSON.stringify(tr.result)
          : "completed successfully";
      summaryParts.push(`[${tr.toolName}] Granted — ${resultStr}`);
    } else {
      summaryParts.push(`[${tr.toolName}] Denied by user`);
    }
  }

  await messagesCol.add({
    role: "system",
    content: `Tool results:\n${summaryParts.join("\n")}`,
    toolCalls: null,
    toolResults: toolResults.map((tr) => ({
      toolName: tr.toolName,
      success: tr.granted && !tr.error,
      data: tr.result ?? null,
      error: tr.granted ? (tr.error ?? null) : `User denied the ${tr.toolName} action`,
      traceId,
      executedAt: Date.now(),
    })),
    renderBlocks: null,
    inputTokens: 0,
    outputTokens: 0,
    model: null,
    traceId,
    createdAt: Date.now(),
  });

  await threadRef.update({
    messageCount: FieldValue.increment(1),
    updatedAt: Date.now(),
  });
}

// ─── Cloud Function ────────────────────────────────────────────────────────────

/**
 * v2ChatContinue — Accepts tool results from client and generates final AI response.
 *
 * POST /v2ChatContinue
 * Headers: Authorization: Bearer <Firebase ID Token>
 * Body: { conversationId: string, toolResults: ClientToolResult[] }
 *
 * Returns:
 * - 200: { content, thinking?, citations?, memoryUpdates? }
 * - 400: { error: string } for invalid input
 * - 401: { error: string } for auth failures
 * - 429: { error: string, resetTime: string } for budget exceeded
 * - 500: { error: string } for server errors
 */
export const v2ChatContinue = onRequest(
  {
    cors: true,
    maxInstances: 100,
    timeoutSeconds: 120,
    memory: "512MiB",
  },
  async (req, res) => {
    // Only allow POST
    if (req.method !== "POST") {
      res.status(405).json({ error: "Method not allowed" });
      return;
    }

    // Step 1: Verify Firebase Auth token (Req 8.5)
    const uid = await verifyAuth(req.headers.authorization);
    if (!uid) {
      res.status(401).json({ error: "Authentication required" });
      return;
    }

    // Step 1b: Rate limiting (max 10 req/min per user, Req 26.3)
    if (await enforceRateLimit(uid, res)) return;

    // Step 2: Validate input
    const validationError = validateRequestBody(req.body);
    if (validationError) {
      res.status(400).json({ error: validationError });
      return;
    }

    const { conversationId, toolResults } = req.body as ChatContinueRequestBody;

    // Step 3: Start trace
    const trace = startTrace(uid, "chat_continue");

    try {
      // Resolve model
      const modelConfig: ModelConfig = await resolveModel();

      // Estimate tokens for budget check
      const estimatedTokens = estimateTokens(
        toolResults.map((tr) => JSON.stringify(tr)).join(" "),
        modelConfig
      );
      const estimatedCost =
        (estimatedTokens * 2 * modelConfig.costPerMillionTokens) / 1_000_000;

      // Check budget
      const budgetCheck = await checkBudget(uid, estimatedCost);
      if (!budgetCheck.allowed) {
        logFailure(trace, "BUDGET_EXCEEDED", Date.now() - trace.startTime);
        endTrace(trace, "error");
        res.status(429).json({
          error: "Daily token budget exceeded",
          resetTime: budgetCheck.resetTime,
          remainingBudget: budgetCheck.remainingBudget,
          dailyBudget: budgetCheck.dailyBudget,
        });
        return;
      }

      // Step 4: Store tool result messages in conversation for continuity
      await storeToolResultMessage(uid, conversationId, toolResults, trace.traceId);

      // Step 5: Get conversation history
      const history = await getHistory(uid, conversationId);

      // Retrieve relevant memories for context
      // Use the latest user message in history for memory search
      const lastUserMessage = [...history]
        .reverse()
        .find((msg) => msg.role === "user");
      const memoryQuery = lastUserMessage?.content ?? "";
      const memories = memoryQuery
        ? await search(uid, memoryQuery, { limit: 10 })
        : [];

      // Build tool result messages for injection into the prompt
      const toolResultMessages = buildToolResultMessages(toolResults, trace.traceId);

      // Build complete history with tool results appended
      const fullHistory: ConversationMessage[] = [
        ...history,
        ...toolResultMessages,
      ];

      // Step 6: Build prompt and generate response (non-streaming for continue)
      const prompt = buildChatPrompt({
        message: "Please respond based on the tool execution results above.",
        conversationId,
        history: fullHistory,
        memories,
        mode: "concise",
        activeModel: modelConfig,
      });

      const startMs = Date.now();
      const modelResponse = await generate(prompt, modelConfig);
      const latencyMs = Date.now() - startMs;

      logModelCall(
        trace,
        modelConfig.modelId,
        modelResponse.inputTokens,
        modelResponse.outputTokens,
        latencyMs
      );

      // Step 7: Validate response
      let responseContent: string;
      let responseThinking: string | undefined;
      let responseCitations: Array<{ title: string; url: string; snippet?: string }> | undefined;
      let responseMemoryUpdates: Array<{ op: "upsert" | "delete"; text: string; category: string }> | undefined;

      // Try to parse as structured JSON first
      try {
        const parsed = JSON.parse(modelResponse.text);
        const validated = validateChatResponse(parsed);

        if (validated.valid) {
          responseContent = validated.data.content;
          responseThinking = validated.data.thinking;
          responseCitations = validated.data.citations;
          responseMemoryUpdates = validated.data.memoryUpdates;
        } else {
          // Validation failed — use raw text as content
          responseContent = modelResponse.text;
          responseThinking = modelResponse.thinkingText;
        }
      } catch {
        // Not valid JSON — use raw text as content
        responseContent = modelResponse.text;
        responseThinking = modelResponse.thinkingText;
      }

      // Step 8: Store assistant message in conversation
      await storeAssistantMessage(
        uid,
        conversationId,
        responseContent,
        modelConfig.modelId,
        trace.traceId,
        modelResponse.inputTokens,
        modelResponse.outputTokens
      );

      // Record token usage
      await recordUsage(uid, {
        inputTokens: modelResponse.inputTokens,
        outputTokens: modelResponse.outputTokens,
        model: modelConfig.modelId,
        costPerMillionTokens: modelConfig.costPerMillionTokens,
        normalizedCost:
          ((modelResponse.inputTokens + modelResponse.outputTokens) *
            modelConfig.costPerMillionTokens) /
          1_000_000,
        traceId: trace.traceId,
      });

      // End trace successfully
      endTrace(trace, "success");

      // Step 9: Return response JSON
      const responseBody: Record<string, unknown> = {
        content: responseContent,
        traceId: trace.traceId,
      };

      if (responseThinking) {
        responseBody.thinking = responseThinking;
      }
      if (responseCitations && responseCitations.length > 0) {
        responseBody.citations = responseCitations;
      }
      if (responseMemoryUpdates && responseMemoryUpdates.length > 0) {
        responseBody.memoryUpdates = responseMemoryUpdates;
      }

      // Include info about which tools were granted vs denied
      responseBody.toolResultsSummary = toolResults.map((tr) => ({
        toolName: tr.toolName,
        granted: tr.granted,
        success: tr.granted && !tr.error,
      }));

      res.status(200).json(responseBody);
    } catch (error) {
      const elapsedMs = Date.now() - trace.startTime;
      const errorMessage = error instanceof Error ? error.message : "Internal server error";
      logFailure(trace, errorMessage, elapsedMs);
      endTrace(trace, "error");

      // Handle specific error types
      if (error instanceof Error && "statusCode" in error) {
        const statusCode = (error as { statusCode: number }).statusCode;
        res.status(statusCode).json({ error: errorMessage });
        return;
      }

      res.status(500).json({ error: "An error occurred processing the tool results" });
    }
  }
);
