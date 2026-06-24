package com.theblankstate.preamble.circles

/**
 * Pure logic for the shared-circles feature.
 *
 * This file deliberately parallels [com.theblankstate.preamble.collab.CollaborativeDocument]
 * and is free of Android, Firestore, and serialization dependencies so that the
 * canonical `/circles/{circleId}` document invariants can be validated with fast
 * JVM/property-based tests. The Firestore gateway (`CircleRepository`) delegates
 * document construction and validation here and serializes the resulting plain
 * `Map<String, Any?>` payload.
 *
 * Feature: shared-circles.
 */

/** Lightweight, pure descriptor of a circle member (admin or member). */
data class CircleMemberRef(val uid: String, val name: String)

/** Outcome of attempting to build the canonical circle document. */
sealed interface CircleDocumentResult {
    /** A valid canonical document was produced. */
    data class Created(val document: Map<String, Any?>) : CircleDocumentResult

    /**
     * The normalized [CircleDocument.normalizeName] is blank, so no circle is
     * created (Requirement 1.2).
     */
    data object EmptyName : CircleDocumentResult

    /**
     * More than [CircleDocument.MAX_MEMBERS] distinct members were requested. The
     * request is rejected and no document is produced (Requirements 4.6, 8.2).
     */
    data object TooManyMembers : CircleDocumentResult
}

/**
 * Builds the single canonical `/circles/{circleId}` document and enforces every
 * structural invariant required by Requirements 1.3 and 8.1–8.5 (mirrored by the
 * Security_Rules, Requirement 13.7).
 *
 * The document shape parallels the deployed `/collaborativeTasks` shape so the
 * membership-via-map / admin-gated / own-slice rule idioms port over directly:
 *
 * ```
 * {
 *   "circleId":     String,
 *   "adminUid":     String,
 *   "name":         String,                       // non-empty
 *   "memberUids":   List<String>,                 // includes adminUid, distinct, size 1..50
 *   "memberUidMap": Map<String, Boolean>,         // keys == memberUids, [adminUid] == true
 *   "memberStates": Map<String, Map<String,Any?>> // one entry per member: name/role/status/joinedAt
 *   "createdAt":    Long,                          // UTC
 *   "updatedAt":    Long                           // UTC
 * }
 * ```
 */
object CircleDocument {

    /** Maximum number of Circle_Members in a single Circle, including the admin (Requirement 8.2). */
    const val MAX_MEMBERS = 50

    /** Every Member_Status value the canonical document is allowed to carry (Requirement 8.4). */
    val MEMBER_STATUSES: Set<String> = setOf("active", "left", "removed")

    /** Role value for the single Circle_Admin. */
    const val ROLE_ADMIN = "admin"

    /** Role value for a non-admin Circle_Member. */
    const val ROLE_MEMBER = "member"

    private const val DEFAULT_NAME = "Preamble user"

    /** Normalizes a Circle_Name by removing all leading/trailing whitespace (Requirements 1.1, 3.2). */
    fun normalizeName(raw: String): String = raw.trim()

    /**
     * The membership map whose keys equal [memberUids] exactly: blank uids are
     * dropped, duplicates are collapsed, and every remaining uid maps to `true`
     * (Requirement 8.3, queryability + rule consistency).
     */
    fun memberUidMap(memberUids: List<String>): Map<String, Boolean> =
        memberUids
            .filter(String::isNotBlank)
            .distinct()
            .associateWith { true }

    /**
     * Builds the canonical document at creation: the creator is the sole
     * Circle_Admin and the only Circle_Member, with `role=admin` and
     * Member_Status `active` (Requirements 1.3, 8.1).
     *
     * @param circleId canonical document id.
     * @param adminUid the single admin / owner uid (must be non-blank).
     * @param adminName the admin's display name (blank is replaced with a default).
     * @param name the raw Circle_Name; normalized internally.
     * @param now timestamp applied to createdAt/updatedAt and the admin's joinedAt.
     *
     * @return [CircleDocumentResult.EmptyName] when the normalized name is blank
     * (Requirement 1.2), otherwise [CircleDocumentResult.Created] with a document
     * satisfying every [isValid] invariant.
     */
    fun build(
        circleId: String,
        adminUid: String,
        adminName: String,
        name: String,
        now: Long
    ): CircleDocumentResult {
        require(adminUid.isNotBlank()) { "adminUid is required" }

        val normalizedName = normalizeName(name)
        if (normalizedName.isEmpty()) return CircleDocumentResult.EmptyName

        val safeAdminName = adminName.trim().ifBlank { DEFAULT_NAME }

        // The creator is the only member: sole admin, status active.
        val memberStates = linkedMapOf<String, Any?>(
            adminUid to memberState(
                uid = adminUid,
                name = safeAdminName,
                role = ROLE_ADMIN,
                status = "active",
                now = now
            )
        )

        val members = listOf(adminUid)
        val document = mapOf(
            "circleId" to circleId,
            "adminUid" to adminUid,
            "name" to normalizedName,
            "memberUids" to members,
            "memberUidMap" to memberUidMap(members),
            "memberStates" to memberStates,
            "createdAt" to now,
            "updatedAt" to now
        )
        return CircleDocumentResult.Created(document)
    }

    /**
     * Validates every invariant a create/update must satisfy, mirroring the
     * Security_Rules schema (Requirements 8.1–8.5, 13.7):
     *
     *  - exactly one non-blank `adminUid`, present in `memberUids` (8.1);
     *  - `memberUids` is a non-empty, duplicate-free list of size 1..[MAX_MEMBERS]
     *    containing the admin (8.2);
     *  - `memberUidMap` keys equal `memberUids` exactly and `memberUidMap[adminUid] == true` (8.3);
     *  - `memberStates` has exactly one entry per member and none for a non-member, each with a
     *    non-blank `name`, `role ∈ {admin, member}`, `status ∈ {active, left, removed}`, and a
     *    `joinedAt` timestamp (8.4);
     *  - a non-empty `name`, a `createdAt`, and an `updatedAt` (8.5).
     */
    fun isValid(document: Map<String, Any?>): Boolean {
        val adminUid = document["adminUid"] as? String ?: return false
        if (adminUid.isBlank()) return false

        // name (8.5)
        val name = document["name"] as? String ?: return false
        if (name.isEmpty()) return false

        // timestamps (8.5)
        if (document["createdAt"] !is Number) return false
        if (document["updatedAt"] !is Number) return false

        // memberUids (8.1, 8.2)
        val rawMembers = document["memberUids"] as? List<*> ?: return false
        val members = rawMembers.map { it as? String ?: return false }
        if (members.any(String::isBlank)) return false
        if (members.size != members.distinct().size) return false // no duplicates
        if (members.isEmpty() || members.size > MAX_MEMBERS) return false
        if (adminUid !in members) return false

        // memberUidMap: keys == memberUids exactly, admin maps to true (8.3)
        val rawMap = document["memberUidMap"] as? Map<*, *> ?: return false
        val mapKeys = rawMap.keys.map { it as? String ?: return false }
        if (mapKeys.toSet() != members.toSet()) return false
        if (rawMap[adminUid] != true) return false

        // memberStates: exactly one entry per member, none for non-members (8.4)
        val rawStates = document["memberStates"] as? Map<*, *> ?: return false
        val stateKeys = rawStates.keys.map { it as? String ?: return false }
        if (stateKeys.toSet() != members.toSet()) return false
        for (uid in members) {
            val entry = (rawStates[uid] as? Map<*, *>) ?: return false
            val entryName = entry["name"] as? String ?: return false
            if (entryName.isBlank()) return false
            val role = entry["role"] as? String ?: return false
            if (role != ROLE_ADMIN && role != ROLE_MEMBER) return false
            val status = entry["status"] as? String ?: return false
            if (status !in MEMBER_STATUSES) return false
            if (entry["joinedAt"] !is Number) return false
        }

        return true
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
}
