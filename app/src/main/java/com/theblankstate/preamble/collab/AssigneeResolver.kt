package com.theblankstate.preamble.collab

import com.theblankstate.preamble.repository.Friend

/**
 * Outcome of resolving intended assignees from a natural-language task input
 * against the current friend set (Requirement 9).
 *
 * The classification is total: every input maps to exactly one of these cases.
 */
sealed interface AssigneeResolution {
    /** One or more intended assignees, each uniquely matching exactly one friend (Requirement 9.3). */
    data class Assigned(val friends: List<Friend>) : AssigneeResolution

    /** No assignment was intended in the input (Requirement 9.5). */
    data object NoAssignee : AssigneeResolution

    /** An intended assignee matched no existing friend (Requirement 9.6). */
    data object Unmatched : AssigneeResolution

    /** An intended term matched more than one friend; carries the offending term (Requirement 9.7). */
    data class Ambiguous(val term: String) : AssigneeResolution

    /** Resolution failed or timed out (Requirement 9.8). */
    data object Failed : AssigneeResolution
}

/**
 * Detects intended assignees from natural-language task input and maps them to
 * friends, running separately from task creation (Assignee_Resolver, Requirement 9).
 *
 * Implementations run client-side and MUST NOT depend on the removed
 * `aiResolveAssignees` Cloud Function (Requirement 9.9).
 */
interface AssigneeResolver {
    suspend fun resolve(rawText: String, friends: List<Friend>): AssigneeResolution
}

/**
 * Pure, deterministic classification logic for the Assignee_Resolver.
 *
 * This object contains no Android, Firebase, or coroutine dependencies so it can
 * be exercised directly by JVM property-based tests. The wiring task layers the
 * 30 s timeout and the `assignTaskToMultiple` trigger on top of [classify].
 */
object AssigneeMatching {

    /**
     * Phrases that signal assignment intent. The text that follows one of these
     * markers is treated as the intended-assignee clause. Longer markers are
     * listed first so the greedy scan prefers the most specific phrasing
     * (e.g. "assign this to" before "assign to" before "assign").
     */
    private val INTENT_MARKERS: List<String> = listOf(
        "assign this to",
        "assign it to",
        "assign that to",
        "assign to",
        "assigned to",
        "share this with",
        "share it with",
        "share with",
        "send this to",
        "send it to",
        "send to",
        "delegate this to",
        "delegate to",
        "give this to",
        "give it to",
        "give to",
        "hand this to",
        "hand to",
        "for my friend",
        "assign"
    )

    /**
     * Connectors that separate multiple intended assignees within a single
     * clause, e.g. "assign to Alice and Bob, Carol".
     */
    private val CONNECTORS: List<String> = listOf(" and ", " & ", ",", "&")

    /**
     * Tokens that terminate an assignee term. Once one of these is reached the
     * remaining words are considered task content rather than part of a name.
     */
    private val STOP_WORDS: Set<String> = setOf(
        "the", "a", "an", "task", "to", "about", "for", "regarding", "re",
        "tomorrow", "today", "tonight", "please", "this", "it", "that",
        "with", "on", "by", "at", "before", "after", "and", "so", "then"
    )

    /**
     * Classifies the intended assignees in [rawText] against [friends].
     *
     * Algorithm:
     * 1. Detect an assignment-intent marker. If none is present, no assignment
     *    is intended (Requirement 9.5 → [AssigneeResolution.NoAssignee]).
     * 2. Extract the intended terms that follow the marker.
     * 3. For each term, count the friends it matches by normalized name or
     *    normalized Preamble ID.
     * 4. Classify:
     *    - Any term matching more than one friend → [AssigneeResolution.Ambiguous]
     *      (the first such term, in reading order; Requirement 9.7).
     *    - Otherwise any term matching no friend → [AssigneeResolution.Unmatched]
     *      (Requirement 9.6).
     *    - Otherwise every term matched exactly one friend →
     *      [AssigneeResolution.Assigned] with the distinct matched friends
     *      in reading order (Requirement 9.3).
     *
     * This function never throws and never returns [AssigneeResolution.Failed];
     * that case is reserved for genuine errors/timeouts at the wiring layer.
     */
    fun classify(rawText: String, friends: List<Friend>): AssigneeResolution {
        val terms = extractIntendedTerms(rawText)
        if (terms.isEmpty()) return AssigneeResolution.NoAssignee

        val assigned = LinkedHashMap<String, Friend>()
        var firstAmbiguousTerm: String? = null
        var sawUnmatched = false

        for (term in terms) {
            val matches = friends.filter { matchesFriend(term, it) }
            when {
                matches.size > 1 -> {
                    if (firstAmbiguousTerm == null) firstAmbiguousTerm = term
                }
                matches.isEmpty() -> sawUnmatched = true
                else -> {
                    val friend = matches.single()
                    // Dedup by uid so "assign to Alice and Alice" yields one friend.
                    assigned.putIfAbsent(friend.uid, friend)
                }
            }
        }

        return when {
            firstAmbiguousTerm != null -> AssigneeResolution.Ambiguous(firstAmbiguousTerm)
            sawUnmatched -> AssigneeResolution.Unmatched
            else -> AssigneeResolution.Assigned(assigned.values.toList())
        }
    }

    /**
     * Extracts the intended-assignee terms from [rawText] by locating the first
     * assignment-intent marker and parsing the clause that follows it. Returns
     * an empty list when no assignment intent is detected.
     */
    private fun extractIntendedTerms(rawText: String): List<String> {
        val lower = rawText.lowercase()
        val marker = INTENT_MARKERS
            .map { it to lower.indexOf(it) }
            .filter { it.second >= 0 }
            // Prefer the earliest marker; break ties toward the longer (more specific) phrase.
            .minWithOrNull(compareBy({ it.second }, { -it.first.length }))
            ?: return emptyList()

        val clauseStart = marker.second + marker.first.length
        val clause = rawText.substring(clauseStart)
        return parseClause(clause)
    }

    /**
     * Splits an assignee clause into individual terms on connectors, then trims
     * each segment down to its leading name span (stopping at the first stop
     * word or non-name token).
     */
    private fun parseClause(clause: String): List<String> {
        val segments = splitOnConnectors(clause)
        val terms = ArrayList<String>()
        for (segment in segments) {
            val term = leadingNameSpan(segment)
            if (term.isNotEmpty()) terms.add(term)
        }
        return terms
    }

    /** Splits [clause] into segments on any configured connector. */
    private fun splitOnConnectors(clause: String): List<String> {
        var segments = listOf(clause)
        for (connector in CONNECTORS) {
            segments = segments.flatMap { it.split(connector, ignoreCase = true) }
        }
        return segments
    }

    /**
     * Returns the leading name span of [segment]: the consecutive word tokens at
     * the start of the segment, stopping at the first stop word or token that is
     * neither a name word nor a Preamble-ID-like token. Surrounding punctuation
     * is stripped from each token.
     */
    private fun leadingNameSpan(segment: String): String {
        val tokens = segment.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val span = ArrayList<String>()
        for (token in tokens) {
            val cleaned = token.trim { !it.isLetterOrDigit() }
            if (cleaned.isEmpty()) {
                if (span.isEmpty()) continue else break
            }
            if (cleaned.lowercase() in STOP_WORDS) {
                if (span.isEmpty()) continue else break
            }
            if (!isNameOrIdToken(cleaned)) break
            span.add(cleaned)
        }
        return span.joinToString(" ").trim()
    }

    /** A token is name/ID-like when every character is a letter or digit. */
    private fun isNameOrIdToken(token: String): Boolean = token.all { it.isLetterOrDigit() }

    /**
     * Returns `true` when [term] resolves to [friend] by either a normalized
     * name match or a normalized Preamble ID match.
     *
     * Name matching is whole-name or any whole-token match (so "Alex" matches
     * a friend named "Alex Johnson"), which is what makes two friends sharing a
     * first name resolve as ambiguous (Requirement 9.7).
     */
    private fun matchesFriend(term: String, friend: Friend): Boolean {
        val normalizedTerm = normalizeName(term)
        if (normalizedTerm.isEmpty()) return false

        // Preamble ID match (case-insensitive, whitespace-trimmed).
        if (friend.preambleId.isNotBlank() &&
            PreambleId.normalize(friend.preambleId) == PreambleId.normalize(term)
        ) {
            return true
        }

        val normalizedName = normalizeName(friend.name)
        if (normalizedName.isEmpty()) return false
        if (normalizedName == normalizedTerm) return true

        // Whole-token match: the term equals one of the name's tokens.
        val nameTokens = normalizedName.split(" ").filter { it.isNotEmpty() }.toSet()
        return normalizedTerm in nameTokens
    }

    /** Lowercases, trims, and collapses internal whitespace for name comparison. */
    private fun normalizeName(value: String): String =
        value.trim().lowercase().replace(Regex("\\s+"), " ")
}

/**
 * Default [AssigneeResolver] that performs deterministic client-side matching via
 * [AssigneeMatching.classify]. It runs entirely on-device and does not call the
 * removed `aiResolveAssignees` Cloud Function (Requirement 9.9).
 */
class DefaultAssigneeResolver : AssigneeResolver {
    override suspend fun resolve(rawText: String, friends: List<Friend>): AssigneeResolution =
        AssigneeMatching.classify(rawText, friends)
}
