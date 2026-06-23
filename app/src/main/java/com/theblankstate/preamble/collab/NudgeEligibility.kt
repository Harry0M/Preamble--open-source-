package com.theblankstate.preamble.collab

/**
 * Pure, side-effect-free nudge-eligibility logic for collaborative tasks.
 *
 * Feature: social-engagement. This object classifies a nudge attempt purely from the
 * sender/target identities, the task's membership set, and the target's current
 * member status (Requirement 11). It performs no I/O, touches no clock, and is the
 * client-side mirror of the authoritative checks the `sendNudge` Cloud Function
 * enforces with the Admin SDK.
 *
 * A nudge is [Result.Eligible] only when all of the following hold:
 *  - the sender is a member of the task (Req 11.1),
 *  - the target is not the sender (Req 11.3), and
 *  - the target's member status is `pending` (Req 11.2).
 *
 * Because the collaborative-task admin is itself a member, this same rule lets an admin
 * nudge any pending member without a special case (Req 11.4).
 *
 * Precedence (first matching rule wins) is deliberately fixed so the classification is
 * deterministic and property-testable:
 *  1. [Result.SenderNotMember] — sender absent from `memberUids` (Req 11.1)
 *  2. [Result.SelfNudge]       — target equals sender (Req 11.3)
 *  3. [Result.TargetNotPending] — target status is not `pending` (Req 11.2)
 *  4. [Result.Eligible]        — otherwise
 *
 * Checking membership before self-nudge means a non-member nudging themselves is reported
 * as [Result.SenderNotMember] (the more fundamental failure), keeping the outcome
 * independent of the order in which checks happen to be written.
 */
object NudgeEligibility {

    /** The member status that makes a target eligible to be nudged (Req 11.2). */
    const val STATUS_PENDING: String = "pending"

    /**
     * Classifies a nudge attempt from [senderUid] toward [targetUid] given the task's
     * [memberUids] and the target's [targetStatus] (`null` when the target has no
     * recorded member state).
     */
    fun classify(
        senderUid: String,
        targetUid: String,
        memberUids: Set<String>,
        targetStatus: String?
    ): Result = when {
        senderUid !in memberUids -> Result.SenderNotMember
        targetUid == senderUid -> Result.SelfNudge
        targetStatus != STATUS_PENDING -> Result.TargetNotPending
        else -> Result.Eligible
    }

    /**
     * The outcome of [classify].
     *
     * [Eligible] means the nudge may be sent; the remaining cases each identify why the
     * nudge is rejected, so callers can surface the appropriate message (Req 11).
     */
    sealed interface Result {
        /** The nudge is permitted (sender is a member, target differs and is pending). */
        data object Eligible : Result

        /** The sender is not a member of the task (Req 11.1). */
        data object SenderNotMember : Result

        /** The target's member status is not `pending` (Req 11.2). */
        data object TargetNotPending : Result

        /** The target is the sender — self-nudges are not permitted (Req 11.3). */
        data object SelfNudge : Result
    }
}
