package com.theblankstate.preamble.collab

import com.theblankstate.preamble.data.Task

/**
 * Pure, side-effect-free selection of the signed-in user's own incoming (pending) collaborative
 * tasks for the home Incoming_Section.
 *
 * Feature: collaborative-tasks. This object is the single source of truth for deciding which tasks
 * appear in the home Incoming_Section (Requirement 19.1) and whether that section's header should
 * be shown at all (Requirement 19.8). A task is "incoming" exactly when the current user's own
 * [Task.assignmentStatus] equals [INCOMING_STATUS] (`"pending"`); the projection of the canonical
 * `memberStates[uid]` into the local `Task` already lives in `assignmentStatus`, so this function
 * only needs to inspect that single field.
 *
 * The functions carry no Android, Firestore, or serialization dependencies so the selection can be
 * validated with fast JVM/property-based tests (Property 19).
 */
object IncomingTasks {

    /** The own member status that marks a collaborative task as incoming (Requirement 19.1). */
    const val INCOMING_STATUS: String = "pending"

    /**
     * Returns exactly the tasks whose own [Task.assignmentStatus] equals [INCOMING_STATUS],
     * preserving the input order (Requirement 19.1). Every other status value (`accepted`,
     * `completed`, `declined`, `left`, `removed`, or absent/`null`) is excluded.
     */
    fun incoming(tasks: List<Task>): List<Task> =
        tasks.filter { it.assignmentStatus == INCOMING_STATUS }

    /**
     * Whether the user has at least one incoming (own-pending) collaborative task, used to decide
     * whether the Incoming_Section header is displayed (Requirement 19.8).
     */
    fun hasIncoming(tasks: List<Task>): Boolean =
        tasks.any { it.assignmentStatus == INCOMING_STATUS }
}
