/**
 * Unit tests for PromptFactoryV2 task intent detection and decomposition instructions.
 *
 * Validates Requirements: 25.1, 25.2, 25.3, 25.4, 24.1, 24.2
 */
import { describe, it, expect } from "vitest";
import { buildChatPrompt, WRITE_TOOLS } from "../../src/v2/services/prompt-factory";
import { ChatInput, ModelConfig } from "../../src/v2/models/types";

const DEFAULT_MODEL: ModelConfig = {
  provider: "google",
  modelId: "gemini-2.5-flash",
  displayName: "Gemini Flash",
  costPerMillionTokens: 0.15,
  supportsReasoning: false,
  maxContextWindow: 1048576,
  enabled: true,
  isDefault: true,
};

function buildDefaultChatInput(overrides?: Partial<ChatInput>): ChatInput {
  return {
    message: "I need to buy groceries tomorrow",
    conversationId: "conv-123",
    history: [],
    memories: [],
    mode: "detailed",
    activeModel: DEFAULT_MODEL,
    ...overrides,
  };
}

describe("PromptFactoryV2 — Task Intent Detection", () => {
  describe("Requirement 25.1: Detect task intent in natural conversation", () => {
    it("should include task intent detection instructions in the chat system prompt", () => {
      const result = buildChatPrompt(buildDefaultChatInput());
      expect(result.systemPrompt).toContain("Task Intent Detection");
      expect(result.systemPrompt).toContain("task intent");
    });

    it("should instruct the model to recognize action-oriented language", () => {
      const result = buildChatPrompt(buildDefaultChatInput());
      expect(result.systemPrompt).toContain("I need to");
      expect(result.systemPrompt).toContain("I have to");
      expect(result.systemPrompt).toContain("Remind me to");
    });
  });

  describe("Requirement 25.2: Present extracted details before creating", () => {
    it("should instruct model to present task details for user confirmation", () => {
      const result = buildChatPrompt(buildDefaultChatInput());
      expect(result.systemPrompt).toContain("Present the extracted details");
      expect(result.systemPrompt).toContain("confirmation");
    });

    it("should instruct extraction of title, date, time, and priority", () => {
      const result = buildChatPrompt(buildDefaultChatInput());
      expect(result.systemPrompt).toContain("title");
      expect(result.systemPrompt).toContain("date");
      expect(result.systemPrompt).toContain("time");
      expect(result.systemPrompt).toContain("priority");
    });
  });

  describe("Requirement 25.3: Create task via tool call after confirmation", () => {
    it("should instruct model to propose create_task only after confirmation", () => {
      const result = buildChatPrompt(buildDefaultChatInput());
      expect(result.systemPrompt).toContain("Only after the user confirms");
      expect(result.systemPrompt).toContain("create_task");
    });
  });

  describe("Requirement 25.4: Never auto-create without confirmation", () => {
    it("should explicitly prohibit automatic task creation", () => {
      const result = buildChatPrompt(buildDefaultChatInput());
      expect(result.systemPrompt).toContain("NEVER automatically create tasks without explicit user confirmation");
    });
  });

  describe("Requirement 24.1: Task decomposition for complex tasks", () => {
    it("should include task decomposition section in chat prompt", () => {
      const result = buildChatPrompt(buildDefaultChatInput());
      expect(result.systemPrompt).toContain("Task Decomposition");
    });

    it("should instruct model to identify complex tasks", () => {
      const result = buildChatPrompt(buildDefaultChatInput());
      expect(result.systemPrompt).toContain("complex");
      expect(result.systemPrompt).toContain("multi-step");
      expect(result.systemPrompt).toContain("subtasks");
    });

    it("should instruct model to present subtask suggestions before creating", () => {
      const result = buildChatPrompt(buildDefaultChatInput());
      expect(result.systemPrompt).toContain("Ask the user if they'd like you to create these subtasks");
    });
  });

  describe("Requirement 24.2: Create subtasks via tool calls", () => {
    it("should instruct model to propose multiple create_task calls for subtasks", () => {
      const result = buildChatPrompt(buildDefaultChatInput());
      expect(result.systemPrompt).toContain("multiple create_task tool calls");
    });

    it("should reference isSubtask and parentTaskId fields", () => {
      const result = buildChatPrompt(buildDefaultChatInput());
      expect(result.systemPrompt).toContain("isSubtask");
      expect(result.systemPrompt).toContain("parentTaskId");
    });
  });
});

describe("PromptFactoryV2 — create_task Tool Definition", () => {
  it("should include parentTaskId in create_task parameters", () => {
    const createTask = WRITE_TOOLS.find((t) => t.name === "create_task");
    expect(createTask).toBeDefined();
    expect(createTask!.parameters.properties).toHaveProperty("parentTaskId");
  });

  it("should include isSubtask in create_task parameters", () => {
    const createTask = WRITE_TOOLS.find((t) => t.name === "create_task");
    expect(createTask).toBeDefined();
    expect(createTask!.parameters.properties).toHaveProperty("isSubtask");
  });

  it("should have description mentioning subtask support", () => {
    const createTask = WRITE_TOOLS.find((t) => t.name === "create_task");
    expect(createTask).toBeDefined();
    expect(createTask!.description).toContain("subtask");
  });

  it("should still only require title as mandatory field", () => {
    const createTask = WRITE_TOOLS.find((t) => t.name === "create_task");
    expect(createTask).toBeDefined();
    expect(createTask!.parameters.required).toEqual(["title"]);
  });
});
