/**
 * Property 3: Invalid input rejection without model invocation
 *
 * For empty, whitespace-only, or >500 char inputs, verify rejection without AI model invocation.
 * The key invariant: validateParseInput MUST return { valid: false } for all invalid inputs,
 * meaning the ModelRouter is never reached (it sits downstream of validation in the pipeline).
 *
 * **Validates: Requirements 1.6**
 */

import { describe, it, expect } from "vitest";
import * as fc from "fast-check";
import { validateParseInput } from "../../src/v2/utils/parse-input-validator";

// ─── Generators ────────────────────────────────────────────────────────────────

/**
 * Generates an empty string input (length 0).
 */
const arbEmptyText = fc.constant("");

/**
 * Generates whitespace-only strings (spaces, tabs, newlines, mixed).
 */
const arbWhitespaceOnlyText = fc
  .array(fc.constantFrom(" ", "\t", "\n", "\r", "\f", "\v", " \t", "  "), {
    minLength: 1,
    maxLength: 50,
  })
  .map((parts) => parts.join(""));

/**
 * Generates strings exceeding 500 characters.
 */
const arbOverLimitText = fc
  .string({ minLength: 501, maxLength: 1000 })
  .filter((s) => s.length > 500);

/**
 * Generates a valid timezone string for use in request bodies.
 */
const arbTimezone = fc.constantFrom(
  "America/New_York",
  "Europe/London",
  "Asia/Kolkata",
  "UTC",
  "Pacific/Auckland"
);

/**
 * Generates a request body with empty text.
 */
const arbEmptyBody = arbTimezone.map((tz) => ({
  text: "",
  timezone: tz,
}));

/**
 * Generates a request body with whitespace-only text.
 */
const arbWhitespaceBody = fc
  .tuple(arbWhitespaceOnlyText, arbTimezone)
  .map(([text, timezone]) => ({
    text,
    timezone,
  }));

/**
 * Generates a request body with text exceeding 500 characters.
 */
const arbOverLimitBody = fc
  .tuple(arbOverLimitText, arbTimezone)
  .map(([text, timezone]) => ({
    text,
    timezone,
  }));

/**
 * Generates request bodies with missing or non-string text field.
 */
const arbMissingTextField = fc.oneof(
  // text field missing entirely
  arbTimezone.map((tz) => ({ timezone: tz })),
  // text field is null
  arbTimezone.map((tz) => ({ text: null, timezone: tz })),
  // text field is a number
  arbTimezone.map((tz) => ({ text: 42, timezone: tz })),
  // text field is an array
  arbTimezone.map((tz) => ({ text: ["hello"], timezone: tz })),
  // text field is an object
  arbTimezone.map((tz) => ({ text: { value: "hello" }, timezone: tz })),
  // text field is undefined (missing key)
  arbTimezone.map((tz) => ({ text: undefined, timezone: tz }))
);

/**
 * Generates non-object bodies (null, arrays, strings, numbers).
 */
const arbNonObjectBody = fc.oneof(
  fc.constant(null),
  fc.constant(undefined),
  fc.constant(""),
  fc.constant(42),
  fc.constant([]),
  fc.constant(true)
);

/**
 * Generates a valid parse input (1–500 non-whitespace-only chars).
 */
const arbValidBody = fc
  .tuple(
    fc
      .string({ minLength: 1, maxLength: 500 })
      .filter((s) => s.trim().length > 0),
    arbTimezone
  )
  .map(([text, timezone]) => ({
    text,
    timezone,
  }));

// ─── Property Tests ────────────────────────────────────────────────────────────

describe("Property 3: Invalid input rejection without model invocation", () => {
  describe("Empty inputs are rejected (no model invocation)", () => {
    it("empty string text is rejected", () => {
      fc.assert(
        fc.property(arbEmptyBody, (body) => {
          const result = validateParseInput(body);
          expect(result.valid).toBe(false);
          if (!result.valid) {
            expect(result.error).toContain("empty");
          }
        }),
        { numRuns: 50 }
      );
    });
  });

  describe("Whitespace-only inputs are rejected (no model invocation)", () => {
    it("strings containing only whitespace characters are rejected", () => {
      fc.assert(
        fc.property(arbWhitespaceBody, (body) => {
          const result = validateParseInput(body);
          expect(result.valid).toBe(false);
          if (!result.valid) {
            expect(result.error).toContain("whitespace");
          }
        }),
        { numRuns: 200 }
      );
    });
  });

  describe("Over-limit inputs are rejected (no model invocation)", () => {
    it("strings exceeding 500 characters are rejected", () => {
      fc.assert(
        fc.property(arbOverLimitBody, (body) => {
          expect(body.text.length).toBeGreaterThan(500);
          const result = validateParseInput(body);
          expect(result.valid).toBe(false);
          if (!result.valid) {
            expect(result.error).toContain("500");
          }
        }),
        { numRuns: 200 }
      );
    });
  });

  describe("Missing or invalid text field is rejected (no model invocation)", () => {
    it("non-string or missing text field is rejected", () => {
      fc.assert(
        fc.property(arbMissingTextField, (body) => {
          const result = validateParseInput(body);
          expect(result.valid).toBe(false);
        }),
        { numRuns: 100 }
      );
    });
  });

  describe("Non-object bodies are rejected (no model invocation)", () => {
    it("null, undefined, primitives, and arrays are rejected", () => {
      fc.assert(
        fc.property(arbNonObjectBody, (body) => {
          const result = validateParseInput(body);
          expect(result.valid).toBe(false);
        }),
        { numRuns: 50 }
      );
    });
  });

  describe("Valid inputs are accepted (model invocation allowed)", () => {
    it("inputs with 1-500 non-whitespace chars and valid timezone pass validation", () => {
      fc.assert(
        fc.property(arbValidBody, (body) => {
          const result = validateParseInput(body);
          expect(result.valid).toBe(true);
          if (result.valid) {
            expect(result.text).toBe(body.text);
            expect(result.timezone).toBe(body.timezone);
          }
        }),
        { numRuns: 200 }
      );
    });
  });

  describe("Boundary: exactly 500 characters is accepted, 501 is rejected", () => {
    it("500-char input is accepted, 501-char input is rejected", () => {
      fc.assert(
        fc.property(
          fc.constantFrom(..."abcdefghijklmnopqrstuvwxyz".split("")),
          arbTimezone,
          (char, timezone) => {
            const text500 = char.repeat(500);
            const text501 = char.repeat(501);

            const result500 = validateParseInput({ text: text500, timezone });
            const result501 = validateParseInput({ text: text501, timezone });

            expect(result500.valid).toBe(true);
            expect(result501.valid).toBe(false);
          }
        ),
        { numRuns: 26 }
      );
    });
  });

  describe("Invariant: rejected inputs never reach model invocation", () => {
    it("all invalid input categories produce valid=false before any downstream processing", () => {
      // This property combines all invalid categories into a single assertion:
      // If validateParseInput returns { valid: false }, the endpoint returns 400
      // BEFORE calling ModelRouter.generate(). This is the architectural guarantee.
      const arbAnyInvalidInput = fc.oneof(
        arbEmptyBody,
        arbWhitespaceBody,
        arbOverLimitBody,
        arbMissingTextField as fc.Arbitrary<unknown>,
        arbNonObjectBody
      );

      fc.assert(
        fc.property(arbAnyInvalidInput, (body) => {
          const result = validateParseInput(body);
          // Core invariant: all invalid inputs MUST be rejected at the validation layer
          expect(result.valid).toBe(false);
          // When rejected, an error message is provided
          if (!result.valid) {
            expect(typeof result.error).toBe("string");
            expect(result.error.length).toBeGreaterThan(0);
          }
        }),
        { numRuns: 300 }
      );
    });
  });
});
