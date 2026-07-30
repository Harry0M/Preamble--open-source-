/**
 * Property-based tests for Low Confidence Field Omission.
 *
 * Property 5: Low confidence field omission
 * For parse results where temporal/recurrence field confidence < 0.5,
 * verify field is omitted rather than included with a guessed value.
 *
 * The system contract is: when a temporal (date, time) or recurrence field has
 * confidence below 0.5, that field SHALL be omitted from the structured output.
 * This is enforced by the `applyLowConfidenceOmission` function which post-processes
 * parse results based on field-level confidence scores.
 *
 * **Validates: Requirements 2.6, 3.5, 3.6, 4.5**
 */

import { describe, it, expect } from "vitest";
import * as fc from "fast-check";
import { applyLowConfidenceOmission } from "../../src/v2/utils/confidence-filter";
import type { FieldConfidences, RawParseResult } from "../../src/v2/utils/confidence-filter";

// ─── Constants ─────────────────────────────────────────────────────────────────

/** The threshold below which fields must be omitted (Req 2.6, 3.6, 4.5). */
const CONFIDENCE_THRESHOLD = 0.5;

// ─── Generators ────────────────────────────────────────────────────────────────

/**
 * Generates a valid date string in YYYY-MM-DD format.
 */
const arbValidDate = fc
  .tuple(
    fc.integer({ min: 2020, max: 2030 }),
    fc.integer({ min: 1, max: 12 }),
    fc.integer({ min: 1, max: 28 })
  )
  .map(
    ([y, m, d]) =>
      `${y}-${String(m).padStart(2, "0")}-${String(d).padStart(2, "0")}`
  );

/**
 * Generates a valid time string in HH:mm format.
 */
const arbValidTime = fc
  .tuple(fc.integer({ min: 0, max: 23 }), fc.integer({ min: 0, max: 59 }))
  .map(([h, m]) => `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`);

/**
 * Generates a valid recurrence object.
 */
const arbRecurrence = fc.record(
  {
    recurrenceType: fc.constantFrom(
      "daily" as const,
      "weekly" as const,
      "monthly" as const,
      "yearly" as const
    ),
    recurrenceInterval: fc.integer({ min: 1, max: 365 }),
    recurrenceDays: fc.option(
      fc.array(fc.integer({ min: 0, max: 6 }), { minLength: 1, maxLength: 7 }),
      { nil: undefined }
    ),
    recurrenceEndDate: fc.option(arbValidDate, { nil: undefined }),
  },
  { requiredKeys: ["recurrenceType", "recurrenceInterval"] }
);

/**
 * Generates a raw parse result with all temporal/recurrence fields present.
 * This simulates an AI model response that includes all fields before filtering.
 */
const arbRawParseResultWithAllFields: fc.Arbitrary<RawParseResult> = fc.record(
  {
    title: fc.string({ minLength: 1, maxLength: 200 }),
    confidence: fc.double({ min: 0, max: 1, noNaN: true }),
    date: arbValidDate,
    time: arbValidTime,
    recurrence: arbRecurrence,
    description: fc.option(fc.string({ minLength: 1, maxLength: 1000 }), { nil: undefined }),
    detectedLanguage: fc.option(fc.string({ minLength: 2, maxLength: 10 }), { nil: undefined }),
  },
  { requiredKeys: ["title", "confidence", "date", "time", "recurrence"] }
);

/**
 * Generates field-level confidences for date, time, and recurrence.
 */
const arbFieldConfidences: fc.Arbitrary<FieldConfidences> = fc.record({
  date: fc.double({ min: 0, max: 1, noNaN: true }),
  time: fc.double({ min: 0, max: 1, noNaN: true }),
  recurrence: fc.double({ min: 0, max: 1, noNaN: true }),
});

/**
 * Generates field confidences where ALL fields are below threshold.
 */
const arbAllLowConfidence: fc.Arbitrary<FieldConfidences> = fc.record({
  date: fc.double({ min: 0, max: 0.49, noNaN: true }),
  time: fc.double({ min: 0, max: 0.49, noNaN: true }),
  recurrence: fc.double({ min: 0, max: 0.49, noNaN: true }),
});

/**
 * Generates field confidences where ALL fields are at or above threshold.
 */
const arbAllHighConfidence: fc.Arbitrary<FieldConfidences> = fc.record({
  date: fc.double({ min: 0.5, max: 1, noNaN: true }),
  time: fc.double({ min: 0.5, max: 1, noNaN: true }),
  recurrence: fc.double({ min: 0.5, max: 1, noNaN: true }),
});

// ─── Property Tests ────────────────────────────────────────────────────────────

describe("Property 5: Low confidence field omission", () => {
  describe("Fields with confidence < 0.5 are omitted", () => {
    it("when date confidence < 0.5, date field is omitted from output", () => {
      fc.assert(
        fc.property(
          arbRawParseResultWithAllFields,
          fc.double({ min: 0, max: 0.49, noNaN: true }),
          (rawResult, dateConfidence) => {
            const confidences: FieldConfidences = {
              date: dateConfidence,
              time: 0.9,       // high confidence — should be kept
              recurrence: 0.9, // high confidence — should be kept
            };

            const filtered = applyLowConfidenceOmission(rawResult, confidences);

            // Date must be omitted (Req 2.6, 3.6)
            expect(filtered.date).toBeUndefined();

            // Time and recurrence should still be present
            expect(filtered.time).toBeDefined();
            expect(filtered.recurrence).toBeDefined();

            // Title and confidence always preserved
            expect(filtered.title).toBe(rawResult.title);
            expect(filtered.confidence).toBe(rawResult.confidence);
          }
        ),
        { numRuns: 200 }
      );
    });

    it("when time confidence < 0.5, time field is omitted from output", () => {
      fc.assert(
        fc.property(
          arbRawParseResultWithAllFields,
          fc.double({ min: 0, max: 0.49, noNaN: true }),
          (rawResult, timeConfidence) => {
            const confidences: FieldConfidences = {
              date: 0.9,       // high confidence — should be kept
              time: timeConfidence,
              recurrence: 0.9, // high confidence — should be kept
            };

            const filtered = applyLowConfidenceOmission(rawResult, confidences);

            // Time must be omitted (Req 3.5, 3.6)
            expect(filtered.time).toBeUndefined();

            // Date and recurrence should still be present
            expect(filtered.date).toBeDefined();
            expect(filtered.recurrence).toBeDefined();
          }
        ),
        { numRuns: 200 }
      );
    });

    it("when recurrence confidence < 0.5, recurrence field is omitted from output", () => {
      fc.assert(
        fc.property(
          arbRawParseResultWithAllFields,
          fc.double({ min: 0, max: 0.49, noNaN: true }),
          (rawResult, recurrenceConfidence) => {
            const confidences: FieldConfidences = {
              date: 0.9,       // high confidence — should be kept
              time: 0.9,       // high confidence — should be kept
              recurrence: recurrenceConfidence,
            };

            const filtered = applyLowConfidenceOmission(rawResult, confidences);

            // Recurrence must be omitted (Req 4.5)
            expect(filtered.recurrence).toBeUndefined();

            // Date and time should still be present
            expect(filtered.date).toBeDefined();
            expect(filtered.time).toBeDefined();
          }
        ),
        { numRuns: 200 }
      );
    });

    it("when ALL field confidences < 0.5, ALL temporal/recurrence fields are omitted", () => {
      fc.assert(
        fc.property(
          arbRawParseResultWithAllFields,
          arbAllLowConfidence,
          (rawResult, confidences) => {
            const filtered = applyLowConfidenceOmission(rawResult, confidences);

            // All temporal/recurrence fields must be omitted
            expect(filtered.date).toBeUndefined();
            expect(filtered.time).toBeUndefined();
            expect(filtered.recurrence).toBeUndefined();

            // Non-temporal fields preserved
            expect(filtered.title).toBe(rawResult.title);
            expect(filtered.confidence).toBe(rawResult.confidence);
            if (rawResult.description !== undefined) {
              expect(filtered.description).toBe(rawResult.description);
            }
          }
        ),
        { numRuns: 200 }
      );
    });
  });

  describe("Fields with confidence >= 0.5 are preserved", () => {
    it("when ALL field confidences >= 0.5, ALL temporal/recurrence fields are preserved", () => {
      fc.assert(
        fc.property(
          arbRawParseResultWithAllFields,
          arbAllHighConfidence,
          (rawResult, confidences) => {
            const filtered = applyLowConfidenceOmission(rawResult, confidences);

            // All fields should be preserved
            expect(filtered.date).toBe(rawResult.date);
            expect(filtered.time).toBe(rawResult.time);
            expect(filtered.recurrence).toEqual(rawResult.recurrence);
            expect(filtered.title).toBe(rawResult.title);
            expect(filtered.confidence).toBe(rawResult.confidence);
          }
        ),
        { numRuns: 200 }
      );
    });

    it("confidence exactly at 0.5 preserves the field (threshold is exclusive below)", () => {
      fc.assert(
        fc.property(arbRawParseResultWithAllFields, (rawResult) => {
          const confidences: FieldConfidences = {
            date: 0.5,
            time: 0.5,
            recurrence: 0.5,
          };

          const filtered = applyLowConfidenceOmission(rawResult, confidences);

          // Exactly 0.5 means ≥ threshold → field preserved
          expect(filtered.date).toBe(rawResult.date);
          expect(filtered.time).toBe(rawResult.time);
          expect(filtered.recurrence).toEqual(rawResult.recurrence);
        }),
        { numRuns: 100 }
      );
    });
  });

  describe("Mixed confidence scenarios", () => {
    it("each field is independently evaluated — omission of one does not affect others", () => {
      fc.assert(
        fc.property(
          arbRawParseResultWithAllFields,
          arbFieldConfidences,
          (rawResult, confidences) => {
            const filtered = applyLowConfidenceOmission(rawResult, confidences);

            // Each field independently follows the threshold rule
            if (confidences.date < CONFIDENCE_THRESHOLD) {
              expect(filtered.date).toBeUndefined();
            } else {
              expect(filtered.date).toBe(rawResult.date);
            }

            if (confidences.time < CONFIDENCE_THRESHOLD) {
              expect(filtered.time).toBeUndefined();
            } else {
              expect(filtered.time).toBe(rawResult.time);
            }

            if (confidences.recurrence < CONFIDENCE_THRESHOLD) {
              expect(filtered.recurrence).toBeUndefined();
            } else {
              expect(filtered.recurrence).toEqual(rawResult.recurrence);
            }

            // Non-threshold fields always preserved
            expect(filtered.title).toBe(rawResult.title);
            expect(filtered.confidence).toBe(rawResult.confidence);
          }
        ),
        { numRuns: 300 }
      );
    });
  });

  describe("Non-temporal fields are never affected by confidence filtering", () => {
    it("title, description, tags, priority, and detectedLanguage are never omitted by confidence filter", () => {
      const arbFullResult: fc.Arbitrary<RawParseResult> = fc.record(
        {
          title: fc.string({ minLength: 1, maxLength: 200 }),
          confidence: fc.double({ min: 0, max: 1, noNaN: true }),
          date: arbValidDate,
          time: arbValidTime,
          recurrence: arbRecurrence,
          description: fc.string({ minLength: 1, maxLength: 1000 }),
          detectedLanguage: fc.string({ minLength: 2, maxLength: 10 }),
          priority: fc.integer({ min: 1, max: 4 }),
          tags: fc.array(fc.string({ minLength: 1, maxLength: 50 }), { minLength: 1, maxLength: 10 }),
        },
        { requiredKeys: ["title", "confidence", "date", "time", "recurrence", "description", "detectedLanguage", "priority", "tags"] }
      );

      fc.assert(
        fc.property(
          arbFullResult,
          arbAllLowConfidence,
          (rawResult, confidences) => {
            const filtered = applyLowConfidenceOmission(rawResult, confidences);

            // Non-temporal fields are ALWAYS preserved regardless of confidence
            expect(filtered.title).toBe(rawResult.title);
            expect(filtered.confidence).toBe(rawResult.confidence);
            expect(filtered.description).toBe(rawResult.description);
            expect(filtered.detectedLanguage).toBe(rawResult.detectedLanguage);
            expect(filtered.priority).toBe(rawResult.priority);
            expect(filtered.tags).toEqual(rawResult.tags);
          }
        ),
        { numRuns: 200 }
      );
    });
  });

  describe("Absent fields remain absent (no fabrication)", () => {
    it("if a temporal field was already absent, it remains absent regardless of confidence", () => {
      const arbMinimalResult: fc.Arbitrary<RawParseResult> = fc.record(
        {
          title: fc.string({ minLength: 1, maxLength: 200 }),
          confidence: fc.double({ min: 0, max: 1, noNaN: true }),
        },
        { requiredKeys: ["title", "confidence"] }
      );

      fc.assert(
        fc.property(
          arbMinimalResult,
          arbFieldConfidences,
          (rawResult, confidences) => {
            const filtered = applyLowConfidenceOmission(rawResult, confidences);

            // No fields should be fabricated
            expect(filtered.date).toBeUndefined();
            expect(filtered.time).toBeUndefined();
            expect(filtered.recurrence).toBeUndefined();

            // Required fields preserved
            expect(filtered.title).toBe(rawResult.title);
            expect(filtered.confidence).toBe(rawResult.confidence);
          }
        ),
        { numRuns: 100 }
      );
    });
  });
});
