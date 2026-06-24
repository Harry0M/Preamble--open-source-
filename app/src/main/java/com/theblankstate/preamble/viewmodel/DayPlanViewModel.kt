package com.theblankstate.preamble.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.theblankstate.preamble.ai.CloudAiService
import com.theblankstate.preamble.ai.PlanAssignmentDto
import com.theblankstate.preamble.ai.PlanDayResult
import com.theblankstate.preamble.ai.PlanFixedDto
import com.theblankstate.preamble.ai.PlanTaskDto
import com.theblankstate.preamble.analytics.AnalyticsManager
import com.theblankstate.preamble.data.Task
import com.theblankstate.preamble.planner.DayPlanInput
import com.theblankstate.preamble.planner.DayPlanService
import com.theblankstate.preamble.planner.GatheredInput
import com.theblankstate.preamble.planner.PlanOutcome
import com.theblankstate.preamble.planner.ProposedSchedule
import com.theblankstate.preamble.planner.RawAssignment
import com.theblankstate.preamble.planner.ScheduleNormalizer
import com.theblankstate.preamble.repository.TaskRepository
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Track A (AI Plan-My-Day) orchestration edge — the thin, Android-side state machine
 * that drives a day-plan request from gather → AI call → normalize → review → apply.
 *
 * All correctness enforcement lives in the pure planner core ([ScheduleNormalizer]);
 * this ViewModel only sequences the edges (task gathering, the bounded Cloud AI call,
 * analytics, and the existing [TaskViewModel.updateTask] apply path) and exposes the
 * resulting [DayPlanState] via a [StateFlow].
 *
 * State machine (per design):
 *
 *   Idle → Loading → Review → Applying → (Applied | Failed)
 *
 * plus the terminal message states [DayPlanState.NoSchedulableTasks],
 * [DayPlanState.NoRemainingTimeToday], [DayPlanState.CouldNotGenerate],
 * [DayPlanState.InsufficientCredits], and the retryable [DayPlanState.Error].
 *
 * Safety: nothing is written to any task until [accept]; [discard] and every terminal
 * message state leave all tasks untouched.
 */
class DayPlanViewModel(
    private val repository: TaskRepository,
    private val taskViewModel: TaskViewModel,
    private val dayPlanService: DayPlanService = DayPlanService(repository),
) : ViewModel() {

    private val _state = MutableStateFlow<DayPlanState>(DayPlanState.Idle)
    val state: StateFlow<DayPlanState> = _state.asStateFlow()

    // Cached context from the last [requestPlan] gather so [submitAdjustment] can replan
    // through the SAME [DayPlanInput] (including the Effective_Window_Start floor) and the
    // SAME Current_Local_Datetime context (Req 15.2, 15.3). This guarantees a Revised_Schedule
    // flows through the identical [ScheduleNormalizer.normalize] input as the first request,
    // so Req 13/16 constraints are enforced unchanged.
    private var lastGathered: GatheredInput? = null
    private var lastToday: String? = null

    // The most recently reviewed/proposed schedule, retained so a post-apply Plan_Adjustment
    // (Req 15.6) can echo the prior assignments even after the Review state has transitioned
    // to Applied.
    private var lastSchedule: ProposedSchedule? = null

    /**
     * Request a day plan for the current day (Req 1.1, 1.4, 1.5, 5.1–5.3, 5.5, 6.1).
     *
     * Short-circuits to [DayPlanState.NoSchedulableTasks] with **no** AI call when there
     * are no schedulable tasks (Req 1.4). Otherwise records the request analytics (Req 6.1),
     * calls [CloudAiService.planDay] bounded by [PLAN_TIMEOUT_MS] (Req 5.1).
     *
     * Failure remapping (Req 18.4): timeout, generic exceptions, and a `null` result
     * (network / HTTP 500 / parse failure) all map to the **retryable**
     * [DayPlanState.Error]; insufficient-credits maps to [DayPlanState.InsufficientCredits].
     * [DayPlanState.CouldNotGenerate] is reserved for the case where the model **did**
     * respond but produced no usable schedule (the [PlanOutcome.CouldNotGenerate] branch
     * from [ScheduleNormalizer.normalize]). In every failure case all tasks are left
     * untouched (Req 5.2, 5.3, 5.5). A [PlanDayResult.Success] is fed through the pure
     * [ScheduleNormalizer] (CouldNotGenerate → terminal; Valid → [DayPlanState.Review]).
     */
    fun requestPlan() {
        viewModelScope.launch {
            _state.value = DayPlanState.Loading

            val today = TaskRepository.todayString()
            val gathered = dayPlanService.gatherInput(today)
            val input = gathered.input

            // Cache the gather context so a later [submitAdjustment] reuses the SAME input
            // and Current_Local_Datetime (Req 15.2, 15.3).
            lastGathered = gathered
            lastToday = today

            // Req 1.4: no schedulable tasks ⇒ no AI call, just a message.
            if (input.schedulable.isEmpty()) {
                _state.value = DayPlanState.NoSchedulableTasks
                return@launch
            }

            // Req 13.6: the Effective_Window_Start is at/after the working-window end ⇒
            // there is no remaining time today to plan, so make NO AI call.
            if (input.earliestStartMinute >= input.dayEndMinute) {
                _state.value = DayPlanState.NoRemainingTimeToday
                return@launch
            }

            // Req 6.1: a day plan was requested, including the schedulable count.
            AnalyticsManager.trackDayPlanRequested(input.schedulable.size)

            val schedulableDtos = input.schedulable.map {
                PlanTaskDto(id = it.id, title = it.title, priority = it.priority)
            }
            val fixedDtos = input.fixed.map {
                PlanFixedDto(start = formatHHmm(it.startMinute), end = it.endMinute?.let(::formatHHmm))
            }
            // Tell the model the real earliest start (the Effective_Window_Start floor), not the
            // raw working-window start, so it is time-aware (Req 13.5). Correctness does not depend
            // on the model honoring it — the pure normalizer enforces the floor regardless.
            val dayStart = formatHHmm(input.earliestStartMinute)
            val dayEnd = formatHHmm(input.dayEndMinute)

            // Req 5.1: bound the Cloud AI call by a timeout.
            val result: PlanDayResult? = try {
                withTimeout(PLAN_TIMEOUT_MS) {
                    CloudAiService.planDay(
                        schedulable = schedulableDtos,
                        fixed = fixedDtos,
                        date = today,
                        dayStart = dayStart,
                        dayEnd = dayEnd,
                        // Current_Local_Datetime planning context (Req 13.5).
                        dayOfWeek = gathered.dayOfWeek,
                        nowTime = gathered.nowTime,
                    )
                }
            } catch (e: TimeoutCancellationException) {
                // Req 5.2 + 18.4: timed out ⇒ retryable Error, tasks untouched.
                _state.value = DayPlanState.Error
                return@launch
            } catch (e: Exception) {
                // Req 5.3 + 18.4: error ⇒ retryable Error, tasks untouched.
                _state.value = DayPlanState.Error
                return@launch
            }

            when (result) {
                // Req 5.3 + 18.4: network/HTTP 500/parse failure ⇒ retryable Error.
                null -> _state.value = DayPlanState.Error
                // Req 5.5: server rejected for insufficient credits.
                is PlanDayResult.InsufficientCredits -> _state.value = DayPlanState.InsufficientCredits
                is PlanDayResult.Success -> {
                    // Assignments are untrusted; the pure normalizer is the authority.
                    val raw = result.assignments.map { RawAssignment(taskId = it.id, time = it.time) }
                    when (val outcome = ScheduleNormalizer.normalize(input, raw)) {
                        is PlanOutcome.CouldNotGenerate -> _state.value = DayPlanState.CouldNotGenerate
                        is PlanOutcome.Valid -> {
                            // Capture the original Task objects for the apply lookup.
                            val tasksById = repository.getTasksForDateWithRecurrence(today)
                                .associateBy { it.id }
                            // Retain for a possible post-apply adjustment (Req 15.6). The normal
                            // requestPlan path carries no advisory (advisory = null).
                            lastSchedule = outcome.schedule
                            _state.value = DayPlanState.Review(outcome.schedule, tasksById)
                        }
                    }
                }
            }
        }
    }

    /**
     * Accept the reviewed schedule (Req 4.1, 4.4, 6.2).
     *
     * Applies each proposed time through the existing [TaskViewModel.updateTask] path,
     * passing every other field as the task's current value so **only** `deadlineTime`
     * changes (Req 4.2). If applying throws, surfaces [DayPlanState.Failed] (Req 4.4).
     * On success records the accept analytics (Req 6.2).
     */
    fun accept() {
        val current = _state.value
        if (current !is DayPlanState.Review) return

        viewModelScope.launch {
            _state.value = DayPlanState.Applying
            try {
                for (assignment in current.schedule.assignments) {
                    val task = current.tasksById[assignment.taskId] ?: continue
                    // Only deadlineTime changes; all other fields keep their current values.
                    taskViewModel.updateTask(
                        task = task,
                        newTitle = task.title,
                        newDate = task.createdDate,
                        newDeadlineTime = assignment.time,
                    )
                }
                AnalyticsManager.trackDayPlanAccepted() // Req 6.2
                _state.value = DayPlanState.Applied
            } catch (e: Exception) {
                // Req 4.4: could not apply ⇒ surface a failure message.
                _state.value = DayPlanState.Failed
            }
        }
    }

    /**
     * Submit a free-text Plan_Adjustment to revise the current proposal (Req 15.1–15.6).
     *
     * Valid only while in [DayPlanState.Review] (Req 15.1) or, for a post-apply adjustment,
     * [DayPlanState.Applied] (Req 15.6); from any other state this is a no-op. The prior
     * schedule's assignments are echoed back to the model as `priorAssignments` and the
     * user's [text] is passed through **unmodified** as `adjustment` so any language/script
     * is preserved (Req 15.2).
     *
     * The call reuses the cached [GatheredInput] from the originating [requestPlan], so the
     * Revised_Schedule is produced from the SAME [DayPlanInput] (including the
     * `Effective_Window_Start` floor) and the SAME Current_Local_Datetime context. The
     * untrusted response is fed through the SAME [ScheduleNormalizer.normalize] with the SAME
     * `input`, guaranteeing the Revised_Schedule obeys the Requirement 13/16 constraints
     * (Req 15.3). On success it re-enters [DayPlanState.Review] writing NOTHING until
     * [accept] (Req 15.4).
     *
     * Failure remapping mirrors [requestPlan]: timeout/exception/`null` ⇒ retryable
     * [DayPlanState.Error]; insufficient credits ⇒ [DayPlanState.InsufficientCredits];
     * a usable-but-empty response ⇒ [DayPlanState.CouldNotGenerate].
     *
     * Advisory (Req 15.5): when the response is usable but the adjustment cannot be fully
     * honored (some Schedulable_Tasks remain in [ProposedSchedule.unplaced]), the re-entered
     * [DayPlanState.Review] carries an [DayPlanState.Review.advisory] message naming the
     * tasks that could not be placed while still leaving a reviewable schedule.
     */
    fun submitAdjustment(text: String) {
        val current = _state.value
        // Req 15.1: only from Review; Req 15.6: also allowed post-apply from Applied.
        val priorSchedule: ProposedSchedule = when (current) {
            is DayPlanState.Review -> current.schedule
            DayPlanState.Applied -> lastSchedule ?: return
            else -> return
        }
        // Need the original gather context to replan through the identical input (Req 15.3).
        val gathered = lastGathered ?: return
        val input = gathered.input
        val today = lastToday ?: gathered.date

        viewModelScope.launch {
            _state.value = DayPlanState.Loading

            val schedulableDtos = input.schedulable.map {
                PlanTaskDto(id = it.id, title = it.title, priority = it.priority)
            }
            val fixedDtos = input.fixed.map {
                PlanFixedDto(start = formatHHmm(it.startMinute), end = it.endMinute?.let(::formatHHmm))
            }
            val dayStart = formatHHmm(input.earliestStartMinute)
            val dayEnd = formatHHmm(input.dayEndMinute)

            // Echo the prior proposal so the model can revise it (Req 15.2).
            val priorAssignments = priorSchedule.assignments.map {
                PlanAssignmentDto(id = it.taskId, time = it.time)
            }

            // Req 5.1: bound the Cloud AI call by the same timeout as requestPlan.
            val result: PlanDayResult? = try {
                withTimeout(PLAN_TIMEOUT_MS) {
                    CloudAiService.planDay(
                        schedulable = schedulableDtos,
                        fixed = fixedDtos,
                        date = today,
                        dayStart = dayStart,
                        dayEnd = dayEnd,
                        dayOfWeek = gathered.dayOfWeek,
                        nowTime = gathered.nowTime,
                        priorAssignments = priorAssignments,
                        // Passed UNMODIFIED for any language/script (Req 15.2).
                        adjustment = text,
                    )
                }
            } catch (e: TimeoutCancellationException) {
                _state.value = DayPlanState.Error
                return@launch
            } catch (e: Exception) {
                _state.value = DayPlanState.Error
                return@launch
            }

            when (result) {
                null -> _state.value = DayPlanState.Error
                is PlanDayResult.InsufficientCredits -> _state.value = DayPlanState.InsufficientCredits
                is PlanDayResult.Success -> {
                    // Untrusted assignments; the pure normalizer with the SAME input is the authority (Req 15.3).
                    val raw = result.assignments.map { RawAssignment(taskId = it.id, time = it.time) }
                    when (val outcome = ScheduleNormalizer.normalize(input, raw)) {
                        is PlanOutcome.CouldNotGenerate -> _state.value = DayPlanState.CouldNotGenerate
                        is PlanOutcome.Valid -> {
                            val tasksById = repository.getTasksForDateWithRecurrence(today)
                                .associateBy { it.id }
                            lastSchedule = outcome.schedule
                            // Req 15.4: re-enter Review writing NOTHING until accept().
                            // Req 15.5: surface an advisory when the adjustment can't be fully honored.
                            _state.value = DayPlanState.Review(
                                schedule = outcome.schedule,
                                tasksById = tasksById,
                                advisory = buildAdvisory(outcome.schedule),
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Build the Req 15.5 advisory message, or null when every schedulable task was placed.
     * Names the tasks left in [ProposedSchedule.unplaced] so the user understands what could
     * not be accommodated while a reviewable schedule remains available.
     */
    private fun buildAdvisory(schedule: ProposedSchedule): String? {
        if (schedule.unplaced.isEmpty()) return null
        val titles = schedule.unplaced.joinToString(", ") { it.title }
        return "Some tasks couldn't be placed in the remaining time: $titles"
    }

    /**
     * Discard the reviewed schedule (Req 4.3, 6.3): return to [DayPlanState.Idle] without
     * mutating any task, and record the discard analytics.
     */
    fun discard() {
        AnalyticsManager.trackDayPlanDiscarded() // Req 6.3
        _state.value = DayPlanState.Idle
    }

    /** Dismiss a terminal message state back to [DayPlanState.Idle] (no mutation). */
    fun reset() {
        _state.value = DayPlanState.Idle
    }

    /**
     * Retry a failed plan request (Req 18.5): re-invoke [requestPlan] from the retryable
     * [DayPlanState.Error] state. No task is mutated by retrying.
     */
    fun retry() {
        requestPlan()
    }

    /** Format a minute-of-day to canonical `HH:mm`. */
    private fun formatHHmm(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

    companion object {
        /** Upper bound on the Cloud AI planning call (Req 5.1). */
        const val PLAN_TIMEOUT_MS = 30_000L
    }

    class Factory(
        private val repository: TaskRepository,
        private val taskViewModel: TaskViewModel,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DayPlanViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return DayPlanViewModel(repository, taskViewModel) as T
        }
    }
}

/**
 * The Plan-My-Day state machine (Track A). `Idle → Loading → Review → Applying →
 * (Applied | Failed)`, plus the terminal message states for the three non-review
 * outcomes. Nothing is written to any task except along the [accept] path.
 */
sealed interface DayPlanState {
    /** Nothing in progress. */
    data object Idle : DayPlanState

    /** Gathering input and awaiting the AI proposal. */
    data object Loading : DayPlanState

    /**
     * A valid proposal is ready for the user to accept or discard. While in this state
     * nothing is written to any task (Req 3.2). [tasksById] holds the original tasks for
     * the apply lookup.
     *
     * [advisory] is null on the normal [requestPlan] path. After a [submitAdjustment] that
     * could not be fully honored (e.g. tasks remained in [ProposedSchedule.unplaced]), it
     * carries a human-readable message naming what could not be accommodated while leaving
     * this schedule reviewable (Req 15.5).
     */
    data class Review(
        val schedule: ProposedSchedule,
        val tasksById: Map<String, Task>,
        val advisory: String? = null,
    ) : DayPlanState

    /** Applying the accepted schedule. */
    data object Applying : DayPlanState

    /** The accepted schedule was applied successfully. */
    data object Applied : DayPlanState

    /** Applying the schedule failed; tasks preserved (Req 4.4). */
    data object Failed : DayPlanState

    /** There were no schedulable tasks to plan; no AI call was made (Req 1.4). */
    data object NoSchedulableTasks : DayPlanState

    /**
     * The Effective_Window_Start is at or after the working-window end, so there is no
     * remaining time today to plan; no AI call was made (Req 13.6). Terminal message.
     */
    data object NoRemainingTimeToday : DayPlanState

    /**
     * The day plan could not be generated because the AI **responded** but produced no
     * usable schedule (the [PlanOutcome.CouldNotGenerate] branch). Terminal message
     * (Req 5.3). For timeout/network/HTTP 500/parse failures see [Error] instead.
     */
    data object CouldNotGenerate : DayPlanState

    /**
     * A recoverable failure reaching the AI: timeout, network error, HTTP 500, or an
     * unparseable response. The user can [retry] (Req 18.4, 18.5). Tasks are untouched.
     */
    data object Error : DayPlanState

    /** The request was rejected for insufficient AI credits (Req 5.5). */
    data object InsufficientCredits : DayPlanState
}
