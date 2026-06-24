package com.theblankstate.preamble.collab

/**
 * An Outgoing_Invite: a Friend_Request the signed-in user has sent to another
 * user, mirrored under the sender's account at
 * `/users/{senderUid}/outgoingInvites/{targetUid}` (social-hub-redesign,
 * Requirement 4.1).
 *
 * This model is intentionally free of Android, Firebase, and serialization
 * dependencies so the reconciliation logic that consumes it can be exercised
 * directly by JVM property-based tests. The default values let Firestore
 * deserialize the document into this class.
 *
 * @property targetUid the recipient's user identifier; the document id under the
 *   mirror subcollection.
 * @property targetPreambleId the recipient's normalized Preamble_ID, shown in the
 *   Outgoing_Invite (Requirement 4.2).
 * @property timestamp creation time, used for ordering.
 */
data class OutgoingInvite(
    val targetUid: String = "",
    val targetPreambleId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * Pure, side-effect-free derivation of the displayed Outgoing_Invite list from
 * the mirrored outgoing-invite set and the signed-in user's friend set
 * (social-hub-redesign, Requirements 4.1, 4.4, 5.2).
 *
 * The displayed Outgoing_Invites are exactly the mirrored invites whose target
 * uid is not yet in the friend set:
 *  - a not-yet-accepted invite (including a just-sent one for a non-friend
 *    target) is shown (Requirements 4.1, 5.2);
 *  - an invite whose target has become a friend drops out automatically, even
 *    before its mirror doc is pruned (Requirement 4.4).
 *
 * The result preserves the input order and never duplicates or fabricates an
 * invite: every returned element is an element of [mirrored], appearing the same
 * number of times it appears in the input.
 *
 * This object is free of Android and Firebase dependencies so it can be
 * validated with fast JVM/property-based tests (Property 3).
 */
object OutgoingInviteReconciler {

    /**
     * Returns the mirrored Outgoing_Invites whose [OutgoingInvite.targetUid] is
     * not present in [friendUids], preserving the order of [mirrored].
     *
     * @param mirrored the Outgoing_Invites mirrored under the sender's account.
     * @param friendUids the uids of the signed-in user's established friends.
     */
    fun visibleOutgoing(
        mirrored: List<OutgoingInvite>,
        friendUids: Set<String>,
    ): List<OutgoingInvite> =
        mirrored.filter { it.targetUid !in friendUids }
}
