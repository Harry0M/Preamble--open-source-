/**
 * Unit tests for StructuredOutputValidator.
 *
 * Tests validateParseResponse(), validateChatResponse(), and validateToolCallResult()
 * with both valid and invalid inputs.
 */
import { describe, it, expect } from "vitest";
import {
  validateParseResponse,
  validateChatResponse,
  validateToolCallResult,
} from "../../src/v2/services/structured-output-validator";

describe("StructuredOutputValidator", () => {
  describe("validateParseResponse", () => {
    it("accepts a minimal valid parse response", () => {
      const input = { title: "Buy groceries", confidence: 0.9 };
      const result = validateParseResponse(input);

      expect(result.valid).toBe(true);
      if (result.valid) {
        expect(result.data.title).toBe("Buy groceries");
        expect(result.data.confidence).toBe(0.9);
      }
    });

    it("accepts a full parse response with all optional fields", () => {
      const input = {
        title: "Morning gym session",
        confidence: 0.95,
        date: "2025-01-20",
        time: "07:00",
        priority: 2,
        tags: ["fitness", "health"],
        recurrence: {
          recurrenceType: "daily",
          recurrenceInterval: 1,
        },
        description: "Do strength training at the gym",
        detectedLanguage: "en",
        duplicates: [
          { taskId: "abc-123", similarity: 0.9, title: "Go to gym" },
        ],
      };
      const result = validateParseResponse(input);

      expect(result.valid).toBe(true);
      if (result.valid) {
        expect(result.data.title).toBe("Morning gym session");
        expect(result.data.date).toBe("2025-01-20");
        expect(result.data.time).toBe("07:00");
        expect(result.data.priority).toBe(2);
        expect(result.data.tags).toEqual(["fitness", "health"]);
        expect(result.data.recurrence?.recurrenceType).toBe("daily");
        expect(result.data.description).toBe("Do strength training at the gym");
        expect(result.data.detectedLanguage).toBe("en");
        expect(result.data.duplicates).toHaveLength(1);
      }
    });

    it("rejects when title is missing", () => {
      const input = { confidence: 0.8 };
      const result = validateParseResponse(input);

      expect(result.valid).toBe(false);
      if (!result.valid) {
        expect(result.errors.length).toBeGreaterThan(0);
        expect(result.errors.some((e) => e.keyword === "required")).toBe(true);
      }
    });

    it("rejects when confidence is missing", () => {
      const input = { title: "Test task" };
      const result = validateParseResponse(input);

      expect(result.valid).toBe(false);
      if (!result.valid) {
        expect(result.errors.some((e) => e.keyword === "required")).toBe(true);
      }
    });

    it("rejects when confidence is out of range", () => {
      const input = { title: "Test", confidence: 1.5 };
      const result = validateParseResponse(input);

      expect(result.valid).toBe(false);
      if (!result.valid) {
        expect(result.errors.some((e) => e.keyword === "maximum")).toBe(true);
      }
    });

    it("rejects when priority is out of range", () => {
      const input = { title: "Test", confidence: 0.5, priority: 5 };
      const result = validateParseResponse(input);

      expect(result.valid).toBe(false);
      if (!result.valid) {
        expect(result.errors.some((e) => e.keyword === "maximum")).toBe(true);
      }
    });

    it("rejects when date format is invalid", () => {
      const input = { title: "Test", confidence: 0.5, date: "2025/01/20" };
      const result = validateParseResponse(input);

      expect(result.valid).toBe(false);
      if (!result.valid) {
        expect(result.errors.some((e) => e.keyword === "pattern")).toBe(true);
      }
    });

    it("rejects when time format is invalid", () => {
      const input = { title: "Test", confidence: 0.5, time: "25:00" };
      const result = validateParseResponse(input);

      expect(result.valid).toBe(false);
      if (!result.valid) {
        expect(result.errors.some((e) => e.keyword === "pattern")).toBe(true);
      }
    });

    it("rejects when tags exceed maxItems", () => {
      const input = {
        title: "Test",
        confidence: 0.5,
        tags: Array.from({ length: 11 }, (_, i) => `tag${i}`),
      };
      const result = validateParseResponse(input);

      expect(result.valid).toBe(false);
      if (!result.valid) {
        expect(result.errors.some((e) => e.keyword === "maxItems")).toBe(true);
      }
    });

    it("rejects when additional properties are present", () => {
      const input = { title: "Test", confidence: 0.5, unknownField: "oops" };
      const result = validateParseResponse(input);

      expect(result.valid).toBe(false);
      if (!result.valid) {
        expect(
          result.errors.some((e) => e.keyword === "additionalProperties")
        ).toBe(true);
      }
    });

    it("rejects null input", () => {
      const result = validateParseResponse(null);
      expect(result.valid).toBe(false);
    });

    it("rejects non-object input", () => {
      const result = validateParseResponse("string input");
      expect(result.valid).toBe(false);
    });
  });

  describe("validateChatResponse", () => {
    it("accepts a minimal valid chat response", () => {
      const input = { content: "Hello! How can I help you today?" };
      const result = validateChatResponse(input);

      expect(result.valid).toBe(true);
      if (result.valid) {
        expect(result.data.content).toBe("Hello! How can I help you today?");
      }
    });

    it("accepts a chat response with tool calls", () => {
      const input = {
        content: "I'll create that task for you.",
        toolCalls: [
          {
            name: "create_task",
            category: "write",
            description: "Create a new task",
            targetData: "tasks",
            args: { title: "Buy milk", priority: 2 },
          },
        ],
      };
      const result = validateChatResponse(input);

      expect(result.valid).toBe(true);
      if (result.valid) {
        expect(result.data.toolCalls).toHaveLength(1);
        expect(result.data.toolCalls![0].name).toBe("create_task");
      }
    });

    it("accepts a chat response with thinking and citations", () => {
      const input = {
        content: "Based on my research...",
        thinking: "The user wants to know about X",
        citations: [
          {
            title: "Wikipedia",
            url: "https://en.wikipedia.org/wiki/Test",
            snippet: "A brief explanation",
          },
        ],
      };
      const result = validateChatResponse(input);

      expect(result.valid).toBe(true);
      if (result.valid) {
        expect(result.data.thinking).toBe("The user wants to know about X");
        expect(result.data.citations).toHaveLength(1);
      }
    });

    it("accepts a chat response with memory updates", () => {
      const input = {
        content: "Got it, I'll remember that.",
        memoryUpdates: [
          { op: "upsert", text: "User prefers morning tasks", category: "preference" },
        ],
      };
      const result = validateChatResponse(input);

      expect(result.valid).toBe(true);
      if (result.valid) {
        expect(result.data.memoryUpdates).toHaveLength(1);
        expect(result.data.memoryUpdates![0].op).toBe("upsert");
      }
    });

    it("rejects when content is missing", () => {
      const input = { thinking: "Some thought" };
      const result = validateChatResponse(input);

      expect(result.valid).toBe(false);
      if (!result.valid) {
        expect(result.errors.some((e) => e.keyword === "required")).toBe(true);
      }
    });

    it("rejects when tool call category is invalid", () => {
      const input = {
        content: "test",
        toolCalls: [
          {
            name: "create_task",
            category: "delete",
            description: "bad",
            args: {},
          },
        ],
      };
      const result = validateChatResponse(input);

      expect(result.valid).toBe(false);
    });

    it("rejects when additional properties are present", () => {
      const input = { content: "test", foo: "bar" };
      const result = validateChatResponse(input);

      expect(result.valid).toBe(false);
    });
  });

  describe("validateToolCallResult", () => {
    it("accepts a minimal valid tool call result", () => {
      const input = { toolName: "get_today_tasks", success: true };
      const result = validateToolCallResult(input);

      expect(result.valid).toBe(true);
      if (result.valid) {
        expect(result.data.toolName).toBe("get_today_tasks");
        expect(result.data.success).toBe(true);
      }
    });

    it("accepts a tool call result with data", () => {
      const input = {
        toolName: "get_today_tasks",
        success: true,
        data: { tasks: [{ title: "Buy milk" }] },
        executedAt: 1720000000000,
        traceId: "550e8400-e29b-41d4-a716-446655440000",
      };
      const result = validateToolCallResult(input);

      expect(result.valid).toBe(true);
    });

    it("accepts a failed tool call result with error", () => {
      const input = {
        toolName: "create_task",
        success: false,
        error: { code: "PERMISSION_DENIED", message: "Not authorized" },
        executedAt: 1720000000000,
        traceId: "550e8400-e29b-41d4-a716-446655440000",
      };
      const result = validateToolCallResult(input);

      expect(result.valid).toBe(true);
    });

    it("rejects when toolName is missing", () => {
      const input = { success: true };
      const result = validateToolCallResult(input);

      expect(result.valid).toBe(false);
      if (!result.valid) {
        expect(result.errors.some((e) => e.keyword === "required")).toBe(true);
      }
    });

    it("rejects when success is missing", () => {
      const input = { toolName: "test" };
      const result = validateToolCallResult(input);

      expect(result.valid).toBe(false);
      if (!result.valid) {
        expect(result.errors.some((e) => e.keyword === "required")).toBe(true);
      }
    });

    it("rejects when additional properties are present", () => {
      const input = { toolName: "test", success: true, badField: "oops" };
      const result = validateToolCallResult(input);

      expect(result.valid).toBe(false);
    });

    it("rejects non-object input", () => {
      const result = validateToolCallResult(42);
      expect(result.valid).toBe(false);
    });
  });
});
