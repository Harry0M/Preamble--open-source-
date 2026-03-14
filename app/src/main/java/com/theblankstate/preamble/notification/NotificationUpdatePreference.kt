package com.theblankstate.preamble.notification

/**
 * Controls notification update frequency based on user preference.
 *
 * Balances between:
 * - Real-time accuracy: How quickly task changes appear in notification
 * - Battery impact: How often the service wakes up and queries the database
 */
enum class NotificationUpdatePreference(val intervalMs: Long, val displayName: String) {
    /**
     * Aggressive mode: Updates every 10 seconds
     * Best for: Users who want to see changes immediately
     * Battery impact: High - frequent wakeups
     */
    AGGRESSIVE(10_000L, "Aggressive (10 sec)"),

    /**
     * Balanced mode: Updates every 30 seconds (default)
     * Best for: Most users - good balance between responsiveness and battery
     * Battery impact: Medium
     */
    BALANCED(30_000L, "Balanced (30 sec)"),

    /**
     * Battery Saver mode: Updates every 2 minutes when no tasks, every 30 seconds when tasks exist
     * Best for: Battery-conscious users
     * Battery impact: Low - minimal wakeups when list is empty
     */
    BATTERY_SAVER(120_000L, "Battery Saver (2 min)");

    companion object {
        const val PREF_NAME = "preamble_prefs"
        const val PREF_UPDATE_MODE = "notification_update_mode"

        fun fromStringOrDefault(value: String?): NotificationUpdatePreference {
            return try {
                valueOf(value ?: BALANCED.name)
            } catch (e: IllegalArgumentException) {
                BALANCED
            }
        }

        fun getIntervalForMode(mode: NotificationUpdatePreference, hasTasks: Boolean): Long {
            return when (mode) {
                AGGRESSIVE, BALANCED -> mode.intervalMs
                BATTERY_SAVER -> if (hasTasks) 30_000L else mode.intervalMs
            }
        }
    }
}
