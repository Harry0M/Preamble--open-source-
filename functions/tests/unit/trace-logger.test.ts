/**
 * Unit tests for TraceLogger service.
 *
 * Validates: Requirements 20.1, 20.2, 20.3, 20.4, 20.5
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import {
  startTrace,
  logMemoryRetrieval,
  logModelCall,
  logFailure,
  endTrace,
} from "../../src/v2/services/trace-logger";
import { TraceContext, V2Operation } from "../../src/v2/models/types";

// Mock firebase-functions/v2 logger
vi.mock("firebase-functions/v2", () => ({
  logger: {
    info: vi.fn(),
    error: vi.fn(),
  },
}));

import { logger } from "firebase-functions/v2";

const mockedLoggerInfo = vi.mocked(logger.info);
const mockedLoggerError = vi.mocked(logger.error);

describe("TraceLogger", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("startTrace", () => {
    it("generates a valid UUID v4 trace ID", () => {
      const ctx = startTrace("user-123", "chat");

      // UUID v4 format: xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx
      const uuidV4Regex =
        /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
      expect(ctx.traceId).toMatch(uuidV4Regex);
    });

    it("returns a TraceContext with all required fields", () => {
      const ctx = startTrace("user-456", "parse");

      expect(ctx.uid).toBe("user-456");
      expect(ctx.operation).toBe("parse");
      expect(ctx.startTime).toBeLessThanOrEqual(Date.now());
      expect(ctx.startTime).toBeGreaterThan(0);
    });

    it("logs the trace start with structured metadata", () => {
      const ctx = startTrace("user-789", "briefing");

      expect(mockedLoggerInfo).toHaveBeenCalledWith("v2.trace.start", {
        traceId: ctx.traceId,
        uid: "user-789",
        operation: "briefing",
      });
    });

    it("generates unique trace IDs for each call", () => {
      const ctx1 = startTrace("user-1", "chat");
      const ctx2 = startTrace("user-1", "chat");

      expect(ctx1.traceId).not.toBe(ctx2.traceId);
    });

    it("works with all V2 operation types", () => {
      const operations: V2Operation[] = [
        "parse",
        "chat",
        "chat_continue",
        "briefing",
        "tool_exec",
      ];

      for (const op of operations) {
        const ctx = startTrace("user-test", op);
        expect(ctx.operation).toBe(op);
      }
    });
  });

  describe("logMemoryRetrieval", () => {
    it("logs entry count and top confidence score", () => {
      const ctx: TraceContext = {
        traceId: "test-trace-id",
        uid: "user-123",
        operation: "chat",
        startTime: Date.now(),
      };

      logMemoryRetrieval(ctx, 5, 0.92);

      expect(mockedLoggerInfo).toHaveBeenCalledWith("v2.trace.memory_retrieval", {
        traceId: "test-trace-id",
        uid: "user-123",
        operation: "chat",
        entryCount: 5,
        topConfidence: 0.92,
      });
    });

    it("handles zero entries retrieved", () => {
      const ctx: TraceContext = {
        traceId: "trace-empty",
        uid: "user-456",
        operation: "parse",
        startTime: Date.now(),
      };

      logMemoryRetrieval(ctx, 0, 0);

      expect(mockedLoggerInfo).toHaveBeenCalledWith("v2.trace.memory_retrieval", {
        traceId: "trace-empty",
        uid: "user-456",
        operation: "parse",
        entryCount: 0,
        topConfidence: 0,
      });
    });

    it("does not log memory text content (Req 20.4)", () => {
      const ctx: TraceContext = {
        traceId: "trace-privacy",
        uid: "user-789",
        operation: "chat",
        startTime: Date.now(),
      };

      logMemoryRetrieval(ctx, 3, 0.85);

      const logCall = mockedLoggerInfo.mock.calls[0];
      const logPayload = logCall[1] as Record<string, unknown>;

      // Ensure no text/content fields are present
      expect(logPayload).not.toHaveProperty("text");
      expect(logPayload).not.toHaveProperty("content");
      expect(logPayload).not.toHaveProperty("memory");
      expect(logPayload).not.toHaveProperty("entries");
    });
  });

  describe("logModelCall", () => {
    it("logs model name, token counts, and latency", () => {
      const ctx: TraceContext = {
        traceId: "trace-model",
        uid: "user-123",
        operation: "parse",
        startTime: Date.now(),
      };

      logModelCall(ctx, "gemini-2.5-flash", 1200, 350, 2450);

      expect(mockedLoggerInfo).toHaveBeenCalledWith("v2.trace.model_call", {
        traceId: "trace-model",
        uid: "user-123",
        operation: "parse",
        model: "gemini-2.5-flash",
        inputTokens: 1200,
        outputTokens: 350,
        latencyMs: 2450,
      });
    });

    it("does not log prompt content or response text (Req 20.4)", () => {
      const ctx: TraceContext = {
        traceId: "trace-no-content",
        uid: "user-456",
        operation: "chat",
        startTime: Date.now(),
      };

      logModelCall(ctx, "claude-sonnet-4", 5000, 1200, 3800);

      const logCall = mockedLoggerInfo.mock.calls[0];
      const logPayload = logCall[1] as Record<string, unknown>;

      expect(logPayload).not.toHaveProperty("prompt");
      expect(logPayload).not.toHaveProperty("response");
      expect(logPayload).not.toHaveProperty("message");
      expect(logPayload).not.toHaveProperty("content");
    });
  });

  describe("logFailure", () => {
    it("logs failure reason and elapsed time", () => {
      const ctx: TraceContext = {
        traceId: "trace-fail",
        uid: "user-123",
        operation: "tool_exec",
        startTime: Date.now(),
      };

      logFailure(ctx, "SCHEMA_VALIDATION_FAILED", 1500);

      expect(mockedLoggerError).toHaveBeenCalledWith("v2.trace.failure", {
        traceId: "trace-fail",
        uid: "user-123",
        operation: "tool_exec",
        reason: "SCHEMA_VALIDATION_FAILED",
        elapsedMs: 1500,
      });
    });

    it("uses error-level logging for failures", () => {
      const ctx: TraceContext = {
        traceId: "trace-err",
        uid: "user-789",
        operation: "chat_continue",
        startTime: Date.now(),
      };

      logFailure(ctx, "MODEL_TIMEOUT", 30000);

      expect(mockedLoggerError).toHaveBeenCalled();
      expect(mockedLoggerInfo).not.toHaveBeenCalled();
    });
  });

  describe("endTrace", () => {
    it("logs success outcome with elapsed time", () => {
      const startTime = Date.now() - 2000; // 2 seconds ago
      const ctx: TraceContext = {
        traceId: "trace-end-success",
        uid: "user-123",
        operation: "parse",
        startTime,
      };

      endTrace(ctx, "success");

      expect(mockedLoggerInfo).toHaveBeenCalledWith(
        "v2.trace.end",
        expect.objectContaining({
          traceId: "trace-end-success",
          uid: "user-123",
          operation: "parse",
          outcome: "success",
        })
      );

      const logPayload = mockedLoggerInfo.mock.calls[0][1] as Record<string, unknown>;
      expect(logPayload.elapsedMs).toBeGreaterThanOrEqual(2000);
    });

    it("logs error outcome", () => {
      const ctx: TraceContext = {
        traceId: "trace-end-error",
        uid: "user-456",
        operation: "chat",
        startTime: Date.now() - 500,
      };

      endTrace(ctx, "error");

      expect(mockedLoggerInfo).toHaveBeenCalledWith(
        "v2.trace.end",
        expect.objectContaining({
          traceId: "trace-end-error",
          uid: "user-456",
          operation: "chat",
          outcome: "error",
        })
      );
    });

    it("computes elapsed time from startTime", () => {
      const startTime = Date.now() - 100;
      const ctx: TraceContext = {
        traceId: "trace-elapsed",
        uid: "user-789",
        operation: "briefing",
        startTime,
      };

      endTrace(ctx, "success");

      const logPayload = mockedLoggerInfo.mock.calls[0][1] as Record<string, unknown>;
      const elapsed = logPayload.elapsedMs as number;
      expect(elapsed).toBeGreaterThanOrEqual(100);
      expect(elapsed).toBeLessThan(5000); // Sanity check
    });
  });
});
