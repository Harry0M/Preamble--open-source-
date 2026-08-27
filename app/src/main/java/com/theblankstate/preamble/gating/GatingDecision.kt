package com.theblankstate.preamble.gating

import com.theblankstate.preamble.data.EntitlementStore
import com.theblankstate.preamble.data.EntitlementTier
import com.theblankstate.preamble.data.PremiumFeature

/**
 * PURE premium-gating policy — no Android, network, or AI imports.
 *
 * The whole gating decision is expressed as one deterministic function of three
 * inputs so it is unit/property testable in isolation. The Android edge
 * ([com.theblankstate.preamble.data.FeatureGate]) is responsible for resolving the
 * effective [EntitlementTier] and reading the remote master switch, then delegating
 * here.
 *
 * Safety invariant: while the master switch is OFF the decision ignores the tier and
 * returns unlocked for every feature — byte-for-byte the same observable behavior as
 * the previous always-`true` stub.
 */
object GatingDecision {

    /**
     * The candidate set that becomes locked for non-premium tiers when the master
     * switch is ON. Everything outside this set stays unlocked regardless of tier.
     */
    val PREMIUM_CANDIDATES: Set<PremiumFeature> = setOf(
        PremiumFeature.AI_AUTO_PLANNING,        // AI auto-planning (Track A)
        PremiumFeature.WRAPPED,                 // advanced stats / Wrapped
        PremiumFeature.STATS_EXTENDED_RANGE,
        PremiumFeature.STATS_DEDICATED_SCREEN,
        PremiumFeature.UNLIMITED_AI_CREDITS,    // unlimited AI credits
        PremiumFeature.CIRCLES_COLLABORATION,   // circles & team collaboration
        PremiumFeature.TASK_SHARING,            // assigning/sharing tasks with friends
    )

    /**
     * PURE. The entire gating policy.
     *
     * - master switch OFF  -> always unlocked (tier ignored)            (Req 8.1–8.3)
     * - feature not a candidate -> always unlocked                      (Req 10.5)
     * - otherwise -> reuse [EntitlementStore.unlocks] tier classifier   (Req 10.1–10.3)
     */
    fun isFeatureUnlocked(
        feature: PremiumFeature,
        tier: EntitlementTier,
        masterSwitchEnabled: Boolean,
    ): Boolean = when {
        !masterSwitchEnabled -> true
        feature !in PREMIUM_CANDIDATES -> true
        else -> EntitlementStore.unlocks(tier)
    }
}
