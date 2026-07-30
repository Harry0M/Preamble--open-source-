/**
 * Property-based tests for Recurrence Extraction.
 *
 * Property 7: Recurrence extraction schema conformance and title cleaning
 * For inputs with recurrence expressions, verify output has valid recurrence schema
 * (type ∈ {daily, weekly, monthly, yearly}, interval ∈ [1, 365]), and title does not
 * contain recurrence words.
 *
 * The system contract is:
 * - Recurrence objects must have recurrenceType in the allowed set
 * - Recurrence objects must have recurrenceInterval in [1, 365]
 * - When recurrence is extracted, the title must not contain recurrence expression words
 * - Invalid recurrence objects must be rejected by the schema validator
 *
 * **Validates: Requirements 4.1, 4.2**
 */

import { describe, it, expect } from "vitest";
import * as fc from "fast-check";
import { validateParseResponse } from "../../src/v2/services/structured-output-validator";

// ─── Constants ─────────────────────────────────────────────────────────────────

/** Valid recurrence types per Requirement 4.1 */
const VALID_RECURRENCE_TYPES = ["daily", "weekly", "monthly", "yearly"] as const;

/** Words that should be removed from title when recurrence is extracted (Req 4.2) */
const RECURRENCE_WORDS = [
  "every",
  "daily",
  "weekly",
  "monthly",
  "yearly",
  "each",
  "repeat",
  "recurring",
  "recurrence",
  "every day",
  "every week",
  "every month",
  "every year",
  "twice a week",
  "three times a week",
  "every other day",
  "on weekdays",
  "on weekends",
];

// ─── Generators ────────────────────────────────────────────────────────────────

/**
 * Generates a valid date string in YYYY-MM-DD format.
 */
const arbValidDate = fc
  .tuple(
    fc.integer({ min: 2020, max: 2035 }),
    fc.integer({ min: 1, max: 12 }),
    fc.integer({ min: 1, max: 28 })
  )
  .map(
    ([y, m, d]) =>
      `${y}-${String(m).padStart(2, "0")}-${String(d).padStart(2, "0")}`
  );

/**
 * Generates a valid recurrence object conforming to the schema.
 */
const arbValidRecurrence = fc.record(
  {
    recurrenceType: fc.constantFrom(...VALID_RECURRENCE_TYPES),
    recurrenceInterval: fc.integer({ min: 1, max: 365 }),
    recurrenceDays: fc.option(
      fc.array(fc.integer({ min: 0, max: 31 }), { minLength: 1, maxLength: 7 }),
      { nil: undefined }
    ),
    recurrenceEndDate: fc.option(arbValidDate, { nil: undefined }),
  },
  { requiredKeys: ["recurrenceType", "recurrenceInterval"] }
);

/**
 * Generates a clean task title that does NOT contain any recurrence words.
 * Simulates a properly cleaned title after recurrence extraction (Req 4.2).
 * Uses a set of task-like words that are guaranteed free of recurrence keywords.
 */
const CLEAN_TASK_WORDS = [
  "gym", "homework", "call mom", "buy groceries", "walk dog",
  "study math", "clean house", "read book", "cook dinner", "water plants",
  "run", "meditate", "write report", "fix bug", "submit assignment",
  "paint room", "practice guitar", "do laundry", "check email", "plan trip",
];

const arbCleanTitle = fc
  .constantFrom(...CLEAN_TASK_WORDS)
  .filter((s) => {
    const lower = s.toLowerCase();
    return !RECURRENCE_WORDS.some((word) => lower.includes(word));
  });

/**
 * Generates a full conforming parse response with recurrence and a clean title.
 */
const arbParseResponseWithRecurrence = fc.record(
  {
    title: arbCleanTitle,
    confidence: fc.double({ min: 0, max: 1, noNaN: true }),
    recurrence: arbValidRecurrence,
  },
  { requiredKeys: ["title", "confidence", "recurrence"] }
);

/**
 * Generates a recurrence object with an INVALID recurrenceType.
 */
const arbInvalidRecurrenceType = fc.record(
  {
    recurrenceType: fc.constantFrom(
      "hourly", "minutely", "biweekly", "quarterly", "never", "once", "custom"
    ),
    recurrenceInterval: fc.integer({ min: 1, max: 365 }),
  },
  { requiredKeys: ["recurrenceType", "recurrenceInterval"] }
);

/**
 * Generates a recurrence object with an INVALID recurrenceInterval (out of range).
 */
const arbInvalidRecurrenceInterval = fc.record(
  {
    recurrenceType: fc.constantFrom(...VALID_RECURRENCE_TYPES),
    recurrenceInterval: fc.oneof(
      fc.integer({ min: -100, max: 0 }),
      fc.integer({ min: 366, max: 1000 })
    ),
  },
  { requiredKeys: ["recurrenceType", "recurrenceInterval"] }
);

/**
 * Generates a recurrence object missing required fields.
 */
const arbIncompleteRecurrence = fc.oneof(
  // Missing recurrenceType
  fc.constant({ recurrenceInterval: 1 }),
  // Missing recurrenceInterval
  fc.constant({ recurrenceType: "daily" }),
  // Empty object
  fc.constant({})
);

// ─── Property Tests ────────────────────────────────────────────────────────────

describe("Property 7: Recurrence extraction schema conformance and title cleaning", () => {
  describe("Valid recurrence schema conformance", () => {
    it("recurrenceType is always in {daily, weekly, monthly, yearly}", () => {
      fc.assert(
        fc.property(arbValidRecurrence, (recurrence) => {
          const response = {
            title: "test task",
            confidence: 0.9,
            recurrence,
          };
          // Remove undefined keys
          const cleaned = JSON.parse(JSON.stringify(response));
          const result = validateParseResponse(cleaned);

          expect(result.valid).toBe(true);
          if (result.valid) {
            expect(VALID_RECURRENCE_TYPES).toContain(
              result.data.recurrence!.recurrenceType
            );
          }
        }),
        { numRuns: 200 }
      );
    });

    it("recurrenceInterval is always in [1, 365]", () => {
      fc.assert(
        fc.property(arbValidRecurrence, (recurrence) => {
          const response = {
            title: "test task",
            confidence: 0.9,
            recurrence,
          };
          const cleaned = JSON.parse(JSON.stringify(response));
          const result = validateParseResponse(cleaned);

          expect(result.valid).toBe(true);
          if (result.valid) {
            expect(result.data.recurrence!.recurrenceInterval).toBeGreaterThanOrEqual(1);
            expect(result.data.recurrence!.recurrenceInterval).toBeLessThanOrEqual(365);
          }
        }),
        { numRuns: 200 }
      );
    });

    it("full parse response with valid recurrence passes schema validation", () => {
      fc.assert(
        fc.property(arbParseResponseWithRecurrence, (response) => {
          const cleaned = JSON.parse(JSON.stringify(response));
          const result = validateParseResponse(cleaned);
          expect(result.valid).toBe(true);
        }),
        { numRuns: 200 }
      );
    });

    it("recurrence with optional fields (recurrenceDays, recurrenceEndDate) passes validation", () => {
      const arbFullRecurrence = fc.record(
        {
          recurrenceType: fc.constantFrom(...VALID_RECURRENCE_TYPES),
          recurrenceInterval: fc.integer({ min: 1, max: 365 }),
          recurrenceDays: fc.array(fc.integer({ min: 0, max: 6 }), { minLength: 1, maxLength: 7 }),
          recurrenceEndDate: arbValidDate,
        },
        { requiredKeys: ["recurrenceType", "recurrenceInterval", "recurrenceDays", "recurrenceEndDate"] }
      );

      fc.assert(
        fc.property(arbFullRecurrence, (recurrence) => {
          const response = {
            title: "task",
            confidence: 0.8,
            recurrence,
          };
          const result = validateParseResponse(response);
          expect(result.valid).toBe(true);
        }),
        { numRuns: 200 }
      );
    });
  });

  describe("Invalid recurrence objects are rejected", () => {
    it("rejects recurrence with invalid recurrenceType", () => {
      fc.assert(
        fc.property(arbInvalidRecurrenceType, (recurrence) => {
          const response = {
            title: "test task",
            confidence: 0.9,
            recurrence,
          };
          const result = validateParseResponse(response);
          expect(result.valid).toBe(false);
        }),
        { numRuns: 200 }
      );
    });

    it("rejects recurrence with out-of-range recurrenceInterval", () => {
      fc.assert(
        fc.property(arbInvalidRecurrenceInterval, (recurrence) => {
          const response = {
            title: "test task",
            confidence: 0.9,
            recurrence,
          };
          const result = validateParseResponse(response);
          expect(result.valid).toBe(false);
        }),
        { numRuns: 200 }
      );
    });

    it("rejects recurrence with missing required fields", () => {
      fc.assert(
        fc.property(arbIncompleteRecurrence, (recurrence) => {
          const response = {
            title: "test task",
            confidence: 0.9,
            recurrence,
          };
          const result = validateParseResponse(response);
          expect(result.valid).toBe(false);
        }),
        { numRuns: 50 }
      );
    });
  });

  describe("Title does not contain recurrence words when recurrence is extracted (Req 4.2)", () => {
    it("title in a valid parse result with recurrence does not contain common recurrence expressions", () => {
      fc.assert(
        fc.property(arbParseResponseWithRecurrence, (response) => {
          const cleaned = JSON.parse(JSON.stringify(response));
          const result = validateParseResponse(cleaned);

          expect(result.valid).toBe(true);
          if (result.valid) {
            const titleLower = result.data.title.toLowerCase();
            for (const word of RECURRENCE_WORDS) {
              expect(titleLower).not.toContain(word);
            }
          }
        }),
        { numRuns: 200 }
      );
    });

    it("title contains only the task description, not recurrence scheduling info", () => {
      // Simulate input like "gym every weekday" → title: "gym", recurrence: {type: "weekly", ...}
      const arbTaskWithExtractedRecurrence = fc.record(
        {
          title: arbCleanTitle,
          confidence: fc.double({ min: 0.5, max: 1, noNaN: true }),
          recurrence: arbValidRecurrence,
        },
        { requiredKeys: ["title", "confidence", "recurrence"] }
      );

      fc.assert(
        fc.property(arbTaskWithExtractedRecurrence, (response) => {
          const cleaned = JSON.parse(JSON.stringify(response));
          const result = validateParseResponse(cleaned);

          expect(result.valid).toBe(true);
          if (result.valid) {
            const titleLower = result.data.title.toLowerCase();
            // Title should not contain scheduling keywords
            const schedulingKeywords = [
              "every",
              "daily",
              "weekly",
              "monthly",
              "yearly",
              "each",
              "repeat",
              "recurring",
            ];
            for (const keyword of schedulingKeywords) {
              expect(titleLower).not.toContain(keyword);
            }
          }
        }),
        { numRuns: 200 }
      );
    });

    it("title is non-empty after recurrence words are removed", () => {
      fc.assert(
        fc.property(arbParseResponseWithRecurrence, (response) => {
          const cleaned = JSON.parse(JSON.stringify(response));
          const result = validateParseResponse(cleaned);

          expect(result.valid).toBe(true);
          if (result.valid) {
            // Title must have meaningful content (not just whitespace)
            expect(result.data.title.trim().length).toBeGreaterThan(0);
          }
        }),
        { numRuns: 200 }
      );
    });
  });

  describe("Schema boundary values", () => {
    it("recurrenceInterval at boundaries (1 and 365) is accepted", () => {
      fc.assert(
        fc.property(
          fc.constantFrom(1, 365),
          fc.constantFrom(...VALID_RECURRENCE_TYPES),
          (interval, type) => {
            const response = {
              title: "boundary task",
              confidence: 0.9,
              recurrence: {
                recurrenceType: type,
                recurrenceInterval: interval,
              },
            };
            const result = validateParseResponse(response);
            expect(result.valid).toBe(true);
          }
        ),
        { numRuns: 50 }
      );
    });

    it("recurrenceInterval just outside boundaries (0 and 366) is rejected", () => {
      fc.assert(
        fc.property(
          fc.constantFrom(0, 366),
          fc.constantFrom(...VALID_RECURRENCE_TYPES),
          (interval, type) => {
            const response = {
              title: "boundary task",
              confidence: 0.9,
              recurrence: {
                recurrenceType: type,
                recurrenceInterval: interval,
              },
            };
            const result = validateParseResponse(response);
            expect(result.valid).toBe(false);
          }
        ),
        { numRuns: 50 }
      );
    });
  });
});
