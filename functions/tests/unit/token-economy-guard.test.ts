/**
 * Unit tests for TokenEconomyGuard service.
 *
 * Validates: Requirements 17.1, 17.2, 17.3, 17.4, 17.5, 17.6, 18.1, 18.4
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

// ─── Mock Firestore ────────────────────────────────────────────────────────────

const mockGet = vi.fn();
const mockSet = vi.fn();
const mockUpdate = vi.fn();

const mockDoc = vi.fn(() => ({
  get: mockGet,
  set: mockSet,
  update: mockUpdate,
}));

const mockCollection = vi.fn(() => ({
  doc: mockDoc,
}));

vi.mock("firebase-admin/firestore", () => ({
  getFirestore: vi.fn(() => ({
    collection: mockCollection,
  })),
}));

import {
  checkBudget,
  recordUsage,
  getBalance,
  resetDailyUsage,
} from "../../src/v2/services/token-economy-guard";
import { TokenUsage } from "../../src/v2/models/types";

// ─── Test Helpers ──────────────────────────────────────────────────────────────

function mockFirestoreDoc(collectionName: string, data: Record<string, unknown> | null) {
  return {
    exists: data !== null,
    data: () => data,
  };
}

/**
 * Sets up Firestore mock to return different docs based on collection/doc path.
 */
function setupFirestoreMocks(config: {
  configDoc?: Record<string, unknown> | null;
  usageDoc?: Record<string, unknown> | null;
  userDoc?: Record<string, unknown> | null;
}) {
  const { configDoc = null, usageDoc = null, userDoc = null } = config;

  mockCollection.mockImplementation((collection: string) => ({
    doc: vi.fn((docId?: string) => {
      if (collection === "v2_config" && docId === "models") {
        return {
          get: vi.fn().mockResolvedValue(mockFirestoreDoc("v2_config", configDoc)),
          set: mockSet,
          update: mockUpdate,
        };
      }
      if (collection === "v2_token_usage") {
        return {
          get: vi.fn().mockResolvedValue(mockFirestoreDoc("v2_token_usage", usageDoc)),
          set: mockSet,
          update: mockUpdate,
        };
      }
      if (collection === "users") {
        return {
          get: vi.fn().mockResolvedValue(mockFirestoreDoc("users", userDoc)),
          set: mockSet,
          update: mockUpdate,
        };
      }
      return {
        get: vi.fn().mockResolvedValue(mockFirestoreDoc("unknown", null)),
        set: mockSet,
        update: mockUpdate,
      };
    }),
  }));
}

const DEFAULT_TIER_BUDGETS = {
  tierBudgets: {
    pro_student: 50000,
    pro_youth: 100000,
    pro_standard: 200000,
  },
};

describe("TokenEconomyGuard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSet.mockResolvedValue(undefined);
    mockUpdate.mockResolvedValue(undefined);
  });

  describe("checkBudget", () => {
    it("allows request when user has sufficient remaining budget", async () => {
      setupFirestoreMocks({
        configDoc: DEFAULT_TIER_BUDGETS,
        userDoc: { subscriptionTier: "pro_student", timezone: "UTC" },
        usageDoc: {
          date: new Date().toISOString().split("T")[0],
          consumed: 10000,
        },
      });

      const result = await checkBudget("user-123", 5000);

      expect(result.allowed).toBe(true);
      expect(result.dailyBudget).toBe(50000);
      expect(result.remainingBudget).toBe(40000);
    });

    it("rejects request when estimated cost exceeds remaining budget (Req 17.4)", async () => {
      setupFirestoreMocks({
        configDoc: DEFAULT_TIER_BUDGETS,
        userDoc: { subscriptionTier: "pro_student", timezone: "UTC" },
        usageDoc: {
          date: new Date().toISOString().split("T")[0],
          consumed: 48000,
        },
      });

      const result = await checkBudget("user-123", 5000);

      expect(result.allowed).toBe(false);
      expect(result.remainingBudget).toBe(2000);
      expect(result.dailyBudget).toBe(50000);
    });

    it("rejects request when budget is already exhausted (Req 17.2)", async () => {
      setupFirestoreMocks({
        configDoc: DEFAULT_TIER_BUDGETS,
        userDoc: { subscriptionTier: "pro_student", timezone: "UTC" },
        usageDoc: {
          date: new Date().toISOString().split("T")[0],
          consumed: 50000,
        },
      });

      const result = await checkBudget("user-123", 100);

      expect(result.allowed).toBe(false);
      expect(result.remainingBudget).toBe(0);
    });

    it("rejects when config is unavailable (Req 17.6)", async () => {
      mockCollection.mockImplementation(() => ({
        doc: vi.fn(() => ({
          get: vi.fn().mockRejectedValue(new Error("Firestore unavailable")),
        })),
      }));

      const result = await checkBudget("user-123", 1000);

      expect(result.allowed).toBe(false);
    });

    it("treats missing usage doc as zero consumption (new user)", async () => {
      setupFirestoreMocks({
        configDoc: DEFAULT_TIER_BUDGETS,
        userDoc: { subscriptionTier: "pro_youth", timezone: "UTC" },
        usageDoc: null,
      });

      const result = await checkBudget("new-user", 5000);

      expect(result.allowed).toBe(true);
      expect(result.remainingBudget).toBe(100000);
      expect(result.dailyBudget).toBe(100000);
    });

    it("treats stale date as zero consumption (new day, Req 17.3)", async () => {
      setupFirestoreMocks({
        configDoc: DEFAULT_TIER_BUDGETS,
        userDoc: { subscriptionTier: "pro_student", timezone: "UTC" },
        usageDoc: {
          date: "2020-01-01", // Old date
          consumed: 49000,
        },
      });

      const result = await checkBudget("user-123", 5000);

      expect(result.allowed).toBe(true);
      expect(result.remainingBudget).toBe(50000);
    });

    it("uses correct budget for pro_standard tier (Req 18.4)", async () => {
      setupFirestoreMocks({
        configDoc: DEFAULT_TIER_BUDGETS,
        userDoc: { subscriptionTier: "pro_standard", timezone: "UTC" },
        usageDoc: {
          date: new Date().toISOString().split("T")[0],
          consumed: 150000,
        },
      });

      const result = await checkBudget("user-premium", 10000);

      expect(result.allowed).toBe(true);
      expect(result.dailyBudget).toBe(200000);
      expect(result.remainingBudget).toBe(50000);
    });

    it("includes reset time in ISO 8601 format", async () => {
      setupFirestoreMocks({
        configDoc: DEFAULT_TIER_BUDGETS,
        userDoc: { subscriptionTier: "pro_student", timezone: "UTC" },
        usageDoc: null,
      });

      const result = await checkBudget("user-123", 1000);

      expect(result.resetTime).toBeDefined();
      expect(typeof result.resetTime).toBe("string");
      // Should contain a date-like pattern
      expect(result.resetTime).toMatch(/\d{4}-\d{2}-\d{2}T00:00:00/);
    });

    it("allows request when cost exactly matches remaining budget", async () => {
      setupFirestoreMocks({
        configDoc: DEFAULT_TIER_BUDGETS,
        userDoc: { subscriptionTier: "pro_student", timezone: "UTC" },
        usageDoc: {
          date: new Date().toISOString().split("T")[0],
          consumed: 45000,
        },
      });

      const result = await checkBudget("user-123", 5000);

      // 45000 + 5000 = 50000 <= 50000, should be allowed
      expect(result.allowed).toBe(true);
    });
  });

  describe("recordUsage", () => {
    it("computes normalized cost correctly: (input + output) × rate / 1_000_000", async () => {
      setupFirestoreMocks({
        configDoc: DEFAULT_TIER_BUDGETS,
        userDoc: { subscriptionTier: "pro_student", timezone: "UTC" },
        usageDoc: null,
      });

      const usage: TokenUsage = {
        inputTokens: 1200,
        outputTokens: 350,
        model: "gemini-2.5-flash",
        costPerMillionTokens: 0.15,
        normalizedCost: 0, // will be recalculated
        traceId: "trace-123",
      };

      await recordUsage("user-123", usage);

      expect(mockSet).toHaveBeenCalled();
      const setCall = mockSet.mock.calls[0][0];
      // (1200 + 350) * 0.15 / 1_000_000 = 0.0002325
      expect(setCall.consumed).toBeCloseTo(0.0002325, 8);
      expect(setCall.requests[0].normalizedCost).toBeCloseTo(0.0002325, 8);
    });

    it("increments existing consumption on same day", async () => {
      const today = new Date().toISOString().split("T")[0];
      setupFirestoreMocks({
        configDoc: DEFAULT_TIER_BUDGETS,
        userDoc: { subscriptionTier: "pro_student", timezone: "UTC" },
        usageDoc: {
          date: today,
          consumed: 10000,
          requests: [{ traceId: "prev-trace", normalizedCost: 10000 }],
        },
      });

      const usage: TokenUsage = {
        inputTokens: 5000,
        outputTokens: 2000,
        model: "claude-sonnet-4",
        costPerMillionTokens: 3.0,
        normalizedCost: 0,
        traceId: "trace-456",
      };

      await recordUsage("user-123", usage);

      expect(mockUpdate).toHaveBeenCalled();
      const updateCall = mockUpdate.mock.calls[0][0];
      // (5000 + 2000) * 3.0 / 1_000_000 = 0.021
      const expectedCost = (7000 * 3.0) / 1_000_000;
      expect(updateCall.consumed).toBeCloseTo(10000 + expectedCost, 8);
    });

    it("creates fresh record on new day", async () => {
      setupFirestoreMocks({
        configDoc: DEFAULT_TIER_BUDGETS,
        userDoc: { subscriptionTier: "pro_youth", timezone: "UTC" },
        usageDoc: {
          date: "2020-01-01", // Old date
          consumed: 45000,
          requests: [],
        },
      });

      const usage: TokenUsage = {
        inputTokens: 1000,
        outputTokens: 500,
        model: "gemini-2.5-flash",
        costPerMillionTokens: 0.15,
        normalizedCost: 0,
        traceId: "trace-new-day",
      };

      await recordUsage("user-123", usage);

      // Should call set (new day), not update
      expect(mockSet).toHaveBeenCalled();
      const setCall = mockSet.mock.calls[0][0];
      expect(setCall.consumed).toBeCloseTo((1500 * 0.15) / 1_000_000, 8);
      expect(setCall.tier).toBe("pro_youth");
      expect(setCall.budget).toBe(100000);
    });

    it("stores request in audit trail", async () => {
      setupFirestoreMocks({
        configDoc: DEFAULT_TIER_BUDGETS,
        userDoc: { subscriptionTier: "pro_student", timezone: "UTC" },
        usageDoc: null,
      });

      const usage: TokenUsage = {
        inputTokens: 800,
        outputTokens: 200,
        model: "gemini-2.5-flash",
        costPerMillionTokens: 0.15,
        normalizedCost: 0,
        traceId: "trace-audit",
      };

      await recordUsage("user-123", usage);

      const setCall = mockSet.mock.calls[0][0];
      expect(setCall.requests).toHaveLength(1);
      expect(setCall.requests[0].traceId).toBe("trace-audit");
      expect(setCall.requests[0].model).toBe("gemini-2.5-flash");
      expect(setCall.requests[0].inputTokens).toBe(800);
      expect(setCall.requests[0].outputTokens).toBe(200);
      expect(setCall.requests[0].timestamp).toBeGreaterThan(0);
    });
  });

  describe("getBalance", () => {
    it("returns correct balance for existing user (Req 17.5)", async () => {
      const today = new Date().toISOString().split("T")[0];
      setupFirestoreMocks({
        configDoc: DEFAULT_TIER_BUDGETS,
        userDoc: { subscriptionTier: "pro_student", timezone: "UTC" },
        usageDoc: {
          date: today,
          consumed: 30000,
        },
      });

      const balance = await getBalance("user-123");

      expect(balance.consumed).toBe(30000);
      expect(balance.budget).toBe(50000);
      expect(balance.remaining).toBe(20000);
      expect(balance.tier).toBe("pro_student");
      expect(balance.resetTime).toBeDefined();
    });

    it("returns full budget for user with no usage record", async () => {
      setupFirestoreMocks({
        configDoc: DEFAULT_TIER_BUDGETS,
        userDoc: { subscriptionTier: "pro_standard", timezone: "UTC" },
        usageDoc: null,
      });

      const balance = await getBalance("new-user");

      expect(balance.consumed).toBe(0);
      expect(balance.budget).toBe(200000);
      expect(balance.remaining).toBe(200000);
      expect(balance.tier).toBe("pro_standard");
    });

    it("resets to zero for stale usage from previous day", async () => {
      setupFirestoreMocks({
        configDoc: DEFAULT_TIER_BUDGETS,
        userDoc: { subscriptionTier: "pro_youth", timezone: "UTC" },
        usageDoc: {
          date: "2020-01-01",
          consumed: 80000,
        },
      });

      const balance = await getBalance("user-123");

      expect(balance.consumed).toBe(0);
      expect(balance.remaining).toBe(100000);
    });

    it("ensures consumed + remaining = budget invariant", async () => {
      const today = new Date().toISOString().split("T")[0];
      setupFirestoreMocks({
        configDoc: DEFAULT_TIER_BUDGETS,
        userDoc: { subscriptionTier: "pro_student", timezone: "UTC" },
        usageDoc: {
          date: today,
          consumed: 12345,
        },
      });

      const balance = await getBalance("user-123");

      expect(balance.consumed + balance.remaining).toBe(balance.budget);
    });

    it("clamps remaining to zero when over budget", async () => {
      const today = new Date().toISOString().split("T")[0];
      setupFirestoreMocks({
        configDoc: DEFAULT_TIER_BUDGETS,
        userDoc: { subscriptionTier: "pro_student", timezone: "UTC" },
        usageDoc: {
          date: today,
          consumed: 55000, // Over the 50000 budget
        },
      });

      const balance = await getBalance("user-123");

      expect(balance.remaining).toBe(0);
      expect(balance.consumed).toBe(55000);
    });
  });

  describe("resetDailyUsage", () => {
    it("resets consumed to zero", async () => {
      setupFirestoreMocks({
        configDoc: DEFAULT_TIER_BUDGETS,
        userDoc: { subscriptionTier: "pro_student", timezone: "UTC" },
        usageDoc: {
          date: new Date().toISOString().split("T")[0],
          consumed: 45000,
        },
      });

      await resetDailyUsage("user-123");

      expect(mockSet).toHaveBeenCalled();
      const setCall = mockSet.mock.calls[0][0];
      expect(setCall.consumed).toBe(0);
      expect(setCall.requests).toEqual([]);
      expect(setCall.tier).toBe("pro_student");
      expect(setCall.budget).toBe(50000);
    });

    it("sets the current date in the usage document", async () => {
      setupFirestoreMocks({
        configDoc: DEFAULT_TIER_BUDGETS,
        userDoc: { subscriptionTier: "pro_youth", timezone: "UTC" },
        usageDoc: null,
      });

      await resetDailyUsage("user-123");

      const setCall = mockSet.mock.calls[0][0];
      const today = new Date().toISOString().split("T")[0];
      expect(setCall.date).toBe(today);
    });
  });

  describe("tier budget lookups (Req 18.1, 18.4)", () => {
    it("uses pro_student budget for pro_student tier", async () => {
      setupFirestoreMocks({
        configDoc: DEFAULT_TIER_BUDGETS,
        userDoc: { subscriptionTier: "pro_student", timezone: "UTC" },
        usageDoc: null,
      });

      const balance = await getBalance("user-123");
      expect(balance.budget).toBe(50000);
    });

    it("uses pro_youth budget for pro_youth tier", async () => {
      setupFirestoreMocks({
        configDoc: DEFAULT_TIER_BUDGETS,
        userDoc: { subscriptionTier: "pro_youth", timezone: "UTC" },
        usageDoc: null,
      });

      const balance = await getBalance("user-123");
      expect(balance.budget).toBe(100000);
    });

    it("uses pro_standard budget for pro_standard tier", async () => {
      setupFirestoreMocks({
        configDoc: DEFAULT_TIER_BUDGETS,
        userDoc: { subscriptionTier: "pro_standard", timezone: "UTC" },
        usageDoc: null,
      });

      const balance = await getBalance("user-123");
      expect(balance.budget).toBe(200000);
    });

    it("falls back to pro_student for unknown tier", async () => {
      setupFirestoreMocks({
        configDoc: DEFAULT_TIER_BUDGETS,
        userDoc: { subscriptionTier: "invalid_tier", timezone: "UTC" },
        usageDoc: null,
      });

      const balance = await getBalance("user-123");
      expect(balance.tier).toBe("pro_student");
      expect(balance.budget).toBe(50000);
    });
  });
});
