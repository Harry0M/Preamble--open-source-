package com.theblankstate.preamble.focus

import android.content.Context
import android.content.SharedPreferences

enum class TimerVariant(val label: String) {
    ALIVE_SHAPE("Material Alive Wavy")
}

enum class TimeExtensionAnimationVariant(val label: String) {
    PULSING_RIPPLE("Pulsing Ripple Wave")
}

object TimerPreferences {

    private const val PREF_NAME = "preamble_timer_prefs"
    private const val KEY_LAST_PRESET_MINUTES = "last_preset_minutes"
    private const val KEY_TIMER_VARIANT = "timer_variant"
    private const val KEY_TIME_EXTENSION_ANIMATION = "time_extension_animation"
    private const val DEFAULT_PRESET_MINUTES = 25

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getSelectedDurationMinutes(context: Context): Int {
        return getPrefs(context).getInt(KEY_LAST_PRESET_MINUTES, DEFAULT_PRESET_MINUTES)
    }

    fun setSelectedDurationMinutes(context: Context, minutes: Int) {
        getPrefs(context).edit().putInt(KEY_LAST_PRESET_MINUTES, minutes).apply()
    }

    fun getSelectedVariant(context: Context): TimerVariant {
        return TimerVariant.ALIVE_SHAPE
    }

    fun setSelectedVariant(context: Context, variant: TimerVariant) {
        getPrefs(context).edit().putString(KEY_TIMER_VARIANT, TimerVariant.ALIVE_SHAPE.name).apply()
    }

    fun getTimeExtensionAnimation(context: Context): TimeExtensionAnimationVariant {
        return TimeExtensionAnimationVariant.PULSING_RIPPLE
    }

    fun setTimeExtensionAnimation(context: Context, animation: TimeExtensionAnimationVariant) {
        getPrefs(context).edit().putString(KEY_TIME_EXTENSION_ANIMATION, TimeExtensionAnimationVariant.PULSING_RIPPLE.name).apply()
    }
}
