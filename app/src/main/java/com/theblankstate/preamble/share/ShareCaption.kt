package com.theblankstate.preamble.share

import com.theblankstate.preamble.collab.InviteLink

/**
 * Pure logic for share-caption construction (Requirement 11).
 *
 * Every caption carries non-empty body text describing the shared moment, plus
 * a trailing invite-link line built from the sharer's normalized Preamble_ID —
 * but only when a Preamble_ID is present (Requirements 11.1, 11.2). When the id
 * is blank or null the body is still produced (so the image is still shareable)
 * with the link line omitted (Requirement 11.3). The embedded link always uses
 * the normalized form, delegated to [InviteLink.build].
 *
 * This object is deliberately free of Android and Firebase dependencies so the
 * caption rule can be exercised directly by JVM property-based tests (Property 4).
 */
object ShareCaption {

    private fun body(kind: ShareKind): String = when (kind) {
        ShareKind.WEEKLY_RECAP -> "Here's my week on Preamble \uD83D\uDCC5"
        ShareKind.STREAK_MILESTONE -> "I'm on a streak with Preamble \uD83D\uDD25"
        ShareKind.PERFECT_DAY -> "I had a perfect day on Preamble \u2705"
    }

    /**
     * Builds the share caption (Requirements 11.1–11.3).
     *
     * Returns the body text for [kind], plus a trailing line containing
     * `InviteLink.build(normalizedPreambleId)` when [normalizedPreambleId] is
     * non-blank; when it is blank or null, returns the body with no link line.
     */
    fun build(kind: ShareKind, normalizedPreambleId: String?): String {
        val body = body(kind)
        return if (normalizedPreambleId.isNullOrBlank()) {
            body
        } else {
            body + "\n\n" + InviteLink.build(normalizedPreambleId)
        }
    }
}
