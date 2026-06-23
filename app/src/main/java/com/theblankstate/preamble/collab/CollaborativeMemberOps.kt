package com.theblankstate.preamble.collab

/**
 * Pure, side-effect-free transforms over the schema-v2 canonical collaborative
 * document (the same map shape produced by [CollaborativeDocument.build]).
 *
 * Feature: collaborative-tasks. This object is the single source of truth for the
 * member-management transforms described in design.md and Requirements 8.6, 11, 12:
 *
 *  - [updateMemberState] / [applyMemberStateToDocument] : single-member state update
 *    that touches only the target member's `memberStates` entry (Requirement 8.6).
 *  - [transferOwnership] : promote a member to admin, retaining the previous admin as
 *    an `accepted` member, preserving every canonical-document invariant; reject
 *    non-member (and current-admin) targets (Requirements 11.4, 11.5).
 *  - [leaveCollaborativeTask] : remove only the leaving member from `memberUids`,
 *    `assigneeUids`, and `memberUidMap`, setting only that member's status to `left`;
 *    reject admin self-removal (Requirements 12.2, 12.3, 12.4).
 *
 * Like the rest of this package, the file is deliberately free of Android, Firestore,
 * and serialization dependencies so these transforms are fast/JVM property-testable.
 * The Firestore gateway (`WorkspaceRepository`) applies the resulting document (or the
 * equivalent field-level updates) inside a transaction.
 */
object CollaborativeMemberOps {

    /** Message returned when ownership transfer targets a user who is not a member (11.5). */
    const val REASON_TRANSFER_NON_MEMBER = "The new admin must already be a task member."

    /** Message returned when ownership transfer targets the current admin (11.3, 11.5). */
    const val REASON_TRANSFER_TO_ADMIN = "Choose a member other than the current admin."

    /** Message returned when the admin attempts to leave their own task (12.4). */
    const val REASON_ADMIN_CANNOT_LEAVE =
        "Transfer ownership before leaving a task you administer."

    /** Message returned when a self-removal target is not a member of the task. */
    const val REASON_NOT_A_MEMBER = "You are not a member of this task."

    /** Outcome of a document-level member transform. */
    sealed interface DocumentOpResult {
        /** The transform was applied; [document] is the new canonical document. */
        data class Updated(val document: Map<String, Any?>) : DocumentOpResult

        /** The transform was rejected; the caller leaves all records unchanged (11.5, 12.4). */
        data class Rejected(val reason: String) : DocumentOpResult
    }

    // ---------------------------------------------------------------------------------------------
    // Requirement 8.6 — single-member state isolation (Property 9)
    // ---------------------------------------------------------------------------------------------

    /**
     * Returns a copy of [memberStates] in which only [targetUid]'s entry has its
     * `status`, `isCompleted`, and `completedTimestamp` fields replaced. Every other
     * member's entry is left byte-for-byte identical, and the target entry's other
     * fields (`uid`, `name`, `role`, `assignedTimestamp`, ...) are preserved.
     *
     * If [targetUid] has no existing entry, the map is returned unchanged (there is no
     * member to update).
     */
    fun updateMemberState(
        memberStates: Map<String, Any?>,
        targetUid: String,
        status: String,
        isCompleted: Boolean,
        completedTimestamp: Long?
    ): Map<String, Any?> {
        val existing = memberStates[targetUid].asStringMap() ?: return memberStates
        val updatedEntry = LinkedHashMap<String, Any?>(existing).apply {
            this["status"] = status
            this["isCompleted"] = isCompleted
            this["completedTimestamp"] = completedTimestamp
        }
        val result = LinkedHashMap<String, Any?>(memberStates)
        result[targetUid] = updatedEntry
        return result
    }

    /**
     * Document-level convenience for [updateMemberState]: applies a single member's
     * state change to [document]'s `memberStates` and bumps `updatedAt`, leaving every
     * other field (including every other member's state) untouched (Requirement 8.6).
     */
    fun applyMemberStateToDocument(
        document: Map<String, Any?>,
        targetUid: String,
        status: String,
        isCompleted: Boolean,
        completedTimestamp: Long?,
        now: Long
    ): Map<String, Any?> {
        val memberStates = document["memberStates"].asStringMap() ?: emptyMap()
        val updatedStates =
            updateMemberState(memberStates, targetUid, status, isCompleted, completedTimestamp)
        val result = LinkedHashMap<String, Any?>(document)
        result["memberStates"] = updatedStates
        result["updatedAt"] = now
        return result
    }

    // ---------------------------------------------------------------------------------------------
    // Requirements 11.4, 11.5 — ownership transfer (Property 14)
    // ---------------------------------------------------------------------------------------------

    /**
     * Transfers ownership of the collaborative [document] to [newAdminUid].
     *
     * On success: [newAdminUid] becomes the sole admin (`adminUid`, `adminName`, and its
     * member-state `role=admin`/`status=accepted`), the previous admin is retained as a
     * member with `role=member`/`status=accepted`, and `assigneeUids`/`memberUidMap` are
     * recomputed so every canonical-document invariant of [CollaborativeDocument] is
     * preserved (members unchanged; assignees == members minus the new admin) (11.4).
     *
     * The transform is rejected — leaving the admin, member, and assignee records
     * unchanged — when [newAdminUid] is not a current member, or is the current admin
     * (11.5).
     */
    fun transferOwnership(
        document: Map<String, Any?>,
        newAdminUid: String,
        now: Long
    ): DocumentOpResult {
        val currentAdminUid = document["adminUid"] as? String
        val members = document["memberUids"].asStringList()

        if (newAdminUid.isBlank() || newAdminUid == currentAdminUid) {
            return DocumentOpResult.Rejected(REASON_TRANSFER_TO_ADMIN)
        }
        if (newAdminUid !in members) {
            return DocumentOpResult.Rejected(REASON_TRANSFER_NON_MEMBER)
        }

        val memberStates = document["memberStates"].asStringMap() ?: emptyMap()
        val newAdminName = (memberStates[newAdminUid].asStringMap()?.get("name") as? String)
            ?.trim()
            ?.ifBlank { null }
            ?: (document["adminName"] as? String ?: "Preamble user")

        // Members are unchanged; assignees are every member except the new admin.
        val newAssignees = members.filterNot { it == newAdminUid }

        val updatedStates = LinkedHashMap<String, Any?>(memberStates)
        // Promote the new admin.
        memberStates[newAdminUid].asStringMap()?.let { entry ->
            updatedStates[newAdminUid] = LinkedHashMap<String, Any?>(entry).apply {
                this["role"] = "admin"
                this["status"] = "accepted"
            }
        }
        // Retain the previous admin as an accepted member (11.4).
        if (currentAdminUid != null) {
            memberStates[currentAdminUid].asStringMap()?.let { entry ->
                updatedStates[currentAdminUid] = LinkedHashMap<String, Any?>(entry).apply {
                    this["role"] = "member"
                    this["status"] = "accepted"
                }
            }
        }

        val result = LinkedHashMap<String, Any?>(document)
        result["adminUid"] = newAdminUid
        result["adminName"] = newAdminName
        result["memberUids"] = members
        result["assigneeUids"] = newAssignees
        result["memberUidMap"] = CollaborativeDocument.memberUidMap(members)
        result["memberStates"] = updatedStates
        result["updatedAt"] = now
        return DocumentOpResult.Updated(result)
    }

    // ---------------------------------------------------------------------------------------------
    // Requirements 12.2, 12.3, 12.4 — non-admin self-removal (Property 15)
    // ---------------------------------------------------------------------------------------------

    /**
     * Removes the non-admin member [leavingUid] from the collaborative [document].
     *
     * On success: [leavingUid] is removed from `memberUids`, `assigneeUids`, and
     * `memberUidMap`, and only that member's `memberStates` status is set to `left`
     * (the entry itself is retained for history). Every other member's records — their
     * membership, assignee status, and full state entry — are left unchanged (12.2, 12.3).
     *
     * The transform is rejected — leaving all records unchanged — when [leavingUid] is the
     * current admin (the admin must transfer ownership first) (12.4) or is not a member.
     */
    fun leaveCollaborativeTask(
        document: Map<String, Any?>,
        leavingUid: String,
        now: Long
    ): DocumentOpResult {
        val currentAdminUid = document["adminUid"] as? String
        val members = document["memberUids"].asStringList()

        if (leavingUid == currentAdminUid) {
            return DocumentOpResult.Rejected(REASON_ADMIN_CANNOT_LEAVE)
        }
        if (leavingUid !in members) {
            return DocumentOpResult.Rejected(REASON_NOT_A_MEMBER)
        }

        val assignees = document["assigneeUids"].asStringList()
        val memberStates = document["memberStates"].asStringMap() ?: emptyMap()

        val newMembers = members.filterNot { it == leavingUid }
        val newAssignees = assignees.filterNot { it == leavingUid }

        // Retain the leaving member's state entry but flip only its status to `left`.
        val updatedStates = updateLeftStatus(memberStates, leavingUid)

        val result = LinkedHashMap<String, Any?>(document)
        result["memberUids"] = newMembers
        result["assigneeUids"] = newAssignees
        result["memberUidMap"] = CollaborativeDocument.memberUidMap(newMembers)
        result["memberStates"] = updatedStates
        result["updatedAt"] = now
        return DocumentOpResult.Updated(result)
    }

    /** Sets only [leavingUid]'s `status` to `left`, preserving every other field/member. */
    private fun updateLeftStatus(
        memberStates: Map<String, Any?>,
        leavingUid: String
    ): Map<String, Any?> {
        val existing = memberStates[leavingUid].asStringMap() ?: return memberStates
        val updatedEntry = LinkedHashMap<String, Any?>(existing).apply { this["status"] = "left" }
        val result = LinkedHashMap<String, Any?>(memberStates)
        result[leavingUid] = updatedEntry
        return result
    }

    // ---------------------------------------------------------------------------------------------
    // Safe accessors for the loosely-typed canonical document map.
    // ---------------------------------------------------------------------------------------------

    private fun Any?.asStringList(): List<String> =
        (this as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

    private fun Any?.asStringMap(): Map<String, Any?>? {
        val map = this as? Map<*, *> ?: return null
        val result = LinkedHashMap<String, Any?>(map.size)
        for ((key, value) in map) {
            if (key is String) result[key] = value
        }
        return result
    }
}
