package com.theblankstate.preamble.pomodoro

data class PomodoroState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val currentPhase: PomodoroPhase = PomodoroPhase.WORK,
    val remainingSeconds: Int = PomodoroDefaults.WORK_MINUTES * 60,
    val totalSeconds: Int = PomodoroDefaults.WORK_MINUTES * 60,
    val sessionsCompleted: Int = 0,
    val taskId: String? = null,
    val taskTitle: String? = null
)

enum class PomodoroPhase {
    WORK, SHORT_BREAK, LONG_BREAK
}

object PomodoroDefaults {
    const val WORK_MINUTES = 25
    const val SHORT_BREAK_MINUTES = 5
    const val LONG_BREAK_MINUTES = 15
    const val SESSIONS_BEFORE_LONG_BREAK = 4
}
