package com.theblankstate.preamble.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

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

data class PerTaskTimerStats(
    val taskId: String?,
    val taskTitle: String?,
    val totalSeconds: Int,
    val sessionCount: Int,
    val avgDurationSeconds: Int,
    val lastSessionDate: String?,
    val longestSessionSeconds: Int
)

@Dao
interface FocusSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: FocusSession)

    // Today's stats
    @Query("SELECT COUNT(*) FROM focus_sessions WHERE date = :date")
    suspend fun getSessionCountForDate(date: String): Int

    @Query("SELECT COALESCE(SUM(actualDurationCompletedSeconds), SUM(durationSeconds)) FROM focus_sessions WHERE date = :date")
    suspend fun getTotalFocusSecondsForDate(date: String): Int

    // Date range queries (for weekly/monthly charts)
    @Query("SELECT date, COUNT(*) as sessionCount, COALESCE(SUM(actualDurationCompletedSeconds), SUM(durationSeconds)) as totalSeconds FROM focus_sessions WHERE date IN (:dates) GROUP BY date")
    suspend fun getFocusStatsByDates(dates: List<String>): List<DailyFocusStats>

    // All-time totals
    @Query("SELECT COALESCE(SUM(actualDurationCompletedSeconds), SUM(durationSeconds)) FROM focus_sessions")
    suspend fun getTotalFocusSecondsAllTime(): Int

    @Query("SELECT COUNT(*) FROM focus_sessions")
    suspend fun getTotalSessionCount(): Int

    @Query("SELECT MIN(date) FROM focus_sessions")
    suspend fun getFirstSessionDate(): String?

    // Best focus day
    @Query("SELECT date, COALESCE(SUM(actualDurationCompletedSeconds), SUM(durationSeconds)) as totalSeconds FROM focus_sessions GROUP BY date ORDER BY totalSeconds DESC LIMIT 1")
    suspend fun getBestFocusDay(): BestFocusDay?

    // Top focused tasks
    @Query("SELECT taskId, taskTitle, COUNT(*) as sessionCount, COALESCE(SUM(actualDurationCompletedSeconds), SUM(durationSeconds)) as totalSeconds FROM focus_sessions WHERE taskId IS NOT NULL GROUP BY taskId ORDER BY totalSeconds DESC LIMIT :limit")
    suspend fun getTopFocusedTasks(limit: Int = 5): List<TaskFocusSummary>

    // Average daily focus (total seconds / distinct days with sessions)
    @Query("SELECT COALESCE(CAST(SUM(COALESCE(actualDurationCompletedSeconds, durationSeconds)) AS REAL) / MAX(COUNT(DISTINCT date), 1), 0) FROM focus_sessions")
    suspend fun getAverageDailyFocusSeconds(): Int

    // Per Task Detailed Statistics
    @Query("""
        SELECT 
            taskId,
            taskTitle,
            COALESCE(SUM(COALESCE(actualDurationCompletedSeconds, durationSeconds)), 0) AS totalSeconds,
            COUNT(*) AS sessionCount,
            COALESCE(CAST(AVG(COALESCE(actualDurationCompletedSeconds, durationSeconds)) AS INTEGER), 0) AS avgDurationSeconds,
            MAX(date) AS lastSessionDate,
            COALESCE(MAX(COALESCE(actualDurationCompletedSeconds, durationSeconds)), 0) AS longestSessionSeconds
        FROM focus_sessions
        WHERE taskId = :taskId
        GROUP BY taskId
    """)
    suspend fun getPerTaskStats(taskId: String): PerTaskTimerStats?

    @Query("""
        SELECT 
            taskId,
            taskTitle,
            COALESCE(SUM(COALESCE(actualDurationCompletedSeconds, durationSeconds)), 0) AS totalSeconds,
            COUNT(*) AS sessionCount,
            COALESCE(CAST(AVG(COALESCE(actualDurationCompletedSeconds, durationSeconds)) AS INTEGER), 0) AS avgDurationSeconds,
            MAX(date) AS lastSessionDate,
            COALESCE(MAX(COALESCE(actualDurationCompletedSeconds, durationSeconds)), 0) AS longestSessionSeconds
        FROM focus_sessions
        WHERE taskId = :taskId OR (:taskTitle IS NOT NULL AND taskTitle IS NOT NULL AND LOWER(TRIM(taskTitle)) = LOWER(TRIM(:taskTitle)))
        ORDER BY totalSeconds DESC LIMIT 1
    """)
    suspend fun getPerTaskStatsByTitleOrId(taskId: String, taskTitle: String?): PerTaskTimerStats?

    @Query("""
        SELECT 
            date,
            COUNT(*) AS sessionCount,
            COALESCE(SUM(COALESCE(actualDurationCompletedSeconds, durationSeconds)), 0) AS totalSeconds
        FROM focus_sessions
        WHERE taskId = :taskId OR (:taskTitle IS NOT NULL AND taskTitle IS NOT NULL AND LOWER(TRIM(taskTitle)) = LOWER(TRIM(:taskTitle)))
        GROUP BY date
        ORDER BY date DESC
    """)
    suspend fun getDailyFocusHistoryForTask(taskId: String, taskTitle: String?): List<DailyFocusStats>

    @Query("""
        SELECT 
            taskId,
            taskTitle,
            COALESCE(SUM(COALESCE(actualDurationCompletedSeconds, durationSeconds)), 0) AS totalSeconds,
            COUNT(*) AS sessionCount,
            COALESCE(CAST(AVG(COALESCE(actualDurationCompletedSeconds, durationSeconds)) AS INTEGER), 0) AS avgDurationSeconds,
            MAX(date) AS lastSessionDate,
            COALESCE(MAX(COALESCE(actualDurationCompletedSeconds, durationSeconds)), 0) AS longestSessionSeconds
        FROM focus_sessions
        WHERE taskId IS NOT NULL
        GROUP BY taskId
        ORDER BY totalSeconds DESC
    """)
    suspend fun getAllPerTaskStats(): List<PerTaskTimerStats>

    // Recent Sessions
    @Query("SELECT * FROM focus_sessions ORDER BY startTimestamp DESC LIMIT :limit")
    suspend fun getRecentSessions(limit: Int = 20): List<FocusSession>

    @Query("SELECT * FROM focus_sessions ORDER BY startTimestamp DESC LIMIT :limit")
    fun getRecentSessionsFlow(limit: Int = 20): Flow<List<FocusSession>>

    @Query("SELECT * FROM focus_sessions WHERE taskId = :taskId ORDER BY startTimestamp DESC")
    suspend fun getSessionsForTask(taskId: String): List<FocusSession>

    @Query("SELECT * FROM focus_sessions WHERE taskId = :taskId OR (:taskTitle IS NOT NULL AND taskTitle IS NOT NULL AND LOWER(TRIM(taskTitle)) = LOWER(TRIM(:taskTitle))) ORDER BY startTimestamp DESC")
    suspend fun getSessionsForTaskByTitleOrId(taskId: String, taskTitle: String?): List<FocusSession>
}
