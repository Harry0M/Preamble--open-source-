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

    enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

    private val _themeColor = MutableStateFlow<Color?>(null)
    val themeColor: StateFlow<Color?> = _themeColor.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _colorfulCards = MutableStateFlow(false)
    val colorfulCards: StateFlow<Boolean> = _colorfulCards.asStateFlow()

    private val _timelineUi = MutableStateFlow(true)
    val timelineUi: StateFlow<Boolean> = _timelineUi.asStateFlow()

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

}
