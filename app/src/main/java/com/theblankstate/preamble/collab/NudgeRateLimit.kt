package com.theblankstate.preamble.collab

/**
 * Pure, side-effect-free nudge rate-limit arithmetic for the social-engagement feature.
 *
 * Feature: social-engagement. This object enforces the 60-minute rolling window that
 * governs how often a nudge may be sent for a given `(senderUid, targetUid, taskId)`
 * triple (Requirement 12):
 *
 *  - 12.1 / 12.3 : a nudge is allowed when none has ever been sent for the triple, or when
 *                  at least [WINDOW_MILLIS] (60 minutes) have elapsed since the last send.
 *  - 12.2        : while inside the window, [cooldownRemaining] reports the millis left
 *                  before the next nudge is allowed, driving the cooldown UI message.
 *  - 12.4        : the result depends only on the single supplied last-sent timestamp, so
 *                  each `(sender, target, task)` triple is rate-limited independently of
 *                  every other triple (per-triple independence).
 *
 * The functions are intentionally pure (no clock access, no I/O): the caller supplies
 * both `lastSentAt` (the stored timestamp for the triple, or null when never sent) and
 * `now`, so the logic is deterministic and property-testable.
 */
object NudgeRateLimit {

    /** Length of the rolling rate-limit window: 60 minutes, expressed in milliseconds. */
    const val WINDOW_MILLIS: Long = 60L * 60L * 1000L

    /**
     * True iff a nudge may be sent now for the triple whose last send was [lastSentAt].
     *
     * Returns true when [lastSentAt] is null (the triple has never been nudged — 12.1/12.3)
     * or when at least [WINDOW_MILLIS] have elapsed since [lastSentAt] (`now - lastSentAt >= WINDOW_MILLIS`).
     */
    fun canSend(lastSentAt: Long?, now: Long): Boolean =
        lastSentAt == null || (now - lastSentAt) >= WINDOW_MILLIS

    /**
     * Millis remaining before the next nudge is allowed for the triple (12.2).
     *
     * Returns 0 whenever [canSend] is true (including when [lastSentAt] is null); otherwise
     * returns the positive number of millis until 60 minutes have elapsed since [lastSentAt]
     * (`WINDOW_MILLIS - (now - lastSentAt)`).
     */
    fun cooldownRemaining(lastSentAt: Long?, now: Long): Long =
        if (canSend(lastSentAt, now)) 0L else WINDOW_MILLIS - (now - lastSentAt!!)
}
