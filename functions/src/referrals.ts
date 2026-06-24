/**
 * Referral rewards (growth-loops) — two-sided referral invites.
 *
 * This module holds:
 *   1. `classifyReferralEligibility` — a PURE, firebase-free decision function
 *      that decides whether a referred signup may be rewarded. It is the single
 *      source of truth for the eligibility gates and is property-tested.
 *   2. the `onReferralFriendship` Firestore trigger that wires this pure logic
 *      to the Admin SDK credit transaction.
 *
 * The reward itself (incrementing `ai_credits` and flipping the referral state
 * to "rewarded") is server-authoritative and Admin-SDK-only; this pure function
 * performs no Firestore/Auth I/O so it can be exhaustively tested in isolation.
 */
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions/v2";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getAuth } from "firebase-admin/auth";
import { REFERRAL_REWARD, REFERRAL_NEW_ACCOUNT_WINDOW_MS } from "./config";

/** Lifecycle state of a `/referrals/{referredUid}` attribution record. */
export type ReferralState = "pending" | "rewarded" | "rejected";

/** All inputs needed to decide whether a referral may be rewarded. */
export interface ReferralInput {
  /** uid of the existing user who referred the new account. */
  referrerUid: string;
  /** uid of the newly created (referred) account; equals the doc id. */
  referredUid: string;
  /** When the attribution was created, in ms (from the /referrals doc). */
  attributedAt: number;
  /** Referred account's creation time, in ms (from Admin Auth metadata). */
  referredAccountCreatedAt: number;
  /** Current state of the attribution record. */
  state: ReferralState;
  /** Whether the reciprocal friendship (both friend docs) exists. */
  friendshipEstablished: boolean;
  /** Current time, in ms. */
  now: number;
  /** New-account tolerance window, in ms (REFERRAL_NEW_ACCOUNT_WINDOW_MS). */
  windowMs: number;
}

/** Outcome of the eligibility classification. */
export type ReferralDecision =
  | { kind: "Eligible" }
  | { kind: "RejectSelfReferral" }    // Req 4.1
  | { kind: "RejectNotNewAccount" }   // Req 4.2
  | { kind: "RejectAlreadyRewarded" } // Req 3.2, 4.3, 4.4
  | { kind: "RejectNoFriendship" };   // Req 3.1 precondition unmet

/**
 * Decides whether a referred signup is eligible for the Referral_Reward.
 *
 * Pure function — no Firestore/Auth calls. Gates are evaluated IN ORDER, and the
 * first failing gate determines the rejection reason:
 *
 *   1. state === "rewarded"               -> RejectAlreadyRewarded (short-circuit,
 *                                            beats every other gate so a reward can
 *                                            never be granted twice — Req 3.2/4.3/4.4)
 *   2. referrerUid === referredUid        -> RejectSelfReferral (Req 4.1)
 *   3. !friendshipEstablished             -> RejectNoFriendship (Req 3.1)
 *   4. referredAccountCreatedAt outside
 *      [attributedAt - windowMs,
 *       attributedAt + windowMs]          -> RejectNotNewAccount (Req 4.2)
 *   5. otherwise                          -> Eligible (Req 3.1)
 */
export function classifyReferralEligibility(input: ReferralInput): ReferralDecision {
  // 1. Already rewarded — durable at-most-once short-circuit (Req 3.2, 4.3, 4.4).
  if (input.state === "rewarded") {
    return { kind: "RejectAlreadyRewarded" };
  }

  // 2. Self-referral (Req 4.1).
  if (input.referrerUid === input.referredUid) {
    return { kind: "RejectSelfReferral" };
  }

  // 3. Reciprocal friendship must exist (Req 3.1 precondition).
  if (!input.friendshipEstablished) {
    return { kind: "RejectNoFriendship" };
  }

  // 4. Genuinely-new-account window (Req 4.2): creation time must fall within
  //    ±windowMs of the attribution timestamp, else the account materially
  //    predates the attribution and is treated as pre-existing.
  const lowerBound = input.attributedAt - input.windowMs;
  const upperBound = input.attributedAt + input.windowMs;
  if (
    input.referredAccountCreatedAt < lowerBound ||
    input.referredAccountCreatedAt > upperBound
  ) {
    return { kind: "RejectNotNewAccount" };
  }

  // 5. All gates passed.
  return { kind: "Eligible" };
}

// ---------------------------------------------------------------------------
// onReferralFriendship — Firestore trigger + atomic credit transaction
// ---------------------------------------------------------------------------

/** Shape of a `/referrals/{referredUid}` attribution doc (defensive subset). */
interface ReferralDoc {
  referrerUid?: unknown;
  referredUid?: unknown;
  attributedAt?: unknown;
  state?: unknown;
}

function cleanUid(value: unknown): string | null {
  return typeof value === "string" && value.trim().length > 0 ? value.trim() : null;
}

function asMillis(value: unknown): number {
  return typeof value === "number" && Number.isFinite(value) ? value : 0;
}

/**
 * A `/referrals` doc matches this friendship pair when its (referrerUid, referredUid)
 * equals the unordered {a, b} pair AND the two are distinct. Returns the normalized
 * pair when it matches, else null.
 */
function matchPair(
  doc: ReferralDoc | undefined,
  a: string,
  b: string
): { referrerUid: string; referredUid: string; attributedAt: number; state: ReferralState } | null {
  if (!doc) return null;
  const referrerUid = cleanUid(doc.referrerUid);
  const referredUid = cleanUid(doc.referredUid);
  if (!referrerUid || !referredUid) return null;
  if (referrerUid === referredUid) return null;
  const matches =
    (referrerUid === a && referredUid === b) ||
    (referrerUid === b && referredUid === a);
  if (!matches) return null;
  const state: ReferralState =
    doc.state === "rewarded" || doc.state === "rejected" ? doc.state : "pending";
  return {
    referrerUid,
    referredUid,
    attributedAt: asMillis(doc.attributedAt),
    state,
  };
}

/**
 * onReferralFriendship — `onDocumentCreated` trigger on a friend-doc creation.
 *
 * Fires when `users/{ownerUid}/friends/{friendUid}` is created. The referral may be
 * attributed in either direction, so it looks up `/referrals/{ownerUid}` and
 * `/referrals/{friendUid}` and selects the doc whose (referrerUid, referredUid)
 * matches this pair. If neither matches → manual invite, no reward (Req 5.1/5.2).
 *
 * It then verifies the reciprocal friendship (both friend docs exist), reads the
 * referred account's auth-metadata creation time (fail-closed on lookup error),
 * and runs the pure `classifyReferralEligibility`. A non-`Eligible` result records
 * `state: "rejected"` with a reason and exits without incrementing (Req 3.3). An
 * `Eligible` result runs a single transaction that re-checks `state === "pending"`,
 * increments BOTH users' `ai_credits` (+REFERRAL_REWARD) and `ai_credits_total_earned`,
 * and flips `state` pending → rewarded with `rewardedAt`, all atomically (Req 3.5, 3.6).
 */
export const onReferralFriendship = onDocumentCreated(
  {
    database: "preamble",
    document: "users/{ownerUid}/friends/{friendUid}",
  },
  async (event) => {
    const ownerUid = cleanUid(event.params.ownerUid);
    const friendUid = cleanUid(event.params.friendUid);
    if (!ownerUid || !friendUid || ownerUid === friendUid) return;

    const db = getFirestore("preamble");

    // (1) Select the /referrals doc that matches this friendship pair (either direction).
    const [ownerRefSnap, friendRefSnap] = await Promise.all([
      db.doc(`referrals/${ownerUid}`).get(),
      db.doc(`referrals/${friendUid}`).get(),
    ]);

    const match =
      matchPair(ownerRefSnap.data() as ReferralDoc | undefined, ownerUid, friendUid) ??
      matchPair(friendRefSnap.data() as ReferralDoc | undefined, ownerUid, friendUid);

    // (2) No attribution for this pair → manual invite, no reward (Req 5.1/5.2).
    if (!match) return;

    const { referrerUid, referredUid, attributedAt, state } = match;
    const referredRef = db.doc(`referrals/${referredUid}`);

    // (3) Verify the reciprocal friendship: both friend docs must exist.
    const [aToB, bToA] = await Promise.all([
      db.doc(`users/${referrerUid}/friends/${referredUid}`).get(),
      db.doc(`users/${referredUid}/friends/${referrerUid}`).get(),
    ]);
    const friendshipEstablished = aToB.exists && bToA.exists;

    // Track the friendship funnel event on first reciprocal establishment, while the
    // referral is still pending (best-effort, never affects the reward — Req 6.4).
    if (friendshipEstablished && state === "pending") {
      logger.info("referral-friendship", { referrerUid, referredUid });
    }

    // (4) Read the referred account's creation time from auth metadata.
    // A lookup failure is fail-closed: treat the referral as rejected (no reward).
    let referredAccountCreatedAt: number;
    try {
      const userRecord = await getAuth().getUser(referredUid);
      const creationTime = userRecord.metadata.creationTime;
      const parsed = creationTime ? Date.parse(creationTime) : NaN;
      if (!Number.isFinite(parsed)) {
        throw new Error("missing or unparseable creationTime");
      }
      referredAccountCreatedAt = parsed;
    } catch (err: any) {
      logger.error("onReferralFriendship: auth metadata lookup failed (fail-closed)", {
        referrerUid,
        referredUid,
        error: err?.message,
      });
      await referredRef
        .set({ state: "rejected", rejectedReason: "AuthMetadataLookupFailed" }, { merge: true })
        .catch(() => {});
      return;
    }

    // (5) Classify eligibility with the pure decision function.
    const decision = classifyReferralEligibility({
      referrerUid,
      referredUid,
      attributedAt,
      referredAccountCreatedAt,
      state,
      friendshipEstablished,
      now: Date.now(),
      windowMs: REFERRAL_NEW_ACCOUNT_WINDOW_MS,
    });

    // (5a) Non-eligible → record rejection and exit without incrementing (Req 3.3).
    if (decision.kind !== "Eligible") {
      // Don't clobber an already-granted reward record.
      if (decision.kind === "RejectAlreadyRewarded") return;
      await referredRef
        .set({ state: "rejected", rejectedReason: decision.kind }, { merge: true })
        .catch(() => {});
      return;
    }

    // (6) Eligible → atomic transaction: re-check pending, increment both balances,
    //     flip pending → rewarded. Concurrent triggers / friend churn cannot double-pay
    //     because the second transaction sees state !== "pending" (Req 3.2, 3.5, 3.6, 4.3, 4.4).
    let granted = false;
    try {
      await db.runTransaction(async (tx) => {
        const snap = await tx.get(referredRef);
        if (snap.data()?.state !== "pending") return; // already handled — at-most-once
        tx.set(
          db.doc(`users/${referrerUid}`),
          {
            ai_credits: FieldValue.increment(REFERRAL_REWARD),
            ai_credits_total_earned: FieldValue.increment(REFERRAL_REWARD),
          },
          { merge: true }
        );
        tx.set(
          db.doc(`users/${referredUid}`),
          {
            ai_credits: FieldValue.increment(REFERRAL_REWARD),
            ai_credits_total_earned: FieldValue.increment(REFERRAL_REWARD),
          },
          { merge: true }
        );
        tx.update(referredRef, { state: "rewarded", rewardedAt: Date.now() });
        granted = true;
      });
    } catch (err: any) {
      logger.error("onReferralFriendship: reward transaction failed", {
        referrerUid,
        referredUid,
        error: err?.message,
      });
      return;
    }

    // (7) Track the reward grant after commit (best-effort, never rolls back — Req 6.5).
    if (granted) {
      logger.info("referral-reward-granted", {
        referrerUid,
        referredUid,
        reward: REFERRAL_REWARD,
      });
    }
  }
);
