/**
 * JSON Schema definition for Tool Call Result validation.
 * Used by StructuredOutputValidator to validate tool execution results at runtime via ajv.
 *
 * Validates: Requirements 1.2, 21.1
 */

export const TOOL_CALL_RESULT_SCHEMA = {
  $schema: "http://json-schema.org/draft-07/schema#",
  type: "object",
  required: ["toolName", "success"],
  properties: {
    toolName: { type: "string" },
    success: { type: "boolean" },
    data: {
      type: "object",
      description: "The result data returned by the tool execution",
    },
    error: {
      type: "object",
      properties: {
        code: { type: "string" },
        message: { type: "string" },
      },
      additionalProperties: false,
    },
    executedAt: { type: "number", description: "Unix timestamp of execution" },
    traceId: { type: "string", description: "Trace ID for observability" },
  },
  additionalProperties: false,
} as const;
