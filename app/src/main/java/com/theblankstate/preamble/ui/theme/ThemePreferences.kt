package com.theblankstate.preamble.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ThemePreferences {
    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_COLOR = "primary_color"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_COLORFUL_CARDS = "colorful_cards"
    private const val KEY_TIMELINE_UI = "timeline_ui"
    private const val KEY_SHOW_RECURRENCE_LABEL = "show_recurrence_label"

    // Personal Mode keys
    private const val KEY_PERSONAL_MODE       = "pm_enabled"
    private const val KEY_PM_GREETING         = "pm_greeting"
    private const val KEY_PM_SMART_PROGRESS   = "pm_smart_progress"
    private const val KEY_PM_LATE_NIGHT       = "pm_late_night"
    private const val KEY_PM_SMART_EMPTY      = "pm_smart_empty"
    private const val KEY_PM_LAST_TASK        = "pm_last_task"
    private const val KEY_PM_STREAK_WARN      = "pm_streak_warn"
    private const val KEY_PM_BESTS            = "pm_bests"
    private const val KEY_PM_MILESTONES       = "pm_milestones"
    private const val KEY_PM_SPARKLE          = "pm_sparkle"
    private const val KEY_PM_EASTER_EGG       = "pm_easter_egg"
    private const val KEY_PM_ENDOWED          = "pm_endowed"
    private const val KEY_PM_VARIABLE         = "pm_variable"
    private const val KEY_PM_HEATMAP          = "pm_heatmap"

    // Expressive Navigation
    private const val KEY_EXPRESSIVE_NAV      = "expressive_nav"

    enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

    private val _themeColor = MutableStateFlow<Color?>(null)
    val themeColor: StateFlow<Color?> = _themeColor.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _colorfulCards = MutableStateFlow(false)
    val colorfulCards: StateFlow<Boolean> = _colorfulCards.asStateFlow()

    private val _timelineUi = MutableStateFlow(true)
    val timelineUi: StateFlow<Boolean> = _timelineUi.asStateFlow()

    private val _showRecurrenceLabel = MutableStateFlow(false)
    val showRecurrenceLabel: StateFlow<Boolean> = _showRecurrenceLabel.asStateFlow()

    // Personal Mode flows
    private val _personalMode     = MutableStateFlow(false)
    val personalMode: StateFlow<Boolean> = _personalMode.asStateFlow()

    private val _pmGreeting       = MutableStateFlow(true)
    val pmGreeting: StateFlow<Boolean> = _pmGreeting.asStateFlow()

    private val _pmSmartProgress  = MutableStateFlow(true)
    val pmSmartProgress: StateFlow<Boolean> = _pmSmartProgress.asStateFlow()

    private val _pmLateNight      = MutableStateFlow(true)
    val pmLateNight: StateFlow<Boolean> = _pmLateNight.asStateFlow()

    private val _pmSmartEmpty     = MutableStateFlow(true)
    val pmSmartEmpty: StateFlow<Boolean> = _pmSmartEmpty.asStateFlow()

    private val _pmLastTask       = MutableStateFlow(true)
    val pmLastTask: StateFlow<Boolean> = _pmLastTask.asStateFlow()

    private val _pmStreakWarn     = MutableStateFlow(true)
    val pmStreakWarn: StateFlow<Boolean> = _pmStreakWarn.asStateFlow()

    private val _pmBests          = MutableStateFlow(true)
    val pmBests: StateFlow<Boolean> = _pmBests.asStateFlow()

    private val _pmMilestones     = MutableStateFlow(true)
    val pmMilestones: StateFlow<Boolean> = _pmMilestones.asStateFlow()

    private val _pmSparkle        = MutableStateFlow(true)
    val pmSparkle: StateFlow<Boolean> = _pmSparkle.asStateFlow()

    private val _pmEasterEgg      = MutableStateFlow(true)
    val pmEasterEgg: StateFlow<Boolean> = _pmEasterEgg.asStateFlow()

    private val _pmEndowedProgress = MutableStateFlow(true)
    val pmEndowedProgress: StateFlow<Boolean> = _pmEndowedProgress.asStateFlow()

    private val _pmVariableRewards = MutableStateFlow(true)
    val pmVariableRewards: StateFlow<Boolean> = _pmVariableRewards.asStateFlow()

    private val _pmYearHeatmap    = MutableStateFlow(true)
    val pmYearHeatmap: StateFlow<Boolean> = _pmYearHeatmap.asStateFlow()

    // Expressive Navigation
    private val _expressiveNav    = MutableStateFlow(false)
    val expressiveNav: StateFlow<Boolean> = _expressiveNav.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val colorHex = prefs.getString(KEY_COLOR, null)
        if (colorHex != null) {
            try {
                _themeColor.value = Color(android.graphics.Color.parseColor(colorHex))
            } catch (e: Exception) {
                _themeColor.value = null
            }
        }
        _themeMode.value = ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        _colorfulCards.value = prefs.getBoolean(KEY_COLORFUL_CARDS, false)
        _timelineUi.value = prefs.getBoolean(KEY_TIMELINE_UI, true)
        _showRecurrenceLabel.value = prefs.getBoolean(KEY_SHOW_RECURRENCE_LABEL, false)

        // Personal Mode
        _personalMode.value    = prefs.getBoolean(KEY_PERSONAL_MODE, false)
        _pmGreeting.value      = prefs.getBoolean(KEY_PM_GREETING, true)
        _pmSmartProgress.value = prefs.getBoolean(KEY_PM_SMART_PROGRESS, true)
        _pmLateNight.value     = prefs.getBoolean(KEY_PM_LATE_NIGHT, true)
        _pmSmartEmpty.value    = prefs.getBoolean(KEY_PM_SMART_EMPTY, true)
        _pmLastTask.value      = prefs.getBoolean(KEY_PM_LAST_TASK, true)
        _pmStreakWarn.value    = prefs.getBoolean(KEY_PM_STREAK_WARN, true)
        _pmBests.value         = prefs.getBoolean(KEY_PM_BESTS, true)
        _pmMilestones.value    = prefs.getBoolean(KEY_PM_MILESTONES, true)
        _pmSparkle.value       = prefs.getBoolean(KEY_PM_SPARKLE, true)
        _pmEasterEgg.value     = prefs.getBoolean(KEY_PM_EASTER_EGG, true)
        _pmEndowedProgress.value= prefs.getBoolean(KEY_PM_ENDOWED, true)
        _pmVariableRewards.value= prefs.getBoolean(KEY_PM_VARIABLE, true)
        _pmYearHeatmap.value   = prefs.getBoolean(KEY_PM_HEATMAP, true)
        _expressiveNav.value   = prefs.getBoolean(KEY_EXPRESSIVE_NAV, false)
    }

    fun setColor(context: Context, color: Color?) {
        _themeColor.value = color
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (color == null) {
            prefs.edit().remove(KEY_COLOR).apply()
        } else {
            val argb = color.toArgb()
            val hexString = String.format("#%06X", 0xFFFFFF and argb)
            prefs.edit().putString(KEY_COLOR, hexString).apply()
        }
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        _themeMode.value = mode
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, mode.name)
            .apply()
    }

    fun setColorfulCards(context: Context, enabled: Boolean) {
        _colorfulCards.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_COLORFUL_CARDS, enabled)
            .apply()
    }

    fun setTimelineUi(context: Context, enabled: Boolean) {
        _timelineUi.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_TIMELINE_UI, enabled)
            .apply()
    }

    fun setShowRecurrenceLabel(context: Context, enabled: Boolean) {
        _showRecurrenceLabel.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOW_RECURRENCE_LABEL, enabled)
            .apply()
    }

    // Personal Mode setters
    private fun setBool(context: Context, key: String, flow: MutableStateFlow<Boolean>, value: Boolean) {
        flow.value = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(key, value).apply()
    }

    fun setPersonalMode(context: Context, v: Boolean)    = setBool(context, KEY_PERSONAL_MODE, _personalMode, v)
    fun setPmGreeting(context: Context, v: Boolean)      = setBool(context, KEY_PM_GREETING, _pmGreeting, v)
    fun setPmSmartProgress(context: Context, v: Boolean) = setBool(context, KEY_PM_SMART_PROGRESS, _pmSmartProgress, v)
    fun setPmLateNight(context: Context, v: Boolean)     = setBool(context, KEY_PM_LATE_NIGHT, _pmLateNight, v)
    fun setPmSmartEmpty(context: Context, v: Boolean)    = setBool(context, KEY_PM_SMART_EMPTY, _pmSmartEmpty, v)
    fun setPmLastTask(context: Context, v: Boolean)      = setBool(context, KEY_PM_LAST_TASK, _pmLastTask, v)
    fun setPmStreakWarn(context: Context, v: Boolean)    = setBool(context, KEY_PM_STREAK_WARN, _pmStreakWarn, v)
    fun setPmBests(context: Context, v: Boolean)         = setBool(context, KEY_PM_BESTS, _pmBests, v)
    fun setPmMilestones(context: Context, v: Boolean)    = setBool(context, KEY_PM_MILESTONES, _pmMilestones, v)
    fun setPmSparkle(context: Context, v: Boolean)       = setBool(context, KEY_PM_SPARKLE, _pmSparkle, v)
    fun setPmEasterEgg(context: Context, v: Boolean)     = setBool(context, KEY_PM_EASTER_EGG, _pmEasterEgg, v)
    fun setPmEndowedProgress(context: Context, v: Boolean) = setBool(context, KEY_PM_ENDOWED, _pmEndowedProgress, v)
    fun setPmVariableRewards(context: Context, v: Boolean) = setBool(context, KEY_PM_VARIABLE, _pmVariableRewards, v)
    fun setPmYearHeatmap(context: Context, v: Boolean)   = setBool(context, KEY_PM_HEATMAP, _pmYearHeatmap, v)
    fun setExpressiveNav(context: Context, v: Boolean)   = setBool(context, KEY_EXPRESSIVE_NAV, _expressiveNav, v)

}
