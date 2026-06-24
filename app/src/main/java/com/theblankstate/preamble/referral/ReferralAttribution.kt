package com.theblankstate.preamble.referral

/**
 * Pure logic for the client-side attribution-candidate decision (Requirement 2).
 *
 * Given a retained referrer `Preamble_ID`, the uid that id resolves to (if any),
 * and the just-created account's identity, [ReferralAttribution.decide] decides
 * whether the new account should record a referral attribution. The decision
 * names at most one referrer, never attributes a self-referral, and always
 * permits account creation to continue regardless of the outcome.
 *
 * This object is deliberately free of Android, Firebase, and serialization
 * dependencies so the decision can be exercised directly by JVM property-based
 * tests (Property 1). The repository adapts Firestore resolution to a string
 * uid and delegates here.
 */
sealed interface AttributionDecision {
    /**
     * The new account should record an attribution to the single resolved
     * referrer (Requirement 2.2).
     */
    data class Attribute(
        val referrerUid: String,
        val referrerPreambleId: String,
    ) : AttributionDecision

    /** Reasons no attribution is recorded. */
    enum class Skip {
        /** No referrer id was retained through account creation (Requirement 2.5). */
        NoPendingReferrer,

        /** The referrer id did not resolve to exactly one existing account (Requirement 2.4). */
        Unresolved,

        /** The referrer resolved to the new account itself (Requirement 2.6). */
        SelfReferral,
    }

    /** No attribution is recorded; account creation still completes unchanged. */
    data class Skipped(val reason: Skip) : AttributionDecision
}

object ReferralAttribution {

    /**
     * Decides whether a new account should record an attribution (Requirements 2.2–2.6).
     *
     * @param pendingReferrerId normalized referrer Preamble_ID, or null/blank if none retained (2.5)
     * @param resolvedReferrerUid uid the referrer Preamble_ID resolves to, or null if it does not
     *        resolve to exactly one account (2.4)
     * @param newAccountUid the just-created account's uid
     * @param newAccountPreambleId the new account's own normalized Preamble_ID (for the self check, 2.6)
     */
    fun decide(
        pendingReferrerId: String?,
        resolvedReferrerUid: String?,
        newAccountUid: String,
        newAccountPreambleId: String,
    ): AttributionDecision {
        if (pendingReferrerId.isNullOrBlank()) {
            return AttributionDecision.Skipped(AttributionDecision.Skip.NoPendingReferrer)
        }
        if (resolvedReferrerUid == null) {
            return AttributionDecision.Skipped(AttributionDecision.Skip.Unresolved)
        }
        if (resolvedReferrerUid == newAccountUid || pendingReferrerId == newAccountPreambleId) {
            return AttributionDecision.Skipped(AttributionDecision.Skip.SelfReferral)
        }
        return AttributionDecision.Attribute(resolvedReferrerUid, pendingReferrerId)
    }
}
