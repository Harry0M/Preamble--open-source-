package com.theblankstate.preamble.collab

import net.jqwik.api.ForAll
import net.jqwik.api.Property
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * JVM test source set for the collaborative-tasks pure logic.
 *
 * This file establishes and documents the conventions every collaborative-tasks
 * test in this package must follow:
 *
 *  1. Property-based tests use jqwik and run on the JUnit Platform alongside
 *     JUnit 5 (Jupiter) example tests.
 *
 *  2. Each property test is tagged with a single-line comment of the form:
 *
 *         // Feature: collaborative-tasks, Property {n}: {property text}
 *
 *     where {n} is the property number from design.md and {property text} is the
 *     short property statement. Each property test implements exactly one of the
 *     18 correctness properties.
 *
 *  3. Property tests declare a minimum of 100 iterations via
 *     `@Property(tries = 100)` (or higher).
 *
 * The two tests below are the wiring sanity checks confirming the JUnit Platform,
 * Jupiter, and jqwik are all configured correctly. They are not part of the 18
 * correctness properties.
 */
class TestInfrastructureTest {

    @Test
    fun junitPlatform_runsJupiterTests() {
        // Confirms JUnit 5 (Jupiter) example tests execute on the platform.
        assertEquals(4, 2 + 2)
    }

    // Wiring check: confirms jqwik properties execute with the required minimum
    // of 100 tries. This mirrors the tagging/iteration convention real property
    // tests must follow, but is itself only an infrastructure sanity check.
    @Property(tries = 100)
    fun jqwik_runsPropertyTests(@ForAll value: Int) {
        // A trivially-true property over all generated integers.
        assertTrue(value == value)
    }
}
