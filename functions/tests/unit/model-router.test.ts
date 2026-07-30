/**
 * Unit tests for ModelRouter service.
 *
 * Tests model routing, fallback behavior, error handling, and uniform response format.
 * Validates: Requirements 15.6, 15.7, 15.8
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

// ─── Mock AI Provider SDKs ─────────────────────────────────────────────────────

const mockGoogleGenerateContent = vi.fn();
const mockGoogleGenerateContentStream = vi.fn();

vi.mock("@google/genai", () => {
  const GoogleGenAI = function () {
    return {
      models: {
        generateContent: mockGoogleGenerateContent,
        generateContentStream: mockGoogleGenerateContentStream,
      },
    };
  };
  return { GoogleGenAI, FunctionCallingConfigMode: { ANY: "ANY" } };
});

const mockAnthropicCreate = vi.fn();
vi.mock("@anthropic-ai/sdk", () => {
  const Anthropic = function () {
    return {
      messages: {
        create: mockAnthropicCreate,
        stream: vi.fn(),
      },
    };
  };
  return { default: Anthropic };
});

const mockOpenAICreate = vi.fn();
vi.mock("openai", () => {
  const OpenAI = function () {
    return {
      chat: {
        completions: {
          create: mockOpenAICreate,
        },
      },
    };
  };
  return { default: OpenAI };
});

const mockMistralComplete = vi.fn();
vi.mock("@mistralai/mistralai", () => {
  const Mistral = function () {
    return {
      chat: {
        complete: mockMistralComplete,
        stream: vi.fn(),
      },
    };
  };
  return { Mistral };
});

// ─── Mock Firestore ────────────────────────────────────────────────────────────

const mockFirestoreGet = vi.fn();

vi.mock("firebase-admin/firestore", () => ({
  getFirestore: vi.fn(() => ({
    collection: vi.fn(() => ({
      doc: vi.fn(() => ({
        get: mockFirestoreGet,
      })),
    })),
  })),
}));

// ─── Import module under test (after mocks) ────────────────────────────────────

import {
  resolveModel,
  generate,
  streamGenerate,
  estimateTokens,
  getEnabledModels,
  invalidateConfigCache,
  ModelRouterError,
} from "../../src/v2/services/model-router";
import { ModelConfig, ModelRequest, ModelResponse } from "../../src/v2/models/types";

// ─── Test Helpers ──────────────────────────────────────────────────────────────

function makeModelConfig(overrides: Partial<ModelConfig> = {}): ModelConfig {
  return {
    provider: "google",
    modelId: "gemini-2.5-flash",
    displayName: "Gemini 2.5 Flash",
    costPerMillionTokens: 0.15,
    supportsReasoning: false,
    maxContextWindow: 1000000,
    enabled: true,
    isDefault: true,
    ...overrides,
  };
}

function makeModelRequest(overrides: Partial<ModelRequest> = {}): ModelRequest {
  return {
    systemPrompt: "You are a helpful assistant.",
    messages: [{ role: "user", content: "Hello" }],
    ...overrides,
  };
}

function setupModelsConfig(models: ModelConfig[]) {
  mockFirestoreGet.mockResolvedValue({
    exists: true,
    data: () => ({
      models,
      tierBudgets: { pro_student: 50000, pro_youth: 100000, pro_standard: 200000 },
    }),
  });
}

// ─── Tests ─────────────────────────────────────────────────────────────────────

describe("ModelRouter", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    invalidateConfigCache();
  });

  // ─── resolveModel: Routing to correct provider ────────────────────────────────

  describe("resolveModel — routing to correct provider based on config", () => {
    it("returns the user's preferred model when it is enabled", async () => {
      const gemini = makeModelConfig({ modelId: "gemini-2.5-flash", provider: "google", isDefault: true });
      const claude = makeModelConfig({ modelId: "claude-sonnet-4", provider: "anthropic", isDefault: false });
      setupModelsConfig([gemini, claude]);

      const resolved = await resolveModel("claude-sonnet-4");

      expect(resolved.modelId).toBe("claude-sonnet-4");
      expect(resolved.provider).toBe("anthropic");
    });

    it("returns the default model when no preferred model is specified", async () => {
      const gemini = makeModelConfig({ modelId: "gemini-2.5-flash", provider: "google", isDefault: true });
      const claude = makeModelConfig({ modelId: "claude-sonnet-4", provider: "anthropic", isDefault: false });
      setupModelsConfig([gemini, claude]);

      const resolved = await resolveModel();

      expect(resolved.modelId).toBe("gemini-2.5-flash");
      expect(resolved.isDefault).toBe(true);
    });

    it("returns the default model when preferred model is undefined", async () => {
      const gemini = makeModelConfig({ modelId: "gemini-2.5-flash", provider: "google", isDefault: true });
      setupModelsConfig([gemini]);

      const resolved = await resolveModel(undefined);

      expect(resolved.modelId).toBe("gemini-2.5-flash");
    });

    it("resolves google provider correctly", async () => {
      const model = makeModelConfig({ provider: "google", modelId: "gemini-2.5-pro" });
      setupModelsConfig([model]);

      const resolved = await resolveModel("gemini-2.5-pro");
      expect(resolved.provider).toBe("google");
    });

    it("resolves anthropic provider correctly", async () => {
      const model = makeModelConfig({ provider: "anthropic", modelId: "claude-sonnet-4", isDefault: true });
      setupModelsConfig([model]);

      const resolved = await resolveModel("claude-sonnet-4");
      expect(resolved.provider).toBe("anthropic");
    });

    it("resolves openai provider correctly", async () => {
      const model = makeModelConfig({ provider: "openai", modelId: "gpt-4o", isDefault: true });
      setupModelsConfig([model]);

      const resolved = await resolveModel("gpt-4o");
      expect(resolved.provider).toBe("openai");
    });

    it("resolves mistral provider correctly", async () => {
      const model = makeModelConfig({ provider: "mistral", modelId: "mistral-large", isDefault: true });
      setupModelsConfig([model]);

      const resolved = await resolveModel("mistral-large");
      expect(resolved.provider).toBe("mistral");
    });
  });

  // ─── resolveModel: Fallback to default (Req 15.6) ────────────────────────────

  describe("resolveModel — fallback to default model when user's selected model is disabled (Req 15.6)", () => {
    it("falls back to default when preferred model is disabled", async () => {
      const gemini = makeModelConfig({ modelId: "gemini-2.5-flash", provider: "google", isDefault: true });
      const claude = makeModelConfig({ modelId: "claude-sonnet-4", provider: "anthropic", isDefault: false, enabled: false });
      setupModelsConfig([gemini, claude]);

      const resolved = await resolveModel("claude-sonnet-4");

      expect(resolved.modelId).toBe("gemini-2.5-flash");
      expect(resolved.isDefault).toBe(true);
    });

    it("falls back to default when preferred model does not exist in registry", async () => {
      const gemini = makeModelConfig({ modelId: "gemini-2.5-flash", provider: "google", isDefault: true });
      setupModelsConfig([gemini]);

      const resolved = await resolveModel("nonexistent-model-id");

      expect(resolved.modelId).toBe("gemini-2.5-flash");
    });

    it("falls back to first enabled model when no default is explicitly set", async () => {
      const openai = makeModelConfig({ modelId: "gpt-4o", provider: "openai", isDefault: false });
      const mistral = makeModelConfig({ modelId: "mistral-large", provider: "mistral", isDefault: false });
      setupModelsConfig([openai, mistral]);

      const resolved = await resolveModel("nonexistent-model");

      expect(resolved.modelId).toBe("gpt-4o");
    });

    it("falls back correctly when preferred model was removed entirely", async () => {
      const gemini = makeModelConfig({ modelId: "gemini-2.5-flash", provider: "google", isDefault: true });
      const mistral = makeModelConfig({ modelId: "mistral-large", provider: "mistral", isDefault: false });
      setupModelsConfig([gemini, mistral]);

      // User had "claude-sonnet-4" selected but it was removed from registry
      const resolved = await resolveModel("claude-sonnet-4");

      expect(resolved.modelId).toBe("gemini-2.5-flash");
    });
  });

  // ─── resolveModel: Error when all models disabled (Req 15.7) ──────────────────

  describe("resolveModel — error when all models disabled (Req 15.7)", () => {
    it("throws 503 ModelRouterError when all models are disabled", async () => {
      const gemini = makeModelConfig({ modelId: "gemini-2.5-flash", enabled: false });
      const claude = makeModelConfig({ modelId: "claude-sonnet-4", enabled: false });
      setupModelsConfig([gemini, claude]);

      await expect(resolveModel()).rejects.toThrow(ModelRouterError);
      await expect(resolveModel()).rejects.toThrow("No AI models are currently available");

      try {
        await resolveModel();
      } catch (err) {
        expect(err).toBeInstanceOf(ModelRouterError);
        expect((err as ModelRouterError).statusCode).toBe(503);
      }
    });

    it("throws 503 when models array is empty", async () => {
      setupModelsConfig([]);

      await expect(resolveModel()).rejects.toThrow(ModelRouterError);

      try {
        await resolveModel();
      } catch (err) {
        expect((err as ModelRouterError).statusCode).toBe(503);
      }
    });

    it("throws 503 when preferred model is disabled and no other models enabled", async () => {
      const claude = makeModelConfig({ modelId: "claude-sonnet-4", enabled: false, isDefault: false });
      setupModelsConfig([claude]);

      await expect(resolveModel("claude-sonnet-4")).rejects.toThrow(ModelRouterError);

      try {
        await resolveModel("claude-sonnet-4");
      } catch (err) {
        expect((err as ModelRouterError).statusCode).toBe(503);
      }
    });

    it("throws 503 when config document does not exist", async () => {
      mockFirestoreGet.mockResolvedValue({ exists: false, data: () => null });

      await expect(resolveModel()).rejects.toThrow(ModelRouterError);

      try {
        await resolveModel();
      } catch (err) {
        expect((err as ModelRouterError).statusCode).toBe(503);
      }
    });
  });

  // ─── generate: Uniform response format (Req 15.8) ────────────────────────────

  describe("generate — uniform response format across providers (Req 15.8)", () => {
    it("returns uniform ModelResponse from Google GenAI", async () => {
      mockGoogleGenerateContent.mockResolvedValue({
        text: "Hello from Gemini!",
        usageMetadata: { promptTokenCount: 10, candidatesTokenCount: 5 },
        functionCalls: undefined,
      });

      const config = makeModelConfig({ provider: "google", modelId: "gemini-2.5-flash" });
      const request = makeModelRequest();

      const response = await generate(request, config);

      assertUniformResponse(response);
      expect(response.text).toBe("Hello from Gemini!");
      expect(response.inputTokens).toBe(10);
      expect(response.outputTokens).toBe(5);
      expect(response.finishReason).toBe("stop");
      expect(response.toolCalls).toBeUndefined();
    });

    it("returns uniform ModelResponse from Anthropic", async () => {
      mockAnthropicCreate.mockResolvedValue({
        content: [{ type: "text", text: "Hello from Claude!" }],
        usage: { input_tokens: 12, output_tokens: 8 },
        stop_reason: "end_turn",
      });

      const config = makeModelConfig({ provider: "anthropic", modelId: "claude-sonnet-4" });
      const request = makeModelRequest();

      const response = await generate(request, config);

      assertUniformResponse(response);
      expect(response.text).toBe("Hello from Claude!");
      expect(response.inputTokens).toBe(12);
      expect(response.outputTokens).toBe(8);
      expect(response.finishReason).toBe("stop");
    });

    it("returns uniform ModelResponse from OpenAI", async () => {
      mockOpenAICreate.mockResolvedValue({
        choices: [
          {
            message: { content: "Hello from GPT!", tool_calls: undefined },
            finish_reason: "stop",
          },
        ],
        usage: { prompt_tokens: 15, completion_tokens: 6 },
      });

      const config = makeModelConfig({ provider: "openai", modelId: "gpt-4o" });
      const request = makeModelRequest();

      const response = await generate(request, config);

      assertUniformResponse(response);
      expect(response.text).toBe("Hello from GPT!");
      expect(response.inputTokens).toBe(15);
      expect(response.outputTokens).toBe(6);
      expect(response.finishReason).toBe("stop");
    });

    it("returns uniform ModelResponse from Mistral", async () => {
      mockMistralComplete.mockResolvedValue({
        choices: [
          {
            message: { content: "Hello from Mistral!", toolCalls: undefined },
            finishReason: "stop",
          },
        ],
        usage: { promptTokens: 11, completionTokens: 7 },
      });

      const config = makeModelConfig({ provider: "mistral", modelId: "mistral-large" });
      const request = makeModelRequest();

      const response = await generate(request, config);

      assertUniformResponse(response);
      expect(response.text).toBe("Hello from Mistral!");
      expect(response.inputTokens).toBe(11);
      expect(response.outputTokens).toBe(7);
      expect(response.finishReason).toBe("stop");
    });

    it("all providers return same shape with tool calls", async () => {
      // Google with tool calls
      mockGoogleGenerateContent.mockResolvedValue({
        text: "",
        usageMetadata: { promptTokenCount: 20, candidatesTokenCount: 10 },
        functionCalls: [{ name: "get_today_tasks", args: {} }],
      });

      const googleConfig = makeModelConfig({ provider: "google", modelId: "gemini-2.5-flash" });
      const request = makeModelRequest({
        tools: [{ name: "get_today_tasks", description: "Get today tasks", category: "read", parameters: { type: "object" } }],
      });

      const googleResponse = await generate(request, googleConfig);

      assertUniformResponse(googleResponse);
      expect(googleResponse.toolCalls).toBeDefined();
      expect(googleResponse.toolCalls!.length).toBeGreaterThan(0);
      expect(googleResponse.toolCalls![0].name).toBe("get_today_tasks");
      expect(googleResponse.finishReason).toBe("tool_calls");
    });

    it("throws ModelRouterError for unsupported provider", async () => {
      const config = makeModelConfig({ provider: "unknown" as ModelConfig["provider"] });
      const request = makeModelRequest();

      await expect(generate(request, config)).rejects.toThrow(ModelRouterError);

      try {
        await generate(request, config);
      } catch (err) {
        expect((err as ModelRouterError).statusCode).toBe(400);
      }
    });

    it("includes thinkingText from Anthropic when reasoning is provided", async () => {
      mockAnthropicCreate.mockResolvedValue({
        content: [
          { type: "thinking", thinking: "Let me think about this..." },
          { type: "text", text: "Here's my answer." },
        ],
        usage: { input_tokens: 20, output_tokens: 15 },
        stop_reason: "end_turn",
      });

      const config = makeModelConfig({ provider: "anthropic", modelId: "claude-sonnet-4", supportsReasoning: true });
      const request = makeModelRequest();

      const response = await generate(request, config);

      assertUniformResponse(response);
      expect(response.thinkingText).toBe("Let me think about this...");
      expect(response.text).toBe("Here's my answer.");
    });

    it("handles empty text response gracefully", async () => {
      mockGoogleGenerateContent.mockResolvedValue({
        text: undefined,
        usageMetadata: { promptTokenCount: 5, candidatesTokenCount: 0 },
        functionCalls: undefined,
      });

      const config = makeModelConfig({ provider: "google" });
      const request = makeModelRequest();

      const response = await generate(request, config);

      assertUniformResponse(response);
      expect(response.text).toBe("");
    });
  });

  // ─── streamGenerate: Error handling ───────────────────────────────────────────

  describe("streamGenerate — error handling", () => {
    it("yields error chunk for unsupported provider", async () => {
      const config = makeModelConfig({ provider: "unknown" as ModelConfig["provider"] });
      const request = makeModelRequest();

      const chunks: Array<{ type: string; error?: string }> = [];
      for await (const chunk of streamGenerate(request, config)) {
        chunks.push(chunk);
      }

      expect(chunks).toHaveLength(1);
      expect(chunks[0].type).toBe("error");
      expect(chunks[0].error).toContain("Unsupported provider");
    });
  });

  // ─── estimateTokens ──────────────────────────────────────────────────────────

  describe("estimateTokens", () => {
    it("estimates non-CJK text at roughly 4 chars per token", () => {
      const model = makeModelConfig();
      const tokens = estimateTokens("Hello world, this is a test!", model);

      // 28 chars / 4 = 7 tokens
      expect(tokens).toBe(7);
    });

    it("estimates CJK text at roughly 2 chars per token", () => {
      const model = makeModelConfig();
      const tokens = estimateTokens("こんにちは世界", model);

      // 7 CJK chars / 2 = 3.5 -> ceil = 4
      expect(tokens).toBe(4);
    });

    it("handles mixed CJK and non-CJK text", () => {
      const model = makeModelConfig();
      const tokens = estimateTokens("Hello こんにちは", model);

      // 6 non-CJK chars / 4 = 1.5, 5 CJK chars / 2 = 2.5 -> ceil(4) = 4
      expect(tokens).toBe(4);
    });

    it("returns 0 for empty text", () => {
      const model = makeModelConfig();
      const tokens = estimateTokens("", model);
      expect(tokens).toBe(0);
    });

    it("returns at least 1 for non-empty text", () => {
      const model = makeModelConfig();
      const tokens = estimateTokens("a", model);
      expect(tokens).toBeGreaterThanOrEqual(1);
    });
  });

  // ─── getEnabledModels ────────────────────────────────────────────────────────

  describe("getEnabledModels", () => {
    it("returns only enabled models", async () => {
      const enabled1 = makeModelConfig({ modelId: "model-a", enabled: true });
      const disabled = makeModelConfig({ modelId: "model-b", enabled: false });
      const enabled2 = makeModelConfig({ modelId: "model-c", enabled: true, isDefault: false });
      setupModelsConfig([enabled1, disabled, enabled2]);

      const result = await getEnabledModels();

      expect(result).toHaveLength(2);
      expect(result.map((m) => m.modelId)).toEqual(["model-a", "model-c"]);
    });

    it("returns empty array when all models are disabled", async () => {
      const disabled1 = makeModelConfig({ modelId: "model-a", enabled: false });
      const disabled2 = makeModelConfig({ modelId: "model-b", enabled: false });
      setupModelsConfig([disabled1, disabled2]);

      const result = await getEnabledModels();

      expect(result).toHaveLength(0);
    });
  });

  // ─── Config caching ──────────────────────────────────────────────────────────

  describe("config caching", () => {
    it("caches config and does not hit Firestore on repeated calls within TTL", async () => {
      const model = makeModelConfig();
      setupModelsConfig([model]);

      await resolveModel();
      await resolveModel();
      await resolveModel();

      // Should only call Firestore once due to caching
      expect(mockFirestoreGet).toHaveBeenCalledTimes(1);
    });

    it("invalidateConfigCache forces re-read from Firestore", async () => {
      const model = makeModelConfig();
      setupModelsConfig([model]);

      await resolveModel();
      expect(mockFirestoreGet).toHaveBeenCalledTimes(1);

      invalidateConfigCache();
      await resolveModel();

      expect(mockFirestoreGet).toHaveBeenCalledTimes(2);
    });
  });
});

// ─── Assertion Helpers ─────────────────────────────────────────────────────────

/**
 * Asserts that a response conforms to the uniform ModelResponse shape (Req 15.8).
 */
function assertUniformResponse(response: ModelResponse) {
  expect(response).toBeDefined();
  expect(typeof response.text).toBe("string");
  expect(typeof response.inputTokens).toBe("number");
  expect(typeof response.outputTokens).toBe("number");
  expect(response.inputTokens).toBeGreaterThanOrEqual(0);
  expect(response.outputTokens).toBeGreaterThanOrEqual(0);
  expect(["stop", "tool_calls", "length", "error"]).toContain(response.finishReason);

  // toolCalls should be undefined or a non-empty array
  if (response.toolCalls !== undefined) {
    expect(Array.isArray(response.toolCalls)).toBe(true);
    expect(response.toolCalls.length).toBeGreaterThan(0);
    for (const tc of response.toolCalls) {
      expect(typeof tc.name).toBe("string");
      expect(["read", "write"]).toContain(tc.category);
      expect(typeof tc.args).toBe("object");
    }
  }

  // thinkingText should be undefined or a string
  if (response.thinkingText !== undefined) {
    expect(typeof response.thinkingText).toBe("string");
  }
}
