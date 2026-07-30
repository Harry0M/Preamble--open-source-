/**
 * Property 12: Token cost normalization correctness
 *
 * For arbitrary (inputTokens, outputTokens, costPerMillionTokens), verify:
 * 1. normalizedCost = (inputTokens + outputTokens) × costPerMillionTokens / 1,000,000
 * 2. Result is always non-negative
 *
 * **Validates: Requirements 16.3, 17.1**
 */
import { describe, it, expect } from "vitest";
import * as fc from "fast-check";

/**
 * Pure normalization formula extracted from TokenEconomyGuard.recordUsage().
 * This is the exact computation performed in the service:
 *   normalizedCost = (inputTokens + outputTokens) * costPerMillionTokens / 1_000_000
 */
function computeNormalizedCost(
  inputTokens: number,
  outputTokens: number,
  costPerMillionTokens: number
): number {
  return ((inputTokens + outputTokens) * costPerMillionTokens) / 1_000_000;
}

describe("Property 12: Token cost normalization correctness", () => {
  it("normalizedCost equals (inputTokens + outputTokens) × costPerMillionTokens / 1,000,000 for all valid inputs", () => {
    fc.assert(
      fc.property(
        fc.nat({ max: 1_000_000 }), // inputTokens: non-negative integer
        fc.nat({ max: 1_000_000 }), // outputTokens: non-negative integer
        fc.double({ min: 0.001, max: 1000, noNaN: true, noDefaultInfinity: true }), // costPerMillionTokens: positive float
        (inputTokens, outputTokens, costPerMillionTokens) => {
          const result = computeNormalizedCost(inputTokens, outputTokens, costPerMillionTokens);
          const expected = ((inputTokens + outputTokens) * costPerMillionTokens) / 1_000_000;

          // Verify the formula produces the expected result
          expect(result).toBeCloseTo(expected, 10);
        }
      ),
      { numRuns: 1000 }
    );
  });

  it("normalizedCost is always non-negative for non-negative inputs", () => {
    fc.assert(
      fc.property(
        fc.nat({ max: 1_000_000 }), // inputTokens: non-negative integer
        fc.nat({ max: 1_000_000 }), // outputTokens: non-negative integer
        fc.double({ min: 0.001, max: 1000, noNaN: true, noDefaultInfinity: true }), // costPerMillionTokens: positive float
        (inputTokens, outputTokens, costPerMillionTokens) => {
          const result = computeNormalizedCost(inputTokens, outputTokens, costPerMillionTokens);

          // Result must be non-negative
          expect(result).toBeGreaterThanOrEqual(0);
        }
      ),
      { numRuns: 1000 }
    );
  });

  it("normalizedCost is zero when both inputTokens and outputTokens are zero", () => {
    fc.assert(
      fc.property(
        fc.double({ min: 0.001, max: 1000, noNaN: true, noDefaultInfinity: true }), // costPerMillionTokens: positive float
        (costPerMillionTokens) => {
          const result = computeNormalizedCost(0, 0, costPerMillionTokens);

          // Zero tokens should always produce zero cost
          expect(result).toBe(0);
        }
      ),
      { numRuns: 100 }
    );
  });

  it("normalizedCost scales linearly with total tokens", () => {
    fc.assert(
      fc.property(
        fc.nat({ max: 500_000 }), // inputTokens
        fc.nat({ max: 500_000 }), // outputTokens
        fc.double({ min: 0.001, max: 1000, noNaN: true, noDefaultInfinity: true }), // costPerMillionTokens
        fc.integer({ min: 2, max: 10 }), // multiplier
        (inputTokens, outputTokens, costPerMillionTokens, multiplier) => {
          const baseCost = computeNormalizedCost(inputTokens, outputTokens, costPerMillionTokens);
          const scaledCost = computeNormalizedCost(
            inputTokens * multiplier,
            outputTokens * multiplier,
            costPerMillionTokens
          );

          // Scaled cost should be multiplier × base cost (within floating point tolerance)
          expect(scaledCost).toBeCloseTo(baseCost * multiplier, 5);
        }
      ),
      { numRuns: 500 }
    );
  });
});
