/**
 * Property-based tests for StructuredOutputValidator.
 *
 * Property 2: Structured output validation correctness
 * Generate arbitrary JSON objects (conforming and non-conforming) and verify the validator
 * accepts all conforming and rejects all non-conforming with zero false positives/negatives.
 *
 * **Validates: Requirements 1.2, 21.2**
 */

import { describe, it, expect } from "vitest";
import * as fc from "fast-check";
import {
  validateParseResponse,
  validateChatResponse,
} from "../../src/v2/services/structured-output-validator";

// ─── Generators: Conforming Parse Response ─────────────────────────────────────

/**
 * Generates a valid date string in YYYY-MM-DD format.
 */
const arbValidDate = fc
  .tuple(
    fc.integer({ min: 2000, max: 2099 }),
    fc.integer({ min: 1, max: 12 }),
    fc.integer({ min: 1, max: 28 })
  )
  .map(([y, m, d]) => `${y}-${String(m).padStart(2, "0")}-${String(d).padStart(2, "0")}`);

/**
 * Generates a valid time string in HH:mm format (00:00–23:59).
 */
const arbValidTime = fc
  .tuple(fc.integer({ min: 0, max: 23 }), fc.integer({ min: 0, max: 59 }))
  .map(([h, m]) => `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`);

/**
 * Generates a valid recurrence object.
 */
const arbRecurrence = fc.record({
  recurrenceType: fc.constantFrom("daily", "weekly", "monthly", "yearly"),
  recurrenceInterval: fc.integer({ min: 1, max: 365 }),
  recurrenceDays: fc.option(fc.array(fc.integer({ min: 0, max: 31 }), { maxLength: 7 }), { nil: undefined }),
  recurrenceEndDate: fc.option(arbValidDate, { nil: undefined }),
});

/**
 * Generates a conforming Parse Response object that must pass validation.
 */
const arbConformingParseResponse = fc.record({
  title: fc.string({ minLength: 1, maxLength: 200 }),
  confidence: fc.double({ min: 0, max: 1, noNaN: true }),
  date: fc.option(arbValidDate, { nil: undefined }),
  time: fc.option(arbValidTime, { nil: undefined }),
  priority: fc.option(fc.integer({ min: 1, max: 4 }), { nil: undefined }),
  tags: fc.option(
    fc.array(fc.string({ minLength: 0, maxLength: 50 }), { maxLength: 10 }),
    { nil: undefined }
  ),
  recurrence: fc.option(arbRecurrence, { nil: undefined }),
  description: fc.option(fc.string({ minLength: 0, maxLength: 1000 }), { nil: undefined }),
  detectedLanguage: fc.option(fc.string({ minLength: 1, maxLength: 20 }), { nil: undefined }),
  duplicates: fc.option(
    fc.array(
      fc.record({
        taskId: fc.string({ minLength: 1, maxLength: 50 }),
        similarity: fc.double({ min: 0, max: 1, noNaN: true }),
        title: fc.string({ minLength: 1, maxLength: 200 }),
      }),
      { maxLength: 5 }
    ),
    { nil: undefined }
  ),
});

// ─── Generators: Non-Conforming Parse Response ─────────────────────────────────

/**
 * Generate objects that violate the parse schema in various ways.
 */
const arbNonConformingParseResponse = fc.oneof(
  // Missing required "title" field
  fc.record({
    confidence: fc.double({ min: 0, max: 1, noNaN: true }),
  }),
  // Missing required "confidence" field
  fc.record({
    title: fc.string({ minLength: 1, maxLength: 200 }),
  }),
  // title exceeds maxLength (201+ chars)
  fc.record({
    title: fc.string({ minLength: 201, maxLength: 300 }),
    confidence: fc.double({ min: 0, max: 1, noNaN: true }),
  }),
  // confidence out of range (> 1)
  fc.record({
    title: fc.string({ minLength: 1, maxLength: 200 }),
    confidence: fc.double({ min: 1.01, max: 100, noNaN: true }),
  }),
  // confidence out of range (< 0)
  fc.record({
    title: fc.string({ minLength: 1, maxLength: 200 }),
    confidence: fc.double({ min: -100, max: -0.01, noNaN: true }),
  }),
  // priority out of range
  fc.record({
    title: fc.string({ minLength: 1, maxLength: 200 }),
    confidence: fc.double({ min: 0, max: 1, noNaN: true }),
    priority: fc.oneof(
      fc.integer({ min: 5, max: 100 }),
      fc.integer({ min: -100, max: 0 })
    ),
  }),
  // Invalid date format
  fc.record({
    title: fc.string({ minLength: 1, maxLength: 200 }),
    confidence: fc.double({ min: 0, max: 1, noNaN: true }),
    date: fc.constantFrom("not-a-date", "13/01/2024", "2024-1-1", "01-01-2024"),
  }),
  // Invalid time format
  fc.record({
    title: fc.string({ minLength: 1, maxLength: 200 }),
    confidence: fc.double({ min: 0, max: 1, noNaN: true }),
    time: fc.constantFrom("25:00", "12:60", "noon", "3pm", "12:5"),
  }),
  // tags with item exceeding maxLength (51+ chars)
  fc.record({
    title: fc.string({ minLength: 1, maxLength: 200 }),
    confidence: fc.double({ min: 0, max: 1, noNaN: true }),
    tags: fc.constant([
      "a".repeat(51),
    ]),
  }),
  // Too many tags (more than 10)
  fc.record({
    title: fc.string({ minLength: 1, maxLength: 200 }),
    confidence: fc.double({ min: 0, max: 1, noNaN: true }),
    tags: fc.constant(Array.from({ length: 11 }, (_, i) => `tag${i}`)),
  }),
  // Additional properties not allowed
  fc.record({
    title: fc.string({ minLength: 1, maxLength: 200 }),
    confidence: fc.double({ min: 0, max: 1, noNaN: true }),
    unknownField: fc.string(),
  }),
  // description exceeding maxLength (1001+ chars)
  fc.record({
    title: fc.string({ minLength: 1, maxLength: 200 }),
    confidence: fc.double({ min: 0, max: 1, noNaN: true }),
    description: fc.constant("x".repeat(1001)),
  }),
  // Completely wrong type (not an object)
  fc.oneof(
    fc.constant(null),
    fc.constant(42),
    fc.constant("string"),
    fc.constant([1, 2, 3]),
    fc.constant(true)
  )
);

// ─── Generators: Conforming Chat Response ──────────────────────────────────────

/**
 * Generates a conforming Chat Response object that must pass validation.
 */
const arbConformingChatResponse = fc.record({
  content: fc.string(),
  thinking: fc.option(fc.string(), { nil: undefined }),
  toolCalls: fc.option(
    fc.array(
      fc.record({
        name: fc.string({ minLength: 1, maxLength: 50 }),
        category: fc.constantFrom("read", "write") as fc.Arbitrary<"read" | "write">,
        description: fc.string({ minLength: 1, maxLength: 200 }),
        targetData: fc.option(fc.string({ minLength: 1, maxLength: 100 }), { nil: undefined }),
        args: fc.dictionary(
          fc.string({ minLength: 1, maxLength: 20 }).filter((s) => /^[a-zA-Z_]\w*$/.test(s)),
          fc.oneof(fc.string(), fc.integer(), fc.boolean())
        ),
      }),
      { maxLength: 3 }
    ),
    { nil: undefined }
  ),
  citations: fc.option(
    fc.array(
      fc.record({
        title: fc.option(fc.string({ minLength: 1, maxLength: 100 }), { nil: undefined }),
        url: fc.option(
          fc.webUrl(),
          { nil: undefined }
        ),
        snippet: fc.option(fc.string({ minLength: 0, maxLength: 200 }), { nil: undefined }),
      }),
      { maxLength: 5 }
    ),
    { nil: undefined }
  ),
  memoryUpdates: fc.option(
    fc.array(
      fc.record({
        op: fc.option(fc.constantFrom("upsert", "delete") as fc.Arbitrary<"upsert" | "delete">, { nil: undefined }),
        text: fc.option(fc.string({ minLength: 1, maxLength: 200 }), { nil: undefined }),
        category: fc.option(fc.string({ minLength: 1, maxLength: 50 }), { nil: undefined }),
      }),
      { maxLength: 5 }
    ),
    { nil: undefined }
  ),
});

// ─── Generators: Non-Conforming Chat Response ──────────────────────────────────

/**
 * Generate objects that violate the chat response schema.
 */
const arbNonConformingChatResponse = fc.oneof(
  // Missing required "content" field
  fc.record({
    thinking: fc.string(),
  }),
  // content is not a string
  fc.constant({ content: 42 }),
  fc.constant({ content: null }),
  fc.constant({ content: ["hello"] }),
  // toolCalls with invalid category
  fc.record({
    content: fc.string(),
    toolCalls: fc.constant([
      { name: "test", category: "invalid", description: "test", args: {} },
    ]),
  }),
  // toolCalls missing required fields
  fc.record({
    content: fc.string(),
    toolCalls: fc.constant([{ name: "test" }]),
  }),
  // citation snippet exceeding maxLength
  fc.record({
    content: fc.string(),
    citations: fc.constant([{ snippet: "x".repeat(201) }]),
  }),
  // Additional properties not allowed
  fc.record({
    content: fc.string(),
    extraField: fc.string(),
  }),
  // Additional properties not allowed in toolCalls items
  fc.record({
    content: fc.string(),
    toolCalls: fc.constant([
      { name: "x", category: "read", description: "y", args: {}, extraProp: true },
    ]),
  }),
  // Completely wrong type (not an object)
  fc.oneof(
    fc.constant(null),
    fc.constant(42),
    fc.constant("string"),
    fc.constant([1, 2, 3]),
    fc.constant(true)
  )
);

// ─── Property Tests ────────────────────────────────────────────────────────────

describe("Property 2: Structured output validation correctness", () => {
  describe("validateParseResponse", () => {
    it("accepts all conforming parse response objects (zero false negatives)", () => {
      fc.assert(
        fc.property(arbConformingParseResponse, (response) => {
          // Remove undefined keys to match JSON-like structure
          const cleaned = JSON.parse(JSON.stringify(response));
          const result = validateParseResponse(cleaned);
          expect(result.valid).toBe(true);
        }),
        { numRuns: 200 }
      );
    });

    it("rejects all non-conforming parse response objects (zero false positives)", () => {
      fc.assert(
        fc.property(arbNonConformingParseResponse, (response) => {
          const result = validateParseResponse(response);
          expect(result.valid).toBe(false);
        }),
        { numRuns: 200 }
      );
    });

    it("valid results contain required fields with correct types", () => {
      fc.assert(
        fc.property(arbConformingParseResponse, (response) => {
          const cleaned = JSON.parse(JSON.stringify(response));
          const result = validateParseResponse(cleaned);
          if (result.valid) {
            expect(typeof result.data.title).toBe("string");
            expect(result.data.title.length).toBeLessThanOrEqual(200);
            expect(typeof result.data.confidence).toBe("number");
            expect(result.data.confidence).toBeGreaterThanOrEqual(0);
            expect(result.data.confidence).toBeLessThanOrEqual(1);
          }
        }),
        { numRuns: 100 }
      );
    });

    it("invalid results contain meaningful error details", () => {
      fc.assert(
        fc.property(arbNonConformingParseResponse, (response) => {
          const result = validateParseResponse(response);
          if (!result.valid) {
            expect(result.errors).toBeDefined();
            expect(result.errors.length).toBeGreaterThan(0);
            for (const err of result.errors) {
              expect(typeof err.path).toBe("string");
              expect(typeof err.message).toBe("string");
              expect(typeof err.keyword).toBe("string");
            }
          }
        }),
        { numRuns: 100 }
      );
    });
  });

  describe("validateChatResponse", () => {
    it("accepts all conforming chat response objects (zero false negatives)", () => {
      fc.assert(
        fc.property(arbConformingChatResponse, (response) => {
          const cleaned = JSON.parse(JSON.stringify(response));
          const result = validateChatResponse(cleaned);
          expect(result.valid).toBe(true);
        }),
        { numRuns: 200 }
      );
    });

    it("rejects all non-conforming chat response objects (zero false positives)", () => {
      fc.assert(
        fc.property(arbNonConformingChatResponse, (response) => {
          const result = validateChatResponse(response);
          expect(result.valid).toBe(false);
        }),
        { numRuns: 200 }
      );
    });

    it("valid results contain required fields with correct types", () => {
      fc.assert(
        fc.property(arbConformingChatResponse, (response) => {
          const cleaned = JSON.parse(JSON.stringify(response));
          const result = validateChatResponse(cleaned);
          if (result.valid) {
            expect(typeof result.data.content).toBe("string");
          }
        }),
        { numRuns: 100 }
      );
    });

    it("invalid results contain meaningful error details", () => {
      fc.assert(
        fc.property(arbNonConformingChatResponse, (response) => {
          const result = validateChatResponse(response);
          if (!result.valid) {
            expect(result.errors).toBeDefined();
            expect(result.errors.length).toBeGreaterThan(0);
            for (const err of result.errors) {
              expect(typeof err.path).toBe("string");
              expect(typeof err.message).toBe("string");
              expect(typeof err.keyword).toBe("string");
            }
          }
        }),
        { numRuns: 100 }
      );
    });
  });
});
