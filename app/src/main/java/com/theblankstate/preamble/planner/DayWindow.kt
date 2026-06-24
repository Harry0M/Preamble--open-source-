package com.theblankstate.preamble.planner

/**
 * Pure helpers for the time-aware day window (Req 13.2, 13.4).
 *
 * Computes the `Effective_Window_Start` — the floor below which the planner must
 * never propose a start — from the configured working-window start and the current
 * minute-of-day, plus a small lead-time buffer so we never propose a start that is
 * effectively already past.
 *
 * Pure Kotlin — no Android/Firebase/AI imports.
 */
object DayWindow {
    /** Schedule_Lead_Time: a small buffer so we never propose a start that is effectively already past. 0..30 (Req 13.4). */
    const val SCHEDULE_LEAD_TIME_MIN = 10

    /**
     * Effective_Window_Start = max(workingWindowStart, nowMinuteOfDay + leadTime), clamped to a valid
     * minute-of-day in `0..24*60` (Req 13.2). The lead time is itself clamped to `0..30` minutes (Req 13.4).
     */
    fun effectiveWindowStart(
        workingWindowStartMin: Int,
        nowMinuteOfDay: Int,
        leadTimeMin: Int = SCHEDULE_LEAD_TIME_MIN,
    ): Int {
        val lead = leadTimeMin.coerceIn(0, 30)
        return maxOf(workingWindowStartMin, nowMinuteOfDay + lead).coerceIn(0, 24 * 60)
    }
}
