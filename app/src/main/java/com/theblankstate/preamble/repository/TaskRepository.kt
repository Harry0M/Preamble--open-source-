package com.theblankstate.preamble.repository

import com.theblankstate.preamble.data.Task
import com.theblankstate.preamble.data.TaskDao
import com.theblankstate.preamble.sync.FirebaseTaskSyncManager
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TaskRepository(
    private val dao: TaskDao,
    private val syncManager: FirebaseTaskSyncManager? = null
) {

    val tasksFlow: Flow<List<Task>> = dao.getAllTasksFlow()

    fun getTasksForDate(date: String): Flow<List<Task>> = dao.getTasksByDate(date)

    fun getTasksForDates(dates: List<String>): Flow<List<Task>> = dao.getTasksForDates(dates)

    fun getCompletedCount(): Flow<Int> = dao.getCompletedTasksCount()

    fun getTotalCount(): Flow<Int> = dao.getTotalTasksCount()

    suspend fun addTask(title: String, date: String? = null, deadlineTime: String? = null): Task {
        val taskDate = date ?: todayString()
        val now = System.currentTimeMillis()
        val task = Task(
            title = title,
            createdDate = taskDate,
            deadlineTime = deadlineTime,
            createdTimestamp = now,
            updatedTimestamp = now
        )
        dao.insertTask(task)
        syncManager?.pushTask(task)
        return task
    }

    suspend fun toggleTask(task: Task) {
        val updated = task.copy(
            isCompleted = !task.isCompleted,
            completedTimestamp = if (!task.isCompleted) System.currentTimeMillis() else null,
            updatedTimestamp = System.currentTimeMillis()
        )
        dao.updateTask(updated)
        syncManager?.pushTask(updated)
    }

    suspend fun deleteTask(task: Task) {
        dao.deleteTask(task)
        syncManager?.deleteTask(task.id)
    }

    suspend fun syncNow() {
        syncManager?.syncAllLocalToRemote()
    }

    suspend fun flushAndClearLocalOnLogout() {
        syncManager?.flushPendingWrites()
        dao.clearAllTasks()
    }

    /**
     * Optimized streak calculation using batch query.
     * Fetches stats for all dates in a single SQL query instead of 2 queries per day.
     */
    suspend fun calculateStreak(): Int {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val calendar = Calendar.getInstance()

        // Generate all dates we might need (up to 365 days back)
        val dates = mutableListOf<String>()
        val tempCal = Calendar.getInstance()
        for (i in 0..365) {
            dates.add(sdf.format(tempCal.time))
            tempCal.add(Calendar.DAY_OF_YEAR, -1)
        }

        // Single batch query for all dates
        val statsMap = dao.getStatsForDates(dates).associate { it.createdDate to it }

        var streak = 0
        val today = todayString()
        val todayStats = statsMap[today]

        if (todayStats != null && todayStats.total > 0 && todayStats.total == todayStats.completed) {
            streak = 1
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        } else {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }

        for (i in 0 until 365) {
            val dateStr = sdf.format(calendar.time)
            val stats = statsMap[dateStr]

            if (stats != null && stats.total > 0 && stats.total == stats.completed) {
                streak++
                calendar.add(Calendar.DAY_OF_YEAR, -1)
            } else if (stats == null) {
                // No tasks on this day — skip
                calendar.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }

        return streak
    }

    /**
     * Optimized weekly stats using batch query.
     */
    suspend fun getWeeklyStats(): List<Pair<String, Float>> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val daySdf = SimpleDateFormat("EEE", Locale.getDefault())
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -6)

        val dates = (0 until 7).map { i ->
            val tempCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6 + i) }
            sdf.format(tempCal.time)
        }

        val statsMap = dao.getStatsForDates(dates).associate { it.createdDate to it }

        return dates.map { dateStr ->
            val tempCal = Calendar.getInstance().apply { time = sdf.parse(dateStr)!! }
            val dayLabel = daySdf.format(tempCal.time)
            val stats = statsMap[dateStr]
            val rate = if (stats != null && stats.total > 0) stats.completed.toFloat() / stats.total else 0f
            dayLabel to rate
        }
    }

    /**
     * Optimized monthly heat map using batch query.
     */
    suspend fun getMonthlyHeatMap(year: Int, month: Int): Map<Int, Pair<Int, Int>> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val calendar = Calendar.getInstance()
        calendar.set(year, month, 1)
        val maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

        val dates = (1..maxDay).map { day ->
            calendar.set(year, month, day)
            sdf.format(calendar.time)
        }

        val statsMap = dao.getStatsForDates(dates).associate { it.createdDate to it }

        val result = mutableMapOf<Int, Pair<Int, Int>>()
        for (day in 1..maxDay) {
            calendar.set(year, month, day)
            val dateStr = sdf.format(calendar.time)
            val stats = statsMap[dateStr]
            if (stats != null && stats.total > 0) {
                result[day] = stats.completed to stats.total
            }
        }
        return result
    }

    /**
     * Optimized daily stats using batch query.
     */
    suspend fun getDailyStatsForRange(days: Int): List<Pair<String, Int>> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val daySdf = SimpleDateFormat("dd", Locale.getDefault())
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -(days - 1))

        val dates = (0 until days).map { i ->
            val tempCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -(days - 1) + i) }
            sdf.format(tempCal.time)
        }

        val statsMap = dao.getStatsForDates(dates).associate { it.createdDate to it }

        return dates.map { dateStr ->
            val tempCal = Calendar.getInstance().apply { time = sdf.parse(dateStr)!! }
            val dayLabel = daySdf.format(tempCal.time)
            val completed = statsMap[dateStr]?.completed ?: 0
            dayLabel to completed
        }
    }

    suspend fun getPendingCountToday(): Int {
        return dao.getPendingCountForDate(todayString())
    }

    suspend fun getPendingTasksToday(): List<Task> {
        return dao.getPendingTasksForDate(todayString())
    }

    suspend fun getPendingTasksForDate(date: String): List<Task> {
        return dao.getPendingTasksForDate(date)
    }

    companion object {
        fun todayString(): String {
            return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        }
    }

    // ── Google Calendar Sync ──

    /**
     * Sync Google Calendar events into the local database.
     * Uses upsert logic: new events are inserted, existing ones are updated,
     * removed events are deleted.
     */
    suspend fun syncCalendarEvents(events: List<Task>) {
        val existingCalendarIds = dao.getAllCalendarTaskIds().toSet()
        val newEventIds = events.map { it.id }.toSet()

        // Insert or update events
        dao.insertTasks(events)

        // Delete events that are no longer in the calendar
        val removedIds = existingCalendarIds - newEventIds
        for (id in removedIds) {
            val existingTasks = dao.getAllCalendarTasks()
            val toRemove = existingTasks.filter { it.id in removedIds }
            for (task in toRemove) {
                dao.deleteTask(task)
            }
            break // Only need one pass
        }
    }

    /**
     * Remove all calendar events from local database (when unlinking).
     */
    suspend fun clearCalendarEvents() {
        dao.deleteAllCalendarTasks()
    }

    // ── Google Tasks Sync ──

    /**
     * Sync Google Tasks into the local database.
     */
    suspend fun syncGoogleTasks(tasks: List<Task>) {
        val existingIds = dao.getAllGoogleTaskIds().toSet()
        val newIds = tasks.map { it.id }.toSet()

        // Insert or update tasks
        dao.insertTasks(tasks)

        // Delete tasks no longer in Google
        val removedIds = existingIds - newIds
        if (removedIds.isNotEmpty()) {
            val existing = dao.getAllGoogleTasks()
            for (task in existing.filter { it.id in removedIds }) {
                dao.deleteTask(task)
            }
        }
    }

    /**
     * Remove all Google Tasks from local database (when unlinking).
     */
    suspend fun clearGoogleTasks() {
        dao.deleteAllGoogleTasks()
    }
}
