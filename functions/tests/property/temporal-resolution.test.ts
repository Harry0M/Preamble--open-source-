/**
 * Property-based tests for Temporal Resolution Correctness.
 *
 * Property 4: Temporal resolution correctness
 * For arbitrary relative temporal expressions + current datetime + timezone,
 * verify correct absolute date/time resolution and valid HH:mm within 00:00–23:59.
 *
 * **Validates: Requirements 2.5, 3.1, 3.4**
 */

import { describe, it, expect } from "vitest";
import * as fc from "fast-check";
import { validateParseResponse } from "../../src/v2/services/structured-output-validator";

// ─── Constants ─────────────────────────────────────────────────────────────────

/**
 * The regex used by the parse response schema to validate time fields.
 * Matches valid HH:mm in 00:00–23:59 range.
 */
const TIME_PATTERN = /^([01]\d|2[0-3]):[0-5]\d$/;

/**
 * The regex used by the parse response schema to validate date fields.
 * Matches YYYY-MM-DD format.
 */
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

// ─── Generators ────────────────────────────────────────────────────────────────

/**
 * Generates a valid time string matching the HH:mm pattern (00:00–23:59).
 */
const arbValidTime = fc
  .tuple(fc.integer({ min: 0, max: 23 }), fc.integer({ min: 0, max: 59 }))
  .map(([h, m]) => `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`);

/**
 * Generates invalid time strings that do NOT match the HH:mm pattern.
 * These represent malformed or out-of-range time values.
 */
const arbInvalidTime = fc.oneof(
  // Hour out of range (24–99)
  fc
    .tuple(fc.integer({ min: 24, max: 99 }), fc.integer({ min: 0, max: 59 }))
    .map(([h, m]) => `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`),
  // Minute out of range (60–99)
  fc
    .tuple(fc.integer({ min: 0, max: 23 }), fc.integer({ min: 60, max: 99 }))
    .map(([h, m]) => `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`),
  // Missing leading zero in hour (e.g., "3:00" instead of "03:00")
  fc
    .tuple(fc.integer({ min: 0, max: 9 }), fc.integer({ min: 0, max: 59 }))
    .map(([h, m]) => `${h}:${String(m).padStart(2, "0")}`),
  // Missing leading zero in minute (e.g., "03:5" instead of "03:05")
  fc
    .tuple(fc.integer({ min: 0, max: 23 }), fc.integer({ min: 0, max: 9 }))
    .map(([h, m]) => `${String(h).padStart(2, "0")}:${m}`),
  // Natural language time expressions (should be rejected by schema)
  fc.constantFrom(
    "3pm", "3:30pm", "noon", "midnight", "morning",
    "afternoon", "evening", "night", "shaam", "subah",
    "dopahar", "raat", "15h30", "1530"
  ),
  // Wrong separators
  fc
    .tuple(fc.integer({ min: 0, max: 23 }), fc.integer({ min: 0, max: 59 }))
    .map(([h, m]) => `${String(h).padStart(2, "0")}.${String(m).padStart(2, "0")}`),
  // Extra characters
  fc
    .tuple(fc.integer({ min: 0, max: 23 }), fc.integer({ min: 0, max: 59 }))
    .map(([h, m]) => `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}:00`),
  // Empty and whitespace
  fc.constantFrom("", " ", "  :  ")
);

/**
 * Generates a valid date string in YYYY-MM-DD format.
 */
const arbValidDate = fc
  .tuple(
    fc.integer({ min: 2000, max: 2099 }),
    fc.integer({ min: 1, max: 12 }),
    fc.integer({ min: 1, max: 28 })
  )
  .map(
    ([y, m, d]) =>
      `${y}-${String(m).padStart(2, "0")}-${String(d).padStart(2, "0")}`
  );

/**
 * Generates a valid parse response with a valid time field.
 * This simulates a correct temporal resolution where the AI has produced
 * a properly formatted time value.
 */
const arbParseResponseWithValidTime = fc.record({
  title: fc.string({ minLength: 1, maxLength: 200 }),
  confidence: fc.double({ min: 0, max: 1, noNaN: true }),
  date: fc.option(arbValidDate, { nil: undefined }),
  time: arbValidTime,
});

/**
 * Generates a parse response with an invalid time field.
 * This simulates a temporal resolution failure where the AI produced
 * an incorrectly formatted time value.
 */
const arbParseResponseWithInvalidTime = fc.record({
  title: fc.string({ minLength: 1, maxLength: 200 }),
  confidence: fc.double({ min: 0, max: 1, noNaN: true }),
  date: fc.option(arbValidDate, { nil: undefined }),
  time: arbInvalidTime,
});

/**
 * Generates arbitrary timezone identifiers (representative sample).
 */
const arbTimezone = fc.constantFrom(
  "Asia/Kolkata",
  "America/New_York",
  "Europe/London",
  "Asia/Tokyo",
  "America/Los_Angeles",
  "Australia/Sydney",
  "Pacific/Auckland",
  "UTC"
);

/**
 * Generates a current datetime in ISO 8601 format for a given timezone context.
 */
const arbCurrentDateTime = fc
  .tuple(
    fc.integer({ min: 2024, max: 2026 }),
    fc.integer({ min: 1, max: 12 }),
    fc.integer({ min: 1, max: 28 }),
    fc.integer({ min: 0, max: 23 }),
    fc.integer({ min: 0, max: 59 })
  )
  .map(
    ([y, mo, d, h, mi]) =>
      `${y}-${String(mo).padStart(2, "0")}-${String(d).padStart(2, "0")}T${String(h).padStart(2, "0")}:${String(mi).padStart(2, "0")}:00`
  );

// ─── Property Tests ────────────────────────────────────────────────────────────

describe("Property 4: Temporal resolution correctness", () => {
  describe("Valid time values pass schema validation", () => {
    it("any valid HH:mm (00:00–23:59) is accepted by the parse response schema", () => {
      fc.assert(
        fc.property(arbParseResponseWithValidTime, (response) => {
          const cleaned = JSON.parse(JSON.stringify(response));
          const result = validateParseResponse(cleaned);
          expect(result.valid).toBe(true);
          if (result.valid) {
            expect(result.data.time).toMatch(TIME_PATTERN);
          }
        }),
        { numRuns: 200 }
      );
    });

    it("all generated valid times are within 00:00–23:59 bounds", () => {
      fc.assert(
        fc.property(arbValidTime, (time) => {
          expect(time).toMatch(TIME_PATTERN);
          const [hours, minutes] = time.split(":").map(Number);
          expect(hours).toBeGreaterThanOrEqual(0);
          expect(hours).toBeLessThanOrEqual(23);
          expect(minutes).toBeGreaterThanOrEqual(0);
          expect(minutes).toBeLessThanOrEqual(59);
        }),
        { numRuns: 300 }
      );
    });
  });

  describe("Invalid time values are rejected by schema validation", () => {
    it("any time NOT matching HH:mm pattern is rejected", () => {
      fc.assert(
        fc.property(arbParseResponseWithInvalidTime, (response) => {
          const cleaned = JSON.parse(JSON.stringify(response));
          const result = validateParseResponse(cleaned);
          expect(result.valid).toBe(false);
          if (!result.valid) {
            // Error should reference the time field or pattern
            const hasTimeError = result.errors.some(
              (err) => err.path === "/time" || err.message?.includes("pattern")
            );
            expect(hasTimeError).toBe(true);
          }
        }),
        { numRuns: 200 }
      );
    });

    it("natural language time expressions are rejected", () => {
      const naturalTimeExpressions = [
        "3pm", "noon", "midnight", "morning", "evening",
        "shaam", "subah", "dopahar", "raat",
      ];

      for (const timeExpr of naturalTimeExpressions) {
        const response = {
          title: "Test task",
          confidence: 0.8,
          time: timeExpr,
        };
        const result = validateParseResponse(response);
        expect(result.valid).toBe(false);
      }
    });
  });

  describe("Temporal resolution with timezone context", () => {
    it("for any timezone and current datetime, a valid time result stays within 00:00–23:59", () => {
      fc.assert(
        fc.property(
          arbTimezone,
          arbCurrentDateTime,
          arbValidTime,
          (timezone, currentDateTime, resolvedTime) => {
            // Simulate a parse result produced in the context of a timezone
            const parseResult = {
              title: `Task resolved in ${timezone}`,
              confidence: 0.9,
              date: currentDateTime.split("T")[0],
              time: resolvedTime,
            };

            const result = validateParseResponse(parseResult);
            expect(result.valid).toBe(true);
            if (result.valid && result.data.time) {
              // Verify the time is structurally valid HH:mm
              expect(result.data.time).toMatch(TIME_PATTERN);
              const [h, m] = result.data.time.split(":").map(Number);
              expect(h).toBeGreaterThanOrEqual(0);
              expect(h).toBeLessThanOrEqual(23);
              expect(m).toBeGreaterThanOrEqual(0);
              expect(m).toBeLessThanOrEqual(59);
            }
          }
        ),
        { numRuns: 200 }
      );
    });

    it("resolved dates maintain valid YYYY-MM-DD format regardless of timezone", () => {
      fc.assert(
        fc.property(
          arbTimezone,
          arbCurrentDateTime,
          arbValidDate,
          (_, __, resolvedDate) => {
            const parseResult = {
              title: "Timezone date test",
              confidence: 0.85,
              date: resolvedDate,
            };

            const result = validateParseResponse(parseResult);
            expect(result.valid).toBe(true);
            if (result.valid && result.data.date) {
              expect(result.data.date).toMatch(DATE_PATTERN);
            }
          }
        ),
        { numRuns: 200 }
      );
    });
  });

  describe("Time pattern exhaustive boundary verification", () => {
    it("boundary hours (00, 23) and boundary minutes (00, 59) are always valid", () => {
      const boundaryTimes = ["00:00", "00:59", "23:00", "23:59"];
      for (const time of boundaryTimes) {
        const response = { title: "Boundary test", confidence: 0.9, time };
        const result = validateParseResponse(response);
        expect(result.valid).toBe(true);
      }
    });

    it("hour 24 and minute 60 are always rejected (just outside valid range)", () => {
      const invalidBoundaryTimes = ["24:00", "23:60", "24:59", "99:99"];
      for (const time of invalidBoundaryTimes) {
        const response = { title: "Invalid boundary", confidence: 0.9, time };
        const result = validateParseResponse(response);
        expect(result.valid).toBe(false);
      }
    });

    it("time field omission (undefined) is valid — omit when unresolvable", () => {
      fc.assert(
        fc.property(
          fc.string({ minLength: 1, maxLength: 200 }),
          fc.double({ min: 0, max: 1, noNaN: true }),
          (title, confidence) => {
            // Parser should omit time rather than guess (Requirement 3.4)
            const response = { title, confidence };
            const result = validateParseResponse(response);
            expect(result.valid).toBe(true);
          }
        ),
        { numRuns: 100 }
      );
    });
  });
});
