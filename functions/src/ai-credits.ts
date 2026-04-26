/**
 * AI Credits management endpoints.
 * - reward: Called after user watches a rewarded ad
 * - balance: Get current credit balance
 */
import { onRequest } from "firebase-functions/v2/https";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getAuth } from "firebase-admin/auth";
import { CREDITS_PER_AD, FIRST_TIME_BONUS, AD_COOLDOWN_SECONDS } from "./config";

async function verifyAuth(authHeader: string | undefined): Promise<string | null> {
  if (!authHeader?.startsWith("Bearer ")) return null;
  try {
    const decoded = await getAuth().verifyIdToken(authHeader.slice(7));
    return decoded.uid;
  } catch {
    return null;
  }
}

/**
 * POST /ai/credits/reward
 * Called after user successfully watches a rewarded ad.
 * Body: { adId?: string }  (optional ad unit verification)
 */
export const aiCreditsReward = onRequest(
  { cors: true, timeoutSeconds: 30 },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).json({ error: "Method not allowed" });
      return;
    }

    const uid = await verifyAuth(req.headers.authorization);
    if (!uid) {
      res.status(401).json({ error: "Unauthorized" });
      return;
    }

    const db = getFirestore("preamble");
    const userRef = db.doc(`users/${uid}`);
    const userDoc = await userRef.get();
    const data = userDoc.data() || {};

    // Anti-spam: check cooldown
    const lastAd = data.ai_credits_last_ad || 0;
    const now = Date.now();
    if (now - lastAd < AD_COOLDOWN_SECONDS * 1000) {
      res.status(429).json({
        error: "Too soon. Please wait before watching another ad.",
        retryAfterSeconds: Math.ceil(AD_COOLDOWN_SECONDS - (now - lastAd) / 1000),
      });
      return;
    }

    // First-time bonus check
    const isFirstTime = !data.ai_credits_first_bonus;
    const creditsToAdd = CREDITS_PER_AD + (isFirstTime ? FIRST_TIME_BONUS : 0);

    await userRef.set({
      ai_credits: FieldValue.increment(creditsToAdd),
      ai_credits_total_earned: FieldValue.increment(creditsToAdd),
      ai_credits_last_ad: now,
      ai_credits_first_bonus: true,
    }, { merge: true });

    const updated = await userRef.get();
    const newBalance = updated.data()?.ai_credits ?? 0;

    res.json({
      success: true,
      creditsAdded: creditsToAdd,
      firstTimeBonus: isFirstTime ? FIRST_TIME_BONUS : 0,
      balance: newBalance,
    });
  }
);

/**
 * GET /ai/credits/balance
 * Returns current credit balance and usage stats.
 */
export const aiCreditsBalance = onRequest(
  { cors: true, timeoutSeconds: 10 },
  async (req, res) => {
    if (req.method !== "GET") {
      res.status(405).json({ error: "Method not allowed" });
      return;
    }

    const uid = await verifyAuth(req.headers.authorization);
    if (!uid) {
      res.status(401).json({ error: "Unauthorized" });
      return;
    }

    const db = getFirestore("preamble");
    const userDoc = await db.doc(`users/${uid}`).get();
    const data = userDoc.data() || {};

    res.json({
      balance: data.ai_credits ?? 0,
      totalEarned: data.ai_credits_total_earned ?? 0,
      totalSpent: data.ai_credits_total_spent ?? 0,
      firstBonusClaimed: data.ai_credits_first_bonus ?? false,
    });
  }
);
