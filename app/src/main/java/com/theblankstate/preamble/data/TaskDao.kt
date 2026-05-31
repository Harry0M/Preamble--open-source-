package com.theblankstate.preamble.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class DateStats(
    val createdDate: String,
    val total: Int,
    val completed: Int
)

data class SubtaskStats(
    val parentTaskId: String,
    val total: Int,
    val completed: Int
)

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE ( (IFNULL(recurrenceType, '') != 'rollover' AND taskCategory = 'task' AND ((endDate IS NULL AND createdDate = :date) OR (endDate IS NOT NULL AND createdDate <= :date AND endDate >= :date))) OR (recurrenceType = 'rollover' AND isCompleted = 0 AND createdDate <= :date) OR (recurrenceType = 'rollover' AND isCompleted = 1 AND completedDate = :date) ) AND parentTaskId IS NULL ORDER BY isCompleted ASC, CASE WHEN recurrenceType = 'rollover' AND isCompleted = 0 THEN 0 ELSE 1 END ASC, priority DESC, createdTimestamp DESC")
    fun getTasksByDate(date: String): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE ( (IFNULL(recurrenceType, '') != 'rollover' AND taskCategory = 'task' AND ((endDate IS NULL AND createdDate = :date) OR (endDate IS NOT NULL AND createdDate <= :date AND endDate >= :date))) OR (recurrenceType = 'rollover' AND isCompleted = 0 AND createdDate <= :date) OR (recurrenceType = 'rollover' AND isCompleted = 1 AND completedDate = :date) ) AND parentTaskId IS NULL ORDER BY isCompleted ASC, CASE WHEN recurrenceType = 'rollover' AND isCompleted = 0 THEN 0 ELSE 1 END ASC, priority DESC, createdTimestamp DESC")
    suspend fun getTasksByDateSync(date: String): List<Task>

    @Query("SELECT * FROM tasks WHERE ( (IFNULL(recurrenceType, '') != 'rollover' AND taskCategory = 'task' AND ((endDate IS NULL AND createdDate IN (:dates)) OR (endDate IS NOT NULL AND createdDate <= :maxDate))) OR (recurrenceType = 'rollover' AND isCompleted = 0 AND createdDate <= :maxDate) OR (recurrenceType = 'rollover' AND isCompleted = 1 AND completedDate IN (:dates)) ) AND parentTaskId IS NULL ORDER BY createdDate DESC, isCompleted ASC, priority DESC, createdTimestamp DESC")
    fun getTasksForDates(dates: List<String>, maxDate: String): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE ( (IFNULL(recurrenceType, '') != 'rollover' AND taskCategory = 'task' AND ((endDate IS NULL AND createdDate IN (:dates)) OR (endDate IS NOT NULL AND createdDate <= :maxDate))) OR (recurrenceType = 'rollover' AND isCompleted = 0 AND createdDate <= :maxDate) OR (recurrenceType = 'rollover' AND isCompleted = 1 AND completedDate IN (:dates)) ) AND parentTaskId IS NULL ORDER BY isCompleted ASC, priority DESC, createdTimestamp DESC")
    suspend fun getTasksForDatesSync(dates: List<String>, maxDate: String): List<Task>

    @Query("SELECT COUNT(*) FROM tasks WHERE isCompleted = 1")
    fun getCompletedTasksCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tasks")
    fun getTotalTasksCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tasks WHERE ( (IFNULL(recurrenceType, '') != 'rollover' AND createdDate = :date) OR (recurrenceType = 'rollover' AND isCompleted = 0 AND createdDate <= :date) OR (recurrenceType = 'rollover' AND isCompleted = 1 AND completedDate = :date) )")
    suspend fun getTaskCountForDate(date: String): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE isCompleted = 1 AND ( (IFNULL(recurrenceType, '') != 'rollover' AND createdDate = :date) OR (recurrenceType = 'rollover' AND completedDate = :date) )")
    suspend fun getCompletedCountForDate(date: String): Int

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND ( (IFNULL(recurrenceType, '') != 'rollover' AND createdDate = :date) OR (recurrenceType = 'rollover' AND createdDate <= :date) ) AND parentTaskId IS NULL ORDER BY CASE WHEN recurrenceType = 'rollover' THEN 0 ELSE 1 END ASC, priority DESC, createdTimestamp DESC")
    suspend fun getPendingTasksForDate(date: String): List<Task>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND ( (IFNULL(recurrenceType, '') != 'rollover' AND createdDate = :date) OR (recurrenceType = 'rollover' AND createdDate <= :date) ) AND parentTaskId IS NULL ORDER BY CASE WHEN recurrenceType = 'rollover' THEN 0 ELSE 1 END ASC, priority DESC, createdTimestamp DESC")
    fun observePendingTasksForDate(date: String): Flow<List<Task>>

    @Query("SELECT DISTINCT createdDate FROM tasks ORDER BY createdDate DESC")
    suspend fun getAllDatesWithTasks(): List<String>

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasks(): List<Task>

    @Query("SELECT * FROM tasks WHERE parentTaskId IS NULL ORDER BY createdDate DESC, isCompleted ASC, priority DESC, createdTimestamp DESC")
    fun getAllTasksFlow(): Flow<List<Task>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<Task>)

    @Query("DELETE FROM tasks")
    suspend fun clearAllTasks()

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: String): Task?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task)

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    // ── Batch stats queries (avoids N+1 per-day queries) ──

    @Query("SELECT CASE WHEN recurrenceType = 'rollover' AND completedDate IS NOT NULL THEN completedDate ELSE createdDate END as createdDate, COUNT(*) as total, SUM(CASE WHEN isCompleted = 1 THEN 1 ELSE 0 END) as completed FROM tasks WHERE ( (IFNULL(recurrenceType, '') != 'rollover' AND createdDate IN (:dates)) OR (recurrenceType = 'rollover' AND isCompleted = 1 AND completedDate IN (:dates)) ) GROUP BY CASE WHEN recurrenceType = 'rollover' AND completedDate IS NOT NULL THEN completedDate ELSE createdDate END")
    suspend fun getStatsForDates(dates: List<String>): List<DateStats>

    @Query("SELECT COUNT(*) FROM tasks WHERE isCompleted = 0 AND ( (IFNULL(recurrenceType, '') != 'rollover' AND createdDate = :date) OR (recurrenceType = 'rollover' AND createdDate <= :date) )")
    suspend fun getPendingCountForDate(date: String): Int

    @Query("SELECT * FROM tasks WHERE source = 'local'")
    suspend fun getAllLocalTasks(): List<Task>

    // Calendar sync queries
    @Query("SELECT * FROM tasks WHERE source = 'google_calendar'")
    suspend fun getAllCalendarTasks(): List<Task>

    @Query("DELETE FROM tasks WHERE source = 'google_calendar'")
    suspend fun deleteAllCalendarTasks()

    @Query("SELECT id FROM tasks WHERE source = 'google_calendar'")
    suspend fun getAllCalendarTaskIds(): List<String>

    // Google Tasks sync queries
    @Query("SELECT * FROM tasks WHERE source = 'google_tasks'")
    suspend fun getAllGoogleTasks(): List<Task>

    @Query("DELETE FROM tasks WHERE source = 'google_tasks'")
    suspend fun deleteAllGoogleTasks()

    @Query("SELECT id FROM tasks WHERE source = 'google_tasks'")
    suspend fun getAllGoogleTaskIds(): List<String>

    @Query("SELECT * FROM tasks WHERE title = :title AND isCompleted = 0 LIMIT 1")
    suspend fun getPendingTaskByTitle(title: String): Task?

    @Query("SELECT * FROM tasks WHERE (title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%') ORDER BY createdDate DESC, priority DESC, isCompleted ASC, createdTimestamp DESC")
    fun searchTasks(query: String): Flow<List<Task>>

    // ── Rollover queries ──

    @Query("SELECT * FROM tasks WHERE recurrenceType = 'rollover' AND isCompleted = 0 AND parentTaskId IS NULL")
    suspend fun getPendingRolloverTasks(): List<Task>

    // ── Recurrence queries ──

    @Query("SELECT * FROM tasks WHERE recurrenceType IS NOT NULL AND recurrenceParentId IS NULL")
    suspend fun getAllRecurrenceTemplates(): List<Task>

    @Query("SELECT MAX(createdDate) FROM tasks WHERE recurrenceParentId = :parentId")
    suspend fun getLatestInstanceDate(parentId: String): String?

    @Query("SELECT COUNT(*) FROM tasks WHERE recurrenceParentId = :parentId AND createdDate = :date")
    suspend fun instanceExistsForDate(parentId: String, date: String): Int

    @Query("SELECT * FROM tasks WHERE recurrenceParentId = :parentId ORDER BY createdDate DESC")
    suspend fun getInstancesForTemplate(parentId: String): List<Task>

    // Batch query: get ALL instances at once (avoids N+1 per-template queries)
    @Query("SELECT * FROM tasks WHERE recurrenceParentId IS NOT NULL")
    suspend fun getAllRecurrenceInstances(): List<Task>

    // Scoped query: only instances for specific template IDs (much faster than loading all 2000+)
    @Query("SELECT * FROM tasks WHERE recurrenceParentId IN (:parentIds)")
    suspend fun getRecurrenceInstancesForParents(parentIds: List<String>): List<Task>

    // ── Subtask queries ──

    @Query("SELECT * FROM tasks WHERE parentTaskId = :parentId ORDER BY isCompleted ASC, createdTimestamp ASC")
    fun getSubtasksForParent(parentId: String): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE parentTaskId = :parentId ORDER BY isCompleted ASC, createdTimestamp ASC")
    suspend fun getSubtasksForParentSync(parentId: String): List<Task>

    @Query("SELECT parentTaskId, COUNT(*) as total, SUM(CASE WHEN isCompleted = 1 THEN 1 ELSE 0 END) as completed FROM tasks WHERE parentTaskId IN (:parentIds) GROUP BY parentTaskId")
    suspend fun getSubtaskStatsForParents(parentIds: List<String>): List<SubtaskStats>

    @Query("SELECT parentTaskId, COUNT(*) as total, SUM(CASE WHEN isCompleted = 1 THEN 1 ELSE 0 END) as completed FROM tasks WHERE parentTaskId IN (:parentIds) GROUP BY parentTaskId")
    fun observeSubtaskStatsForParents(parentIds: List<String>): Flow<List<SubtaskStats>>

    @Query("DELETE FROM tasks WHERE parentTaskId = :parentId")
    suspend fun deleteAllSubtasks(parentId: String)

    // ── Tag queries ──

    @Query("SELECT * FROM tasks WHERE tags LIKE '%' || :tag || '%' AND parentTaskId IS NULL ORDER BY createdDate DESC, isCompleted ASC, priority DESC, createdTimestamp DESC")
    fun getTasksByTag(tag: String): Flow<List<Task>>

    // ── Advanced Stats queries ──

    @Query("""
        SELECT
            CASE
                WHEN recurrenceType = 'rollover' THEN 'rollover'
                WHEN recurrenceType IS NOT NULL AND recurrenceParentId IS NULL THEN 'recurring'
                ELSE source
            END as source,
            COUNT(*) as count,
            SUM(CASE WHEN isCompleted = 1 THEN 1 ELSE 0 END) as completed
        FROM tasks
        WHERE parentTaskId IS NULL
        GROUP BY CASE
            WHEN recurrenceType = 'rollover' THEN 'rollover'
            WHEN recurrenceType IS NOT NULL AND recurrenceParentId IS NULL THEN 'recurring'
            ELSE source
        END
    """)
    suspend fun getTaskCountBySource(): List<SourceBreakdown>

    @Query("SELECT COUNT(*) FROM tasks WHERE recurrenceType = 'rollover' AND parentTaskId IS NULL")
    suspend fun getTotalRolloverCount(): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE recurrenceType = 'rollover' AND isCompleted = 1 AND parentTaskId IS NULL")
    suspend fun getCompletedRolloverCount(): Int

    @Query("SELECT tags, isCompleted FROM tasks WHERE tags IS NOT NULL AND tags != '' AND parentTaskId IS NULL")
    suspend fun getTagCompletionData(): List<TagCompletionRow>

    @Query("""
        SELECT createdDate FROM (
            SELECT
                CASE WHEN recurrenceType = 'rollover' AND completedDate IS NOT NULL THEN completedDate ELSE createdDate END as createdDate,
                COUNT(*) as total,
                SUM(CASE WHEN isCompleted = 1 THEN 1 ELSE 0 END) as completed
            FROM tasks WHERE parentTaskId IS NULL
            GROUP BY CASE WHEN recurrenceType = 'rollover' AND completedDate IS NOT NULL THEN completedDate ELSE createdDate END
        ) WHERE total > 0 AND total = completed ORDER BY createdDate DESC
    """)
    suspend fun getAllPerfectDates(): List<String>

    // ── Tag Override queries (for Google Calendar/Tasks tag persistence) ──

    @Query("SELECT * FROM task_tag_overrides WHERE googleId = :googleId")
    suspend fun getTagOverride(googleId: String): TaskTagOverride?

    @Query("SELECT * FROM task_tag_overrides")
    suspend fun getAllTagOverrides(): List<TaskTagOverride>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTagOverride(override: TaskTagOverride)

    @Query("DELETE FROM task_tag_overrides WHERE googleId = :googleId")
    suspend fun deleteTagOverride(googleId: String)

    // ── Deep Analytics queries ──

    /** Get completedTimestamp for all completed tasks (for hourly distribution analysis) */
    @Query("SELECT completedTimestamp FROM tasks WHERE isCompleted = 1 AND completedTimestamp IS NOT NULL AND parentTaskId IS NULL")
    suspend fun getAllCompletionTimestamps(): List<Long>

    /** Get priority distribution across all non-subtask tasks */
    @Query("SELECT priority, COUNT(*) as cnt FROM tasks WHERE parentTaskId IS NULL GROUP BY priority ORDER BY priority")
    suspend fun getPriorityDistribution(): List<PriorityCount>

    /** Get creation-to-completion time deltas for completed tasks */
    @Query("SELECT createdTimestamp, completedTimestamp FROM tasks WHERE isCompleted = 1 AND completedTimestamp IS NOT NULL AND parentTaskId IS NULL")
    suspend fun getCompletionTimeDeltas(): List<CompletionTimeDelta>

    /** Get pending tasks and their ages */
    @Query("SELECT createdDate FROM tasks WHERE isCompleted = 0 AND parentTaskId IS NULL")
    suspend fun getPendingTaskDates(): List<String>

    // ── Habit queries ──

    @Query("SELECT * FROM tasks WHERE taskCategory = 'habit' AND parentTaskId IS NULL ORDER BY createdTimestamp ASC")
    fun getAllHabits(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE taskCategory = 'habit' AND parentTaskId IS NULL ORDER BY createdTimestamp ASC")
    suspend fun getAllHabitsSync(): List<Task>

    @Query("SELECT * FROM tasks WHERE taskCategory = 'habit' AND id = :habitId LIMIT 1")
    suspend fun getHabitById(habitId: String): Task?

    @Query("SELECT COUNT(*) FROM tasks WHERE taskCategory = 'habit' AND parentTaskId IS NULL")
    suspend fun getHabitCount(): Int
}

data class PriorityCount(
    val priority: Int,
    val cnt: Int
)

data class CompletionTimeDelta(
    val createdTimestamp: Long,
    val completedTimestamp: Long
)
