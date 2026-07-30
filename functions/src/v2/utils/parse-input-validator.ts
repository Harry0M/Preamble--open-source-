/**
 * Parse input validation utility.
 *
 * Extracted from v2-parse.ts endpoint to enable direct testing of the validation
 * logic independently of the HTTP handler. This enforces Requirement 1.6: reject
 * empty, whitespace-only, or >500 char input with 400 WITHOUT calling the AI model.
 */

/**
 * Result of parse input validation.
 * - `valid: true` means the input is acceptable for AI processing.
 * - `valid: false` means the input should be rejected immediately (no model invocation).
 */
export type ParseInputValidationResult =
  | { valid: true; text: string; timezone: string }
  | { valid: false; error: string };

/**
 * Validates parse request input before any AI model invocation.
 *
 * Returns an error message string on failure (input should be rejected with 400),
 * or null if the input is valid and may proceed to AI processing.
 *
 * Requirement 1.6: Reject empty, whitespace-only, or >500 char input
 * WITHOUT calling the AI model.
 */
export function validateParseInput(body: unknown): ParseInputValidationResult {
  if (!body || typeof body !== "object") {
    return { valid: false, error: "Request body must be a JSON object" };
  }

  const { text, timezone } = body as Record<string, unknown>;

  if (text === undefined || text === null || typeof text !== "string") {
    return { valid: false, error: "Field 'text' is required and must be a string" };
  }

  if (text.length === 0) {
    return { valid: false, error: "Field 'text' must not be empty" };
  }

  if (text.trim().length === 0) {
    return { valid: false, error: "Field 'text' must not be whitespace-only" };
  }

  if (text.length > 500) {
    return { valid: false, error: "Field 'text' must not exceed 500 characters" };
  }

  if (timezone === undefined || timezone === null || typeof timezone !== "string") {
    return { valid: false, error: "Field 'timezone' is required and must be a string" };
  }

  if (timezone.trim().length === 0) {
    return { valid: false, error: "Field 'timezone' must not be empty" };
  }

  return { valid: true, text, timezone };
}
