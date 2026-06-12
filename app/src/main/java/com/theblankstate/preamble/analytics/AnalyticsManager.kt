package com.theblankstate.preamble.analytics

import android.util.Log
import com.posthog.PostHog
import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * AnalyticsManager — Singleton utility for PostHog custom event tracking.
 *
 * Yeh class ek central jagah hai jahan se app ke saare analytics events fire hote hain.
 * Har function ek specific user action ko track karta hai with relevant properties.
 * PostHog SDK ko directly access karne ki zaroorat nahi — bas AnalyticsManager call karo.
 *
 * Usage: AnalyticsManager.trackTaskCreated("Work", isPriority = true)
 */
object AnalyticsManager {

    private const val TAG = "AnalyticsManager"

    private var firebaseAnalytics: FirebaseAnalytics? = null

    fun initialize(context: Context) {
        try {
            firebaseAnalytics = FirebaseAnalytics.getInstance(context.applicationContext)
            Log.d(TAG, "Google Analytics (Firebase) initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Google Analytics initialization failed", e)
        }
    }

    // ═══════════════════════════════════════════════════
    //  USER IDENTIFICATION — Firebase UID link karna
    // ═══════════════════════════════════════════════════

    /**
     * Firebase UID se user ko identify karta hai PostHog mein.
     * Isse Firebase aur PostHog ka data cross-reference ho sakta hai.
     * Sign-in ke baad ek baar call karo, aur sign-out pe reset().
     *
     * @param firebaseUid Firebase Authentication se mila UID
     * @param email Optional email for enrichment
     * @param displayName Optional display name
     */
    fun identifyUser(
        firebaseUid: String,
        email: String? = null,
        displayName: String? = null
    ) {
        try {
            // PostHog mein user ko identify karna — unique distinctId set hota hai
            val userProperties = mutableMapOf<String, Any>(
                "firebase_uid" to firebaseUid
            )
            email?.let { userProperties["email"] = it }
            displayName?.let { userProperties["name"] = it }

            PostHog.identify(
                distinctId = firebaseUid,
                userProperties = userProperties
            )
            Log.d(TAG, "User identified: $firebaseUid")
        } catch (e: Exception) {
            Log.e(TAG, "User identify fail hua", e)
        }

        try {
            firebaseAnalytics?.setUserId(firebaseUid)
            displayName?.let { firebaseAnalytics?.setUserProperty("name", it) }
            email?.let { firebaseAnalytics?.setUserProperty("email", it) }
            Log.d(TAG, "Firebase User identified: $firebaseUid")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase User identify fail hua", e)
        }
    }

    /**
     * User sign-out pe call karo — PostHog session reset hota hai.
     * Naya anonymous ID assign hota hai next session ke liye.
     */
    fun resetUser() {
        try {
            PostHog.reset()
            Log.d(TAG, "User session reset ho gaya")
        } catch (e: Exception) {
            Log.e(TAG, "Reset fail hua", e)
        }

        try {
            firebaseAnalytics?.setUserId(null)
            Log.d(TAG, "Firebase User session reset")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Reset fail hua", e)
        }
    }

    // ═══════════════════════════════════════════════════
    //  TASK EVENTS — Task lifecycle tracking
    // ═══════════════════════════════════════════════════

    /**
     * Jab user naya task banata hai tab fire hota hai.
     *
     * @param category Task ki category ya tag (e.g., "Work", "Personal")
     * @param isPriority Kya task high priority hai
     * @param hasDeadline Kya deadline set hai
     * @param isRecurring Kya recurring task hai
     */
    fun trackTaskCreated(
        category: String,
        isPriority: Boolean,
        hasDeadline: Boolean = false,
        isRecurring: Boolean = false
    ) {
        captureEvent(
            event = "task_created",
            properties = mapOf(
                "category" to category,
                "is_priority" to isPriority,
                "has_deadline" to hasDeadline,
                "is_recurring" to isRecurring
            )
        )
    }

    /**
     * Jab user task complete karta hai tab fire hota hai.
     *
     * @param taskId Task ka unique ID — analytics mein funnel tracking ke liye useful
     * @param daysOld Task kitne din puraana tha jab complete hua (procrastination tracking)
     */
    fun trackTaskCompleted(
        taskId: String,
        daysOld: Int = 0
    ) {
        captureEvent(
            event = "task_completed",
            properties = mapOf(
                "task_id" to taskId,
                "days_old" to daysOld
            )
        )
    }

    /**
     * Task delete hone pe track karna — churn analysis ke liye.
     */
    fun trackTaskDeleted(taskId: String) {
        captureEvent(
            event = "task_deleted",
            properties = mapOf("task_id" to taskId)
        )
    }

    // ═══════════════════════════════════════════════════
    //  CRASH & ERROR TRACKING — App crashes pata chalnge
    // ═══════════════════════════════════════════════════

    /**
     * App crash hone pe PostHog ko report karta hai.
     * PreambleApplication.onCreate() mein PostHog setup ke baad call karo.
     * Stacktrace ke saath crash event bhejta hai — PostHog Dashboard pe dikhega.
     */
    fun setupCrashTracking() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                // Crash event PostHog ko bhejo — stacktrace ke saath
                PostHog.capture(
                    event = "app_crashed",
                    properties = mapOf(
                        "exception_class" to (throwable::class.simpleName ?: "Unknown"),
                        "exception_message" to (throwable.message ?: "No message"),
                        "stacktrace" to throwable.stackTraceToString().take(2000),
                        "thread_name" to thread.name
                    )
                )
                // Turant flush karo — crash ke baad app band ho jaayega
                PostHog.flush()
            } catch (_: Exception) { /* ignore — crash handler mein crash nahi chahiye */ }

            // Original handler ko bhi call karo (Firebase Crashlytics etc.)
            defaultHandler?.uncaughtException(thread, throwable)
        }
        Log.d(TAG, "Crash tracking setup ho gaya")
    }

    /**
     * Non-fatal errors track karta hai — try-catch blocks mein use karo.
     * App crash nahi hota but PostHog pe error dikhta hai.
     *
     * @param source Kahan se error aaya (e.g., "GoogleSync", "AiChat")
     * @param error Exception object
     */
    fun trackError(source: String, error: Throwable) {
        captureEvent(
            event = "app_error",
            properties = mapOf(
                "source" to source,
                "error_class" to (error::class.simpleName ?: "Unknown"),
                "error_message" to (error.message ?: ""),
                "stacktrace" to error.stackTraceToString().take(1500)
            )
        )
    }

    // ═══════════════════════════════════════════════════
    //  FOCUS MODE / POMODORO — Productivity tracking
    // ═══════════════════════════════════════════════════

    /**
     * Focus mode ki har action ko track karta hai.
     *
     * @param action "started", "paused", "resumed", "finished", "cancelled"
     * @param durationSeconds Kitne seconds tak focus tha (finish/cancel pe)
     * @param taskId Kis task pe focus tha (optional)
     */
    fun trackFocusMode(
        action: String,
        durationSeconds: Int = 0,
        taskId: String? = null
    ) {
        val props = mutableMapOf<String, Any>(
            "action" to action,
            "duration_seconds" to durationSeconds
        )
        taskId?.let { props["task_id"] = it }

        captureEvent(event = "focus_mode", properties = props)
    }

    // ═══════════════════════════════════════════════════
    //  NOTIFICATIONS — FCM engagement + A/B tracking
    // ═══════════════════════════════════════════════════

    /**
     * FCM notification receive hone pe track karta hai.
     * Admin panel se campaign_id aur variant aayega — A/B test compare ke liye.
     */
    fun trackNotificationReceived(
        campaignId: String,
        variant: String = "default",
        channelType: String = "broadcast"
    ) {
        captureEvent(
            event = "notification_received",
            properties = mapOf(
                "campaign_id" to campaignId,
                "variant" to variant,
                "channel_type" to channelType
            )
        )
    }

    /**
     * Notification click track karta hai.
     * Admin panel se aane waali campaignId link hoti hai.
     *
     * @param campaignId Notification campaign ka ID (FCM/admin panel se)
     * @param notificationType Type of notification (e.g., "reminder", "broadcast", "promo")
     */
    fun trackNotificationClicked(
        campaignId: String,
        notificationType: String = "unknown"
    ) {
        captureEvent(
            event = "notification_clicked",
            properties = mapOf(
                "campaign_id" to campaignId,
                "notification_type" to notificationType
            )
        )
    }

    // ═══════════════════════════════════════════════════
    //  ADMIN ANNOUNCEMENTS — In-app card engagement
    // ═══════════════════════════════════════════════════

    /**
     * Admin announcement / feature discovery card shown hone pe track.
     */
    fun trackAnnouncementShown(taskId: String, type: String) {
        captureEvent(
            event = "announcement_shown",
            properties = mapOf(
                "announcement_id" to taskId,
                "type" to type
            )
        )
    }

    /**
     * Admin announcement action button click.
     */
    fun trackAnnouncementClicked(taskId: String, type: String, actionLabel: String) {
        captureEvent(
            event = "announcement_clicked",
            properties = mapOf(
                "announcement_id" to taskId,
                "type" to type,
                "action_label" to actionLabel
            )
        )
    }

    /**
     * Admin announcement dismiss (swipe/close).
     */
    fun trackAnnouncementDismissed(taskId: String, type: String) {
        captureEvent(
            event = "announcement_dismissed",
            properties = mapOf(
                "announcement_id" to taskId,
                "type" to type
            )
        )
    }

    // ═══════════════════════════════════════════════════
    //  SCREEN VIEWS — Manual screen tracking for Compose
    // ═══════════════════════════════════════════════════

    /**
     * Compose screen view manually track karta hai.
     * Kyunki Compose mein traditional Activity/Fragment nahi hota,
     * har screen change pe yeh call hota hai.
     *
     * @param screenName Screen ka naam (e.g., "HomeScreen", "StatsScreen")
     */
    fun trackScreenView(screenName: String) {
        PostHog.screen(
            screenTitle = screenName,
            properties = mapOf("source" to "compose_navigation")
        )
        try {
            val bundle = android.os.Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, "MainActivity")
            }
            firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
            Log.d(TAG, "Screen view tracked (Firebase): $screenName")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase screen view track fail hua: $screenName", e)
        }
    }

    // ═══════════════════════════════════════════════════
    //  FEATURE FLAGS — A/B Testing support
    // ═══════════════════════════════════════════════════

    /**
     * Feature flag ka value check karta hai PostHog se.
     * A/B testing aur gradual rollout ke liye use hota hai.
     *
     * @param flagKey Flag ka naam (e.g., "new_ui_experiment")
     * @return true agar flag enabled hai, false otherwise
     */
    fun isFeatureEnabled(flagKey: String): Boolean {
        return try {
            val result = PostHog.isFeatureEnabled(flagKey)
            Log.d(TAG, "Feature flag '$flagKey' = $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Feature flag check fail hua: $flagKey", e)
            false
        }
    }

    /**
     * Feature flag ka payload/variant check karta hai.
     * Multivariate experiments mein specific variant milta hai.
     *
     * @param flagKey Flag ka naam
     * @return Variant value (String/Boolean/Number) ya null
     */
    fun getFeatureFlagPayload(flagKey: String): Any? {
        return try {
            PostHog.getFeatureFlagPayload(flagKey)
        } catch (e: Exception) {
            Log.e(TAG, "Feature flag payload fetch fail hua: $flagKey", e)
            null
        }
    }

    /**
     * Saare feature flags ek saath reload karta hai server se.
     * App start pe ya user login ke baad call karo.
     */
    fun reloadFeatureFlags() {
        try {
            PostHog.reloadFeatureFlags()
            Log.d(TAG, "Feature flags reload ho rahe hain...")
        } catch (e: Exception) {
            Log.e(TAG, "Feature flags reload fail hua", e)
        }
    }

    // ═══════════════════════════════════════════════════
    //  GENERIC EVENTS — Miscellaneous tracking
    // ═══════════════════════════════════════════════════

    /**
     * Google Calendar/Tasks sync event track karta hai.
     */
    fun trackSync(
        source: String,
        eventsCount: Int = 0,
        isManual: Boolean = false
    ) {
        captureEvent(
            event = "google_sync",
            properties = mapOf(
                "source" to source,
                "events_synced" to eventsCount,
                "is_manual" to isManual
            )
        )
    }

    /**
     * User ne onboarding complete kiya tab fire hota hai.
     */
    fun trackOnboardingComplete() {
        captureEvent(event = "onboarding_completed")
    }

    fun trackOnboardingComplete(hasAccount: Boolean) {
        captureEvent(
            event = "onboarding_completed",
            properties = mapOf("has_account" to hasAccount)
        )
    }

    fun trackOnboardingStarted() {
        captureEvent(event = "onboarding_started")
    }

    fun trackOnboardingChoice(hasAccount: Boolean) {
        captureEvent(
            event = "user_onboarding_signup",
            properties = mapOf("has_account" to hasAccount)
        )
    }

    fun trackAiParserUsed(inputType: String) {
        captureEvent(
            event = "ai_parser_used",
            properties = mapOf("input_type" to inputType)
        )
    }

    fun trackNotificationAction(type: String) {
        captureEvent(
            event = "notification_action_taken",
            properties = mapOf("type" to type)
        )
    }

    fun trackScreenOpened(screenName: String) {
        captureEvent(event = "${screenName}_opened")
    }

    fun trackScreenClosed(screenName: String, timeSpentSec: Double) {
        captureEvent(
            event = "${screenName}_closed",
            properties = mapOf("time_spent_sec" to timeSpentSec)
        )
    }

    fun trackAiChatMessageSent() {
        captureEvent(event = "ai_chat_message_sent")
    }

    /**
     * Theme change track karta hai — user preferences samajhne ke liye.
     */
    fun trackThemeChanged(themeName: String, isDarkMode: Boolean) {
        captureEvent(
            event = "theme_changed",
            properties = mapOf(
                "theme_name" to themeName,
                "is_dark_mode" to isDarkMode
            )
        )
    }

    /**
     * AI chat interaction track karta hai.
     */
    fun trackAiInteraction(action: String, messageCount: Int = 0) {
        captureEvent(
            event = "ai_interaction",
            properties = mapOf(
                "action" to action,
                "message_count" to messageCount
            )
        )
    }

    /**
     * User profile enrichment — extra properties set karna.
     * Gender, age, premium status etc. PostHog user profile mein store hota hai.
     */
    fun setUserProperties(properties: Map<String, Any>) {
        try {
            PostHog.identify(
                distinctId = PostHog.distinctId(),
                userProperties = properties
            )
            Log.d(TAG, "User properties set: ${properties.keys}")
        } catch (e: Exception) {
            Log.e(TAG, "User properties set fail hua", e)
        }
    }

    // ═══════════════════════════════════════════════════
    //  SUPER PROPERTIES — Har event ke saath jaayengi
    // ═══════════════════════════════════════════════════

    /**
     * App version aur device info register karta hai as super properties.
     * Yeh properties har event ke saath automatically attach hoti hain.
     */
    fun registerSuperProperties(appVersion: String, buildType: String) {
        try {
            PostHog.register(
                key = "app_version",
                value = appVersion
            )
            PostHog.register(
                key = "build_type",
                value = buildType
            )
            Log.d(TAG, "Super properties registered: v$appVersion ($buildType)")
        } catch (e: Exception) {
            Log.e(TAG, "Super properties register fail hua", e)
        }
    }

    // ═══════════════════════════════════════════════════
    //  CAPTURE HELPER — Single capture point
    // ═══════════════════════════════════════════════════

    /**
     * Generic event capture — saare events yahan se fire hote hain.
     * Public hai taki custom events bhi bhej sako AnalyticsManager.captureEvent() se.
     * Centralized error handling aur logging yahan hoti hai.
     */
    fun captureEvent(
        event: String,
        properties: Map<String, Any> = emptyMap()
    ) {
        try {
            PostHog.capture(
                event = event,
                properties = properties
            )
            Log.d(TAG, "Event captured: $event → $properties")
        } catch (e: Exception) {
            Log.e(TAG, "Event capture fail hua: $event", e)
        }

        try {
            val bundle = android.os.Bundle().apply {
                properties.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Double -> putDouble(key, value)
                        is Float -> putFloat(key, value)
                        else -> putString(key, value.toString())
                    }
                }
            }
            firebaseAnalytics?.logEvent(event, bundle)
            Log.d(TAG, "Event captured (Firebase): $event → $properties")
        } catch (e: Exception) {
            Log.e(TAG, "Event capture (Firebase) fail hua: $event", e)
        }
    }
}
