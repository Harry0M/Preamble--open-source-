/**
 * Type declarations for openai
 * These are minimal stubs used when the package is not yet installed.
 * Remove this file once `npm install` completes successfully.
 */
declare module "openai" {
  namespace OpenAI {
    namespace Chat {
      interface ChatCompletionMessageParam {
        role: "system" | "user" | "assistant";
        content: string | null;
      }

      interface ChatCompletionMessageToolCall {
        id: string;
        type: "function";
        function: {
          name: string;
          arguments: string;
        };
      }

      interface ChatCompletionMessage {
        content: string | null;
        tool_calls?: ChatCompletionMessageToolCall[];
      }

      interface ChatCompletionChoice {
        message: ChatCompletionMessage;
        finish_reason: string;
      }

      interface ChatCompletionUsage {
        prompt_tokens: number;
        completion_tokens: number;
        total_tokens: number;
      }

      interface ChatCompletion {
        choices: ChatCompletionChoice[];
        usage?: ChatCompletionUsage;
      }

      interface ChatCompletionChunkDelta {
        content?: string | null;
        tool_calls?: Array<{
          index: number;
          function?: {
            name?: string;
            arguments?: string;
          };
        }>;
      }

      interface ChatCompletionChunkChoice {
        delta: ChatCompletionChunkDelta;
        finish_reason?: string | null;
      }

      interface ChatCompletionChunk {
        choices?: ChatCompletionChunkChoice[];
        usage?: {
          prompt_tokens?: number;
          completion_tokens?: number;
        } | null;
      }
    }
  }

  interface ChatCompletionsAPI {
    create(params: Record<string, unknown> & { stream?: false }): Promise<OpenAI.Chat.ChatCompletion>;
    create(params: Record<string, unknown> & { stream: true; stream_options?: Record<string, unknown> }): Promise<AsyncIterable<OpenAI.Chat.ChatCompletionChunk>>;
    create(params: Record<string, unknown>): Promise<OpenAI.Chat.ChatCompletion | AsyncIterable<OpenAI.Chat.ChatCompletionChunk>>;
  }

  interface ChatAPI {
    completions: ChatCompletionsAPI;
  }

  class OpenAI {
    constructor(config: { apiKey: string });
    chat: ChatAPI;
  }

  export default OpenAI;
  export { OpenAI };
}
