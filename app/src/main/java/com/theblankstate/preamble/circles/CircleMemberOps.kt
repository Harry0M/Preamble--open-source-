package com.theblankstate.preamble.circles

/**
 * Pure, side-effect-free single-member transforms over the canonical
 * `/circles/{circleId}` document produced by [CircleDocument.build] (the same
 * `Map<String, Any?>` shape).
 *
 * This object deliberately parallels
 * [com.theblankstate.preamble.collab.CollaborativeMemberOps] and is the single
 * source of truth for the membership/rename transforms described in design.md and
 * Requirements 3.3, 3.4, 4.2, 4.4, 4.6, 5.2, 5.3, 6.2, 6.3, 8.6:
 *
 *  - [addMember]    : admin appends ONLY the new member to `memberUids` /
 *    `memberUidMap` / `memberStates` (status=active, role=member); rejects
 *    duplicates and over-capacity (Requirements 4.2, 4.4, 4.6, 8.6).
 *  - [removeMember] : admin removes ONLY the target uid from `memberUids` /
 *    `memberUidMap` and sets ONLY that member's status to `removed` (the entry is
 *    retained); rejects removing the admin or a non-member (Requirements 5.2, 5.3, 8.6).
 *  - [leaveCircle]  : non-admin removes ONLY self from `memberUids` / `memberUidMap`
 *    and sets ONLY own status to `left`; rejects admin self-removal (Requirements 6.2, 6.3, 8.6).
 *  - [rename]       : sets ONLY `name` (normalized) + `updatedAt`; rejects an empty
 *    normalized name (Requirements 3.3, 3.4).
 *
 * Every success recomputes `memberUidMap` from the new `memberUids` via
 * [CircleDocument.memberUidMap], so the keys-equal-members invariant (Requirement 8.3)
 * holds by construction, and every OTHER member's `memberUids` / `memberUidMap` /
 * `memberStates` records are left byte-for-byte identical (Requirement 8.6).
 *
 * Like the rest of this package, the file is free of Android, Firestore, and
 * serialization dependencies. The Firestore gateway (`CircleRepository`) applies the
 * resulting document (or the equivalent field-level updates).
 */
object CircleMemberOps {

    /** Message returned when an add targets a user who is not a friend of the admin (Req 4.3). */
    const val REASON_NOT_FRIEND = "Only friends can be added to a Circle."

    /** Message returned when an add targets a user who is already a member (Req 4.4). */
    const val REASON_ALREADY_MEMBER = "That person is already in this Circle."

    /** Message returned when an add would exceed [CircleDocument.MAX_MEMBERS] (Req 4.6). */
    const val REASON_CIRCLE_FULL = "This Circle has reached the maximum number of members."

    /** Message returned when the admin attempts to leave their own Circle (Req 6.3). */
    const val REASON_ADMIN_CANNOT_LEAVE = "Delete the Circle instead of leaving it."

    /** Message returned when the admin attempts to remove their own uid (Req 5.3). */
    const val REASON_ADMIN_CANNOT_REMOVE_SELF = "Delete the Circle instead of removing yourself."

    /** Message returned when a non-admin attempts a membership change (Req 4.7, 5.4). */
    const val REASON_NOT_ADMIN = "Only the Circle admin can change membership."

    /** Message returned when a remove/leave targets a uid that is not a member (Req 5.2, 6.2). */
    const val REASON_NOT_A_MEMBER = "That person is not in this Circle."

    /** Message returned when a rename's normalized name is empty (Req 3.3). */
    const val REASON_EMPTY_NAME = "A Circle name is required."

    /** Outcome of a document-level member transform. */
    sealed interface DocumentOpResult {
        /** The transform was applied; [document] is the new canonical document. */
        data class Updated(val document: Map<String, Any?>) : DocumentOpResult

        /** The transform was rejected; the caller leaves all records unchanged. */
        data class Rejected(val reason: String) : DocumentOpResult
    }

    // ---------------------------------------------------------------------------------------------
    // Requirements 4.2, 4.4, 4.6, 8.6 — admin adds a single member
    // ---------------------------------------------------------------------------------------------

    /**
     * Admin adds [newMember] to the [document]: appends ONLY the new uid to `memberUids`
     * and `memberUidMap`, and adds ONLY that member's `memberStates` entry with
     * `status=active`, `role=member` (Requirements 4.2, 8.6).
     *
     * Rejected — leaving every record unchanged — when [newMember] is already a member
     * (Requirement 4.4) or when adding would exceed [CircleDocument.MAX_MEMBERS]
     * (Requirement 4.6).
     */
    fun addMember(
        document: Map<String, Any?>,
        newMember: CircleMemberRef,
        now: Long
    ): DocumentOpResult {
        val members = document["memberUids"].asStringList()

        if (newMember.uid in members) {
            return DocumentOpResult.Rejected(REASON_ALREADY_MEMBER)
        }
        if (members.size + 1 > CircleDocument.MAX_MEMBERS) {
            return DocumentOpResult.Rejected(REASON_CIRCLE_FULL)
        }

        val memberStates = document["memberStates"].asStringMap() ?: emptyMap()

        // Append ONLY the new member; every existing entry stays byte-for-byte identical.
        val newMembers = members + newMember.uid
        val updatedStates = LinkedHashMap<String, Any?>(memberStates)
        updatedStates[newMember.uid] = memberState(
            uid = newMember.uid,
            name = newMember.name.trim().ifBlank { DEFAULT_NAME },
            role = CircleDocument.ROLE_MEMBER,
            status = "active",
            now = now
        )

        val result = LinkedHashMap<String, Any?>(document)
        result["memberUids"] = newMembers
        result["memberUidMap"] = CircleDocument.memberUidMap(newMembers)
        result["memberStates"] = updatedStates
        result["updatedAt"] = now
        return DocumentOpResult.Updated(result)
    }

    // ---------------------------------------------------------------------------------------------
    // Requirements 5.2, 5.3, 8.6 — admin removes a single non-admin member
    // ---------------------------------------------------------------------------------------------

    /**
     * Admin removes [targetUid] from the [document]: removes ONLY that uid from
     * `memberUids` and `memberUidMap`, and sets ONLY that member's `memberStates`
     * status to `removed` (the entry is retained for history) (Requirements 5.2, 8.6).
     *
     * Rejected — leaving every record unchanged — when [targetUid] is the admin
     * (Requirement 5.3) or is not a member (Requirement 5.2).
     */
    fun removeMember(
        document: Map<String, Any?>,
        targetUid: String,
        now: Long
    ): DocumentOpResult {
        val currentAdminUid = document["adminUid"] as? String
        val members = document["memberUids"].asStringList()

        if (targetUid == currentAdminUid) {
            return DocumentOpResult.Rejected(REASON_ADMIN_CANNOT_REMOVE_SELF)
        }
        if (targetUid !in members) {
            return DocumentOpResult.Rejected(REASON_NOT_A_MEMBER)
        }

        val memberStates = document["memberStates"].asStringMap() ?: emptyMap()

        val newMembers = members.filterNot { it == targetUid }
        val updatedStates = updateStatusOnly(memberStates, targetUid, "removed")

        val result = LinkedHashMap<String, Any?>(document)
        result["memberUids"] = newMembers
        result["memberUidMap"] = CircleDocument.memberUidMap(newMembers)
        result["memberStates"] = updatedStates
        result["updatedAt"] = now
        return DocumentOpResult.Updated(result)
    }

    // ---------------------------------------------------------------------------------------------
    // Requirements 6.2, 6.3, 8.6 — non-admin self-removal (leave)
    // ---------------------------------------------------------------------------------------------

    /**
     * Non-admin [leavingUid] leaves the [document]: removes ONLY self from `memberUids`
     * and `memberUidMap`, and sets ONLY own `memberStates` status to `left` (the entry
     * is retained) (Requirements 6.2, 8.6).
     *
     * Rejected — leaving every record unchanged — when [leavingUid] is the admin
     * (Requirement 6.3) or is not a member.
     */
    fun leaveCircle(
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

        val memberStates = document["memberStates"].asStringMap() ?: emptyMap()

        val newMembers = members.filterNot { it == leavingUid }
        val updatedStates = updateStatusOnly(memberStates, leavingUid, "left")

        val result = LinkedHashMap<String, Any?>(document)
        result["memberUids"] = newMembers
        result["memberUidMap"] = CircleDocument.memberUidMap(newMembers)
        result["memberStates"] = updatedStates
        result["updatedAt"] = now
        return DocumentOpResult.Updated(result)
    }

    // ---------------------------------------------------------------------------------------------
    // Requirements 3.3, 3.4 — admin renames the circle
    // ---------------------------------------------------------------------------------------------

    /**
     * Sets ONLY `name` (normalized via [CircleDocument.normalizeName]) and `updatedAt`
     * on the [document], leaving every membership record unchanged (Requirement 3.4).
     *
     * Rejected — leaving the existing name unchanged — when the normalized name is empty
     * (Requirement 3.3).
     */
    fun rename(
        document: Map<String, Any?>,
        newName: String,
        now: Long
    ): DocumentOpResult {
        val normalizedName = CircleDocument.normalizeName(newName)
        if (normalizedName.isEmpty()) {
            return DocumentOpResult.Rejected(REASON_EMPTY_NAME)
        }

        val result = LinkedHashMap<String, Any?>(document)
        result["name"] = normalizedName
        result["updatedAt"] = now
        return DocumentOpResult.Updated(result)
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private const val DEFAULT_NAME = "Preamble user"

    /** Sets only [targetUid]'s `status`, preserving every other field/member. */
    private fun updateStatusOnly(
        memberStates: Map<String, Any?>,
        targetUid: String,
        status: String
    ): Map<String, Any?> {
        val existing = memberStates[targetUid].asStringMap() ?: return memberStates
        val updatedEntry = LinkedHashMap<String, Any?>(existing).apply { this["status"] = status }
        val result = LinkedHashMap<String, Any?>(memberStates)
        result[targetUid] = updatedEntry
        return result
    }

    private fun memberState(
        uid: String,
        name: String,
        role: String,
        status: String,
        now: Long
    ): Map<String, Any?> = mapOf(
        "uid" to uid,
        "name" to name,
        "role" to role,
        "status" to status,
        "joinedAt" to now
    )

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
