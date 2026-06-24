package com.theblankstate.preamble.collab

/**
 * Pure, side-effect-free leaderboard logic for the social-engagement feature.
 *
 * Feature: social-engagement. This object is the single source of truth for two
 * Leaderboard_Service computations described in design.md and the requirements:
 *
 *  - [award]   : grants exactly [COMPLETION_AWARD] points the first time a task is
 *                completed by a member, idempotent on the task id (Req 7.1, 7.2, 7.3, 7.4)
 *                and monotonically non-decreasing in exactly the Completion_Award (Req 8.2, 8.3).
 *  - [ranking] : builds the Friends_Leaderboard for exactly self + friend uids, scored by
 *                the current Weekly_Window's points and ordered descending (Req 9.1, 9.2, 9.3, 9.4).
 *
 * The functions are intentionally pure (no clock access, no I/O): the caller supplies
 * `now` so weekly bucketing — delegated to [WeeklyWindow.weekKey] — is deterministic and
 * property-testable.
 */
object Leaderboard {

    /** The points granted on the first-ever completion of a task (Req 7.1 / glossary). */
    const val COMPLETION_AWARD: Int = 10

    /**
     * The authoritative score document stored at `/leaderboard/{uid}`.
     *
     * @property uid the owner's user id.
     * @property totalPoints monotonic, increment-only by [COMPLETION_AWARD]; always equals
     *   `COMPLETION_AWARD * awardedTasks.size` (Req 8.2, 8.3).
     * @property weeklyPoints map of [WeeklyWindow.weekKey] to points earned in that window (Req 9).
     * @property awardedTasks the set of task ids already awarded; the at-most-once idempotency
     *   key (Req 7.2).
     */
    data class ScoreDoc(
        val uid: String,
        val totalPoints: Int = 0,
        val weeklyPoints: Map<String, Int> = emptyMap(),
        val awardedTasks: Set<String> = emptySet()
    )

    /**
     * A single row of the Friends_Leaderboard: a participant and their current-window points.
     *
     * Implements [SocialSearch.Searchable] so a leaderboard row can be filtered by the
     * Social_Search control consistently with the Friends_List, matching against both the
     * [preambleId] and the display name (Req 9.3). [displayName] aliases [name] to satisfy
     * the interface without duplicating data.
     *
     * @property preambleId the participant's Preamble_ID, populated from the per-uid directory
     *   map supplied to [ranking]; defaults to "" so existing callers keep compiling and rows
     *   for uids absent from the directory remain non-matching by Preamble_ID.
     */
    data class Entry(
        val uid: String,
        val name: String,
        val weeklyPoints: Int,
        override val preambleId: String = ""
    ) : SocialSearch.Searchable {
        override val displayName: String get() = name
    }

    /**
     * Awards [COMPLETION_AWARD] for [taskId] iff it has not been awarded before (Req 7.1, 7.2).
     *
     * On award: `totalPoints += COMPLETION_AWARD`; the current window bucket
     * (`weeklyPoints[WeeklyWindow.weekKey(now)]`) is incremented by [COMPLETION_AWARD] (Req 7.4);
     * and [taskId] is added to `awardedTasks`.
     *
     * If [taskId] is already in `awardedTasks`, the document is returned byte-for-byte unchanged,
     * so a re-completion (or a retried award) grants nothing and never decrements (Req 7.2, 7.3).
     */
    fun award(doc: ScoreDoc, taskId: String, now: Long): ScoreDoc {
        if (taskId in doc.awardedTasks) return doc

        val weekKey = WeeklyWindow.weekKey(now)
        val updatedWeekly = doc.weeklyPoints.toMutableMap()
        updatedWeekly[weekKey] = (updatedWeekly[weekKey] ?: 0) + COMPLETION_AWARD

        return doc.copy(
            totalPoints = doc.totalPoints + COMPLETION_AWARD,
            weeklyPoints = updatedWeekly,
            awardedTasks = doc.awardedTasks + taskId
        )
    }

    /**
     * Builds the Friends_Leaderboard for [selfUid] + [friendUids] only (Req 9.1, 9.3).
     *
     * Each entry is scored by its score document's current-window points
     * (`weeklyPoints[WeeklyWindow.weekKey(now)]`), defaulting to 0 when the uid has no score
     * document or no points in the current window (pre-window points are excluded, Req 9.4).
     * Entries are ordered by points descending (Req 9.2). Non-friend uids present in [scores],
     * [names], or [preambleIds] are never included.
     *
     * Each entry's `preambleId` is populated from [preambleIds] (sourced from the same friend
     * directory the caller already holds) so leaderboard rows satisfy [SocialSearch.Searchable]
     * and can be filtered by Preamble_ID consistently with the Friends_List (Req 9.3). A uid
     * absent from [preambleIds] gets the default "". [preambleIds] defaults to empty so existing
     * callers keep compiling. Neither the directory nor the new field affects ordering or points.
     */
    fun ranking(
        selfUid: String,
        friendUids: Set<String>,
        scores: Map<String, ScoreDoc>,
        names: Map<String, String>,
        now: Long,
        preambleIds: Map<String, String> = emptyMap()
    ): List<Entry> {
        val weekKey = WeeklyWindow.weekKey(now)
        val participants = LinkedHashSet<String>().apply {
            add(selfUid)
            addAll(friendUids)
        }
        return participants
            .map { uid ->
                val points = scores[uid]?.weeklyPoints?.get(weekKey) ?: 0
                Entry(
                    uid = uid,
                    name = names[uid] ?: uid,
                    weeklyPoints = points,
                    preambleId = preambleIds[uid] ?: ""
                )
            }
            .sortedByDescending { it.weeklyPoints }
    }
}
