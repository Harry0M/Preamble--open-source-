package com.theblankstate.preamble.collab

/**
 * Pure logic for the collaborative-tasks feature.
 *
 * This file is deliberately free of Android, Firestore, and serialization
 * dependencies so that the schema-v2 canonical-document invariants can be
 * validated with fast JVM/property-based tests. The Firestore gateway
 * (`WorkspaceRepository`) serializes the admin's task into a plain payload map
 * and delegates document construction here.
 */

/** Lightweight, pure descriptor of an assignee (a member other than the admin). */
data class AssigneeRef(val uid: String, val name: String)

/** Outcome of attempting to build the schema-v2 canonical collaborative document. */
sealed interface CollaborativeDocumentResult {
    /** A valid canonical document was produced. */
    data class Created(val document: Map<String, Any?>) : CollaborativeDocumentResult

    /**
     * No friend is assigned, so the task stays a normal local task and no
     * canonical document is created (Requirement 6.4).
     */
    data object NotCollaborative : CollaborativeDocumentResult

    /**
     * More than [CollaborativeDocument.MAX_ASSIGNEES] distinct assignees were
     * requested. The request is rejected and no document is produced
     * (Requirements 6.5, 6.6).
     */
    data object TooManyAssignees : CollaborativeDocumentResult
}

/**
 * Builds the single canonical `/collaborativeTasks/{taskId}` document
 * (`schemaVersion == 2`) and enforces every structural invariant required by
 * Requirements 6.3 and 8.1–8.5.
 */
object CollaborativeDocument {

    const val SCHEMA_VERSION = 2

    /** Maximum number of assignees on a single collaborative task (excludes the admin). */
    const val MAX_ASSIGNEES = 50

    /** Maximum number of members (the assignee maximum plus the admin). */
    const val MAX_MEMBERS = MAX_ASSIGNEES + 1

    private const val DEFAULT_NAME = "Preamble user"

    /** Every Member_Status value the canonical document is allowed to carry (Requirement 8.4). */
    val MEMBER_STATUSES: Set<String> =
        setOf("pending", "accepted", "completed", "declined", "left", "removed")

    /**
     * The complete, ordered member-uid list: the admin first, followed by the
     * distinct non-blank assignee uids. The admin is always included exactly
     * once and there are no duplicates (Requirements 8.1, 8.2).
     */
    fun memberUids(adminUid: String, assigneeUids: List<String>): List<String> =
        (listOf(adminUid) + assigneeUids)
            .filter(String::isNotBlank)
            .distinct()

    /** The membership map whose keys equal [memberUids] exactly (queryability + rule consistency). */
    fun memberUidMap(memberUids: List<String>): Map<String, Boolean> =
        memberUids
            .filter(String::isNotBlank)
            .distinct()
            .associateWith { true }

    /**
     * Reduces a raw assignee list to the canonical assignee set: drops blank
     * uids, drops the admin (the admin is never an assignee), and deduplicates
     * by uid while preserving first-seen order (Requirement 8.3).
     */
    fun normalizeAssignees(adminUid: String, assignees: List<AssigneeRef>): List<AssigneeRef> =
        assignees
            .filter { it.uid.isNotBlank() && it.uid != adminUid }
            .distinctBy(AssigneeRef::uid)

    /**
     * Builds the canonical document for the given admin and assignees.
     *
     * @param taskId canonical document id (matches the admin's local task id).
     * @param adminUid the single admin / owner uid (must be non-blank).
     * @param adminName the admin's display name (blank is replaced with a default).
     * @param assignees raw assignee descriptors; normalized internally.
     * @param taskPayload the already-serialized shared task content (built by the caller).
     * @param now timestamp applied to createdAt/updatedAt and member assignedTimestamp.
     * @param adminCompleted the admin's own per-member completion flag.
     * @param adminCompletedTimestamp the admin's own completion timestamp, if any.
     *
     * @return [CollaborativeDocumentResult.NotCollaborative] when no friend is
     * assigned, [CollaborativeDocumentResult.TooManyAssignees] when the distinct
     * assignee count exceeds [MAX_ASSIGNEES], otherwise
     * [CollaborativeDocumentResult.Created] with a document satisfying all invariants.
     */
    fun build(
        taskId: String,
        adminUid: String,
        adminName: String,
        assignees: List<AssigneeRef>,
        taskPayload: Map<String, Any?>,
        now: Long,
        adminCompleted: Boolean = false,
        adminCompletedTimestamp: Long? = null
    ): CollaborativeDocumentResult {
        require(adminUid.isNotBlank()) { "adminUid is required" }

        val distinctAssignees = normalizeAssignees(adminUid, assignees)
        if (distinctAssignees.isEmpty()) return CollaborativeDocumentResult.NotCollaborative
        if (distinctAssignees.size > MAX_ASSIGNEES) return CollaborativeDocumentResult.TooManyAssignees

        val safeAdminName = adminName.trim().ifBlank { DEFAULT_NAME }

        // memberStates: admin first (accepted), then every assignee (pending).
        // A LinkedHashMap keeps the admin-first ordering deterministic for tests.
        val memberStates = linkedMapOf<String, Any?>(
            adminUid to memberState(
                uid = adminUid,
                name = safeAdminName,
                role = "admin",
                status = "accepted",
                isCompleted = adminCompleted,
                completedTimestamp = adminCompletedTimestamp,
                now = now
            )
        )
        distinctAssignees.forEach { friend ->
            memberStates[friend.uid] = memberState(
                uid = friend.uid,
                name = friend.name.trim().ifBlank { DEFAULT_NAME },
                role = "member",
                status = "pending",
                isCompleted = false,
                completedTimestamp = null,
                now = now
            )
        }

        val assigneeUids = distinctAssignees.map(AssigneeRef::uid)
        val members = memberUids(adminUid, assigneeUids)

        val document = mapOf(
            "schemaVersion" to SCHEMA_VERSION,
            "taskId" to taskId,
            "adminUid" to adminUid,
            "adminName" to safeAdminName,
            "memberUids" to members,
            "assigneeUids" to assigneeUids,
            "memberUidMap" to memberUidMap(members),
            "memberStates" to memberStates,
            "task" to taskPayload,
            "createdAt" to now,
            "updatedAt" to now
        )
        return CollaborativeDocumentResult.Created(document)
    }

    private fun memberState(
        uid: String,
        name: String,
        role: String,
        status: String,
        isCompleted: Boolean,
        completedTimestamp: Long?,
        now: Long
    ): Map<String, Any?> = mapOf(
        "uid" to uid,
        "name" to name,
        "role" to role,
        "status" to status,
        "isCompleted" to isCompleted,
        "completedTimestamp" to completedTimestamp,
        "assignedTimestamp" to now
    )
}
