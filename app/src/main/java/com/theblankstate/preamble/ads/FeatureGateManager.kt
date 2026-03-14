package com.theblankstate.preamble.ads

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object FeatureGateManager {

    private const val PREFS_NAME = "preamble_feature_gate"
    private const val KEY_THEME_UNLOCK_TS = "theme_unlock_ts"
    private const val KEY_STATS_UNLOCK_TS = "stats_unlock_ts"
    private const val UNLOCK_DURATION_MS = 12 * 60 * 60 * 1000L // 12 hours

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _themeUnlocked = MutableStateFlow(false)
    val themeUnlocked: StateFlow<Boolean> = _themeUnlocked.asStateFlow()

    private val _statsUnlocked = MutableStateFlow(false)
    val statsUnlocked: StateFlow<Boolean> = _statsUnlocked.asStateFlow()

    private var themeUnlockTs = 0L
    private var statsUnlockTs = 0L

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        themeUnlockTs = prefs.getLong(KEY_THEME_UNLOCK_TS, 0L)
        statsUnlockTs = prefs.getLong(KEY_STATS_UNLOCK_TS, 0L)
        refreshState()
        scope.launch {
            while (true) {
                delay(60_000)
                refreshState()
            }
        }
    }

    fun isThemeUnlocked(): Boolean =
        System.currentTimeMillis() < themeUnlockTs + UNLOCK_DURATION_MS

    fun isStatsUnlocked(): Boolean =
        System.currentTimeMillis() < statsUnlockTs + UNLOCK_DURATION_MS

    fun unlockTheme(context: Context) {
        themeUnlockTs = System.currentTimeMillis()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_THEME_UNLOCK_TS, themeUnlockTs).apply()
        _themeUnlocked.value = true
    }

    fun unlockStats(context: Context) {
        statsUnlockTs = System.currentTimeMillis()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_STATS_UNLOCK_TS, statsUnlockTs).apply()
        _statsUnlocked.value = true
    }

    fun themeRemainingMs(): Long {
        val remaining = (themeUnlockTs + UNLOCK_DURATION_MS) - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0
    }

    fun statsRemainingMs(): Long {
        val remaining = (statsUnlockTs + UNLOCK_DURATION_MS) - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0
    }

    fun formatRemaining(remainingMs: Long): String {
        if (remainingMs <= 0) return ""
        val hours = remainingMs / (60 * 60 * 1000)
        val minutes = (remainingMs % (60 * 60 * 1000)) / (60 * 1000)
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    private fun refreshState() {
        _themeUnlocked.value = isThemeUnlocked()
        _statsUnlocked.value = isStatsUnlocked()
    }
}
