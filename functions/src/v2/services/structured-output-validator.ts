/**
 * StructuredOutputValidator — Runtime JSON Schema validation for all AI model responses.
 *
 * Compiles schemas once at module load and validates any input object against the
 * applicable schema. Returns strongly-typed success results or detailed ValidationError.
 *
 * Validates: Requirements 1.2, 21.1, 21.2
 */

import Ajv, { ValidateFunction } from "ajv";
import addFormats from "ajv-formats";

import { PARSE_RESPONSE_SCHEMA } from "../schemas/parse-response.schema";
import { CHAT_RESPONSE_SCHEMA } from "../schemas/chat-response.schema";
import { TOOL_CALL_RESULT_SCHEMA } from "../schemas/tool-call-result.schema";
import type {
  ParseResult,
  ChatResult,
  ToolCallResult,
  ValidationError,
} from "../models/types";

// ─── Compile schemas once at module load ───────────────────────────────────────

const ajv = new Ajv({ allErrors: true, strict: false });
addFormats(ajv);

const validateParse: ValidateFunction = ajv.compile(PARSE_RESPONSE_SCHEMA);
const validateChat: ValidateFunction = ajv.compile(CHAT_RESPONSE_SCHEMA);
const validateToolCall: ValidateFunction = ajv.compile(TOOL_CALL_RESULT_SCHEMA);

// ─── Helper: Convert ajv errors to ValidationError ─────────────────────────────

function buildValidationError(validate: ValidateFunction): ValidationError {
  const errors = (validate.errors ?? []).map((err) => ({
    path: err.instancePath || "/",
    message: err.message ?? "Unknown validation error",
    keyword: err.keyword,
  }));

  return { valid: false, errors };
}

// ─── Public API ────────────────────────────────────────────────────────────────

/**
 * Validates any object against the Parse Response JSON Schema.
 * Returns a typed ParseResult on success or ValidationError on failure.
 */
export function validateParseResponse(
  response: unknown
): ParseResult | ValidationError {
  const valid = validateParse(response);

  if (!valid) {
    return buildValidationError(validateParse);
  }

  // Safe to cast — schema guarantees shape
  const data = response as ParseResult["data"];

  return { valid: true, data };
}

/**
 * Validates any object against the Chat Response JSON Schema.
 * Returns a typed ChatResult on success or ValidationError on failure.
 */
export function validateChatResponse(
  response: unknown
): ChatResult | ValidationError {
  const valid = validateChat(response);

  if (!valid) {
    return buildValidationError(validateChat);
  }

  const data = response as ChatResult["data"];

  return { valid: true, data };
}

/**
 * Validates any object against the Tool Call Result JSON Schema.
 * Returns a typed ToolCallResult on success or ValidationError on failure.
 */
export function validateToolCallResult(
  response: unknown
): ToolCallResult | ValidationError {
  const valid = validateToolCall(response);

  if (!valid) {
    return buildValidationError(validateToolCall);
  }

  const data = response as ToolCallResult["data"];

  return { valid: true, data };
}
