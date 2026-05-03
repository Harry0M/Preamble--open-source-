package com.theblankstate.preamble.notification

import android.content.Context
import java.util.Calendar

/**
 * Manages the user's preferred "Weekly Recap" day with anti-gaming protection.
 *
 * ## Anti-gaming strategy
 * When the user changes the recap day, a 7-day cooldown starts. During the cooldown:
 *   - The OLD day remains the effective recap day.
 *   - The user sees a message that the new day takes effect next week.
 * After the cooldown, the new day becomes effective and the recap is available on that day.
 *
 * This prevents users from switching the recap day just to access the recap early.
 */
object RecapDayManager {

    private const val PREFS = "preamble_recap_day"
    private const val K_DAY = "recap_day"               // Calendar.SATURDAY, etc.
    private const val K_PENDING_DAY = "pending_day"      // Day that will take effect after cooldown
    private const val K_CHANGE_TS = "day_change_ts"      // Timestamp when user last changed the day
    private const val COOLDOWN_MS = 7L * 24 * 60 * 60 * 1000 // 7 days

    /** Default recap day: Sunday */
    private const val DEFAULT_DAY = Calendar.SUNDAY

    /** All days the user can pick from. */
    val SELECTABLE_DAYS: List<Int> = listOf(
        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
        Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
    )

    /**
     * Returns the currently *effective* recap day (Calendar constant).
     * If a change is pending but cooldown hasn't passed, returns the old day.
     */
    fun getEffectiveDay(context: Context): Int {
        val p = prefs(context)
        val activeDay = p.getInt(K_DAY, DEFAULT_DAY)
        val pendingDay = p.getInt(K_PENDING_DAY, -1)
        val changeTs = p.getLong(K_CHANGE_TS, 0L)

        if (pendingDay != -1 && System.currentTimeMillis() - changeTs >= COOLDOWN_MS) {
            // Cooldown has passed — promote pending day
            p.edit()
                .putInt(K_DAY, pendingDay)
                .remove(K_PENDING_DAY)
                .remove(K_CHANGE_TS)
                .apply()
            return pendingDay
        }
        return activeDay
    }

    /**
     * Returns the display day the user has selected (may be pending).
     */
    fun getSelectedDay(context: Context): Int {
        val p = prefs(context)
        val pending = p.getInt(K_PENDING_DAY, -1)
        return if (pending != -1) pending else p.getInt(K_DAY, DEFAULT_DAY)
    }

    /**
     * Sets a new recap day. If it differs from the current effective day,
     * a 7-day cooldown starts before it takes effect.
     * Returns true if a cooldown was started (i.e. the day actually changed).
     */
    fun setDay(context: Context, calendarDay: Int): Boolean {
        val effective = getEffectiveDay(context)
        if (calendarDay == effective) {
            // User reset to the current day — clear any pending change
            prefs(context).edit()
                .remove(K_PENDING_DAY)
                .remove(K_CHANGE_TS)
                .apply()
            return false
        }
        prefs(context).edit()
            .putInt(K_PENDING_DAY, calendarDay)
            .putLong(K_CHANGE_TS, System.currentTimeMillis())
            .apply()
        // Reschedule notification for the new day (will use getEffectiveDay internally)
        WeeklyRecapScheduler.schedule(context)
        return true
    }

    /**
     * Whether there's a pending day change that hasn't taken effect yet.
     */
    fun hasPendingChange(context: Context): Boolean {
        val p = prefs(context)
        val pending = p.getInt(K_PENDING_DAY, -1)
        if (pending == -1) return false
        return System.currentTimeMillis() - p.getLong(K_CHANGE_TS, 0L) < COOLDOWN_MS
    }

    /**
     * Returns true if today is the effective recap day.
     */
    fun isRecapDayToday(context: Context): Boolean {
        val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return today == getEffectiveDay(context)
    }

    /**
     * Human-readable label for a Calendar day constant.
     */
    fun dayLabel(calendarDay: Int): String = when (calendarDay) {
        Calendar.MONDAY -> "Monday"
        Calendar.TUESDAY -> "Tuesday"
        Calendar.WEDNESDAY -> "Wednesday"
        Calendar.THURSDAY -> "Thursday"
        Calendar.FRIDAY -> "Friday"
        Calendar.SATURDAY -> "Saturday"
        Calendar.SUNDAY -> "Sunday"
        else -> "Saturday"
    }

    /** Short 3-letter label. */
    fun dayShortLabel(calendarDay: Int): String = dayLabel(calendarDay).take(3)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
