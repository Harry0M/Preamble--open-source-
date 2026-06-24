package com.theblankstate.preamble.data

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.theblankstate.preamble.ai.AiConfigService
import com.theblankstate.preamble.gating.GatingDecision
import kotlinx.coroutines.tasks.await

/**
 * Server-driven entitlement. Source of truth = Firestore users/{uid}.entitlement.*
 * Client caches locally for offline use only. Admin panel mutates Firestore
 * directly; client reads on every app start and overwrites local cache.
 */
enum class EntitlementTier {
    FREE_TIER,          // default — locked premium features, upsell shown
    UNPREMIUM,          // previously premium but lapsed — locked + card shown
    PROMOTIONAL,        // launch cohort; everything unlocked, no card
    PREMIUM,            // paid premium — card shown
    PREMIUM_STUDENT,    // paid premium + student discount — special card
    PREMIUM_YOUNGSTER,  // paid premium + under-25 — special card
}

data class Entitlement(
    val tier: EntitlementTier = EntitlementTier.FREE_TIER,
    val expiresAtMs: Long = 0L,                   // 0 = never expires (lifetime)
    val studentValidityExpiresAtMs: Long = 0L,    // when student status re-verification needed
    val youngsterValidityExpiresAtMs: Long = 0L,  // when youngster status expires (user ages out)
    val activatedAtMs: Long = 0L,
    val lastSyncedAtMs: Long = 0L,
)

object EntitlementStore {
    private const val TAG = "EntitlementStore"
    private const val PREFS = "preamble_entitlement"
    private const val K_TIER = "tier"
    private const val K_EXPIRES = "expires_at"
    private const val K_STUDENT_EXPIRES = "student_expires_at"
    private const val K_YOUNGSTER_EXPIRES = "youngster_expires_at"
    private const val K_ACTIVATED = "activated_at"
    private const val K_LAST_SYNCED = "last_synced_at"

    fun load(ctx: Context): Entitlement {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val tier = runCatching {
            EntitlementTier.valueOf(sp.getString(K_TIER, null) ?: EntitlementTier.FREE_TIER.name)
        }.getOrElse { EntitlementTier.FREE_TIER }
        return Entitlement(
            tier = tier,
            expiresAtMs = sp.getLong(K_EXPIRES, 0L),
            studentValidityExpiresAtMs = sp.getLong(K_STUDENT_EXPIRES, 0L),
            youngsterValidityExpiresAtMs = sp.getLong(K_YOUNGSTER_EXPIRES, 0L),
            activatedAtMs = sp.getLong(K_ACTIVATED, 0L),
            lastSyncedAtMs = sp.getLong(K_LAST_SYNCED, 0L),
        )
    }

    fun save(ctx: Context, e: Entitlement) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(K_TIER, e.tier.name)
            .putLong(K_EXPIRES, e.expiresAtMs)
            .putLong(K_STUDENT_EXPIRES, e.studentValidityExpiresAtMs)
            .putLong(K_YOUNGSTER_EXPIRES, e.youngsterValidityExpiresAtMs)
            .putLong(K_ACTIVATED, e.activatedAtMs)
            .putLong(K_LAST_SYNCED, e.lastSyncedAtMs)
            .apply()
    }

    /** Effective tier, applying expiries. If premium expired, downgrades to UNPREMIUM. */
    fun effectiveTier(e: Entitlement): EntitlementTier {
        val now = System.currentTimeMillis()
        val premium = e.tier in setOf(
            EntitlementTier.PREMIUM,
            EntitlementTier.PREMIUM_STUDENT,
            EntitlementTier.PREMIUM_YOUNGSTER,
        )
        if (premium && e.expiresAtMs != 0L && e.expiresAtMs <= now) {
            return EntitlementTier.UNPREMIUM
        }
        // Youngster validity expiry → downgrade to plain PREMIUM (paid) but lose youngster discount
        if (e.tier == EntitlementTier.PREMIUM_YOUNGSTER
            && e.youngsterValidityExpiresAtMs != 0L
            && e.youngsterValidityExpiresAtMs <= now) {
            return EntitlementTier.PREMIUM
        }
        // Student validity expiry → same
        if (e.tier == EntitlementTier.PREMIUM_STUDENT
            && e.studentValidityExpiresAtMs != 0L
            && e.studentValidityExpiresAtMs <= now) {
            return EntitlementTier.PREMIUM
        }
        return e.tier
    }

    fun effectiveTier(ctx: Context): EntitlementTier = effectiveTier(load(ctx))

    fun unlocks(tier: EntitlementTier): Boolean = when (tier) {
        EntitlementTier.FREE_TIER,
        EntitlementTier.UNPREMIUM -> false
        EntitlementTier.PROMOTIONAL,
        EntitlementTier.PREMIUM,
        EntitlementTier.PREMIUM_STUDENT,
        EntitlementTier.PREMIUM_YOUNGSTER -> true
    }

    /** Fetch entitlement from Firestore users/{uid}. Call after sign-in + on app start. */
    suspend fun syncFromFirestore(ctx: Context): Entitlement? {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        return try {
            val snap = FirebaseFirestore.getInstance()
                .collection("users").document(uid).get().await()
            val tierStr = snap.getString("entitlement_tier") ?: EntitlementTier.FREE_TIER.name
            val tier = runCatching { EntitlementTier.valueOf(tierStr) }
                .getOrElse { EntitlementTier.FREE_TIER }
            val fetched = Entitlement(
                tier = tier,
                expiresAtMs = snap.getLong("entitlement_expires_at") ?: 0L,
                studentValidityExpiresAtMs = snap.getLong("entitlement_student_expires_at") ?: 0L,
                youngsterValidityExpiresAtMs = snap.getLong("entitlement_youngster_expires_at") ?: 0L,
                activatedAtMs = snap.getLong("entitlement_activated_at") ?: 0L,
                lastSyncedAtMs = System.currentTimeMillis(),
            )
            save(ctx, fetched)
            fetched
        } catch (e: Exception) {
            Log.w(TAG, "syncFromFirestore failed", e)
            null
        }
    }

    /**
     * Stamp new user as PROMOTIONAL on first sign-in. Idempotent: only writes
     * if user doc has no entitlement_tier yet.
     */
    suspend fun seedPromotionalIfFirstTime(ctx: Context) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        try {
            val ref = FirebaseFirestore.getInstance().collection("users").document(uid)
            val snap = ref.get().await()
            if (snap.getString("entitlement_tier") == null) {
                val now = System.currentTimeMillis()
                ref.set(
                    mapOf(
                        "entitlement_tier" to EntitlementTier.PROMOTIONAL.name,
                        "entitlement_activated_at" to now,
                        "entitlement_expires_at" to 0L,
                    ),
                    SetOptions.merge()
                ).await()
            }
        } catch (e: Exception) {
            Log.w(TAG, "seedPromotionalIfFirstTime failed", e)
        }
    }
}

/** Features that premium gates. Free/Unpremium tier sees lock + upsell. */
enum class PremiumFeature {
    WRAPPED,
    AI_AUTO_SUBTASKS,
    AI_EDIT_FROM_NOTIFICATION,
    EXPRESSIVE_APPEARANCE,
    STATS_EXTENDED_RANGE,
    STATS_DEDICATED_SCREEN,
    AI_AUTO_PLANNING,
    UNLIMITED_AI_CREDITS,
}

/**
 * Android edge for the premium-gating decision.
 *
 * Resolves the two real-world inputs at the edge — the effective [EntitlementTier] (with
 * expiry resolution, Req 9.3) and the remotely fetched master switch
 * ([AiConfigService.premiumGatingEnabled], OFF on unfetched/absent/read failure, Req 7.1/7.2) —
 * then delegates the actual policy to the pure [GatingDecision].
 *
 * SAFETY (Req 8.3): while the master switch is OFF, [GatingDecision] ignores the tier and
 * returns unlocked for every feature, so this collapses to the previous always-`true`
 * behavior — no live paywall and no observable change for existing users. Gating only takes
 * effect when an owner deliberately turns `premium_gating_enabled` ON.
 */
object FeatureGate {
    fun isUnlocked(ctx: Context, feature: PremiumFeature): Boolean {
        val tier = EntitlementStore.effectiveTier(ctx)          // Req 9.3 expiry resolution
        val switch = AiConfigService.premiumGatingEnabled()     // Req 7.1/7.2 remote read, OFF default
        return GatingDecision.isFeatureUnlocked(feature, tier, switch)
    }
}
