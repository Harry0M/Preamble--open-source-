package com.theblankstate.preamble.collab

/**
 * Single source of truth for normalizing and validating Preamble IDs.
 *
 * A Preamble ID is a short, public identifier that maps to exactly one user
 * account. Normalization removes all leading and trailing whitespace and
 * converts every alphabetic character to uppercase, so that the same logical
 * ID always maps to the same directory key regardless of how it was typed
 * (Requirement 1.1).
 *
 * This object is pure logic with no Android or Firebase dependencies so it can
 * be exercised directly by JVM property-based tests.
 */
object PreambleId {

    /**
     * Normalizes [raw] by trimming surrounding whitespace and uppercasing all
     * alphabetic characters.
     *
     * Normalization is idempotent: `normalize(normalize(x)) == normalize(x)`.
     */
    fun normalize(raw: String): String = raw.trim().uppercase()

    /**
     * Returns `true` when [raw] is empty or contains only whitespace, i.e. its
     * normalized form is the empty string (Requirement 1.2).
     */
    fun isBlank(raw: String): Boolean = normalize(raw).isEmpty()
}
