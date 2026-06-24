package com.theblankstate.preamble.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.theblankstate.preamble.ai.CloudAiService
import com.theblankstate.preamble.ai.PlanDayResult
import com.theblankstate.preamble.ai.PlanFixedDto
import com.theblankstate.preamble.ai.PlanTaskDto
import com.theblankstate.preamble.analytics.AnalyticsManager
import com.theblankstate.preamble.data.Task
import com.theblankstate.preamble.planner.DayPlanInput
import com.theblankstate.preamble.planner.DayPlanService
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
 * [DayPlanState.CouldNotGenerate], and [DayPlanState.InsufficientCredits].
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

    /**
     * Request a day plan for the current day (Req 1.1, 1.4, 1.5, 5.1–5.3, 5.5, 6.1).
     *
     * Short-circuits to [DayPlanState.NoSchedulableTasks] with **no** AI call when there
     * are no schedulable tasks (Req 1.4). Otherwise records the request analytics (Req 6.1),
     * calls [CloudAiService.planDay] bounded by [PLAN_TIMEOUT_MS] (Req 5.1), maps timeout /
     * error / null / insufficient-credits to the matching terminal state with tasks left
     * untouched (Req 5.2, 5.3, 5.5), and feeds a [PlanDayResult.Success] through the pure
     * [ScheduleNormalizer] (CouldNotGenerate → terminal; Valid → [DayPlanState.Review]).
     */
    fun requestPlan() {
        viewModelScope.launch {
            _state.value = DayPlanState.Loading

            val today = TaskRepository.todayString()
            val input = dayPlanService.gatherInput(today)

            // Req 1.4: no schedulable tasks ⇒ no AI call, just a message.
            if (input.schedulable.isEmpty()) {
                _state.value = DayPlanState.NoSchedulableTasks
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
            val dayStart = formatHHmm(input.dayStartMinute)
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
                    )
                }
            } catch (e: TimeoutCancellationException) {
                // Req 5.2: timed out ⇒ abandon, tasks untouched.
                _state.value = DayPlanState.CouldNotGenerate
                return@launch
            } catch (e: Exception) {
                // Req 5.3: error ⇒ tasks untouched.
                _state.value = DayPlanState.CouldNotGenerate
                return@launch
            }

            when (result) {
                // Req 5.3: network/parse/limit error.
                null -> _state.value = DayPlanState.CouldNotGenerate
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
     */
    data class Review(
        val schedule: ProposedSchedule,
        val tasksById: Map<String, Task>,
    ) : DayPlanState

    /** Applying the accepted schedule. */
    data object Applying : DayPlanState

    /** The accepted schedule was applied successfully. */
    data object Applied : DayPlanState

    /** Applying the schedule failed; tasks preserved (Req 4.4). */
    data object Failed : DayPlanState

    /** There were no schedulable tasks to plan; no AI call was made (Req 1.4). */
    data object NoSchedulableTasks : DayPlanState

    /** The day plan could not be generated (timeout/error/unusable proposal) (Req 5.2, 5.3). */
    data object CouldNotGenerate : DayPlanState

    /** The request was rejected for insufficient AI credits (Req 5.5). */
    data object InsufficientCredits : DayPlanState
}
