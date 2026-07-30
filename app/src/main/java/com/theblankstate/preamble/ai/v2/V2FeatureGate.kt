package com.theblankstate.preamble.ai.v2

import com.theblankstate.preamble.BuildConfig

/**
 * Feature gate for AI V2 — determines whether the current app version
 * should use the V2 AI system.
 *
 * The V2 system coexists with V1. Old app versions continue using V1;
 * new versions adopt V2. This gate allows a clean transition without
 * requiring both paths to be maintained simultaneously in the UI.
 *
 * The gate is based on app version code. Once the V2 backend is live,
 * set [V2_MIN_VERSION_CODE] to the first release that includes V2.
 * During development, [isV2Enabled] defaults to true so V2 UI is
 * accessible for testing.
 */
object V2FeatureGate {

    /**
     * Minimum app version code that activates AI V2.
     * Set to 0 during development (always enabled).
     * Set to actual release version code in production.
     */
    private const val V2_MIN_VERSION_CODE = 0

    /**
     * Returns true if the current app version should use AI V2.
     *
     * When [V2_MIN_VERSION_CODE] is 0, V2 is always enabled (dev mode).
     * Otherwise, the current build's VERSION_CODE must be ≥ the threshold.
     */
    val isV2Enabled: Boolean
        get() = V2_MIN_VERSION_CODE == 0 ||
                BuildConfig.VERSION_CODE >= V2_MIN_VERSION_CODE
}
