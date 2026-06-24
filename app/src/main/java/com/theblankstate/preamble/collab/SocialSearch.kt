package com.theblankstate.preamble.collab

/**
 * Pure, side-effect-free filtering for Social_Search (Feature: social-hub-redesign,
 * Requirement 9).
 *
 * The Social_Hub lets a user with a Large_Friend_Set locate a person by typing a
 * query that is matched against each entry's Preamble_ID and display name. The
 * matching logic lives here, isolated from the Compose/Firestore layers, so it can
 * be exercised directly by JVM property-based tests.
 *
 * [filter] is always called over the FULL in-memory Friends_List or
 * Friends_Leaderboard, before any paging window is applied, so that search covers
 * the entire set rather than only the entries already loaded through Paged_Loading
 * (Requirement 9.6).
 */
object SocialSearch {

    /**
     * Anything the Social_Hub can search. Exposes the two matchable fields — the
     * normalized [preambleId] and the human-readable [displayName] — against which
     * a Social_Search query is compared (Requirement 9.2, 9.3).
     */
    interface Searchable {
        val preambleId: String
        val displayName: String
    }

    /**
     * Returns the entries from [entries] whose [Searchable.preambleId] or
     * [Searchable.displayName] contains [query], compared case-insensitively
     * (Requirement 9.2, 9.3).
     *
     * A blank query (empty or whitespace-only) returns [entries] unchanged
     * (Requirement 9.4). The result preserves the input order and is always a
     * sublist of [entries] — no entry is fabricated or duplicated, and each
     * returned element appears no more times than it does in the input. Because
     * the comparison is in-memory over whatever list is supplied, callers pass the
     * full list so search is independent of any paging window (Requirement 9.6).
     *
     * @param query the user-entered Social_Search text.
     * @param entries the full list of searchable entries for the current area.
     */
    fun <T : Searchable> filter(query: String, entries: List<T>): List<T> {
        val needle = query.trim()
        if (needle.isEmpty()) return entries
        val lowered = needle.lowercase()
        return entries.filter { entry ->
            entry.preambleId.lowercase().contains(lowered) ||
                entry.displayName.lowercase().contains(lowered)
        }
    }
}
