package com.theblankstate.preamble.collab

/**
 * Pure state holder governing how a deep-linked invite is presented at most once.
 *
 * When the app is opened from an `invite/{id}` deep link, the Social_Hub holds a
 * pending Preamble_ID that should be presented in the Invite_Entry_Experience
 * exactly once. Once the invite has been presented and consumed, re-rendering the
 * Social_Hub must not re-present the same invite (Requirement 7.3).
 *
 * This object is pure logic with no Android or Firebase dependencies so it can be
 * exercised directly by JVM property-based tests.
 */
object DeepLinkInviteState {

    /**
     * Returns the pending Preamble_ID to present, or `null` when there is nothing
     * to present.
     *
     * The host derives the value to present from its held [pending] state. After
     * the pending state has been cleared via [consume], this returns `null`.
     */
    fun toPresent(pending: String?): String? = pending

    /**
     * Consumes the [pending] invite, returning the new pending state.
     *
     * Consuming always clears the pending invite, so the result is always `null`.
     * Consuming again leaves it `null`, making consumption idempotent: once an
     * invite has been consumed, `toPresent(consume(pending))` is always `null`
     * (Requirement 7.3).
     */
    fun consume(pending: String?): String? = null
}
