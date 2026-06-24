package com.theblankstate.preamble.planner

/**
 * Deterministic "now" provider for Track A planning (Req 13.1, 13.7).
 *
 * The current local date-time is **injected**, never globally captured, so the
 * resolution from a `LocalDateTime` to minute-of-day / date / day-of-week stays a
 * pure function: evaluating twice with the same injected value yields the same
 * planning context. Production uses the device clock + zone; tests inject a fixed value.
 *
 * This is pure Kotlin (`java.time` only) — no Android/Firebase/AI imports.
 */
fun interface PlanClock {
    /** The current local date-time used for planning. */
    fun now(): java.time.LocalDateTime

    companion object {
        /** Device clock + device zone (java.time). Pure given the same underlying clock value + zone (Req 13.7). */
        fun system(): PlanClock = PlanClock { java.time.LocalDateTime.now() }
    }
}
