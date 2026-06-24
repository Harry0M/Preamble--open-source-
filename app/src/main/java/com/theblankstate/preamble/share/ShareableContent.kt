package com.theblankstate.preamble.share

/**
 * Pure logic for the share-kind → content mapping (Requirements 7.3, 8.2, 9.2).
 *
 * Each shareable moment is projected from existing recap/stats/celebration data
 * into a flat [ShareableContent] carrying the display fields the branded
 * Composable renders. The mapping performs no new computation — it only
 * embeds the metric that defines each shareable (streak length, completed-task
 * count, or weekly recap summary) so the rendered image depicts it.
 *
 * This file is deliberately free of Android, Firebase, and serialization
 * dependencies so the mapping can be exercised directly by JVM property-based
 * tests (Property 3).
 */
enum class ShareKind { WEEKLY_RECAP, STREAK_MILESTONE, PERFECT_DAY }

/** The display fields a branded shareable Composable renders. */
data class ShareableContent(
    val kind: ShareKind,
    val headline: String,
    val metricLabel: String,
    val subtitle: String,
)

/**
 * A plain carrier projected from existing weekly recap content
 * (`RecapScreen`/`StatsScreenV2` slide data). No new computation; the values
 * are supplied by the caller.
 */
data class WeeklyRecapSummary(
    val weekLabel: String,
    val tasksCompleted: Int,
    val perfectDays: Int,
)

object ShareableContentMapper {

    /** Streak milestone content; the defining metric is the streak length in days (Requirement 8.2). */
    fun fromStreak(days: Int): ShareableContent =
        ShareableContent(
            kind = ShareKind.STREAK_MILESTONE,
            headline = "Streak milestone",
            metricLabel = "Day $days",
            subtitle = "$days days in a row on Preamble",
        )

    /** Perfect-day content; the defining metric is the completed-task count (Requirement 9.2). */
    fun fromPerfectDay(tasksCompleted: Int): ShareableContent =
        ShareableContent(
            kind = ShareKind.PERFECT_DAY,
            headline = "Perfect day",
            metricLabel = "$tasksCompleted/$tasksCompleted done",
            subtitle = "Completed all $tasksCompleted tasks today",
        )

    /** Weekly recap content; the fields reflect the supplied recap summary (Requirement 7.3). */
    fun fromWeeklyRecap(recap: WeeklyRecapSummary): ShareableContent =
        ShareableContent(
            kind = ShareKind.WEEKLY_RECAP,
            headline = "Weekly recap",
            metricLabel = recap.weekLabel,
            subtitle = "${recap.tasksCompleted} tasks done, ${recap.perfectDays} perfect days",
        )
}
