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

    private val _themeColor = MutableStateFlow<Color?>(null)
    val themeColor: StateFlow<Color?> = _themeColor.asStateFlow()

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
}
