/**
 * v2TokenBalance — GET endpoint returning the user's current token usage and budget.
 *
 * Returns: consumed, budget, remaining, tier, resetTime (as TokenBalance).
 * Requires Firebase Auth bearer token.
 *
 * Requirements: 17.5
 */

import { onRequest } from "firebase-functions/v2/https";
import { getAuth } from "firebase-admin/auth";
import { getBalance } from "../services/token-economy-guard";
import { enforceRateLimit } from "../utils/rate-limiter";

/**
 * Verifies the Firebase Auth bearer token and returns the user's UID.
 * Returns null if the token is missing, malformed, or expired.
 */
async function verifyAuth(
  authHeader: string | undefined
): Promise<string | null> {
  if (!authHeader?.startsWith("Bearer ")) return null;
  try {
    const decoded = await getAuth().verifyIdToken(authHeader.slice(7));
    return decoded.uid;
  } catch {
    return null;
  }
}

/**
 * GET /v2TokenBalance
 *
 * Returns the authenticated user's current token balance:
 * - consumed: normalized tokens consumed today
 * - budget: daily budget for the user's tier
 * - remaining: budget - consumed
 * - tier: subscription tier (pro_student | pro_youth | pro_standard)
 * - resetTime: ISO 8601 datetime when the budget resets
 */
export const v2TokenBalance = onRequest(
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

    // Rate limiting (max 10 req/min per user, Req 26.3)
    if (await enforceRateLimit(uid, res)) return;

    try {
      const balance = await getBalance(uid);
      res.json(balance);
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "Internal server error";
      res.status(500).json({ error: message });
    }
  }
);
