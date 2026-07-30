/**
 * JSON Schema definition for Task Parse Response validation.
 * Used by StructuredOutputValidator to validate AI model parse outputs at runtime via ajv.
 *
 * Validates: Requirements 1.1, 1.2
 */

export const PARSE_RESPONSE_SCHEMA = {
  $schema: "http://json-schema.org/draft-07/schema#",
  type: "object",
  required: ["title", "confidence"],
  properties: {
    title: { type: "string", maxLength: 200 },
    date: { type: "string", pattern: "^\\d{4}-\\d{2}-\\d{2}$" },
    time: { type: "string", pattern: "^([01]\\d|2[0-3]):[0-5]\\d$" },
    priority: { type: "integer", minimum: 1, maximum: 4 },
    tags: {
      type: "array",
      items: { type: "string", maxLength: 50 },
      maxItems: 10,
    },
    recurrence: {
      type: "object",
      properties: {
        recurrenceType: { enum: ["daily", "weekly", "monthly", "yearly"] },
        recurrenceInterval: { type: "integer", minimum: 1, maximum: 365 },
        recurrenceDays: { type: "array", items: { type: "integer" } },
        recurrenceEndDate: { type: "string", pattern: "^\\d{4}-\\d{2}-\\d{2}$" },
      },
      required: ["recurrenceType", "recurrenceInterval"],
    },
    description: { type: "string", maxLength: 1000 },
    confidence: { type: "number", minimum: 0, maximum: 1 },
    detectedLanguage: { type: "string" },
    duplicates: {
      type: "array",
      items: {
        type: "object",
        properties: {
          taskId: { type: "string" },
          similarity: { type: "number" },
          title: { type: "string" },
        },
      },
    },
  },
  additionalProperties: false,
} as const;
