/**
 * v2Parse — Cloud Function endpoint for AI V2 natural language task parsing.
 *
 * Converts natural language input into a structured Task object using:
 * - Auth verification via Firebase Admin Auth
 * - Input validation (1-500 chars, not whitespace-only)
 * - Token budget enforcement (TokenEconomyGuard)
 * - Semantic memory retrieval (MemoryEngine)
 * - Prompt assembly (PromptFactory.buildParsePrompt)
 * - AI model generation (ModelRouter)
 * - JSON Schema validation (StructuredOutputValidator)
 * - Retry on validation failure (once, 10s timeout)
 * - Semantic deduplication (MemoryEngine.findDuplicates, threshold ≥ 0.85)
 * - Token usage recording
 * - End-to-end tracing (TraceLogger)
 *
 * Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6,
 *              5.1, 5.2, 5.3, 5.4
 */

import { onRequest } from "firebase-functions/v2/https";
import { getAuth } from "firebase-admin/auth";
import {
  search,
  findDuplicates,
  checkBudget,
  recordUsage,
  buildParsePrompt,
  generate,
  resolveModel,
  estimateTokens,
  validateParseResponse,
} from "../services";
import {
  startTrace,
  logMemoryRetrieval,
  logModelCall,
  logFailure,
  endTrace,
} from "../services/trace-logger";
import { enforceRateLimit } from "../utils/rate-limiter";
import { validateParseInput } from "../utils/parse-input-validator";
import type {
  ParseInput,
  ParseResult,
  ValidationError,
  MemoryEntry,
} from "../models/types";

// ─── Auth Helper ───────────────────────────────────────────────────────────────

/**
 * Verifies the Firebase Auth token from the Authorization header.
 * Returns the UID on success, or null if invalid/missing.
 */
async function verifyAuth(
  authHeader: string | undefined
): Promise<string | null> {
  if (!authHeader?.startsWith("Bearer ")) return null;
  try {
    const decoded = await getAuth().verifyIdToken(authHeader.slice(7));
    return decoded.uid;
  } catch {
    return null;
  }
}

// ─── Input Validation ──────────────────────────────────────────────────────────

// Input validation logic extracted to ../utils/parse-input-validator.ts
// for independent testability (Requirement 1.6).

// ─── Endpoint ──────────────────────────────────────────────────────────────────

export const v2Parse = onRequest(
  { cors: true, timeoutSeconds: 30, memory: "256MiB" },
  async (req, res) => {
    // ── Method check ─────────────────────────────────────────────────────────
    if (req.method !== "POST") {
      res.status(405).json({ error: "Method not allowed" });
      return;
    }

    // ── 1. Auth verification (Req 8.5) ──────────────────────────────────────
    const uid = await verifyAuth(req.headers.authorization);
    if (!uid) {
      res.status(401).json({ error: "Unauthorized" });
      return;
    }

    // ── 1b. Rate limiting (max 10 req/min per user, Req 26.3) ───────────────
    if (await enforceRateLimit(uid, res)) return;

    // ── 2. Input validation (Req 1.6) — BEFORE any AI/service calls ─────────
    const validation = validateParseInput(req.body);
    if (!validation.valid) {
      res.status(400).json({ error: validation.error });
      return;
    }

    const { text, timezone } = validation;

    // ── 3. Start trace (Req 20.1) ───────────────────────────────────────────
    const trace = startTrace(uid, "parse");

    try {
      // ── 4. Token budget check (Req 17.2, 17.4) ─────────────────────────────
      const model = await resolveModel();
      const estimatedCost =
        (estimateTokens(text, model) * model.costPerMillionTokens) / 1_000_000;

      const budget = await checkBudget(uid, estimatedCost);
      if (!budget.allowed) {
        logFailure(trace, "BUDGET_EXCEEDED", Date.now() - trace.startTime);
        endTrace(trace, "error");
        res.status(429).json({
          error: "Daily token budget exceeded",
          resetTime: budget.resetTime,
          remainingBudget: budget.remainingBudget,
          dailyBudget: budget.dailyBudget,
        });
        return;
      }

      // ── 5. Memory retrieval (Req 1.5, 1.7, 14.1) ──────────────────────────
      let memories: MemoryEntry[] = [];
      try {
        memories = await search(uid, text, { limit: 10 });
      } catch {
        // Req 1.7: If memory unavailable, proceed without it
        memories = [];
      }

      logMemoryRetrieval(
        trace,
        memories.length,
        memories.length > 0 ? memories[0].confidence : 0
      );

      // ── 6. Build parse prompt (Req 19.1, 19.2) ────────────────────────────
      const now = new Date();
      const currentDateTime = now.toISOString();

      const parseInput: ParseInput = {
        text,
        timezone,
        currentDateTime,
        memories,
      };

      const promptPayload = buildParsePrompt(parseInput);

      // ── 7. Generate via ModelRouter (Req 15.8) ─────────────────────────────
      const startMs = Date.now();
      const modelResponse = await generate(
        {
          systemPrompt: promptPayload.systemPrompt,
          messages: promptPayload.messages,
          tools: promptPayload.tools,
          forceToolCall: promptPayload.forceToolCall,
          responseSchema: promptPayload.responseSchema,
          temperature: promptPayload.temperature,
          maxOutputTokens: promptPayload.maxOutputTokens,
        },
        model
      );
      const latencyMs = Date.now() - startMs;

      logModelCall(
        trace,
        model.modelId,
        modelResponse.inputTokens,
        modelResponse.outputTokens,
        latencyMs
      );

      // ── 8. Validate structured output (Req 1.2) ───────────────────────────
      let parsed: unknown;
      try {
        parsed = JSON.parse(modelResponse.text);
      } catch {
        parsed = null;
      }

      let validationResult: ParseResult | ValidationError;

      if (parsed !== null) {
        validationResult = validateParseResponse(parsed);
      } else {
        validationResult = {
          valid: false,
          errors: [{ path: "/", message: "Response is not valid JSON", keyword: "type" }],
        };
      }

      // ── 9. Retry on validation failure (Req 1.3) ──────────────────────────
      if (!validationResult.valid) {
        // Retry once with 10s timeout
        const retryStartMs = Date.now();
        const retryPromise = generate(
          {
            systemPrompt: promptPayload.systemPrompt,
            messages: promptPayload.messages,
            tools: promptPayload.tools,
            forceToolCall: promptPayload.forceToolCall,
            responseSchema: promptPayload.responseSchema,
            temperature: promptPayload.temperature,
            maxOutputTokens: promptPayload.maxOutputTokens,
          },
          model
        );

        // Apply 10s timeout
        const timeoutPromise = new Promise<null>((resolve) =>
          setTimeout(() => resolve(null), 10000)
        );

        const retryResponse = await Promise.race([retryPromise, timeoutPromise]);

        if (retryResponse !== null) {
          const retryLatencyMs = Date.now() - retryStartMs;
          logModelCall(
            trace,
            model.modelId,
            retryResponse.inputTokens,
            retryResponse.outputTokens,
            retryLatencyMs
          );

          let retryParsed: unknown;
          try {
            retryParsed = JSON.parse(retryResponse.text);
          } catch {
            retryParsed = null;
          }

          if (retryParsed !== null) {
            validationResult = validateParseResponse(retryParsed);
          } else {
            validationResult = {
              valid: false,
              errors: [{ path: "/", message: "Retry response is not valid JSON", keyword: "type" }],
            };
          }

          // Update model response for token recording
          if (validationResult.valid) {
            modelResponse.inputTokens += retryResponse.inputTokens;
            modelResponse.outputTokens += retryResponse.outputTokens;
          }
        }

        // If retry also failed, return 500 GENERATION_FAILED
        if (!validationResult.valid) {
          logFailure(trace, "GENERATION_FAILED", Date.now() - trace.startTime);
          endTrace(trace, "error");
          res.status(500).json({
            error: "GENERATION_FAILED",
            message: "Task could not be parsed after retry",
            traceId: trace.traceId,
          });
          return;
        }
      }

      // At this point, validationResult is a valid ParseResult
      const parseResult = validationResult as ParseResult;

      // ── 10. Deduplication check (Req 5.1, 5.2, 5.3, 5.4) ─────────────────
      let duplicates: Array<{ taskId: string; similarity: number; title: string }> = [];
      try {
        const duplicateEntries = await findDuplicates(uid, text, 0.85);
        duplicates = duplicateEntries
          .filter((entry) => entry.confidence >= 0.85)
          .map((entry) => ({
            taskId: entry.id,
            similarity: entry.confidence,
            title: entry.text,
          }));
      } catch {
        // Req 5.4: If deduplication unavailable, skip without error
        duplicates = [];
      }

      // Attach duplicates to the parse result data
      if (duplicates.length > 0) {
        parseResult.data.duplicates = duplicates;
      }

      // ── 11. Record token usage (Req 17.1) ─────────────────────────────────
      const totalInputTokens = modelResponse.inputTokens;
      const totalOutputTokens = modelResponse.outputTokens;
      const normalizedCost =
        ((totalInputTokens + totalOutputTokens) * model.costPerMillionTokens) /
        1_000_000;

      await recordUsage(uid, {
        inputTokens: totalInputTokens,
        outputTokens: totalOutputTokens,
        model: model.modelId,
        costPerMillionTokens: model.costPerMillionTokens,
        normalizedCost,
        traceId: trace.traceId,
      });

      // ── 12. End trace and return result ────────────────────────────────────
      endTrace(trace, "success");

      res.status(200).json({
        ...parseResult.data,
        traceId: trace.traceId,
      });
    } catch (err) {
      // Unexpected error fallback
      const elapsedMs = Date.now() - trace.startTime;
      const errorMessage =
        err instanceof Error ? err.message : "Internal server error";
      logFailure(trace, errorMessage, elapsedMs);
      endTrace(trace, "error");

      res.status(500).json({
        error: "INTERNAL_ERROR",
        message: "An unexpected error occurred",
        traceId: trace.traceId,
      });
    }
  }
);
