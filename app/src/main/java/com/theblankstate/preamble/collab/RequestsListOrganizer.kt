package com.theblankstate.preamble.collab

import com.theblankstate.preamble.repository.WorkspaceInvite

/**
 * Pure organizer for the Requests_List surface (Feature: social-hub-redesign).
 *
 * Partitions the signed-in user's invites into two clearly-separated groups —
 * the Outgoing_Invites the user has sent and the Incoming_Invites the user has
 * received — so the Requests_List can render outgoing invites separately from
 * incoming invites (Requirement 5.3) and detect when there are no pending
 * requests of either kind (Requirement 5.5).
 *
 * This object is free of Android and Firebase dependencies so the grouping can
 * be exercised directly by JVM property-based tests. The two input kinds are
 * already distinct types ([OutgoingInvite] vs [WorkspaceInvite]), so grouping is
 * a structure-preserving split: every input invite lands in exactly one group,
 * in its original order, with no loss, duplication, or cross-grouping.
 */
object RequestsListOrganizer {

    /**
     * The grouped view model rendered by the Requests_List.
     *
     * [outgoing] holds exactly the supplied Outgoing_Invites and [incoming] holds
     * exactly the supplied Incoming_Invites, each preserving input order. The two
     * groups never share an entry (the types are disjoint), satisfying the
     * "outgoing separated from incoming" arrangement (Requirement 5.3).
     */
    data class Sections(
        val outgoing: List<OutgoingInvite>,
        val incoming: List<WorkspaceInvite>,
    ) {
        /**
         * True when there are no Outgoing_Invites and no Incoming_Invites, so the
         * Requests_List shows its empty-state (Requirement 5.5).
         */
        val isEmpty: Boolean get() = outgoing.isEmpty() && incoming.isEmpty()
    }

    /**
     * Partitions [outgoing] and [incoming] into a [Sections] grouping.
     *
     * The result groups outgoing invites separately from incoming invites with no
     * loss, duplication, or cross-grouping: the outgoing group is exactly
     * [outgoing] and the incoming group is exactly [incoming], each as an
     * order-preserving copy (Requirement 5.3). When both inputs are empty the
     * resulting [Sections.isEmpty] is true (Requirement 5.5).
     */
    fun organize(
        outgoing: List<OutgoingInvite>,
        incoming: List<WorkspaceInvite>,
    ): Sections = Sections(
        outgoing = outgoing.toList(),
        incoming = incoming.toList(),
    )
}
