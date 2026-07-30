/**
 * TraceLogger — Structured logging service for all AI V2 operations.
 *
 * Provides end-to-end tracing with unique trace IDs (UUID v4) for each request.
 * Logs metadata only — never user message content, memory text, or PII.
 *
 * Uses Firebase Functions logger for structured JSON output compatible with
 * Google Cloud Logging.
 *
 * Requirements: 20.1, 20.2, 20.3, 20.4, 20.5
 */

import { randomUUID } from "crypto";
import { logger } from "firebase-functions/v2";
import { TraceContext, V2Operation } from "../models/types";

/**
 * Starts a new trace for a V2 operation.
 *
 * Generates a UUID v4 trace ID, records the operation start time,
 * and emits an initial structured log entry.
 *
 * @param uid - The authenticated user's UID
 * @param operation - The V2 operation type being performed
 * @returns A TraceContext to pass to subsequent log calls
 */
export function startTrace(uid: string, operation: V2Operation): TraceContext {
  const ctx: TraceContext = {
    traceId: randomUUID(),
    uid,
    operation,
    startTime: Date.now(),
  };

  logger.info("v2.trace.start", {
    traceId: ctx.traceId,
    uid: ctx.uid,
    operation: ctx.operation,
  });

  return ctx;
}

/**
 * Logs memory retrieval results — metadata only.
 *
 * Records the number of entries retrieved and the top confidence score.
 * Does NOT log memory text content (Requirement 20.4).
 *
 * @param ctx - The active trace context
 * @param count - Number of memory entries retrieved
 * @param topConfidence - Highest confidence score among retrieved entries
 */
export function logMemoryRetrieval(
  ctx: TraceContext,
  count: number,
  topConfidence: number
): void {
  logger.info("v2.trace.memory_retrieval", {
    traceId: ctx.traceId,
    uid: ctx.uid,
    operation: ctx.operation,
    entryCount: count,
    topConfidence,
  });
}

/**
 * Logs an AI model call — metadata only.
 *
 * Records model name, token counts, and latency.
 * Does NOT log prompt content or model response text (Requirement 20.4).
 *
 * @param ctx - The active trace context
 * @param model - The model identifier used (e.g., "gemini-2.5-flash")
 * @param inputTokens - Number of input tokens consumed
 * @param outputTokens - Number of output tokens generated
 * @param latencyMs - Request latency in milliseconds
 */
export function logModelCall(
  ctx: TraceContext,
  model: string,
  inputTokens: number,
  outputTokens: number,
  latencyMs: number
): void {
  logger.info("v2.trace.model_call", {
    traceId: ctx.traceId,
    uid: ctx.uid,
    operation: ctx.operation,
    model,
    inputTokens,
    outputTokens,
    latencyMs,
  });
}

/**
 * Logs a failure during V2 processing.
 *
 * Records the failure reason and elapsed time. The reason should be a
 * descriptive error type/code — not user content (Requirement 20.4).
 *
 * @param ctx - The active trace context
 * @param reason - Failure reason (error type or code, not user content)
 * @param elapsedMs - Time elapsed since trace start in milliseconds
 */
export function logFailure(
  ctx: TraceContext,
  reason: string,
  elapsedMs: number
): void {
  logger.error("v2.trace.failure", {
    traceId: ctx.traceId,
    uid: ctx.uid,
    operation: ctx.operation,
    reason,
    elapsedMs,
  });
}

/**
 * Ends the trace and logs the final outcome.
 *
 * @param ctx - The active trace context
 * @param outcome - Whether the operation completed successfully or with an error
 */
export function endTrace(
  ctx: TraceContext,
  outcome: "success" | "error"
): void {
  const elapsedMs = Date.now() - ctx.startTime;

  logger.info("v2.trace.end", {
    traceId: ctx.traceId,
    uid: ctx.uid,
    operation: ctx.operation,
    outcome,
    elapsedMs,
  });
}
