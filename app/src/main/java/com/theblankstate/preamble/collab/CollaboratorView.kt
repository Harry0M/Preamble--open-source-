package com.theblankstate.preamble.collab

/**
 * Pure, side-effect-free collaborator-view filtering for the collaborative-tasks feature.
 *
 * Feature: collaborative-tasks. This object is the single source of truth for deciding which
 * members are shown in the task-detail collaborator view (Requirement 13.1):
 *
 *  - SHOWN  : members whose `status` is one of `pending`, `accepted`, or `completed`.
 *  - HIDDEN : members whose `status` is one of `declined`, `left`, or `removed` (terminal).
 *
 * The functions are intentionally free of Android, Firestore, and serialization dependencies so
 * the filtering can be validated with fast JVM/property-based tests (Property 16). Callers project
 * their concrete member representation (canonical `memberStates[uid]` entries, or the local
 * `CollabAssigneeStatus` list) through the [status] extractor.
 */
object CollaboratorView {

    /** Statuses whose members are displayed in the collaborator view (Requirement 13.1). */
    val VISIBLE_STATUSES: Set<String> = setOf("pending", "accepted", "completed")

    /** Terminal statuses whose members are excluded from the collaborator view (Requirement 13.1). */
    val HIDDEN_STATUSES: Set<String> = setOf("declined", "left", "removed")

    /**
     * Whether a member with the given [status] should be displayed in the collaborator view.
     *
     * Only the three non-terminal statuses are visible; every terminal status (and any
     * unrecognized / null value) is excluded.
     */
    fun isVisible(status: String?): Boolean = status in VISIBLE_STATUSES

    /**
     * Returns exactly the members that should be displayed in the collaborator view, preserving the
     * input order. A member is included when [status] yields `pending`, `accepted`, or `completed`,
     * and excluded when it yields `declined`, `left`, or `removed` (Requirement 13.1).
     *
     * @param members the full member collection (any per-member representation).
     * @param status extracts the member's [Member_Status][VISIBLE_STATUSES] string.
     */
    fun <T> visibleMembers(members: List<T>, status: (T) -> String?): List<T> =
        members.filter { isVisible(status(it)) }
}
