/**
 * Token-based credit pricing.
 *
 * Pricing model: credits per 1000 tokens (input + output combined).
 * Why per-token: a 100-token chat shouldn't cost the same as a 5000-token chat.
 *
 * Rate calibration (1 credit ≈ ₹0.04, derived from ad revenue):
 *   gemini-2.5-flash-lite — 0 credits (free tier covers Tier 1 limits)
 *   gemini-2.5-flash      — 1 credit / 1k  (≈ ₹0.04, real cost ₹0.033)
 *   mistral-small-latest  — 2-3 credits / 1k
 *   mistral-medium-latest — 8-12 credits / 1k
 *
 * "Concise" mode auto-reduces output tokens, so single rate is enough —
 * no need to charge differently for concise vs normal.
 */
export const CREDIT_PER_1K_TOKENS: Record<string, number> = {
  "gemini-2.5-flash-lite": 0,
  "gemini-2.5-flash":      1,
  "mistral-small-latest":  2,
  "mistral-medium-latest": 8,
};

/** Minimum credits reserved before a non-free call (anti-spam). */
export const MIN_CREDITS_FOR_PAID_CALL = 1;

export const DEFAULT_MODEL = "gemini-2.5-flash-lite";
export const DEFAULT_MODE  = "concise";

/** Credits earned per rewarded ad */
export const CREDITS_PER_AD = 10;
/** First-time bonus credits */
export const FIRST_TIME_BONUS = 20;
/** Minimum seconds between ad rewards (anti-spam) */
export const AD_COOLDOWN_SECONDS = 30;

/** True if the model is in our free tier (no credit deduction). */
export function isFreeModel(model: string): boolean {
  return (CREDIT_PER_1K_TOKENS[model] ?? 1) === 0;
}

/**
 * Compute credits for a finished call based on real token usage.
 * Charges minimum 1 credit on paid tiers even for tiny calls (covers fixed overhead).
 */
export function computeTokenCredits(
  model: string,
  inputTokens: number,
  outputTokens: number,
): number {
  const rate = CREDIT_PER_1K_TOKENS[model] ?? 1;
  if (rate === 0) return 0;
  const total = Math.max(0, inputTokens) + Math.max(0, outputTokens);
  return Math.max(1, Math.ceil((total / 1000) * rate));
}

/** Pre-flight credit check before paying for an AI call. */
export function preflightCreditCheck(model: string, balance: number): { ok: boolean; needed: number } {
  if (isFreeModel(model)) return { ok: true, needed: 0 };
  return { ok: balance >= MIN_CREDITS_FOR_PAID_CALL, needed: MIN_CREDITS_FOR_PAID_CALL };
}

/** Predefined tags for task AI */
export const PREDEFINED_TAGS = [
  "Health", "Fitness", "Work", "Meeting", "Study", "Food",
  "Family", "Social", "Shopping", "Finance", "Travel",
  "Home", "Errand", "Creative", "Ideas", "Personal",
];
