package com.theblankstate.preamble.collab

import androidx.compose.runtime.Stable
import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.theblankstate.preamble.data.CollabAssigneeStatus
import com.theblankstate.preamble.data.Task

/**
 * A single reaction projected from the canonical document's top-level `reactions`
 * map into the local [Task.reactionsJson] for rendering (Requirements 3.1, 3.3).
 *
 * Mirrors the style of [CollabAssigneeStatus]: a plain, [Stable] value type with
 * the reactor's resolved display name so the UI can show "name + emoji" without an
 * extra lookup.
 */
@Stable
data class TaskReaction(
    val reactorUid: String,
    val reactorName: String,
    val emoji: String,
    val targetUid: String? = null,
    val createdAt: Long = 0L
)

/**
 * Pure projection logic between the admin's local [Task] and the shared `task`
 * payload carried by the canonical `/collaborativeTasks/{taskId}` document.
 *
 * This object is deliberately free of Android and Firestore dependencies. It
 * serializes with Gson (a plain JVM library) rather than Firestore types, so the
 * payload-fidelity invariants (Requirements 7.2, 7.7) can be validated with fast
 * JVM/property-based tests. [WorkspaceRepository] adapts Firestore
 * `DocumentSnapshot` data into the plain maps these functions consume.
 */
object TaskProjection {

    private const val DEFAULT_NAME = "Preamble user"

    /**
     * Member statuses that are terminal for visibility purposes; assignees in
     * these states are filtered out of the projected task (Requirement 13.1).
     */
    val TERMINAL_MEMBER_STATUSES: Set<String> = setOf("declined", "left", "removed")

    /**
     * Local-only collaboration fields that describe the *current user's* view of
     * a task. They are stripped from the shared payload so the canonical document
     * never carries one member's local projection as shared state (Requirement 7.7).
     */
    val LOCAL_COLLAB_FIELDS: Set<String> = setOf(
        "assignedByUid",
        "assignedByName",
        "assignedToUid",
        "assignedToName",
        "assignmentStatus",
        "collabAssigneesJson",
        "collabAdminUid",
        "collabAdminName",
        // Reactions live at the document top level, not inside the shared `task`
        // payload, so the admin's own local projection is never written back as
        // shared state (Requirement 3.x; mirrors Requirement 7.7).
        "reactionsJson"
    )

    /**
     * Gson configured to ignore Kotlin `by lazy` delegate backing fields (named
     * `<prop>$delegate` and typed [Lazy]) so the [Task] entity round-trips through
     * plain JSON without serializing computed/cached views.
     */
    private val gson: Gson = GsonBuilder()
        .setExclusionStrategies(object : ExclusionStrategy {
            override fun shouldSkipField(field: FieldAttributes): Boolean =
                field.name.endsWith("\$delegate") || field.declaredClass == Lazy::class.java

            override fun shouldSkipClass(clazz: Class<*>): Boolean = clazz == Lazy::class.java
        })
        .create()

    /**
     * Serializes the admin's [task] into the shared payload map.
     *
     * The local-only collaboration fields are removed, and the shared completion
     * state is forced to "not completed": per-member completion is tracked
     * individually in `memberStates[uid]`, never as a shared scalar
     * (Requirements 7.2, 7.7).
     */
    fun taskPayload(task: Task): Map<String, Any?> {
        val type = object : TypeToken<MutableMap<String, Any?>>() {}.type
        val payload: MutableMap<String, Any?> = gson.fromJson(gson.toJson(task), type)
        LOCAL_COLLAB_FIELDS.forEach(payload::remove)
        // Overall completion is represented per member, not as a shared mutable scalar.
        payload["isCompleted"] = false
        payload["completedTimestamp"] = null
        payload["completedDate"] = null
        payload["isSyncing"] = false
        payload["syncFailed"] = false
        return payload
    }

    /**
     * Projects a canonical collaborative document (already reduced to its plain
     * field map) into a per-user [Task] for [currentUserUid].
     *
     * The shared content comes from the `task` payload; the current user's own
     * status and completion are read from `memberStates[currentUserUid]`. Active
     * assignees (those not in [TERMINAL_MEMBER_STATUSES]) are projected into
     * `collabAssigneesJson`.
     *
     * @param data the canonical document's field map (`document.data`).
     * @param documentId fallback id when the document omits `taskId`.
     * @param currentUserUid the signed-in user whose view is being projected.
     * @return the projected [Task], or `null` when the payload is missing or invalid.
     */
    fun documentToTask(
        data: Map<String, Any?>,
        documentId: String,
        currentUserUid: String
    ): Task? = try {
        @Suppress("UNCHECKED_CAST")
        val payload = data["task"] as? Map<String, Any?> ?: return null
        val baseTask = gson.fromJson(gson.toJson(payload), Task::class.java) ?: return null
        val adminUid = data["adminUid"] as? String ?: return null
        val adminName = data["adminName"] as? String ?: DEFAULT_NAME
        val assigneeUids = (data["assigneeUids"] as? List<*>)?.filterIsInstance<String>().orEmpty()
        val memberStates = data["memberStates"] as? Map<*, *> ?: emptyMap<Any, Any>()

        val assignees = assigneeUids.mapNotNull { assigneeUid ->
            @Suppress("UNCHECKED_CAST")
            val state = memberStates[assigneeUid] as? Map<String, Any?> ?: return@mapNotNull null
            val status = state["status"] as? String ?: "pending"
            if (status in TERMINAL_MEMBER_STATUSES) return@mapNotNull null
            CollabAssigneeStatus(
                uid = assigneeUid,
                name = state["name"] as? String ?: DEFAULT_NAME,
                status = status,
                isCompleted = state["isCompleted"] as? Boolean ?: false,
                completedTimestamp = (state["completedTimestamp"] as? Number)?.toLong(),
                assignedTimestamp = (state["assignedTimestamp"] as? Number)?.toLong()
                    ?: baseTask.createdTimestamp
            )
        }

        @Suppress("UNCHECKED_CAST")
        val myState = memberStates[currentUserUid] as? Map<String, Any?>
        val myStatus = myState?.get("status") as? String
            ?: if (currentUserUid == adminUid) "accepted" else "pending"
        val myCompleted = myState?.get("isCompleted") as? Boolean ?: false
        val myCompletedTimestamp = (myState?.get("completedTimestamp") as? Number)?.toLong()

        val reactionsMap = data["reactions"] as? Map<*, *> ?: emptyMap<Any, Any>()
        val reactions = reactionsMap.entries.mapNotNull { (key, value) ->
            val reactorUid = key as? String ?: return@mapNotNull null
            @Suppress("UNCHECKED_CAST")
            val reaction = value as? Map<String, Any?> ?: return@mapNotNull null
            val emoji = reaction["emoji"] as? String ?: return@mapNotNull null
            @Suppress("UNCHECKED_CAST")
            val reactorState = memberStates[reactorUid] as? Map<String, Any?>
            TaskReaction(
                reactorUid = reactorUid,
                reactorName = reactorState?.get("name") as? String ?: DEFAULT_NAME,
                emoji = emoji,
                targetUid = reaction["targetUid"] as? String,
                createdAt = (reaction["createdAt"] as? Number)?.toLong() ?: 0L
            )
        }
        val reactionsJson = if (reactions.isEmpty()) null else gson.toJson(reactions)

        baseTask.copy(
            id = (data["taskId"] as? String).orEmpty().ifBlank { documentId },
            isCompleted = myCompleted,
            completedTimestamp = myCompletedTimestamp,
            collabAdminUid = adminUid,
            collabAdminName = adminName,
            collabAssigneesJson = gson.toJson(assignees),
            reactionsJson = reactionsJson,
            assignedByUid = adminUid,
            assignedByName = adminName,
            assignedToUid = assignees.firstOrNull()?.uid,
            assignedToName = assignees.firstOrNull()?.name,
            assignmentStatus = myStatus,
            updatedTimestamp = (data["updatedAt"] as? Number)?.toLong()
                ?: baseTask.updatedTimestamp
        )
    } catch (exception: Exception) {
        null
    }
}
