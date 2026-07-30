/**
 * Type declarations for @anthropic-ai/sdk
 * These are minimal stubs used when the package is not yet installed.
 * Remove this file once `npm install` completes successfully.
 */
declare module "@anthropic-ai/sdk" {
  interface MessageParam {
    role: "user" | "assistant";
    content: string;
  }

  interface TextBlock {
    type: "text";
    text: string;
  }

  interface ThinkingBlock {
    type: "thinking";
    thinking: string;
  }

  interface ToolUseBlock {
    type: "tool_use";
    id: string;
    name: string;
    input: unknown;
  }

  type ContentBlock = TextBlock | ThinkingBlock | ToolUseBlock;

  interface Tool {
    name: string;
    description: string;
    input_schema: Tool.InputSchema;
  }

  namespace Tool {
    interface InputSchema {
      type: string;
      properties?: Record<string, unknown>;
      required?: string[];
      [key: string]: unknown;
    }
  }

  interface Usage {
    input_tokens: number;
    output_tokens: number;
  }

  interface Message {
    content: ContentBlock[];
    stop_reason: string | null;
    usage: Usage;
  }

  interface StreamEvent {
    type: string;
    delta: {
      type: string;
      text?: string;
      thinking?: string;
    };
  }

  interface MessageStream {
    [Symbol.asyncIterator](): AsyncIterator<StreamEvent>;
    finalMessage(): Promise<Message>;
  }

  interface MessagesAPI {
    create(params: Record<string, unknown>): Promise<Message>;
    stream(params: Record<string, unknown>): MessageStream;
  }

  class Anthropic {
    constructor(config: { apiKey: string });
    messages: MessagesAPI;
  }

  namespace Anthropic {
    export type MessageParam = import("@anthropic-ai/sdk").MessageParam;
    export type ContentBlock = import("@anthropic-ai/sdk").ContentBlock;
    export type ToolUseBlock = import("@anthropic-ai/sdk").ToolUseBlock;
    export type Tool = import("@anthropic-ai/sdk").Tool;
    export namespace Tool {
      export type InputSchema = import("@anthropic-ai/sdk").Tool.InputSchema;
    }
  }

  export default Anthropic;
}
