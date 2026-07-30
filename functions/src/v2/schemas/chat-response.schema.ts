/**
 * JSON Schema definition for Chat Response validation.
 * Used by StructuredOutputValidator to validate AI model chat outputs at runtime via ajv.
 *
 * Validates: Requirements 1.2, 21.1
 */

export const CHAT_RESPONSE_SCHEMA = {
  $schema: "http://json-schema.org/draft-07/schema#",
  type: "object",
  required: ["content"],
  properties: {
    content: { type: "string" },
    thinking: { type: "string" },
    toolCalls: {
      type: "array",
      items: {
        type: "object",
        required: ["name", "category", "description", "args"],
        properties: {
          name: { type: "string" },
          category: { enum: ["read", "write"] },
          description: { type: "string" },
          targetData: { type: "string" },
          args: { type: "object" },
        },
        additionalProperties: false,
      },
    },
    citations: {
      type: "array",
      items: {
        type: "object",
        properties: {
          title: { type: "string" },
          url: { type: "string", format: "uri" },
          snippet: { type: "string", maxLength: 200 },
        },
        additionalProperties: false,
      },
    },
    memoryUpdates: {
      type: "array",
      items: {
        type: "object",
        properties: {
          op: { enum: ["upsert", "delete"] },
          text: { type: "string" },
          category: { type: "string" },
        },
        additionalProperties: false,
      },
    },
  },
  additionalProperties: false,
} as const;
