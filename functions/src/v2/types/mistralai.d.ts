/**
 * Type declarations for @mistralai/mistralai
 * These are minimal stubs used when the package is not yet installed.
 * Remove this file once `npm install` completes successfully.
 */
declare module "@mistralai/mistralai" {
  interface MistralMessage {
    role: "system" | "user" | "assistant";
    content: string;
  }

  interface MistralToolCall {
    function?: {
      name?: string;
      arguments?: string | Record<string, unknown>;
    };
  }

  interface MistralChoice {
    message?: {
      content?: string | object;
      toolCalls?: MistralToolCall[];
    };
    finishReason?: string;
  }

  interface MistralUsage {
    promptTokens?: number;
    completionTokens?: number;
  }

  interface MistralChatResponse {
    choices?: MistralChoice[];
    usage?: MistralUsage;
  }

  interface MistralStreamDelta {
    content?: string | object;
    toolCalls?: Array<{
      function?: {
        name?: string;
        arguments?: string;
      };
    }>;
  }

  interface MistralStreamChoice {
    delta?: MistralStreamDelta;
  }

  interface MistralStreamChunk {
    choices?: MistralStreamChoice[];
    usage?: MistralUsage;
  }

  interface MistralStreamEvent {
    data: MistralStreamChunk;
  }

  interface MistralStream {
    [Symbol.asyncIterator](): AsyncIterator<MistralStreamEvent>;
  }

  interface MistralChatAPI {
    complete(params: Record<string, unknown>): Promise<MistralChatResponse>;
    stream(params: Record<string, unknown>): Promise<MistralStream>;
  }

  export class Mistral {
    constructor(config: { apiKey: string });
    chat: MistralChatAPI;
  }
}
