package com.theblankstate.preamble.ads

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * FeatureGateManager — stub implementation.
 * All features are permanently unlocked. No ads, no premium gates.
 */
object FeatureGateManager {

    /** Always true — theme customisation is free for everyone. */
    private val _themeUnlocked = MutableStateFlow(true)
    val themeUnlocked: StateFlow<Boolean> = _themeUnlocked

    /** No-op. Was previously used to initialise AdMob SDK. */
    fun init(context: Context) {
        // Nothing to initialise — ads removed.
    }

    /** Always returns true — every feature is unlocked. */
    fun isThemeUnlocked(): Boolean = true
}
