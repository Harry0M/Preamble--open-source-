package com.theblankstate.preamble.focus

data class FocusTimerState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val currentPhase: FocusPhase = FocusPhase.WORK,
    val remainingSeconds: Int = FocusTimerDefaults.WORK_MINUTES * 60,
    val totalSeconds: Int = FocusTimerDefaults.WORK_MINUTES * 60,
    val sessionsCompleted: Int = 0,
    val taskId: String? = null,
    val taskTitle: String? = null
)

enum class FocusPhase {
    WORK, SHORT_BREAK, LONG_BREAK
}

object FocusTimerDefaults {
    const val WORK_MINUTES = 25
    const val SHORT_BREAK_MINUTES = 5
    const val LONG_BREAK_MINUTES = 15
    const val SESSIONS_BEFORE_LONG_BREAK = 4
    /** Minimum elapsed work minutes for a session to count in stats */
    const val MIN_TRACKABLE_MINUTES = 20
}
