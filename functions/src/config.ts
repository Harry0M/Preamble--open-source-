/**
 * Preamble AI — Pricing & Limits
 *
 * ARCHITECTURE: No per-response output cap (responses never get cut off).
 *               Flash models = free. Mistral = daily token budget, refillable by ads.
 *
 * ECONOMICS (blended global eCPM ~$10/1K views = $0.01/view):
 *   Model costs (60% input / 40% output split):
 *     gemini-2.5-flash-lite : $0          → FREE (Google free tier ≤ 1500 req/day)
 *     gemini-2.5-flash      : $0.000165/1K → negligible; absorbed as free
 *     mistral-small-latest  : $0.000180/1K → $0.0014 per 8K token ad grant  → 86% margin
 *     mistral-medium-latest : $0.001040/1K → $0.00104 per 1K token ad grant → 90% margin
 *
 *   Per-ad token grant (sized so AI cost ≤ 14% of ad revenue at $0.01/view):
 *     mistral-small  : 8 000 tokens (~6–7 messages)
 *     mistral-medium : 1 000 tokens (~1 message)
 *
 *   Free daily baseline (new-user experience, resets midnight UTC):
 *     mistral-small  : 3 000 tokens  (~2–3 messages/day)
 *     mistral-medium :   500 tokens  (~1 message/day — teaser only)
 *
 * NOTE: Flash daily message limit is purely anti-abuse, not monetization.
 */

// ---------------------------------------------------------------------------
// Central AI Model Configuration (Hardcoded for 0 Firestore reads)
// ---------------------------------------------------------------------------

export const PARSE_MODEL  = "mistral-small-latest";   // High accuracy task parser & function calling
export const CHAT_MODEL   = "ministral-8b-latest";    // Fast, lightweight conversational model for AI chat
export const MEMORY_MODEL = "gemini-2.5-flash-lite";  // Background memory extractor
export const DEFAULT_MODEL = CHAT_MODEL;
export const DEFAULT_MODE  = "concise";

// ---------------------------------------------------------------------------
// Admin UIDs (Optional developer overrides)
// ---------------------------------------------------------------------------
export const ADMIN_UIDS: string[] = [];

export function isAdminUid(uid?: string): boolean {
  if (!uid) return false;
  return ADMIN_UIDS.includes(uid);
}

/** Daily message caps for free (Gemini) models — anti-abuse only. -1 = unlimited. */
export const FLASH_DAILY_MSG_LIMITS: Record<string, number> = {
  "gemini-2.5-flash-lite": -1,   // unlimited
  "gemini-2.5-flash":      100,  // generous; costs us ~$0.02/user/day at cap
};

export function flashDailyMsgField(model: string): string {
  const date = new Date().toISOString().slice(0, 10).replace(/-/g, "");
  const tier  = model.includes("lite") ? "fll" : "fl";
  return `ai_fm_${tier}_${date}`;
}

export function getFlashMsgsRemaining(model: string, usedToday: number): number {
  const limit = FLASH_DAILY_MSG_LIMITS[model] ?? 50;
  if (limit === -1) return -1;
  return Math.max(0, limit - usedToday);
}

// ---------------------------------------------------------------------------
// Mistral models (premium — daily token budget, refillable with ads)
// ---------------------------------------------------------------------------

/** Whether a model is from Mistral AI provider. */
export function isMistralModel(model: string): boolean {
  return (
    model.includes("mistral") ||
    model.includes("ministral") ||
    model.includes("mixtral") ||
    model.includes("codestral") ||
    model.includes("pixtral")
  );
}
export const isMistralPremium = isMistralModel;

/** Daily FREE token baseline per Mistral model (resets midnight UTC). */
export const MISTRAL_DAILY_FREE_TOKENS: Record<string, number> = {
  "ministral-8b-latest": 10_000,
  "mistral-small-latest":  6_000,
  "mistral-medium-latest":   500,
};

/** Tokens added to the daily budget per ad watch. */
export const MISTRAL_TOKENS_PER_AD: Record<string, number> = {
  "ministral-8b-latest": 10_000,
  "mistral-small-latest":  8_000,
  "mistral-medium-latest": 1_000,
};

/** Weighted cost multiplier vs mistral-small baseline. Used so medium calls cost more budget. */
export const MISTRAL_COST_WEIGHT: Record<string, number> = {
  "ministral-8b-latest": 1,
  "mistral-small-latest":  1,
  "mistral-medium-latest": 6, // ~6× more expensive per token
};

/** How many weighted tokens a real call consumes from the daily budget. */
export function mistralWeightedTokens(model: string, totalTokens: number): number {
  return Math.round(totalTokens * (MISTRAL_COST_WEIGHT[model] ?? 1));
}

/** Firestore field: tokens used today (weighted). */
export function mistralUsedField(model: string): string {
  const date = new Date().toISOString().slice(0, 10).replace(/-/g, "");
  const tier  = model.includes("mini") ? "mn" : model.includes("medium") ? "mm" : "ms";
  return `ai_mt_used_${tier}_${date}`;
}

/** Firestore field: bonus tokens granted by ads today. */
export function mistralBonusField(model: string): string {
  const date = new Date().toISOString().slice(0, 10).replace(/-/g, "");
  const tier  = model.includes("mini") ? "mn" : model.includes("medium") ? "mm" : "ms";
  return `ai_mt_bonus_${tier}_${date}`;
}

/** Pro subscriber daily token baseline (5x limit). */
export const MISTRAL_DAILY_PRO_TOKENS: Record<string, number> = {
  "ministral-8b-latest": 50_000,
  "mistral-small-latest":  30_000,
  "mistral-medium-latest":  2_500,
};

/** Total daily budget = baseline (Free or Pro) + ad bonuses. */
export function getMistralDailyBudget(model: string, bonusToday: number, isPro = false): number {
  const base = isPro
    ? (MISTRAL_DAILY_PRO_TOKENS[model] ?? 50_000)
    : (MISTRAL_DAILY_FREE_TOKENS[model] ?? 10_000);
  return base + bonusToday;
}

/** Remaining weighted tokens in today's budget. */
export function getMistralTokensRemaining(
  model: string, usedToday: number, bonusToday: number, isPro = false,
): number {
  return Math.max(0, getMistralDailyBudget(model, bonusToday, isPro) - usedToday);
}

// ---------------------------------------------------------------------------
// Ad system
// ---------------------------------------------------------------------------

export const CREDITS_PER_AD      = 10;
export const FIRST_TIME_BONUS    = 20;
export const AD_COOLDOWN_SECONDS = 300; // 5 minutes between ads

// ---------------------------------------------------------------------------
// Referral rewards (growth-loops — two-sided referral invites)
// ---------------------------------------------------------------------------

/** AI credits granted to EACH side of an eligible referred signup. */
export const REFERRAL_REWARD = 50;

/**
 * Master switch for the two-sided Referral_Reward (social-hub-redesign Req 8).
 *
 * Sourced from the `REFERRAL_REWARDS_ENABLED` environment variable and defaults
 * to disabled in Development_Mode (the current pre-launch state). When false,
 * `gatedReferralDecision` never returns `Eligible`, so the credit-increment
 * transaction in `onReferralFriendship` never runs and no user's AI-credit
 * balance is modified on account of a referral (Req 8.1, 8.5). Flipping this
 * back to "true" re-enables the reward without touching attribution or the
 * invite/friendship flow (Req 8.4).
 */
export const REFERRAL_REWARDS_ENABLED: boolean =
  (process.env.REFERRAL_REWARDS_ENABLED ?? "false") === "true";

/**
 * "Genuinely new account" tolerance window. The referred account's creation
 * time must fall within ±this many ms of the attribution timestamp for the
 * referral to be eligible (guards against rewarding pre-existing accounts).
 */
export const REFERRAL_NEW_ACCOUNT_WINDOW_MS = 24 * 60 * 60 * 1000; // 24 hours

// ---------------------------------------------------------------------------
// Legacy credit system (Flash only — Mistral now uses token budgets)
// ---------------------------------------------------------------------------

export const CREDIT_PER_1K_TOKENS: Record<string, number> = {
  "gemini-2.5-flash-lite": 0,
  "gemini-2.5-flash":      0, // free — absorbed as cost of service
};

export function isFreeModel(model: string): boolean {
  return !isMistralPremium(model);
}

export function computeTokenCredits(): number {
  return 0; // Mistral charges via token budget, not credits
}

export function preflightCreditCheck(): { ok: boolean; needed: number } {
  return { ok: true, needed: 0 }; // credit gate removed; Mistral uses token budget
}

// ---------------------------------------------------------------------------
// Shared
// ---------------------------------------------------------------------------

export const PREDEFINED_TAGS = [
  "Health", "Fitness", "Work", "Meeting", "Study", "Food",
  "Family", "Social", "Shopping", "Finance", "Travel",
  "Home", "Errand", "Creative", "Ideas", "Personal",
];
