/**
 * ModelRouter — Multi-model abstraction layer for AI V2 Ecosystem.
 *
 * Routes requests to configured AI providers (Google GenAI, Anthropic, OpenAI, Mistral)
 * behind a uniform interface. Reads model configuration from Firestore and handles
 * provider-specific request/response translation.
 *
 * Requirements: 15.3, 15.6, 15.7, 15.8
 */

import { getFirestore } from "firebase-admin/firestore";
import { GoogleGenAI, FunctionCallingConfigMode } from "@google/genai";
import Anthropic from "@anthropic-ai/sdk";
import OpenAI from "openai";
import { Mistral } from "@mistralai/mistralai";
import {
  ModelConfig,
  ModelRequest,
  ModelResponse,
  StreamChunk,
} from "../models/types";

// ─── Types ─────────────────────────────────────────────────────────────────────

/**
 * Firestore document shape for v2_config/models.
 */
interface ModelsConfigDoc {
  models: ModelConfig[];
  tierBudgets: Record<string, number>;
}

/**
 * Provider adapter interface — each provider implements this.
 */
interface ProviderAdapter {
  generate(request: ModelRequest, config: ModelConfig): Promise<ModelResponse>;
  streamGenerate(
    request: ModelRequest,
    config: ModelConfig
  ): AsyncIterable<StreamChunk>;
}

// ─── Config Cache ──────────────────────────────────────────────────────────────

let cachedConfig: ModelsConfigDoc | null = null;
let configCacheTime = 0;
const CONFIG_CACHE_TTL_MS = 60_000; // 1 minute cache

/**
 * Reads model configuration from Firestore v2_config/models.
 * Caches for 1 minute to reduce Firestore reads.
 */
async function getModelsConfig(): Promise<ModelsConfigDoc> {
  const now = Date.now();
  if (cachedConfig && now - configCacheTime < CONFIG_CACHE_TTL_MS) {
    return cachedConfig;
  }

  const db = getFirestore("preamble");
  const doc = await db.collection("v2_config").doc("models").get();

  if (!doc.exists) {
    throw new ModelRouterError(
      "Model configuration not found in Firestore",
      503
    );
  }

  cachedConfig = doc.data() as ModelsConfigDoc;
  configCacheTime = now;
  return cachedConfig;
}

// ─── Error Class ───────────────────────────────────────────────────────────────

export class ModelRouterError extends Error {
  constructor(
    message: string,
    public readonly statusCode: number
  ) {
    super(message);
    this.name = "ModelRouterError";
  }
}

// ─── Provider Adapters ─────────────────────────────────────────────────────────

/**
 * Google GenAI (Gemini) adapter.
 */
const googleAdapter: ProviderAdapter = {
  async generate(
    request: ModelRequest,
    config: ModelConfig
  ): Promise<ModelResponse> {
    const client = new GoogleGenAI({
      apiKey: process.env.GOOGLE_GENAI_API_KEY || "",
    });

    const contents = buildGoogleContents(request);

    const response = await client.models.generateContent({
      model: config.modelId,
      contents,
      config: {
        systemInstruction: request.systemPrompt,
        temperature: request.temperature,
        maxOutputTokens: request.maxOutputTokens,
        ...(request.responseSchema
          ? {
              responseMimeType: "application/json",
              responseSchema: request.responseSchema as Record<string, unknown>,
            }
          : {}),
        ...(request.tools && request.tools.length > 0
          ? {
              tools: [
                {
                  functionDeclarations: request.tools.map((t) => ({
                    name: t.name,
                    description: t.description,
                    parameters: t.parameters as Record<string, unknown>,
                  })),
                },
              ],
              ...(request.forceToolCall
                ? { toolConfig: { functionCallingConfig: { mode: FunctionCallingConfigMode.ANY } } }
                : {}),
            }
          : {}),
      },
    });

    const text = response.text ?? "";
    const inputTokens = response.usageMetadata?.promptTokenCount ?? 0;
    const outputTokens = response.usageMetadata?.candidatesTokenCount ?? 0;

    const toolCalls = response.functionCalls?.map((fc) => ({
      name: fc.name ?? "",
      category: "read" as const,
      description: "",
      targetData: "",
      args: (fc.args as Record<string, unknown>) ?? {},
    }));

    return {
      text,
      toolCalls: toolCalls && toolCalls.length > 0 ? toolCalls : undefined,
      thinkingText: undefined,
      inputTokens,
      outputTokens,
      finishReason: toolCalls && toolCalls.length > 0 ? "tool_calls" : "stop",
    };
  },

  async *streamGenerate(
    request: ModelRequest,
    config: ModelConfig
  ): AsyncIterable<StreamChunk> {
    const client = new GoogleGenAI({
      apiKey: process.env.GOOGLE_GENAI_API_KEY || "",
    });

    const contents = buildGoogleContents(request);

    const response = await client.models.generateContentStream({
      model: config.modelId,
      contents,
      config: {
        systemInstruction: request.systemPrompt,
        temperature: request.temperature,
        maxOutputTokens: request.maxOutputTokens,
        ...(request.responseSchema
          ? {
              responseMimeType: "application/json",
              responseSchema: request.responseSchema as Record<string, unknown>,
            }
          : {}),
        ...(request.tools && request.tools.length > 0
          ? {
              tools: [
                {
                  functionDeclarations: request.tools.map((t) => ({
                    name: t.name,
                    description: t.description,
                    parameters: t.parameters as Record<string, unknown>,
                  })),
                },
              ],
              ...(request.forceToolCall
                ? { toolConfig: { functionCallingConfig: { mode: FunctionCallingConfigMode.ANY } } }
                : {}),
            }
          : {}),
      },
    });

    for await (const chunk of response) {
      const text = chunk.text ?? "";
      const functionCalls = chunk.functionCalls;

      if (functionCalls && functionCalls.length > 0) {
        yield {
          type: "tool_calls",
          toolCalls: functionCalls.map((fc) => ({
            name: fc.name ?? "",
            category: "read" as const,
            description: "",
            targetData: "",
            args: (fc.args as Record<string, unknown>) ?? {},
          })),
        };
      } else if (text) {
        yield { type: "delta", text };
      }
    }

    yield {
      type: "done",
      finishReason: "stop",
    };
  },
};

/**
 * Anthropic (Claude) adapter.
 */
const anthropicAdapter: ProviderAdapter = {
  async generate(
    request: ModelRequest,
    config: ModelConfig
  ): Promise<ModelResponse> {
    const client = new Anthropic({
      apiKey: process.env.ANTHROPIC_API_KEY || "",
    });

    const messages = buildAnthropicMessages(request);

    const response = await client.messages.create({
      model: config.modelId,
      max_tokens: request.maxOutputTokens ?? 4096,
      system: request.systemPrompt,
      messages,
      ...(request.temperature !== undefined
        ? { temperature: request.temperature }
        : {}),
      ...(request.tools && request.tools.length > 0
        ? {
            tools: request.tools.map((t) => ({
              name: t.name,
              description: t.description,
              input_schema: t.parameters as Anthropic.Tool.InputSchema,
            })),
            ...(request.forceToolCall
              ? { tool_choice: { type: "any" as const } }
              : {}),
          }
        : {}),
    });

    let text = "";
    let thinkingText: string | undefined;
    const toolCalls: ModelResponse["toolCalls"] = [];

    for (const block of response.content) {
      if (block.type === "text") {
        text += block.text;
      } else if (block.type === "thinking") {
        thinkingText = (thinkingText ?? "") + block.thinking;
      } else if (block.type === "tool_use") {
        toolCalls.push({
          name: block.name,
          category: "read",
          description: "",
          targetData: "",
          args: (block.input as Record<string, unknown>) ?? {},
        });
      }
    }

    const finishReason =
      response.stop_reason === "tool_use"
        ? "tool_calls"
        : response.stop_reason === "max_tokens"
          ? "length"
          : "stop";

    return {
      text,
      toolCalls: toolCalls.length > 0 ? toolCalls : undefined,
      thinkingText,
      inputTokens: response.usage.input_tokens,
      outputTokens: response.usage.output_tokens,
      finishReason,
    };
  },

  async *streamGenerate(
    request: ModelRequest,
    config: ModelConfig
  ): AsyncIterable<StreamChunk> {
    const client = new Anthropic({
      apiKey: process.env.ANTHROPIC_API_KEY || "",
    });

    const messages = buildAnthropicMessages(request);

    const stream = client.messages.stream({
      model: config.modelId,
      max_tokens: request.maxOutputTokens ?? 4096,
      system: request.systemPrompt,
      messages,
      ...(request.temperature !== undefined
        ? { temperature: request.temperature }
        : {}),
      ...(request.tools && request.tools.length > 0
        ? {
            tools: request.tools.map((t) => ({
              name: t.name,
              description: t.description,
              input_schema: t.parameters as Anthropic.Tool.InputSchema,
            })),
            ...(request.forceToolCall
              ? { tool_choice: { type: "any" as const } }
              : {}),
          }
        : {}),
    });

    for await (const event of stream) {
      if (
        event.type === "content_block_delta" &&
        event.delta.type === "text_delta"
      ) {
        yield { type: "delta", text: event.delta.text };
      } else if (
        event.type === "content_block_delta" &&
        event.delta.type === "thinking_delta"
      ) {
        yield { type: "thinking", text: event.delta.thinking };
      }
    }

    const finalMessage = await stream.finalMessage();
    const toolCalls = finalMessage.content
      .filter(
        (b: Anthropic.ContentBlock): b is Anthropic.ToolUseBlock =>
          b.type === "tool_use"
      )
      .map((b: Anthropic.ToolUseBlock) => ({
        name: b.name,
        category: "read" as const,
        description: "",
        targetData: "",
        args: (b.input as Record<string, unknown>) ?? {},
      }));

    if (toolCalls.length > 0) {
      yield { type: "tool_calls", toolCalls };
    }

    yield {
      type: "done",
      inputTokens: finalMessage.usage.input_tokens,
      outputTokens: finalMessage.usage.output_tokens,
      finishReason:
        finalMessage.stop_reason === "tool_use" ? "tool_calls" : "stop",
    };
  },
};

/**
 * OpenAI (GPT) adapter.
 */
const openaiAdapter: ProviderAdapter = {
  async generate(
    request: ModelRequest,
    config: ModelConfig
  ): Promise<ModelResponse> {
    const client = new OpenAI({
      apiKey: process.env.OPENAI_API_KEY || "",
    });

    const messages = buildOpenAIMessages(request);

    const response = await client.chat.completions.create({
      model: config.modelId,
      messages,
      ...(request.temperature !== undefined
        ? { temperature: request.temperature }
        : {}),
      ...(request.maxOutputTokens
        ? { max_tokens: request.maxOutputTokens }
        : {}),
      ...(request.responseSchema
        ? {
            response_format: {
              type: "json_schema" as const,
              json_schema: {
                name: "response",
                schema: request.responseSchema as Record<string, unknown>,
                strict: true,
              },
            },
          }
        : {}),
      ...(request.tools && request.tools.length > 0
        ? {
            tools: request.tools.map((t) => ({
              type: "function" as const,
              function: {
                name: t.name,
                description: t.description,
                parameters: t.parameters as Record<string, unknown>,
              },
            })),
            ...(request.forceToolCall
              ? { tool_choice: "required" as const }
              : {}),
          }
        : {}),
    });

    const choice = response.choices[0];
    const text = choice?.message?.content ?? "";
    const toolCallsRaw = choice?.message?.tool_calls;

    const toolCalls = toolCallsRaw?.map(
      (tc: OpenAI.Chat.ChatCompletionMessageToolCall) => ({
        name: tc.function.name,
        category: "read" as const,
        description: "",
        targetData: "",
        args: JSON.parse(tc.function.arguments || "{}") as Record<
          string,
          unknown
        >,
      })
    );

    const finishReason =
      choice?.finish_reason === "tool_calls"
        ? "tool_calls"
        : choice?.finish_reason === "length"
          ? "length"
          : toolCalls && toolCalls.length > 0
            ? "tool_calls"
            : "stop";

    return {
      text,
      toolCalls: toolCalls && toolCalls.length > 0 ? toolCalls : undefined,
      thinkingText: undefined,
      inputTokens: response.usage?.prompt_tokens ?? 0,
      outputTokens: response.usage?.completion_tokens ?? 0,
      finishReason,
    };
  },

  async *streamGenerate(
    request: ModelRequest,
    config: ModelConfig
  ): AsyncIterable<StreamChunk> {
    const client = new OpenAI({
      apiKey: process.env.OPENAI_API_KEY || "",
    });

    const messages = buildOpenAIMessages(request);

    const stream = await client.chat.completions.create({
      model: config.modelId,
      messages,
      stream: true,
      stream_options: { include_usage: true },
      ...(request.temperature !== undefined
        ? { temperature: request.temperature }
        : {}),
      ...(request.maxOutputTokens
        ? { max_tokens: request.maxOutputTokens }
        : {}),
      ...(request.tools && request.tools.length > 0
        ? {
            tools: request.tools.map((t) => ({
              type: "function" as const,
              function: {
                name: t.name,
                description: t.description,
                parameters: t.parameters as Record<string, unknown>,
              },
            })),
            ...(request.forceToolCall
              ? { tool_choice: "required" as const }
              : {}),
          }
        : {}),
    });

    let inputTokens = 0;
    let outputTokens = 0;
    const toolCallAccumulator: Map<
      number,
      { name: string; arguments: string }
    > = new Map();

    for await (const chunk of stream) {
      const delta = chunk.choices?.[0]?.delta;

      if (delta?.content) {
        yield { type: "delta", text: delta.content };
      }

      // Accumulate tool call deltas
      if (delta?.tool_calls) {
        for (const tc of delta.tool_calls) {
          const existing = toolCallAccumulator.get(tc.index) ?? {
            name: "",
            arguments: "",
          };
          if (tc.function?.name) existing.name = tc.function.name;
          if (tc.function?.arguments)
            existing.arguments += tc.function.arguments;
          toolCallAccumulator.set(tc.index, existing);
        }
      }

      if (chunk.usage) {
        inputTokens = chunk.usage.prompt_tokens ?? 0;
        outputTokens = chunk.usage.completion_tokens ?? 0;
      }
    }

    if (toolCallAccumulator.size > 0) {
      const toolCalls = Array.from(toolCallAccumulator.values()).map((tc) => ({
        name: tc.name,
        category: "read" as const,
        description: "",
        targetData: "",
        args: JSON.parse(tc.arguments || "{}") as Record<string, unknown>,
      }));
      yield { type: "tool_calls", toolCalls };
    }

    yield {
      type: "done",
      inputTokens,
      outputTokens,
      finishReason:
        toolCallAccumulator.size > 0 ? "tool_calls" : "stop",
    };
  },
};

/**
 * Mistral adapter.
 */
const mistralAdapter: ProviderAdapter = {
  async generate(
    request: ModelRequest,
    config: ModelConfig
  ): Promise<ModelResponse> {
    const client = new Mistral({
      apiKey: process.env.MISTRAL_API_KEY || "",
    });

    const messages = buildMistralMessages(request);

    const response = await client.chat.complete({
      model: config.modelId,
      messages,
      ...(request.temperature !== undefined
        ? { temperature: request.temperature }
        : {}),
      ...(request.maxOutputTokens
        ? { maxTokens: request.maxOutputTokens }
        : {}),
      ...(request.responseSchema
        ? {
            responseFormat: {
              type: "json_object" as const,
            },
          }
        : {}),
      ...(request.tools && request.tools.length > 0
        ? {
            tools: request.tools.map((t) => ({
              type: "function" as const,
              function: {
                name: t.name,
                description: t.description,
                parameters: t.parameters as Record<string, unknown>,
              },
            })),
            ...(request.forceToolCall
              ? { toolChoice: "any" as const }
              : {}),
          }
        : {}),
    });

    const choice = response.choices?.[0];
    const text = choice?.message?.content?.toString() ?? "";
    const toolCallsRaw = choice?.message?.toolCalls;

    const toolCalls = toolCallsRaw?.map((tc: { function?: { name?: string; arguments?: string | Record<string, unknown> } }) => ({
      name: tc.function?.name ?? "",
      category: "read" as const,
      description: "",
      targetData: "",
      args: (typeof tc.function?.arguments === "string"
        ? JSON.parse(tc.function.arguments)
        : tc.function?.arguments ?? {}) as Record<string, unknown>,
    }));

    const finishReason =
      choice?.finishReason === "tool_calls"
        ? "tool_calls"
        : choice?.finishReason === "length"
          ? "length"
          : toolCalls && toolCalls.length > 0
            ? "tool_calls"
            : "stop";

    return {
      text,
      toolCalls: toolCalls && toolCalls.length > 0 ? toolCalls : undefined,
      thinkingText: undefined,
      inputTokens: response.usage?.promptTokens ?? 0,
      outputTokens: response.usage?.completionTokens ?? 0,
      finishReason,
    };
  },

  async *streamGenerate(
    request: ModelRequest,
    config: ModelConfig
  ): AsyncIterable<StreamChunk> {
    const client = new Mistral({
      apiKey: process.env.MISTRAL_API_KEY || "",
    });

    const messages = buildMistralMessages(request);

    const stream = await client.chat.stream({
      model: config.modelId,
      messages,
      ...(request.temperature !== undefined
        ? { temperature: request.temperature }
        : {}),
      ...(request.maxOutputTokens
        ? { maxTokens: request.maxOutputTokens }
        : {}),
      ...(request.tools && request.tools.length > 0
        ? {
            tools: request.tools.map((t) => ({
              type: "function" as const,
              function: {
                name: t.name,
                description: t.description,
                parameters: t.parameters as Record<string, unknown>,
              },
            })),
            ...(request.forceToolCall
              ? { toolChoice: "any" as const }
              : {}),
          }
        : {}),
    });

    let inputTokens = 0;
    let outputTokens = 0;
    const toolCallAccumulator: Map<
      number,
      { name: string; arguments: string }
    > = new Map();

    for await (const event of stream) {
      const chunk = event.data;
      const delta = chunk.choices?.[0]?.delta;
      const content = delta?.content;

      if (content && typeof content === "string") {
        yield { type: "delta", text: content };
      }

      // Accumulate tool call deltas
      if (delta?.toolCalls) {
        for (let i = 0; i < delta.toolCalls.length; i++) {
          const tc = delta.toolCalls[i];
          const idx = i;
          const existing = toolCallAccumulator.get(idx) ?? {
            name: "",
            arguments: "",
          };
          if (tc.function?.name) existing.name = tc.function.name;
          if (tc.function?.arguments)
            existing.arguments += tc.function.arguments;
          toolCallAccumulator.set(idx, existing);
        }
      }

      if (chunk.usage) {
        inputTokens = chunk.usage.promptTokens ?? 0;
        outputTokens = chunk.usage.completionTokens ?? 0;
      }
    }

    if (toolCallAccumulator.size > 0) {
      const toolCalls = Array.from(toolCallAccumulator.values()).map((tc) => ({
        name: tc.name,
        category: "read" as const,
        description: "",
        targetData: "",
        args: (typeof tc.arguments === "string"
          ? JSON.parse(tc.arguments || "{}")
          : {}) as Record<string, unknown>,
      }));
      yield { type: "tool_calls", toolCalls };
    }

    yield {
      type: "done",
      inputTokens,
      outputTokens,
      finishReason:
        toolCallAccumulator.size > 0 ? "tool_calls" : "stop",
    };
  },
};

// ─── Helper: Build provider-specific message formats ───────────────────────────

function buildGoogleContents(request: ModelRequest) {
  return request.messages.map((msg) => ({
    role: msg.role === "assistant" ? "model" : "user",
    parts: [{ text: msg.content }],
  }));
}

function buildAnthropicMessages(
  request: ModelRequest
): Anthropic.MessageParam[] {
  return request.messages
    .filter((msg) => msg.role !== "system")
    .map((msg) => ({
      role: msg.role === "assistant" ? ("assistant" as const) : ("user" as const),
      content: msg.content,
    }));
}

function buildOpenAIMessages(
  request: ModelRequest
): OpenAI.Chat.ChatCompletionMessageParam[] {
  const messages: OpenAI.Chat.ChatCompletionMessageParam[] = [
    { role: "system", content: request.systemPrompt },
  ];

  for (const msg of request.messages) {
    messages.push({
      role: msg.role as "user" | "assistant" | "system",
      content: msg.content,
    });
  }

  return messages;
}

function buildMistralMessages(request: ModelRequest) {
  const messages: Array<{
    role: "system" | "user" | "assistant";
    content: string;
  }> = [{ role: "system", content: request.systemPrompt }];

  for (const msg of request.messages) {
    messages.push({
      role: msg.role as "system" | "user" | "assistant",
      content: msg.content,
    });
  }

  return messages;
}

// ─── Provider Registry ─────────────────────────────────────────────────────────

const providerAdapters: Record<ModelConfig["provider"], ProviderAdapter> = {
  google: googleAdapter,
  anthropic: anthropicAdapter,
  openai: openaiAdapter,
  mistral: mistralAdapter,
};

// ─── Public API: ModelRouter ───────────────────────────────────────────────────

/**
 * Resolves the active model config for a request.
 *
 * If the specified modelId is enabled, returns its config.
 * If the specified model is disabled/removed, falls back to the default model (Req 15.6).
 * If all models are disabled, throws 503 (Req 15.7).
 */
export async function resolveModel(
  preferredModelId?: string
): Promise<ModelConfig> {
  const { models } = await getModelsConfig();
  const enabledModels = models.filter((m) => m.enabled);

  if (enabledModels.length === 0) {
    throw new ModelRouterError(
      "No AI models are currently available",
      503
    );
  }

  // Try to use the user's preferred model
  if (preferredModelId) {
    const preferred = enabledModels.find((m) => m.modelId === preferredModelId);
    if (preferred) return preferred;
  }

  // Fall back to default model (Req 15.6)
  const defaultModel = enabledModels.find((m) => m.isDefault);
  if (defaultModel) return defaultModel;

  // If somehow no default is set, use the first enabled model
  return enabledModels[0];
}

/**
 * Generates a single-shot (non-streaming) response from the AI model.
 *
 * Routes to the correct provider adapter based on the model's provider field.
 * Returns a uniform ModelResponse regardless of which provider was used (Req 15.8).
 */
export async function generate(
  request: ModelRequest,
  modelConfig: ModelConfig
): Promise<ModelResponse> {
  const adapter = providerAdapters[modelConfig.provider];
  if (!adapter) {
    throw new ModelRouterError(
      `Unsupported provider: ${modelConfig.provider}`,
      400
    );
  }

  return adapter.generate(request, modelConfig);
}

/**
 * Generates a streaming response from the AI model.
 *
 * Yields StreamChunk objects for SSE transport. Each chunk has a type field
 * indicating the kind of content (delta, thinking, tool_calls, done, error).
 */
export async function* streamGenerate(
  request: ModelRequest,
  modelConfig: ModelConfig
): AsyncIterable<StreamChunk> {
  const adapter = providerAdapters[modelConfig.provider];
  if (!adapter) {
    yield {
      type: "error",
      error: `Unsupported provider: ${modelConfig.provider}`,
    };
    return;
  }

  try {
    yield* adapter.streamGenerate(request, modelConfig);
  } catch (err) {
    yield {
      type: "error",
      error: err instanceof Error ? err.message : "Stream generation failed",
    };
  }
}

/**
 * Estimates the token count for a given text and model.
 *
 * Uses a simple heuristic: characters / 4 for non-CJK text, characters / 2 for CJK text.
 * This provides approximate pre-request cost estimation without exact tokenization.
 */
export function estimateTokens(text: string, model: ModelConfig): number {
  if (!text) return 0;

  // Count CJK characters (Chinese, Japanese, Korean + common CJK punctuation)
  const cjkRegex =
    /[\u2E80-\u9FFF\uF900-\uFAFF\uFE30-\uFE4F\u{20000}-\u{2FA1F}]/gu;
  const cjkMatches = text.match(cjkRegex);
  const cjkCount = cjkMatches?.length ?? 0;
  const nonCjkCount = text.length - cjkCount;

  // CJK characters average ~2 chars per token, non-CJK ~4 chars per token
  const estimatedTokens = Math.ceil(cjkCount / 2 + nonCjkCount / 4);

  return Math.max(1, estimatedTokens);
}

/**
 * Gets the full list of enabled models from the config.
 * Useful for client display (Req 15.3).
 */
export async function getEnabledModels(): Promise<ModelConfig[]> {
  const { models } = await getModelsConfig();
  return models.filter((m) => m.enabled);
}

/**
 * Invalidates the cached config. Useful for testing or after admin updates.
 */
export function invalidateConfigCache(): void {
  cachedConfig = null;
  configCacheTime = 0;
}
