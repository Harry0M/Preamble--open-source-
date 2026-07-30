/**
 * Property 13: Token budget enforcement
 *
 * For arbitrary user consumption/budget combinations, verify requests rejected
 * when consumed ≥ budget OR estimated cost > remaining, and consumed + remaining = budget
 * invariant holds.
 *
 * **Validates: Requirements 17.2, 17.4, 17.5**
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import * as fc from "fast-check";

// ─── Mock Firestore ────────────────────────────────────────────────────────────

const mockGet = vi.fn();
const mockSet = vi.fn().mockResolvedValue(undefined);
const mockUpdate = vi.fn().mockResolvedValue(undefined);

const mockCollection = vi.fn();

vi.mock("firebase-admin/firestore", () => ({
  getFirestore: vi.fn(() => ({
    collection: mockCollection,
  })),
}));

import { checkBudget, getBalance } from "../../src/v2/services/token-economy-guard";
import { SubscriptionTier } from "../../src/v2/models/types";

// ─── Test Helpers ──────────────────────────────────────────────────────────────

const TIERS: SubscriptionTier[] = ["pro_student", "pro_youth", "pro_standard"];

/**
 * Sets up Firestore mocks with specific consumed/budget values for property testing.
 */
function setupMocks(config: {
  tier: SubscriptionTier;
  tierBudgets: Record<SubscriptionTier, number>;
  consumed: number;
  timezone?: string;
}) {
  const today = new Date().toISOString().split("T")[0];
  const { tier, tierBudgets, consumed, timezone = "UTC" } = config;

  mockCollection.mockImplementation((collection: string) => ({
    doc: vi.fn((docId?: string) => {
      if (collection === "v2_config" && docId === "models") {
        return {
          get: vi.fn().mockResolvedValue({
            exists: true,
            data: () => ({ tierBudgets }),
          }),
          set: mockSet,
          update: mockUpdate,
        };
      }
      if (collection === "v2_token_usage") {
        return {
          get: vi.fn().mockResolvedValue({
            exists: consumed > 0,
            data: () => (consumed > 0 ? { date: today, consumed } : null),
          }),
          set: mockSet,
          update: mockUpdate,
        };
      }
      if (collection === "users") {
        return {
          get: vi.fn().mockResolvedValue({
            exists: true,
            data: () => ({ subscriptionTier: tier, timezone }),
          }),
          set: mockSet,
          update: mockUpdate,
        };
      }
      return {
        get: vi.fn().mockResolvedValue({ exists: false, data: () => null }),
        set: mockSet,
        update: mockUpdate,
      };
    }),
  }));
}

// ─── Arbitraries ───────────────────────────────────────────────────────────────

/** Generates a valid subscription tier */
const arbTier = fc.constantFrom(...TIERS);

/** Generates a positive budget value (1 to 1,000,000 normalized tokens) */
const arbBudget = fc.integer({ min: 1, max: 1_000_000 });

/** Generates a non-negative consumed value */
const arbConsumed = fc.integer({ min: 0, max: 1_000_000 });

/** Generates a positive estimated cost */
const arbEstimatedCost = fc.integer({ min: 1, max: 500_000 });

// ─── Property Tests ────────────────────────────────────────────────────────────

describe("Property 13: Token budget enforcement", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("rejects requests when consumed >= budget (Req 17.2)", async () => {
    await fc.assert(
      fc.asyncProperty(
        arbTier,
        arbBudget,
        arbEstimatedCost,
        async (tier, budget, estimatedCost) => {
          // consumed equals budget (at limit)
          const consumed = budget;
          const tierBudgets: Record<SubscriptionTier, number> = {
            pro_student: tier === "pro_student" ? budget : 50000,
            pro_youth: tier === "pro_youth" ? budget : 100000,
            pro_standard: tier === "pro_standard" ? budget : 200000,
          };

          setupMocks({ tier, tierBudgets, consumed });

          const result = await checkBudget("prop-user", estimatedCost);

          // When consumed >= budget, any positive estimatedCost should be rejected
          expect(result.allowed).toBe(false);
        }
      ),
      { numRuns: 100 }
    );
  });

  it("rejects requests when consumed exceeds budget (over-budget, Req 17.2)", async () => {
    await fc.assert(
      fc.asyncProperty(
        arbTier,
        arbBudget,
        fc.integer({ min: 1, max: 500_000 }),
        arbEstimatedCost,
        async (tier, budget, overshoot, estimatedCost) => {
          // consumed is budget + overshoot (clearly over)
          const consumed = budget + overshoot;
          const tierBudgets: Record<SubscriptionTier, number> = {
            pro_student: tier === "pro_student" ? budget : 50000,
            pro_youth: tier === "pro_youth" ? budget : 100000,
            pro_standard: tier === "pro_standard" ? budget : 200000,
          };

          setupMocks({ tier, tierBudgets, consumed });

          const result = await checkBudget("prop-user", estimatedCost);

          expect(result.allowed).toBe(false);
        }
      ),
      { numRuns: 100 }
    );
  });

  it("rejects requests when estimated cost > remaining budget (Req 17.4)", async () => {
    await fc.assert(
      fc.asyncProperty(
        arbTier,
        arbBudget,
        async (tier, budget) => {
          // consumed is less than budget so there IS remaining budget
          const consumed = fc.sample(fc.integer({ min: 0, max: budget - 1 }), 1)[0];
          const remaining = budget - consumed;
          // estimatedCost exceeds what's remaining
          const estimatedCost = remaining + fc.sample(fc.integer({ min: 1, max: 100000 }), 1)[0];

          const tierBudgets: Record<SubscriptionTier, number> = {
            pro_student: tier === "pro_student" ? budget : 50000,
            pro_youth: tier === "pro_youth" ? budget : 100000,
            pro_standard: tier === "pro_standard" ? budget : 200000,
          };

          setupMocks({ tier, tierBudgets, consumed });

          const result = await checkBudget("prop-user", estimatedCost);

          // consumed + estimatedCost > budget → rejected
          expect(result.allowed).toBe(false);
        }
      ),
      { numRuns: 100 }
    );
  });

  it("allows requests when consumed + estimatedCost <= budget", async () => {
    await fc.assert(
      fc.asyncProperty(
        arbTier,
        arbBudget,
        async (tier, budget) => {
          // Generate consumed and estimatedCost such that consumed + estimatedCost <= budget
          const consumed = fc.sample(fc.integer({ min: 0, max: budget - 1 }), 1)[0];
          const remaining = budget - consumed;
          const estimatedCost = fc.sample(fc.integer({ min: 0, max: remaining }), 1)[0];

          const tierBudgets: Record<SubscriptionTier, number> = {
            pro_student: tier === "pro_student" ? budget : 50000,
            pro_youth: tier === "pro_youth" ? budget : 100000,
            pro_standard: tier === "pro_standard" ? budget : 200000,
          };

          setupMocks({ tier, tierBudgets, consumed });

          const result = await checkBudget("prop-user", estimatedCost);

          // consumed + estimatedCost <= budget → allowed
          expect(result.allowed).toBe(true);
        }
      ),
      { numRuns: 100 }
    );
  });

  it("getBalance invariant: consumed + remaining = budget (Req 17.5)", async () => {
    await fc.assert(
      fc.asyncProperty(
        arbTier,
        arbBudget,
        arbConsumed,
        async (tier, budget, consumed) => {
          const tierBudgets: Record<SubscriptionTier, number> = {
            pro_student: tier === "pro_student" ? budget : 50000,
            pro_youth: tier === "pro_youth" ? budget : 100000,
            pro_standard: tier === "pro_standard" ? budget : 200000,
          };

          setupMocks({ tier, tierBudgets, consumed });

          const balance = await getBalance("prop-user");

          // The invariant: consumed_reported + remaining = budget
          // Note: remaining is clamped to max(0, budget - consumed)
          // So when consumed > budget, remaining = 0, consumed stays as-is
          // The "budget" field in the balance is always the tier's daily budget
          if (consumed <= budget) {
            // Standard case: consumed + remaining === budget
            expect(balance.consumed + balance.remaining).toBe(balance.budget);
          } else {
            // Over-budget case: remaining is clamped to 0
            expect(balance.remaining).toBe(0);
            expect(balance.consumed).toBe(consumed);
            // The invariant from the spec perspective:
            // consumed + remaining should conceptually equal budget,
            // but since consumed > budget, remaining is 0 (clamped)
            // The important check is that remaining is non-negative
            expect(balance.remaining).toBeGreaterThanOrEqual(0);
          }
        }
      ),
      { numRuns: 100 }
    );
  });

  it("checkBudget remaining is consistent with budget - consumed", async () => {
    await fc.assert(
      fc.asyncProperty(
        arbTier,
        arbBudget,
        arbConsumed,
        arbEstimatedCost,
        async (tier, budget, consumed, estimatedCost) => {
          const tierBudgets: Record<SubscriptionTier, number> = {
            pro_student: tier === "pro_student" ? budget : 50000,
            pro_youth: tier === "pro_youth" ? budget : 100000,
            pro_standard: tier === "pro_standard" ? budget : 200000,
          };

          setupMocks({ tier, tierBudgets, consumed });

          const result = await checkBudget("prop-user", estimatedCost);

          // remainingBudget should be max(0, budget - consumed)
          const expectedRemaining = Math.max(0, budget - consumed);
          expect(result.remainingBudget).toBe(expectedRemaining);
          expect(result.dailyBudget).toBe(budget);
        }
      ),
      { numRuns: 100 }
    );
  });
});
