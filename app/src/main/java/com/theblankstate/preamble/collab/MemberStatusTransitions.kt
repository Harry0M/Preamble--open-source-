package com.theblankstate.preamble.collab

/**
 * Pure, side-effect-free member-status transition logic for collaborative tasks.
 *
 * Feature: collaborative-tasks. This object is the single source of truth for the
 * guarded member-state transitions described by the member-status state machine in
 * design.md and Requirement 10:
 *
 *  - accept   : permitted only from `pending`            -> `accepted`            (10.1, 10.3)
 *  - decline  : permitted only from `pending`            -> `declined`            (10.2, 10.3)
 *  - complete : permitted only from `accepted`           -> `completed`           (10.4, 10.5)
 *               and, when permitted, sets the completion flag to true and records a
 *               completion timestamp in UTC (epoch milliseconds are inherently UTC).
 *
 * Transitions from any disallowed source status are no-ops: the returned state is
 * byte-for-byte identical to the input and the result is reported as [TransitionResult.Rejected]
 * so callers can surface the appropriate message without mutating local state.
 *
 * The functions are intentionally pure (no clock access, no I/O): the caller supplies
 * `nowUtcMillis` for completion so the logic is deterministic and property-testable.
 */
object MemberStatusTransitions {

    /** The six valid member statuses (mirrors the canonical document's `memberStates[uid].status`). */
    object Status {
        const val PENDING = "pending"
        const val ACCEPTED = "accepted"
        const val COMPLETED = "completed"
        const val DECLINED = "declined"
        const val LEFT = "left"
        const val REMOVED = "removed"

        val ALL: Set<String> = setOf(PENDING, ACCEPTED, COMPLETED, DECLINED, LEFT, REMOVED)
    }

    /**
     * The mutable portion of a member's state that the user-driven transitions touch.
     *
     * @property status the member's current [Status].
     * @property isCompleted the per-member completion flag.
     * @property completedTimestamp UTC epoch-millis completion time, or null when not completed.
     */
    data class MemberStateValue(
        val status: String,
        val isCompleted: Boolean = false,
        val completedTimestamp: Long? = null
    )

    /**
     * Outcome of attempting a transition.
     *
     * [Applied] carries the new state produced by a permitted transition.
     * [Rejected] carries the original, unchanged state together with a [reason] message
     * suitable for display (matching Requirements 10.3 / 10.5).
     */
    sealed interface TransitionResult {
        val state: MemberStateValue

        data class Applied(override val state: MemberStateValue) : TransitionResult
        data class Rejected(override val state: MemberStateValue, val reason: String) : TransitionResult
    }

    /** Message shown when accept/decline is attempted from a non-`pending` status (10.3). */
    const val REASON_NOT_PENDING = "This task is not in a pending state."

    /** Message shown when completion is attempted from a non-`accepted` status (10.5). */
    const val REASON_NOT_ACCEPTED = "This task must be accepted before it can be completed."

    /**
     * Accept an assigned task. Permitted only when the member's status is `pending` (10.1).
     * From any other status this is a no-op (10.3).
     */
    fun accept(current: MemberStateValue): TransitionResult =
        if (current.status == Status.PENDING) {
            TransitionResult.Applied(current.copy(status = Status.ACCEPTED))
        } else {
            TransitionResult.Rejected(current, REASON_NOT_PENDING)
        }

    /**
     * Decline an assigned task. Permitted only when the member's status is `pending` (10.2).
     * From any other status this is a no-op (10.3).
     */
    fun decline(current: MemberStateValue): TransitionResult =
        if (current.status == Status.PENDING) {
            TransitionResult.Applied(current.copy(status = Status.DECLINED))
        } else {
            TransitionResult.Rejected(current, REASON_NOT_PENDING)
        }

    /**
     * Mark an assigned task complete. Permitted only when the member's status is `accepted` (10.4).
     * When permitted, sets the status to `completed`, sets the completion flag to true, and records
     * [nowUtcMillis] as the UTC completion timestamp. From any other status this is a no-op (10.5):
     * the status and completion flag are left unchanged.
     */
    fun complete(current: MemberStateValue, nowUtcMillis: Long): TransitionResult =
        if (current.status == Status.ACCEPTED) {
            TransitionResult.Applied(
                current.copy(
                    status = Status.COMPLETED,
                    isCompleted = true,
                    completedTimestamp = nowUtcMillis
                )
            )
        } else {
            TransitionResult.Rejected(current, REASON_NOT_ACCEPTED)
        }
}
