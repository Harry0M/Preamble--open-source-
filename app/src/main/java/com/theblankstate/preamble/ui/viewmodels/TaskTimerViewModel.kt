package com.theblankstate.preamble.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.theblankstate.preamble.focus.FocusTimerService
import com.theblankstate.preamble.focus.FocusTimerState
import com.theblankstate.preamble.focus.TimeExtensionAnimationVariant
import com.theblankstate.preamble.focus.TimerPreferences
import com.theblankstate.preamble.focus.TimerVariant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TaskTimerViewModel(application: Application) : AndroidViewModel(application) {

    val timerState: StateFlow<FocusTimerState> = FocusTimerService.state

    private val _selectedPresetMinutes = MutableStateFlow(
        TimerPreferences.getSelectedDurationMinutes(application)
    )
    val selectedPresetMinutes: StateFlow<Int> = _selectedPresetMinutes.asStateFlow()

    private val _timerVariant = MutableStateFlow(
        TimerPreferences.getSelectedVariant(application)
    )
    val timerVariant: StateFlow<TimerVariant> = _timerVariant.asStateFlow()

    private val _timeExtensionAnimation = MutableStateFlow(
        TimerPreferences.getTimeExtensionAnimation(application)
    )
    val timeExtensionAnimation: StateFlow<TimeExtensionAnimationVariant> = _timeExtensionAnimation.asStateFlow()

    private val _showCustomDurationSheet = MutableStateFlow(false)
    val showCustomDurationSheet: StateFlow<Boolean> = _showCustomDurationSheet.asStateFlow()

    fun selectPreset(minutes: Int) {
        _selectedPresetMinutes.value = minutes
        TimerPreferences.setSelectedDurationMinutes(getApplication(), minutes)
    }

    fun setTimerVariant(variant: TimerVariant) {
        _timerVariant.value = variant
        TimerPreferences.setSelectedVariant(getApplication(), variant)
    }

    fun setTimeExtensionAnimation(animation: TimeExtensionAnimationVariant) {
        _timeExtensionAnimation.value = animation
        TimerPreferences.setTimeExtensionAnimation(getApplication(), animation)
    }

    fun openCustomDurationSheet() {
        _showCustomDurationSheet.value = true
    }

    fun closeCustomDurationSheet() {
        _showCustomDurationSheet.value = false
    }

    fun setCustomDurationMinutes(minutes: Int) {
        val validMinutes = minutes.coerceIn(1, 180)
        selectPreset(validMinutes)
        closeCustomDurationSheet()
    }

    fun addTimeSeconds(extraSeconds: Int) {
        FocusTimerService.addTime(getApplication(), extraSeconds)
    }

    fun startTimer(taskId: String? = null, taskTitle: String? = null) {
        val durationSeconds = selectedPresetMinutes.value * 60
        FocusTimerService.start(getApplication(), taskId, taskTitle, durationSeconds)
    }

    fun pauseTimer() {
        FocusTimerService.pause(getApplication())
    }

    fun resumeTimer() {
        FocusTimerService.resume(getApplication())
    }

    fun stopTimer() {
        FocusTimerService.stop(getApplication())
    }

    fun finishTimer() {
        FocusTimerService.finish(getApplication())
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TaskTimerViewModel(application) as T
        }
    }
}
