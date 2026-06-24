package com.theblankstate.preamble.referral

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.theblankstate.preamble.repository.WorkspaceRepository
import kotlinx.coroutines.tasks.await

/**
 * Firestore gateway for the client side of referral attribution (Requirement 2).
 *
 * The client can only ever create a single **pending** `/referrals/{referredUid}`
 * document naming someone else — it can never grant credits or move the record to
 * `"rewarded"`/`"rejected"` (that path is Admin-SDK only and enforced by
 * Security_Rules). Every write is wrapped in `runCatching` so a referral failure
 * can never block sign-up: the worst case is no attribution, which simply means no
 * reward.
 *
 * This class is a thin gateway (not pure); the attribution decision itself lives in
 * the Android/Firebase-free [ReferralAttribution].
 */
class ReferralRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(FIRESTORE_DATABASE_ID),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val workspaceRepository: WorkspaceRepository = WorkspaceRepository(),
) {
    val currentUid: String?
        get() = auth.currentUser?.uid

    /**
     * Creates exactly one pending attribution for the current account
     * (Requirements 2.2, 2.3).
     *
     * Writes `/referrals/{currentUid}` with `state: "pending"`, the resolved
     * referrer, and client clocks (`referredCreatedAt`, `attributedAt`). If a
     * document already exists the call is a **no-op success** so a given
     * Referred_User account is associated with at most one Referrer. Any failure
     * is captured into [Result.failure] rather than thrown, so sign-up always
     * completes.
     */
    suspend fun createPendingAttribution(
        referrerUid: String,
        referrerPreambleId: String,
    ): Result<Unit> = runCatching {
        val uid = currentUid ?: error("Sign in to record a referral attribution")
        val reference = db.collection(REFERRALS).document(uid)

        // At-most-one referrer (Req 2.3): never overwrite an existing attribution.
        if (reference.get().await().exists()) {
            return@runCatching
        }

        val now = System.currentTimeMillis()
        val attribution = mapOf(
            "referredUid" to uid,
            "referrerUid" to referrerUid,
            "referrerPreambleId" to referrerPreambleId,
            "state" to STATE_PENDING,
            "referredCreatedAt" to now,
            "attributedAt" to now,
        )
        reference.set(attribution).await()
    }.onFailure { Log.e(TAG, "Failed to create pending referral attribution", it) }

    /**
     * Resolves a referrer Preamble_ID to a single account uid (Requirement 2.4),
     * reusing [WorkspaceRepository.resolvePreambleId]. Returns `null` when the id
     * does not resolve to exactly one existing account.
     */
    suspend fun resolveReferrer(preambleId: String): String? {
        val data = workspaceRepository.resolvePreambleId(preambleId) ?: return null
        return data["uid"] as? String
    }

    private companion object {
        const val TAG = "ReferralRepository"
        const val FIRESTORE_DATABASE_ID = "preamble"
        const val REFERRALS = "referrals"
        const val STATE_PENDING = "pending"
    }
}
