/**
 * Property Test: Parse output schema conformance
 *
 * Generates arbitrary valid ParseResult objects and verifies the
 * StructuredOutputValidator accepts them and the output conforms to:
 * - title ≤200 chars
 * - priority 1–4
 * - tags ≤10 items, each ≤50 chars
 * - description ≤1000 chars
 * - confidence in [0.0, 1.0]
 *
 * **Validates: Requirements 1.1, 1.4, 2.1**
 */

import { describe, it, expect } from "vitest";
import * as fc from "fast-check";
import { validateParseResponse } from "../../src/v2/services/structured-output-validator";
import { arbValidParseResponse } from "../helpers/arbitraries";

describe("Property 1: Parse output schema conformance", () => {
  it("valid parse responses are accepted by the validator and conform to field constraints", () => {
    fc.assert(
      fc.property(arbValidParseResponse, (response) => {
        const result = validateParseResponse(response);

        // Validator must accept all conforming objects
        expect(result.valid).toBe(true);

        if (!result.valid) return; // Type narrowing

        const { data } = result;

        // title ≤ 200 characters
        expect(data.title.length).toBeLessThanOrEqual(200);

        // priority 1–4 (when present)
        if (data.priority !== undefined) {
          expect(data.priority).toBeGreaterThanOrEqual(1);
          expect(data.priority).toBeLessThanOrEqual(4);
        }

        // tags ≤ 10 items, each ≤ 50 chars (when present)
        if (data.tags !== undefined) {
          expect(data.tags.length).toBeLessThanOrEqual(10);
          for (const tag of data.tags) {
            expect(tag.length).toBeLessThanOrEqual(50);
          }
        }

        // description ≤ 1000 characters (when present)
        if (data.description !== undefined) {
          expect(data.description.length).toBeLessThanOrEqual(1000);
        }

        // confidence in [0.0, 1.0]
        expect(data.confidence).toBeGreaterThanOrEqual(0.0);
        expect(data.confidence).toBeLessThanOrEqual(1.0);
      }),
      { numRuns: 200 }
    );
  });

  it("objects violating title length constraint are rejected", () => {
    const arbLongTitle = fc.record(
      {
        title: fc.string({ minLength: 201, maxLength: 500 }),
        confidence: fc.double({ min: 0, max: 1, noNaN: true }),
      },
      { requiredKeys: ["title", "confidence"] }
    );

    fc.assert(
      fc.property(arbLongTitle, (response) => {
        const result = validateParseResponse(response);
        expect(result.valid).toBe(false);
      }),
      { numRuns: 50 }
    );
  });

  it("objects violating priority range are rejected", () => {
    const arbBadPriority = fc.record(
      {
        title: fc.string({ minLength: 1, maxLength: 200 }),
        confidence: fc.double({ min: 0, max: 1, noNaN: true }),
        priority: fc.oneof(
          fc.integer({ min: -100, max: 0 }),
          fc.integer({ min: 5, max: 100 })
        ),
      },
      { requiredKeys: ["title", "confidence", "priority"] }
    );

    fc.assert(
      fc.property(arbBadPriority, (response) => {
        const result = validateParseResponse(response);
        expect(result.valid).toBe(false);
      }),
      { numRuns: 50 }
    );
  });

  it("objects violating tags constraints are rejected (too many items)", () => {
    const arbTooManyTags = fc.record(
      {
        title: fc.string({ minLength: 1, maxLength: 200 }),
        confidence: fc.double({ min: 0, max: 1, noNaN: true }),
        tags: fc.array(fc.string({ minLength: 1, maxLength: 50 }), {
          minLength: 11,
          maxLength: 20,
        }),
      },
      { requiredKeys: ["title", "confidence", "tags"] }
    );

    fc.assert(
      fc.property(arbTooManyTags, (response) => {
        const result = validateParseResponse(response);
        expect(result.valid).toBe(false);
      }),
      { numRuns: 50 }
    );
  });

  it("objects violating tag item length are rejected", () => {
    const arbLongTag = fc.record(
      {
        title: fc.string({ minLength: 1, maxLength: 200 }),
        confidence: fc.double({ min: 0, max: 1, noNaN: true }),
        tags: fc
          .array(fc.string({ minLength: 51, maxLength: 100 }), {
            minLength: 1,
            maxLength: 5,
          }),
      },
      { requiredKeys: ["title", "confidence", "tags"] }
    );

    fc.assert(
      fc.property(arbLongTag, (response) => {
        const result = validateParseResponse(response);
        expect(result.valid).toBe(false);
      }),
      { numRuns: 50 }
    );
  });

  it("objects violating description length are rejected", () => {
    const arbLongDescription = fc.record(
      {
        title: fc.string({ minLength: 1, maxLength: 200 }),
        confidence: fc.double({ min: 0, max: 1, noNaN: true }),
        description: fc.string({ minLength: 1001, maxLength: 2000 }),
      },
      { requiredKeys: ["title", "confidence", "description"] }
    );

    fc.assert(
      fc.property(arbLongDescription, (response) => {
        const result = validateParseResponse(response);
        expect(result.valid).toBe(false);
      }),
      { numRuns: 50 }
    );
  });

  it("objects violating confidence range are rejected", () => {
    const arbBadConfidence = fc.record(
      {
        title: fc.string({ minLength: 1, maxLength: 200 }),
        confidence: fc.oneof(
          fc.double({ min: -100, max: -0.01, noNaN: true }),
          fc.double({ min: 1.01, max: 100, noNaN: true })
        ),
      },
      { requiredKeys: ["title", "confidence"] }
    );

    fc.assert(
      fc.property(arbBadConfidence, (response) => {
        const result = validateParseResponse(response);
        expect(result.valid).toBe(false);
      }),
      { numRuns: 50 }
    );
  });
});
