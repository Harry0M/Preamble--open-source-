package com.theblankstate.preamble.ai

import com.theblankstate.preamble.data.Task
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Pure-algorithm smart task ordering. Zero LLM calls.
 *
 * Score = deadlineUrgency × 5 + priority × 3 + stalenessDays × 1 + rolloverBonus
 *   - deadlineUrgency: exponential decay from minutes-to-deadline
 *   - priority: 0..3
 *   - stalenessDays: clamped to 14
 *   - rolloverBonus: +2 if rollover task
 *
 * Snoozed tasks always rank below non-snoozed (preserves existing UX).
 * Completed tasks always rank below open ones (existing UX preserved).
 *
 * Use [ordered] to get a stable ranked list, [scored] for diagnostics.
 */
object TaskOrderer {

    private const val MAX_STALENESS_DAYS = 14

    fun ordered(tasks: List<Task>): List<Task> {
        if (tasks.isEmpty()) return tasks
        val now = System.currentTimeMillis()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())
        val todayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayMs = runCatching { todayFmt.parse(today)?.time }.getOrNull() ?: now

        return tasks.sortedWith(compareByDescending<Task> {
            // Tier 1: snooze rank — lower is better
            val snoozed = it.snoozedUntil != null && it.snoozedUntil > now
            if (snoozed) -1 else 0
        }.thenByDescending {
            // Tier 2: completed always below open
            if (it.isCompleted) -1 else 0
        }.thenByDescending {
            // Tier 3: smart score
            score(it, todayMs, now)
        }.thenBy {
            // Stable tiebreaker — earlier created first
            it.createdTimestamp
        })
    }

    fun scored(tasks: List<Task>): List<Pair<Task, Float>> {
        val now = System.currentTimeMillis()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())
        val todayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayMs = runCatching { todayFmt.parse(today)?.time }.getOrNull() ?: now
        return tasks.map { it to score(it, todayMs, now) }
    }

    private fun score(task: Task, todayMs: Long, nowMs: Long): Float {
        val priorityScore = task.priority * 3f

        // Deadline urgency — only meaningful for tasks with deadlineTime today
        val deadlineUrgency = task.deadlineTime?.let { dt ->
            val (h, m) = parseHM(dt) ?: return@let 0f
            val cal = Calendar.getInstance().apply {
                timeInMillis = todayMs
                set(Calendar.HOUR_OF_DAY, h)
                set(Calendar.MINUTE, m)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val minsTo = ((cal.timeInMillis - nowMs) / 60_000L).toFloat()
            when {
                minsTo < 0 -> 5f                  // already past — high urgency
                minsTo < 30 -> 8f                  // within 30 min
                minsTo < 90 -> 5f
                minsTo < 240 -> 3f
                minsTo < 720 -> 1.5f
                else -> 0.5f
            }
        } ?: 0f

        // Custom alarm — if alarm set & near, treat similar to deadline
        val alarmUrgency = task.customAlarmTimeMs?.let { alarm ->
            if (task.isAlarmPaused) 0f
            else {
                val minsTo = ((alarm - nowMs) / 60_000L).toFloat()
                when {
                    minsTo < 0 -> 4f
                    minsTo < 30 -> 7f
                    minsTo < 120 -> 4f
                    else -> 1f
                }
            }
        } ?: 0f

        // Staleness — older = higher
        val taskDateMs = runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(task.createdDate)?.time
        }.getOrNull() ?: todayMs
        val stalenessDays = ((todayMs - taskDateMs) / 86_400_000L).coerceIn(0, MAX_STALENESS_DAYS.toLong()).toFloat()

        val rolloverBonus = if (task.recurrenceType == "rollover") 2f else 0f

        return (maxOf(deadlineUrgency, alarmUrgency) * 5f) +
            priorityScore +
            stalenessDays +
            rolloverBonus
    }

    private fun parseHM(time: String): Pair<Int, Int>? {
        val parts = time.split(":")
        if (parts.size < 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h to m
    }
}
