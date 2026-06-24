package com.theblankstate.preamble.planner

import com.theblankstate.preamble.data.Task
import com.theblankstate.preamble.repository.TaskRepository

/**
 * Android-side orchestration edge for Track A (AI Plan-My-Day) task gathering.
 *
 * This is the thin edge that reads the user's real tasks for the current day from
 * [TaskRepository] and maps them into the pure [DayPlanInput] consumed by
 * [ScheduleNormalizer]. All correctness enforcement lives in the pure planner core;
 * this class only selects and shapes inputs.
 *
 * Selection rules (Req 1.2, 1.3):
 *  - **Schedulable_Task**: a current-day task that is not completed, has no existing
 *    `deadlineTime`, and is not an event (`isEvent == false`).
 *  - **Fixed_Commitment**: a current-day task or calendar event that already has a
 *    scheduled time (`deadlineTime != null`). Events additionally reserve the half-open
 *    range `[deadlineTime, endTime)` when an `endTime` is present; everything else
 *    reserves a single point slot.
 */
class DayPlanService(
    private val repository: TaskRepository,
    private val dayStartMinute: Int = DEFAULT_DAY_START_MINUTE,
    private val dayEndMinute: Int = DEFAULT_DAY_END_MINUTE,
    private val slotMinutes: Int = DEFAULT_SLOT_MINUTES,
) {

    /**
     * Gather the day-plan inputs for [today] (defaults to the current local day).
     *
     * Reads the current day's tasks (including materialized recurrence instances) and
     * partitions them into Schedulable_Tasks and Fixed_Commitments, mapping `HH:mm`
     * times to minutes-of-day and attaching the working window.
     */
    suspend fun gatherInput(today: String = TaskRepository.todayString()): DayPlanInput {
        val tasks = repository.getTasksForDateWithRecurrence(today)

        val schedulable = tasks
            .filter { !it.isCompleted && it.deadlineTime == null && !it.isEvent }
            .map { SchedulableTask(id = it.id, title = it.title, priority = it.priority) }

        val fixed = tasks.mapNotNull { it.toFixedCommitment() }

        return DayPlanInput(
            schedulable = schedulable,
            fixed = fixed,
            dayStartMinute = dayStartMinute,
            dayEndMinute = dayEndMinute,
            slotMinutes = slotMinutes,
        )
    }

    /**
     * Map a task that already has a scheduled time into a [FixedCommitment]. Returns null
     * for tasks without a parseable `deadlineTime` (i.e. not actually scheduled). Events
     * with a later `endTime` reserve the half-open range `[start, end)`.
     */
    private fun Task.toFixedCommitment(): FixedCommitment? {
        val start = parseHHmm(deadlineTime) ?: return null
        val end = if (isEvent) parseHHmm(endTime)?.takeIf { it > start } else null
        return FixedCommitment(startMinute = start, endMinute = end)
    }

    /** Parse a strict `HH:mm` (00:00–23:59) string to minute-of-day, or null if malformed. */
    private fun parseHHmm(time: String?): Int? {
        if (time == null) return null
        val match = HHMM.matchEntire(time.trim()) ?: return null
        val h = match.groupValues[1].toInt()
        val m = match.groupValues[2].toInt()
        return h * 60 + m
    }

    companion object {
        /** Default working-window start: 09:00. */
        const val DEFAULT_DAY_START_MINUTE = 9 * 60

        /** Default working-window end: 21:00. */
        const val DEFAULT_DAY_END_MINUTE = 21 * 60

        /** Default slot granularity in minutes. */
        const val DEFAULT_SLOT_MINUTES = 30

        private val HHMM = Regex("^([01]\\d|2[0-3]):([0-5]\\d)$")
    }
}
