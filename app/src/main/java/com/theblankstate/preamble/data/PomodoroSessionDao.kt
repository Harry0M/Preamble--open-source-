package com.theblankstate.preamble.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

data class DailyFocusStats(
    val date: String,
    val sessionCount: Int,
    val totalSeconds: Int
)

data class BestFocusDay(
    val date: String,
    val totalSeconds: Int
)

data class TaskFocusSummary(
    val taskId: String?,
    val taskTitle: String?,
    val sessionCount: Int,
    val totalSeconds: Int
)

@Dao
interface PomodoroSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: PomodoroSession)

    // Today's stats
    @Query("SELECT COUNT(*) FROM pomodoro_sessions WHERE date = :date")
    suspend fun getSessionCountForDate(date: String): Int

    @Query("SELECT COALESCE(SUM(durationSeconds), 0) FROM pomodoro_sessions WHERE date = :date")
    suspend fun getTotalFocusSecondsForDate(date: String): Int

    // Date range queries (for weekly/monthly charts)
    @Query("SELECT date, COUNT(*) as sessionCount, SUM(durationSeconds) as totalSeconds FROM pomodoro_sessions WHERE date IN (:dates) GROUP BY date")
    suspend fun getFocusStatsByDates(dates: List<String>): List<DailyFocusStats>

    // All-time totals
    @Query("SELECT COALESCE(SUM(durationSeconds), 0) FROM pomodoro_sessions")
    suspend fun getTotalFocusSecondsAllTime(): Int

    @Query("SELECT COUNT(*) FROM pomodoro_sessions")
    suspend fun getTotalSessionCount(): Int

    // Best focus day
    @Query("SELECT date, SUM(durationSeconds) as totalSeconds FROM pomodoro_sessions GROUP BY date ORDER BY totalSeconds DESC LIMIT 1")
    suspend fun getBestFocusDay(): BestFocusDay?

    // Top focused tasks
    @Query("SELECT taskId, taskTitle, COUNT(*) as sessionCount, SUM(durationSeconds) as totalSeconds FROM pomodoro_sessions WHERE taskId IS NOT NULL GROUP BY taskId ORDER BY totalSeconds DESC LIMIT :limit")
    suspend fun getTopFocusedTasks(limit: Int = 5): List<TaskFocusSummary>

    // Average daily focus (total seconds / distinct days with sessions)
    @Query("SELECT COALESCE(CAST(SUM(durationSeconds) AS REAL) / MAX(COUNT(DISTINCT date), 1), 0) FROM pomodoro_sessions")
    suspend fun getAverageDailyFocusSeconds(): Int
}
