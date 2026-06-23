package com.theblankstate.preamble.collab

import com.theblankstate.preamble.repository.WorkspaceInvite

/**
 * Outcome of validating a friend-request (invite) submission before any
 * Friend_Request is created (Requirement 1).
 *
 * The classification is total and mutually exclusive: a submission maps to
 * exactly one of these cases. Only [Ok] permits a Friend_Request to be created;
 * every other case rejects the request and identifies the reason so the caller
 * can surface the matching message (Requirements 1.2, 1.4, 1.5, 1.6, 1.7).
 */
sealed interface InviteValidation {
    /** The submission is valid and a Friend_Request may be created. */
    data object Ok : InviteValidation

    /** The normalized Preamble_ID is empty or whitespace-only (Requirement 1.2). */
    data object EmptyId : InviteValidation

    /** The submitter used their own Preamble_ID (Requirement 1.5). */
    data object SelfInvite : InviteValidation

    /** The Preamble_ID belongs to an existing friend (Requirement 1.6). */
    data object AlreadyFriends : InviteValidation

    /** A pending Friend_Request to the same target already exists (Requirement 1.7). */
    data object AlreadyPending : InviteValidation

    /** The Preamble_ID resolved to no user in the directory (Requirement 1.4). */
    data object NotFound : InviteValidation
}

/**
 * Pure gating logic that decides whether a friend-request submission is allowed
 * and, when allowed, constructs the Friend_Request (`WorkspaceInvite`).
 *
 * This object is free of Android and Firebase dependencies so the gating rules
 * can be exercised directly by JVM property-based tests. The Firestore gateway
 * (`WorkspaceRepository.sendInvite`) collects the inputs (normalizing through
 * [PreambleId], reading the friend set, the pending-invite set, and performing
 * the directory lookup), then delegates the decision to [validate] and the
 * Friend_Request construction to [buildInvite].
 *
 * Validation operates entirely on normalized Preamble_IDs so the same logic
 * applies to both manual entry and invite-link entry (Requirement 2.3).
 */
object InviteValidator {

    /**
     * Decides whether a friend-request to [submittedPreambleId] is permitted.
     *
     * All identifiers are normalized via [PreambleId.normalize] before
     * comparison, so callers may pass raw or already-normalized values.
     *
     * Rejection precedence follows the requirement order:
     * 1. empty / whitespace-only id → [InviteValidation.EmptyId] (1.2)
     * 2. submitter's own id → [InviteValidation.SelfInvite] (1.5)
     * 3. an existing friend → [InviteValidation.AlreadyFriends] (1.6)
     * 4. a duplicate pending request → [InviteValidation.AlreadyPending] (1.7)
     * 5. otherwise → [InviteValidation.Ok]
     *
     * Directory non-existence ([InviteValidation.NotFound], Requirement 1.4) is
     * decided by the gateway after this check passes, since it requires a
     * directory lookup that is outside this pure function's inputs.
     *
     * @param submittedPreambleId the Preamble_ID the user submitted.
     * @param ownPreambleId the submitter's own Preamble_ID.
     * @param friendPreambleIds Preamble_IDs of the submitter's existing friends.
     * @param pendingPreambleIds Preamble_IDs the submitter already has a pending
     *   outbound request to.
     */
    fun validate(
        submittedPreambleId: String,
        ownPreambleId: String,
        friendPreambleIds: Set<String>,
        pendingPreambleIds: Set<String>
    ): InviteValidation {
        val target = PreambleId.normalize(submittedPreambleId)
        if (target.isEmpty()) return InviteValidation.EmptyId

        if (target == PreambleId.normalize(ownPreambleId)) return InviteValidation.SelfInvite

        val normalizedFriends = friendPreambleIds.mapTo(HashSet()) { PreambleId.normalize(it) }
        if (target in normalizedFriends) return InviteValidation.AlreadyFriends

        val normalizedPending = pendingPreambleIds.mapTo(HashSet()) { PreambleId.normalize(it) }
        if (target in normalizedPending) return InviteValidation.AlreadyPending

        return InviteValidation.Ok
    }

    /**
     * Constructs the Friend_Request to be stored under the target user's account.
     *
     * The created invite carries the sender's user identifier, the sender's
     * display name, and the sender's normalized Preamble_ID (Requirement 1.8).
     * The invite is keyed by the sender's uid so duplicate-pending detection is
     * a deterministic existence check.
     *
     * @param senderUid the submitter's user identifier (must be non-blank).
     * @param targetUid the resolved target user's identifier (must be non-blank).
     * @param senderName the submitter's display name; blank is replaced with a default.
     * @param senderPreambleId the submitter's Preamble_ID, normalized before storage.
     */
    fun buildInvite(
        senderUid: String,
        targetUid: String,
        senderName: String,
        senderPreambleId: String
    ): WorkspaceInvite {
        require(senderUid.isNotBlank()) { "senderUid is required" }
        require(targetUid.isNotBlank()) { "targetUid is required" }
        return WorkspaceInvite(
            id = senderUid,
            senderUid = senderUid,
            targetUid = targetUid,
            senderName = senderName.trim().ifBlank { DEFAULT_NAME },
            senderPreambleId = PreambleId.normalize(senderPreambleId)
        )
    }

    private const val DEFAULT_NAME = "Preamble user"
}
