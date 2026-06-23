package com.theblankstate.preamble.collab

/**
 * Pure logic for building and parsing friend invite links (Requirement 2).
 *
 * An invite link is a shareable URL of the form
 * `https://preamble.theblankstate.com/invite/{Preamble_ID}` that, when opened
 * in the app, pre-fills a friend request to the link owner. The embedded
 * Preamble_ID is always carried in its normalized (trimmed + uppercased) form.
 *
 * This object is deliberately free of Android (`android.net.Uri`), Firebase,
 * and serialization dependencies so the build/parse round-trip can be exercised
 * directly by JVM property-based tests (Property 2). The ViewModel adapts an
 * `android.net.Uri` to a string and delegates here.
 *
 * A **well-formed Preamble_ID** is one or more characters drawn from the
 * uppercase alphabet and digits (`[A-Z0-9]+`) after normalization, matching the
 * IDs produced by the account directory. Any link whose id segment is empty or
 * contains other characters is treated as malformed (Requirement 2.4).
 */
object InviteLink {

    /** Base URL of the Preamble web entry point. */
    const val BASE_URL: String = "https://preamble.theblankstate.com"

    /** The full prefix that precedes the embedded Preamble_ID in an invite link. */
    const val INVITE_PATH_PREFIX: String = "$BASE_URL/invite/"

    /** A normalized Preamble_ID is one or more uppercase-alphanumeric characters. */
    private val WELL_FORMED_ID = Regex("[A-Z0-9]+")

    /** Outcome of parsing a candidate invite-link string. */
    sealed interface ParseResult {
        /** The link carried a well-formed Preamble_ID, exposed here in normalized form. */
        data class Valid(val preambleId: String) : ParseResult

        /** The string was not a well-formed invite link (Requirement 2.4). */
        data object Invalid : ParseResult
    }

    /**
     * Builds an invite link carrying the normalized form of [rawPreambleId]
     * (Requirement 2.1).
     *
     * The id is normalized through [PreambleId.normalize] so the produced link
     * always embeds the trimmed, uppercased identifier regardless of how it was
     * typed.
     */
    fun build(rawPreambleId: String): String =
        INVITE_PATH_PREFIX + PreambleId.normalize(rawPreambleId)

    /**
     * Parses [candidate] and recovers the normalized Preamble_ID it carries.
     *
     * Returns [ParseResult.Valid] with the normalized id when [candidate] is an
     * invite link of the defined form whose id segment is well-formed
     * (Requirement 2.2). Returns [ParseResult.Invalid] for any string that is
     * not such a link — including links with an empty or malformed id segment
     * (Requirement 2.4). It never returns a usable id for a malformed link.
     */
    fun parse(candidate: String): ParseResult {
        val trimmed = candidate.trim()

        // The scheme and host are case-insensitive; the path is not. Match the
        // prefix case-insensitively, then treat the remainder as the raw segment.
        if (trimmed.length <= INVITE_PATH_PREFIX.length) return ParseResult.Invalid
        if (!trimmed.regionMatches(
                thisOffset = 0,
                other = INVITE_PATH_PREFIX,
                otherOffset = 0,
                length = INVITE_PATH_PREFIX.length,
                ignoreCase = true
            )
        ) {
            return ParseResult.Invalid
        }

        // Everything after the prefix is the id segment, up to the first path
        // separator, query, or fragment delimiter.
        val rawSegment = trimmed
            .substring(INVITE_PATH_PREFIX.length)
            .takeWhile { it != '/' && it != '?' && it != '#' }

        val normalized = PreambleId.normalize(rawSegment)
        return if (normalized.isNotEmpty() && WELL_FORMED_ID.matches(normalized)) {
            ParseResult.Valid(normalized)
        } else {
            ParseResult.Invalid
        }
    }
}
