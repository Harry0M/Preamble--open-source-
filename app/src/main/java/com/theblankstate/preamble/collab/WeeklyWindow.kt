package com.theblankstate.preamble.collab

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

/**
 * Pure, side-effect-free weekly-window bucketing for the Friends_Leaderboard.
 *
 * Feature: social-engagement. This object is the single source of truth for the
 * Weekly_Window described in requirements.md and design.md (Leaderboard_Service):
 * the Weekly_Window begins at the most recent week boundary, defined as
 * **Monday 00:00:00 UTC**, and ends at the present moment. Productivity_Points
 * earned before the Weekly_Window start are excluded from the leaderboard.
 *
 * All computation is performed in UTC because member completion timestamps are
 * recorded in UTC by the collaborative-tasks feature, which makes the bucketing
 * deterministic and property-testable. The functions are intentionally pure: no
 * clock access and no I/O — the caller always supplies the instants.
 */
object WeeklyWindow {

    /**
     * Epoch-millis of the most recent Monday 00:00:00 UTC at or before [utcMillis].
     *
     * When [utcMillis] is itself exactly a Monday 00:00:00 UTC, that same instant is
     * returned (the boundary is inclusive). The result is always <= [utcMillis].
     */
    fun windowStart(utcMillis: Long): Long {
        val date = Instant.ofEpochMilli(utcMillis)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    /**
     * Stable ISO-8601 week key for the window containing [utcMillis], e.g. "2026-W17",
     * computed in UTC.
     *
     * ISO-8601 weeks start on Monday and the week-based-year handles year/week
     * rollovers (week 1 of a year can include late-December dates and vice versa).
     * Because there is exactly one ISO week per Monday-00:00-UTC window, two instants
     * produce equal keys if and only if they fall in the same window
     * (i.e. share the same [windowStart]).
     */
    fun weekKey(utcMillis: Long): String {
        val date = Instant.ofEpochMilli(utcMillis)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
        val weekBasedYear = date.get(IsoFields.WEEK_BASED_YEAR)
        val weekOfYear = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        return String.format("%04d-W%02d", weekBasedYear, weekOfYear)
    }

    /**
     * True iff [awardMillis] falls in the same Weekly_Window as [nowMillis], i.e. the
     * award was earned within the current window and is therefore counted toward the
     * leaderboard. Equivalent to `weekKey(awardMillis) == weekKey(nowMillis)`.
     */
    fun isInCurrentWindow(awardMillis: Long, nowMillis: Long): Boolean =
        weekKey(awardMillis) == weekKey(nowMillis)
}
