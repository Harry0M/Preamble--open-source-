package com.theblankstate.preamble.ai

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages AI credits and AdMob rewarded ads.
 *
 * Credit economy:
 *   - Flash-lite + concise = FREE (no credits needed)
 *   - Better model or normal mode = costs credits
 *   - 1 rewarded ad = 10 credits
 *   - First-time bonus = 20 credits
 *
 * Credits are enforced SERVER-SIDE (Cloud Function checks before AI call).
 * This client-side manager is for UX only (show balance, preload ads).
 */
object AiCreditsManager {
    private const val TAG = "AiCreditsManager"

    // Production AdMob rewarded ad unit ID
    private const val AD_UNIT_ID = "ca-app-pub-1118281305976257/4246436187"

    private const val SUCCESS_COOLDOWN_MS = 5 * 60 * 1000L // 5 min after successful ad (mirrors server)

    private val _balance = MutableStateFlow(0)
    val balance: StateFlow<Int> = _balance

    private val _isAdLoading = MutableStateFlow(false)
    val isAdLoading: StateFlow<Boolean> = _isAdLoading

    private var rewardedAd: RewardedAd? = null
    private var isLoadingAd = false
    private var lastSuccessMs = 0L
    private var notAvailableTaps = 0
    private var notAvailableCooldownMs = 0L

    /**
     * Credit cost per 1000 tokens (input + output combined). Mirrors server config.ts.
     * Used for UI display ("≈ 1⚡/1k") — actual deduction happens server-side using real tokens.
     */
    val creditPer1kTokens = mapOf(
        "gemini-2.5-flash-lite" to 0,
        "gemini-2.5-flash" to 1,
        "mistral-small-latest" to 2,
        "mistral-medium-latest" to 8,
    )

    /** True if model is in free tier (no credits ever charged). */
    fun isFreeTier(model: String): Boolean = (creditPer1kTokens[model] ?: 1) == 0

    /** Display label for cost on a model chip. */
    fun costLabel(model: String): String {
        val rate = creditPer1kTokens[model] ?: 1
        return when {
            rate == 0 -> "FREE"
            else -> "${rate}⚡/1k"
        }
    }

    /** Conservative pre-flight check — paid tiers require at least 1 credit. */
    fun hasCredits(model: String): Boolean {
        if (isFreeTier(model)) return true
        return _balance.value >= 1
    }

    /** Fetch current balance from server */
    fun refreshBalance(scope: CoroutineScope) {
        scope.launch {
            val result = CloudAiService.getBalance()
            if (result != null) {
                _balance.value = result.balance
            }
        }
    }

    /** Preload a rewarded ad so it's ready when needed */
    fun preloadAd(context: Context) {
        if (isLoadingAd || rewardedAd != null) return
        isLoadingAd = true
        _isAdLoading.value = true

        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(context, AD_UNIT_ID, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                Log.d(TAG, "Rewarded ad loaded")
                rewardedAd = ad
                isLoadingAd = false
                _isAdLoading.value = false
                notAvailableTaps = 0
                notAvailableCooldownMs = 0L
            }

            override fun onAdFailedToLoad(err: LoadAdError) {
                Log.w(TAG, "Rewarded ad failed to load: ${err.message}")
                rewardedAd = null
                isLoadingAd = false
                _isAdLoading.value = false
            }
        })
    }

    /**
     * Show rewarded ad and reward credits on completion.
     * @param activity The hosting activity
     * @param onReward Called with new balance after successful reward
     * @param onError Called if ad fails to show or reward fails
     */
    fun showAdForCredits(
        activity: Activity,
        onReward: (newBalance: Int) -> Unit,
        onError: (String) -> Unit,
    ) {
        val now = System.currentTimeMillis()

        // Only gate: 5-min cooldown after a successful ad
        if (lastSuccessMs > 0 && now - lastSuccessMs < SUCCESS_COOLDOWN_MS) {
            val minutesLeft = ((SUCCESS_COOLDOWN_MS - (now - lastSuccessMs)) / 60_000).coerceAtLeast(1)
            onError("Next ad available in $minutesLeft min.")
            return
        }

        val ad = rewardedAd
        if (ad == null) {
            // If user is spamming "try again", enforce a short wait
            if (notAvailableCooldownMs > 0 && now - notAvailableCooldownMs < 30_000) {
                val secsLeft = ((30_000 - (now - notAvailableCooldownMs)) / 1000).coerceAtLeast(1)
                onError("Ad loading... please wait ${secsLeft}s.")
                return
            }
            notAvailableTaps++
            if (notAvailableTaps >= 3) {
                notAvailableTaps = 0
                notAvailableCooldownMs = now
                onError("Ad still loading. Please wait 30s.")
            } else {
                onError("Ad not available. Try again.")
            }
            preloadAd(activity)
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                preloadAd(activity)
            }

            override fun onAdFailedToShowFullScreenContent(err: AdError) {
                rewardedAd = null
                onError("Ad not available. Try again.")
                preloadAd(activity)
            }
        }

        ad.show(activity) { _ ->
            lastSuccessMs = System.currentTimeMillis()
            notAvailableTaps = 0
            notAvailableCooldownMs = 0L
            CoroutineScope(Dispatchers.Main).launch {
                val result = withContext(Dispatchers.IO) { CloudAiService.rewardCredits() }
                if (result != null) {
                    _balance.value = result.newBalance
                    onReward(result.newBalance)
                } else {
                    onError("Failed to add tokens. Try again.")
                }
            }
        }
    }

}
