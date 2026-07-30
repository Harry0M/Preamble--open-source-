/**
 * TokenEconomyGuard — Enforces daily token budgets with multi-model cost normalization.
 *
 * Tracks each user's cumulative normalized token consumption per calendar day,
 * rejects requests that would exceed the daily budget, and provides balance queries.
 *
 * Uses the named Firestore database "preamble" with collection `v2_token_usage/{uid}`.
 * Reads tier budgets from `v2_config/models.tierBudgets`.
 *
 * Requirements: 17.1, 17.2, 17.3, 17.4, 17.5, 17.6, 18.1, 18.4
 */

import { getFirestore } from "firebase-admin/firestore";
import {
  BudgetCheck,
  TokenUsage,
  TokenBalance,
  SubscriptionTier,
} from "../models/types";

// ─── Firestore References ──────────────────────────────────────────────────────

/**
 * Returns the Firestore instance for the "preamble" named database.
 */
function getDb() {
  return getFirestore("preamble");
}

/**
 * Returns the document reference for a user's token usage record.
 */
function getUsageDoc(uid: string) {
  return getDb().collection("v2_token_usage").doc(uid);
}

/**
 * Returns the document reference for the V2 config/models document.
 */
function getConfigDoc() {
  return getDb().collection("v2_config").doc("models");
}

// ─── Internal Helpers ──────────────────────────────────────────────────────────

/**
 * Reads tier budgets from the v2_config/models document.
 * Returns a map of tier → daily budget (normalized tokens).
 * Falls back to defaults if tierBudgets field is not configured.
 */
async function getTierBudgets(): Promise<Record<SubscriptionTier, number>> {
  const DEFAULT_TIER_BUDGETS: Record<SubscriptionTier, number> = {
    pro_student: 50000,
    pro_youth: 100000,
    pro_standard: 200000,
  };

  const doc = await getConfigDoc().get();
  if (!doc.exists) {
    // No config doc at all — use defaults
    return DEFAULT_TIER_BUDGETS;
  }
  const data = doc.data();
  if (!data?.tierBudgets) {
    // Config exists but tierBudgets not set yet — use defaults
    return DEFAULT_TIER_BUDGETS;
  }
  return data.tierBudgets as Record<SubscriptionTier, number>;
}

/**
 * Gets the current date string (YYYY-MM-DD) in the user's timezone.
 */
function getCurrentDateInTimezone(timezone: string): string {
  const now = new Date();
  const formatter = new Intl.DateTimeFormat("en-CA", {
    timeZone: timezone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
  return formatter.format(now);
}

/**
 * Computes the ISO 8601 reset time (start of next calendar day in user's timezone).
 */
function computeResetTime(timezone: string): string {
  const now = new Date();
  const formatter = new Intl.DateTimeFormat("en-CA", {
    timeZone: timezone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
  const todayStr = formatter.format(now);

  // Parse today's date and compute tomorrow
  const [year, month, day] = todayStr.split("-").map(Number);
  const tomorrow = new Date(year, month - 1, day + 1);
  const tomorrowStr = `${tomorrow.getFullYear()}-${String(tomorrow.getMonth() + 1).padStart(2, "0")}-${String(tomorrow.getDate()).padStart(2, "0")}`;

  // Build an ISO 8601 datetime string representing midnight in the user's timezone
  // We use the IANA timezone to compute the UTC offset at that moment
  const midnightLocal = new Date(`${tomorrowStr}T00:00:00`);
  // Get the offset by formatting in the target timezone
  const offsetFormatter = new Intl.DateTimeFormat("en-US", {
    timeZone: timezone,
    timeZoneName: "longOffset",
  });
  const parts = offsetFormatter.formatToParts(midnightLocal);
  const tzPart = parts.find((p) => p.type === "timeZoneName");
  // Extract offset like "GMT+05:30" → "+05:30"
  let offset = "+00:00";
  if (tzPart?.value) {
    const match = tzPart.value.match(/GMT([+-]\d{2}:\d{2})/);
    if (match) {
      offset = match[1];
    }
  }

  return `${tomorrowStr}T00:00:00${offset}`;
}

/**
 * Reads the user's subscription tier from their profile.
 * Falls back to "pro_student" if not found.
 */
async function getUserTier(uid: string): Promise<SubscriptionTier> {
  const db = getDb();
  const userDoc = await db.collection("users").doc(uid).get();
  if (!userDoc.exists) {
    return "pro_student";
  }
  const data = userDoc.data();
  const tier = data?.subscriptionTier || data?.tier || "pro_student";
  // Validate tier is one of the allowed values
  const validTiers: SubscriptionTier[] = ["pro_student", "pro_youth", "pro_standard"];
  return validTiers.includes(tier) ? tier : "pro_student";
}

/**
 * Reads the user's timezone from their profile or usage record.
 * Falls back to "UTC" if not found.
 */
async function getUserTimezone(uid: string): Promise<string> {
  const db = getDb();
  const userDoc = await db.collection("users").doc(uid).get();
  if (userDoc.exists) {
    const data = userDoc.data();
    if (data?.timezone) return data.timezone;
  }
  return "UTC";
}

// ─── Public API ────────────────────────────────────────────────────────────────

/**
 * Checks whether the user has enough daily budget remaining for an estimated cost.
 *
 * Rejects if:
 * - cumulative consumption + estimated cost > daily budget (Req 17.4)
 * - cumulative consumption ≥ daily budget (Req 17.2)
 * - consumption cannot be verified (Req 17.6)
 *
 * @param uid - The authenticated user's UID
 * @param estimatedCost - Estimated normalized token cost for the pending request
 * @returns BudgetCheck indicating whether the request is allowed
 */
export async function checkBudget(
  uid: string,
  estimatedCost: number
): Promise<BudgetCheck> {
  let tierBudgets: Record<SubscriptionTier, number>;
  let tier: SubscriptionTier;
  let timezone: string;

  try {
    [tierBudgets, tier, timezone] = await Promise.all([
      getTierBudgets(),
      getUserTier(uid),
      getUserTimezone(uid),
    ]);
  } catch (err) {
    // Req 17.6: Cannot verify consumption → reject
    // Distinguish between missing config and actual errors
    const message = err instanceof Error ? err.message : "Unknown error";
    console.error(`[TokenEconomyGuard] checkBudget failed for uid=${uid}: ${message}`);
    return {
      allowed: false,
      remainingBudget: 0,
      dailyBudget: 0,
      resetTime: new Date().toISOString(),
    };
  }

  const dailyBudget = tierBudgets[tier] ?? 50000;
  const today = getCurrentDateInTimezone(timezone);
  const resetTime = computeResetTime(timezone);

  try {
    const usageDoc = await getUsageDoc(uid).get();
    let consumed = 0;

    if (usageDoc.exists) {
      const data = usageDoc.data();
      // If the stored date matches today, use stored consumption
      if (data?.date === today) {
        consumed = data.consumed ?? 0;
      }
      // Otherwise, it's a new day — consumed stays 0
    }

    const remainingBudget = Math.max(0, dailyBudget - consumed);
    const allowed = consumed + estimatedCost <= dailyBudget;

    return {
      allowed,
      remainingBudget,
      dailyBudget,
      resetTime,
    };
  } catch {
    // Req 17.6: Cannot verify consumption → reject
    return {
      allowed: false,
      remainingBudget: 0,
      dailyBudget,
      resetTime,
    };
  }
}

/**
 * Records actual token usage after a successful AI request.
 *
 * Computes normalized cost: (inputTokens + outputTokens) × costPerMillionTokens / 1,000,000
 * Stores usage in `v2_token_usage/{uid}` with an audit trail entry.
 *
 * @param uid - The authenticated user's UID
 * @param usage - Token usage details from the completed request
 */
export async function recordUsage(
  uid: string,
  usage: TokenUsage
): Promise<void> {
  const [tier, timezone] = await Promise.all([
    getUserTier(uid),
    getUserTimezone(uid),
  ]);

  const tierBudgets = await getTierBudgets();
  const dailyBudget = tierBudgets[tier] ?? 50000;
  const today = getCurrentDateInTimezone(timezone);

  // Calculate normalized cost
  const normalizedCost =
    ((usage.inputTokens + usage.outputTokens) * usage.costPerMillionTokens) /
    1_000_000;

  const usageRef = getUsageDoc(uid);
  const usageDoc = await usageRef.get();

  const requestEntry = {
    traceId: usage.traceId,
    model: usage.model,
    inputTokens: usage.inputTokens,
    outputTokens: usage.outputTokens,
    normalizedCost,
    timestamp: Date.now(),
  };

  if (usageDoc.exists && usageDoc.data()?.date === today) {
    // Same day: increment consumed and append request
    const currentData = usageDoc.data()!;
    const currentRequests = currentData.requests ?? [];
    await usageRef.update({
      consumed: (currentData.consumed ?? 0) + normalizedCost,
      lastRequestAt: Date.now(),
      requests: [...currentRequests, requestEntry],
    });
  } else {
    // New day or first usage: create fresh record
    await usageRef.set({
      date: today,
      consumed: normalizedCost,
      tier,
      budget: dailyBudget,
      timezone,
      lastRequestAt: Date.now(),
      requests: [requestEntry],
    });
  }
}

/**
 * Returns the user's current token balance: consumed, remaining, budget, tier, and reset time.
 *
 * @param uid - The authenticated user's UID
 * @returns TokenBalance with current usage status
 */
export async function getBalance(uid: string): Promise<TokenBalance> {
  const [tier, timezone, tierBudgets] = await Promise.all([
    getUserTier(uid),
    getUserTimezone(uid),
    getTierBudgets(),
  ]);

  const dailyBudget = tierBudgets[tier] ?? 50000;
  const today = getCurrentDateInTimezone(timezone);
  const resetTime = computeResetTime(timezone);

  const usageDoc = await getUsageDoc(uid).get();
  let consumed = 0;

  if (usageDoc.exists) {
    const data = usageDoc.data();
    if (data?.date === today) {
      consumed = data.consumed ?? 0;
    }
  }

  const remaining = Math.max(0, dailyBudget - consumed);

  return {
    consumed,
    budget: dailyBudget,
    remaining,
    tier,
    resetTime,
  };
}

/**
 * Resets the user's daily usage to zero.
 *
 * Called when a new calendar day starts in the user's timezone.
 * Creates a fresh usage document for the new day.
 *
 * @param uid - The authenticated user's UID
 */
export async function resetDailyUsage(uid: string): Promise<void> {
  const [tier, timezone, tierBudgets] = await Promise.all([
    getUserTier(uid),
    getUserTimezone(uid),
    getTierBudgets(),
  ]);

  const dailyBudget = tierBudgets[tier] ?? 50000;
  const today = getCurrentDateInTimezone(timezone);

  await getUsageDoc(uid).set({
    date: today,
    consumed: 0,
    tier,
    budget: dailyBudget,
    timezone,
    lastRequestAt: Date.now(),
    requests: [],
  });
}
