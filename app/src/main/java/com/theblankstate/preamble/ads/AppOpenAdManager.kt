package com.theblankstate.preamble.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.theblankstate.preamble.BuildConfig
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppOpenAdManager {

    private const val TAG = "AppOpenAdManager"
    private const val PREFS_NAME = "preamble_app_open_ad"
    private const val KEY_LAST_SHOWN_DATE = "last_shown_date"

    private var appOpenAd: AppOpenAd? = null
    private var isShowingAd = false
    private var isLoading = false
    private lateinit var appContext: Context

    // Pending show: when the ad isn't loaded yet, store the activity so we can show as soon as it loads
    private var pendingActivity: WeakReference<Activity>? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Called after MobileAds.initialize() completes — preload the ad so it's ready for showIfNotShownToday(). */
    fun preload() {
        // Skip loading if already shown today — saves network and resources
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastShown = prefs.getString(KEY_LAST_SHOWN_DATE, "") ?: ""
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        if (lastShown == today) {
            Log.d(TAG, "App open ad already shown today, skipping preload")
            return
        }
        Log.d(TAG, "Preloading app open ad (SDK initialized)")
        loadAdAndShowWhenReady(activity = null)
    }

    private fun loadAdAndShowWhenReady(activity: Activity?) {
        // Store pending activity BEFORE the guard — if preload() is already loading,
        // onResume() still needs to register the activity for auto-show on load
        if (activity != null) {
            pendingActivity = WeakReference(activity)
        }
        if (isLoading || appOpenAd != null) return
        isLoading = true
        AppOpenAd.load(
            appContext,
            BuildConfig.AD_APP_OPEN_UNIT_ID,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    isLoading = false
                    Log.d(TAG, "App open ad loaded")
                    // If there's a pending show request, show immediately
                    val pending = pendingActivity?.get()
                    pendingActivity = null
                    if (pending != null && !pending.isFinishing && !pending.isDestroyed) {
                        showAd(pending)
                    }
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    appOpenAd = null
                    isLoading = false
                    pendingActivity = null
                    Log.e(TAG, "App open ad failed to load: ${error.message}")
                }
            }
        )
    }

    fun showIfNotShownToday(activity: Activity) {
        if (isShowingAd) return

        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastShown = prefs.getString(KEY_LAST_SHOWN_DATE, "") ?: ""
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        if (lastShown == today) return

        val ad = appOpenAd
        if (ad == null) {
            // Ad not loaded yet — start loading and show when ready
            loadAdAndShowWhenReady(activity)
            return
        }

        showAd(activity)
    }

    private fun showAd(activity: Activity) {
        val ad = appOpenAd ?: return
        if (isShowingAd || activity.isFinishing || activity.isDestroyed) return

        // Re-check date (async load could have taken time)
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastShown = prefs.getString(KEY_LAST_SHOWN_DATE, "") ?: ""
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        if (lastShown == today) return

        isShowingAd = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                isShowingAd = false
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                appOpenAd = null
                isShowingAd = false
            }
        }

        prefs.edit().putString(KEY_LAST_SHOWN_DATE, today).apply()
        ad.show(activity)
    }
}
