package com.theblankstate.preamble.collab

// collab/RecipientResolution.kt — pure, no Android/Firestore
object RecipientResolution {
    /** Assignee maximum after Circle expansion + dedupe (Req 29.1; = CollaborativeDocument.MAX_ASSIGNEES). */
    const val MAX_ASSIGNEES = 50

    /**
     * The deduped Assignee uid set a selection would produce, plus a cap classification.
     * [assigneeUids] order is deterministic: selected friends first (in selection order),
     * then circle members in selection order, each uid appearing exactly once (Req 28.3, 28.4).
     */
    data class Resolved(
        val assigneeUids: List<String>,   // deduped, sender excluded (Req 28.2-28.5)
        val withinLimit: Boolean,         // size <= MAX_ASSIGNEES (Req 29.1, 29.4)
    ) {
        val size: Int get() = assigneeUids.size
    }

    /**
     * Expand every selected Circle into its current Circle_Members, union with the selected
     * friend uids, drop the sender, and dedupe — preserving a stable order (Req 28.2-28.5).
     * Pure: callers supply each selected circle's member uid list (read from Circle.memberUids).
     *
     * @param selectedFriendUids individually selected friends (uids).
     * @param selectedCircleMemberUids member-uid lists for each selected Circle, in selection order.
     * @param senderUid the sending user's uid, always excluded from the result (Req 28.5).
     */
    fun resolve(
        selectedFriendUids: List<String>,
        selectedCircleMemberUids: List<List<String>>,
        senderUid: String,
    ): Resolved {
        val ordered = LinkedHashSet<String>()        // insertion-ordered dedupe (28.3, 28.4)
        ordered.addAll(selectedFriendUids)
        selectedCircleMemberUids.forEach { ordered.addAll(it) }
        ordered.remove(senderUid)                     // sender is Admin+Member, never Assignee (28.5)
        val uids = ordered.toList()
        return Resolved(assigneeUids = uids, withinLimit = uids.size <= MAX_ASSIGNEES)
    }
}
