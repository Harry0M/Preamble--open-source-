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

    // TODO: Replace with your actual AdMob rewarded ad unit ID
    private const val AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917" // Test ID — replace for production

    private val _balance = MutableStateFlow(0)
    val balance: StateFlow<Int> = _balance

    private val _isAdLoading = MutableStateFlow(false)
    val isAdLoading: StateFlow<Boolean> = _isAdLoading

    private var rewardedAd: RewardedAd? = null
    private var isLoadingAd = false

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

    /** Update balance from a chat response */
    fun updateBalance(creditsRemaining: Int) {
        _balance.value = creditsRemaining
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
        val ad = rewardedAd
        if (ad == null) {
            onError("Ad not ready yet. Please wait...")
            preloadAd(activity)
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                preloadAd(activity)  // Preload next ad
            }

            override fun onAdFailedToShowFullScreenContent(err: AdError) {
                rewardedAd = null
                onError("Ad failed to show: ${err.message}")
                preloadAd(activity)
            }
        }

        ad.show(activity) { _ ->
            // User earned reward — call server to add credits
            CoroutineScope(Dispatchers.Main).launch {
                val result = withContext(Dispatchers.IO) { CloudAiService.rewardCredits() }
                if (result != null) {
                    _balance.value = result.newBalance
                    onReward(result.newBalance)
                } else {
                    onError("Failed to add credits. Please try again.")
                }
            }
        }
    }

}
