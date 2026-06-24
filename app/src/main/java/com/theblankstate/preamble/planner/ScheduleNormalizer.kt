package com.theblankstate.preamble.planner

/**
 * Pure, Android/Firebase/AI-free day-planning core for Track A (AI Plan-My-Day).
 *
 * This package contains NO Android, Firebase, or AI imports. Times cross the
 * wire as `HH:mm` strings and are converted to minute-of-day integers inside the
 * normalizer for arithmetic, then formatted back to `HH:mm` on output.
 */

/** A task eligible for auto-scheduling. Minutes-of-day are never stored here; only identity + priority. */
data class SchedulableTask(val id: String, val title: String, val priority: Int)

/**
 * An immovable current-day item. A point commitment has [endMinute] == null;
 * an event reserves the half-open range [startMinute, endMinute).
 */
data class FixedCommitment(val startMinute: Int, val endMinute: Int? = null)

/**
 * Inputs to a day plan. [dayStartMinute]/[dayEndMinute] define the working window
 * (minutes from 00:00). Candidate slots are enumerated by [slotMinutes].
 */
data class DayPlanInput(
    val schedulable: List<SchedulableTask>,
    val fixed: List<FixedCommitment>,
    val dayStartMinute: Int,
    val dayEndMinute: Int,
    val slotMinutes: Int = 30,
)

/** One assignment exactly as the AI proposed it (untrusted, may be malformed/duplicate/out-of-window). */
data class RawAssignment(val taskId: String, val time: String)

/** A single validated assignment in canonical HH:mm form. */
data class ScheduledAssignment(val taskId: String, val time: String)

/** Validated, non-conflicting, priority-sorted result. */
data class ProposedSchedule(val assignments: List<ScheduledAssignment>)

sealed interface PlanOutcome {
    data class Valid(val schedule: ProposedSchedule) : PlanOutcome

    /** Maps Req 5.3: AI error / unparseable / yields no valid time for any task. */
    data object CouldNotGenerate : PlanOutcome
}

object ScheduleNormalizer {

    /**
     * PURE. Validates and repairs the AI proposal into a correct [ProposedSchedule], or [PlanOutcome.CouldNotGenerate].
     *
     * Algorithm (per design):
     * 1. Build the reserved minute-set from point + ranged [FixedCommitment]s.
     * 2. Enumerate candidate slots from [DayPlanInput.dayStartMinute] by [DayPlanInput.slotMinutes]
     *    up to [DayPlanInput.dayEndMinute], dropping reserved slots.
     * 3. Choose at most one legal free slot per [SchedulableTask] (dedup by id; ignore raw
     *    assignments whose taskId is not schedulable; take the AI time if it parses to a legal
     *    free candidate slot, else repair to the next free slot; leave unscheduled if none remain).
     * 4. Enforce priority ordering by re-zipping: chosen distinct slots sorted ascending, placed
     *    tasks ordered by priority DESC (tie-break: AI proposed time ascending, then id),
     *    zip earliest-slot -> highest-priority-task.
     * 5. Format slots back to canonical HH:mm. Return [PlanOutcome.Valid] if >=1 task placed,
     *    else [PlanOutcome.CouldNotGenerate].
     */
    fun normalize(input: DayPlanInput, raw: List<RawAssignment>): PlanOutcome {
        // --- Step 1: reserved minute-set from fixed commitments ---
        val reserved = HashSet<Int>()
        for (fc in input.fixed) {
            if (fc.endMinute == null) {
                reserved.add(fc.startMinute)
            } else {
                var m = fc.startMinute
                while (m < fc.endMinute) {
                    reserved.add(m)
                    m++
                }
            }
        }

        // --- Step 2: candidate slots (legal, free, in-window) ---
        val candidateSlots = buildList {
            if (input.slotMinutes > 0) {
                var slot = input.dayStartMinute
                while (slot <= input.dayEndMinute) {
                    // A slot is legal only if it is a valid time-of-day and not reserved.
                    if (slot in 0..1439 && slot !in reserved) {
                        add(slot)
                    }
                    slot += input.slotMinutes
                }
            }
        }

        if (candidateSlots.isEmpty()) return PlanOutcome.CouldNotGenerate

        val candidateSet = candidateSlots.toHashSet()
        val freeSlots = candidateSlots.toSortedSet() // remaining free slots, ascending

        // Valid schedulable ids (dedup by id, first occurrence wins for ordering).
        val schedulableIds = LinkedHashSet<String>()
        val taskById = HashMap<String, SchedulableTask>()
        for (t in input.schedulable) {
            if (schedulableIds.add(t.id)) {
                taskById[t.id] = t
            }
        }
        if (schedulableIds.isEmpty()) return PlanOutcome.CouldNotGenerate

        // First raw proposed time per schedulable id (later duplicates ignored). Unknown ids ignored.
        val proposedTimeMin = HashMap<String, Int>()
        for (ra in raw) {
            if (ra.taskId !in schedulableIds) continue
            if (ra.taskId in proposedTimeMin) continue
            val parsed = parseHHmm(ra.time) ?: continue
            proposedTimeMin[ra.taskId] = parsed
        }

        // --- Step 3: choose at most one legal free slot per task ---
        // Holds the chosen slot per placed task, plus the AI proposed time (for tie-breaking).
        data class Placement(val taskId: String, var slot: Int, val proposed: Int?)

        val placements = ArrayList<Placement>()

        // Deterministic processing order: by id so repair is stable.
        val processOrder = schedulableIds.sorted()

        // Phase A: honour AI times that land on a legal free candidate slot.
        for (id in processOrder) {
            val proposed = proposedTimeMin[id]
            if (proposed != null && proposed in candidateSet && proposed in freeSlots) {
                freeSlots.remove(proposed)
                placements.add(Placement(id, proposed, proposed))
            }
        }

        // Phase B: repair tasks that had no usable AI time to the next free slot.
        val placedIds = placements.mapTo(HashSet()) { it.taskId }
        for (id in processOrder) {
            if (id in placedIds) continue
            val next = freeSlots.firstOrNull() ?: break // no free slot remains -> leave unscheduled
            freeSlots.remove(next)
            placements.add(Placement(id, next, proposedTimeMin[id]))
        }

        if (placements.isEmpty()) return PlanOutcome.CouldNotGenerate

        // --- Step 4: enforce priority ordering by re-zipping ---
        // Distinct chosen slots, ascending.
        val chosenSlotsAsc = placements.map { it.slot }.sorted()

        // Order placed tasks by priority DESC, tie-break AI proposed time ascending, then id.
        val orderedTasks = placements.sortedWith(
            compareByDescending<Placement> { taskById.getValue(it.taskId).priority }
                .thenBy { it.proposed ?: Int.MAX_VALUE }
                .thenBy { it.taskId }
        )

        // Zip earliest-slot -> highest-priority-task.
        val assignments = ArrayList<ScheduledAssignment>(orderedTasks.size)
        for (i in orderedTasks.indices) {
            assignments.add(ScheduledAssignment(orderedTasks[i].taskId, formatHHmm(chosenSlotsAsc[i])))
        }

        return PlanOutcome.Valid(ProposedSchedule(assignments))
    }

    /** Parse a strict `HH:mm` (00:00–23:59) string to minute-of-day, or null if malformed. */
    private fun parseHHmm(time: String): Int? {
        val match = Regex("^([01]\\d|2[0-3]):([0-5]\\d)$").matchEntire(time.trim()) ?: return null
        val h = match.groupValues[1].toInt()
        val m = match.groupValues[2].toInt()
        return h * 60 + m
    }

    /** Format a minute-of-day to canonical `HH:mm`. */
    private fun formatHHmm(minute: Int): String {
        val h = minute / 60
        val m = minute % 60
        return "%02d:%02d".format(h, m)
    }
}
