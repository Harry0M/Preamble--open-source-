/**
 * V2 Rate Limiter — Per-user request throttling for V2 Cloud Function endpoints.
 *
 * Enforces max 10 requests/minute per authenticated user across all V2 endpoints.
 * Uses Firestore v2_token_usage/{uid} document to track recent request timestamps.
 *
 * Implementation:
 * - Stores an array of recent request timestamps in the user's token usage document
 * - On each request, filters timestamps within the last 60 seconds
 * - If count >= 10, the request is rejected with 429 Too Many Requests
 * - Otherwise, the new timestamp is appended and stale entries pruned
 *
 * Requirements: 26.3
 */

import { getFirestore } from "firebase-admin/firestore";
import { V2_COLLECTIONS } from "./collection-paths";

// ─── Constants ─────────────────────────────────────────────────────────────────

/** Maximum requests allowed per user within the rate window */
const MAX_REQUESTS_PER_WINDOW = 10;

/** Rate limiting window in milliseconds (60 seconds = 1 minute) */
const RATE_WINDOW_MS = 60 * 1000;

// ─── Types ─────────────────────────────────────────────────────────────────────

export interface RateLimitResult {
  /** Whether the request is allowed */
  allowed: boolean;
  /** Number of requests made in the current window */
  currentCount: number;
  /** Maximum allowed requests per window */
  limit: number;
  /** Milliseconds until the oldest request in the window expires (when blocked) */
  retryAfterMs?: number;
}

// ─── Firestore Helper ──────────────────────────────────────────────────────────

function getDb() {
  return getFirestore("preamble");
}

// ─── Rate Limiter ──────────────────────────────────────────────────────────────

/**
 * Checks and enforces the per-user rate limit for V2 endpoints.
 *
 * Uses a Firestore transaction to atomically read/update the request timestamps
 * stored in `v2_token_usage/{uid}` under the `recentRequests` field.
 *
 * @param uid - The authenticated user's UID
 * @returns RateLimitResult indicating whether the request is allowed
 */
export async function checkRateLimit(uid: string): Promise<RateLimitResult> {
  const db = getDb();
  const docRef = db.collection(V2_COLLECTIONS.TOKEN_USAGE).doc(uid);

  const now = Date.now();
  const windowStart = now - RATE_WINDOW_MS;

  try {
    const result = await db.runTransaction(async (transaction) => {
      const doc = await transaction.get(docRef);
      const data = doc.data();

      // Get existing recent request timestamps, or empty array if none
      const recentRequests: number[] = data?.recentRequests ?? [];

      // Filter to only timestamps within the current window
      const activeRequests = recentRequests.filter((ts) => ts > windowStart);

      if (activeRequests.length >= MAX_REQUESTS_PER_WINDOW) {
        // Rate limit exceeded — calculate retry-after based on oldest request in window
        const oldestInWindow = Math.min(...activeRequests);
        const retryAfterMs = oldestInWindow + RATE_WINDOW_MS - now;

        return {
          allowed: false,
          currentCount: activeRequests.length,
          limit: MAX_REQUESTS_PER_WINDOW,
          retryAfterMs: Math.max(retryAfterMs, 1000), // At least 1 second
        };
      }

      // Request allowed — record the new timestamp and prune stale entries
      const updatedRequests = [...activeRequests, now];

      if (doc.exists) {
        transaction.update(docRef, { recentRequests: updatedRequests });
      } else {
        transaction.set(docRef, { recentRequests: updatedRequests }, { merge: true });
      }

      return {
        allowed: true,
        currentCount: activeRequests.length + 1,
        limit: MAX_REQUESTS_PER_WINDOW,
      };
    });

    return result;
  } catch (error) {
    // On Firestore failure, allow the request through (fail open)
    // to avoid blocking users due to transient infrastructure issues.
    // The token economy guard provides a secondary throttle layer.
    return {
      allowed: true,
      currentCount: 0,
      limit: MAX_REQUESTS_PER_WINDOW,
    };
  }
}

/**
 * Express/Cloud Function middleware-style helper that checks rate limit
 * and sends a 429 response if exceeded.
 *
 * @param uid - The authenticated user's UID
 * @param res - Express response object
 * @returns true if rate limit is exceeded (response already sent), false if allowed
 */
export async function enforceRateLimit(
  uid: string,
  res: { status: (code: number) => { json: (body: unknown) => void }; setHeader: (name: string, value: string) => void }
): Promise<boolean> {
  const result = await checkRateLimit(uid);

  if (!result.allowed) {
    const retryAfterSeconds = Math.ceil((result.retryAfterMs ?? 60000) / 1000);
    res.setHeader("Retry-After", String(retryAfterSeconds));
    res.status(429).json({
      error: "Rate limit exceeded",
      message: `Maximum ${result.limit} requests per minute. Try again in ${retryAfterSeconds} seconds.`,
      retryAfterSeconds,
      currentCount: result.currentCount,
      limit: result.limit,
    });
    return true; // Rate limited — response sent
  }

  return false; // Allowed — proceed
}
