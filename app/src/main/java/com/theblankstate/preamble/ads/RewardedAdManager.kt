package com.theblankstate.preamble.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.theblankstate.preamble.BuildConfig

object RewardedAdManager {

    private const val TAG = "RewardedAdManager"

    private var themeRewardedAd: RewardedAd? = null
    private var statsRewardedAd: RewardedAd? = null
    private var isThemeLoading = false
    private var isStatsLoading = false
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        // Don't preload ads at startup — load on demand when needed
        // This prevents SurfaceTexture buffer leaks from idle preloaded video ads
    }

    fun loadThemeAd() {
        if (isThemeLoading || themeRewardedAd != null) return
        isThemeLoading = true
        RewardedAd.load(appContext, BuildConfig.AD_THEME_UNIT_ID, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    themeRewardedAd = ad
                    isThemeLoading = false
                    Log.d(TAG, "Theme rewarded ad loaded")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    themeRewardedAd = null
                    isThemeLoading = false
                    Log.e(TAG, "Theme ad failed to load: ${error.message}")
                }
            })
    }

    fun loadStatsAd() {
        if (isStatsLoading || statsRewardedAd != null) return
        isStatsLoading = true
        RewardedAd.load(appContext, BuildConfig.AD_STATS_UNIT_ID, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    statsRewardedAd = ad
                    isStatsLoading = false
                    Log.d(TAG, "Stats rewarded ad loaded")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    statsRewardedAd = null
                    isStatsLoading = false
                    Log.e(TAG, "Stats ad failed to load: ${error.message}")
                }
            })
    }

    fun isThemeAdReady(): Boolean = themeRewardedAd != null
    fun isStatsAdReady(): Boolean = statsRewardedAd != null

    fun showThemeAd(activity: Activity, onResult: (Boolean) -> Unit) {
        val ad = themeRewardedAd
        if (ad == null) {
            onResult(false)
            loadThemeAd()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                themeRewardedAd = null
                loadThemeAd()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                themeRewardedAd = null
                loadThemeAd()
                onResult(false)
            }
        }
        ad.show(activity) {
            FeatureGateManager.unlockTheme(activity)
            onResult(true)
        }
    }

    fun showStatsAd(activity: Activity, onResult: (Boolean) -> Unit) {
        val ad = statsRewardedAd
        if (ad == null) {
            onResult(false)
            loadStatsAd()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                statsRewardedAd = null
                loadStatsAd()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                statsRewardedAd = null
                loadStatsAd()
                onResult(false)
            }
        }
        ad.show(activity) {
            FeatureGateManager.unlockStats(activity)
            onResult(true)
        }
    }
}
