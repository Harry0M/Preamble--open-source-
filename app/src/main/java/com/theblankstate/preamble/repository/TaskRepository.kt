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

    suspend fun calculateStreak(): Int {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val calendar = Calendar.getInstance()
        var streak = 0

        // Check today first
        val today = todayString()
        val todayTotal = dao.getTaskCountForDate(today)
        val todayCompleted = dao.getCompletedCountForDate(today)

        if (todayTotal > 0 && todayTotal == todayCompleted) {
            streak = 1
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        } else {
            // Start from yesterday
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }

        // Check consecutive days backwards
        for (i in 0 until 365) {
            val dateStr = sdf.format(calendar.time)
            val total = dao.getTaskCountForDate(dateStr)
            val completed = dao.getCompletedCountForDate(dateStr)

            if (total > 0 && total == completed) {
                streak++
                calendar.add(Calendar.DAY_OF_YEAR, -1)
            } else if (total == 0) {
                calendar.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }

        return streak
    }

    suspend fun getWeeklyStats(): List<Pair<String, Float>> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val daySdf = SimpleDateFormat("EEE", Locale.getDefault())
        val calendar = Calendar.getInstance()
        val stats = mutableListOf<Pair<String, Float>>()

        calendar.add(Calendar.DAY_OF_YEAR, -6)

        for (i in 0 until 7) {
            val dateStr = sdf.format(calendar.time)
            val dayLabel = daySdf.format(calendar.time)
            val total = dao.getTaskCountForDate(dateStr)
            val completed = dao.getCompletedCountForDate(dateStr)
            val rate = if (total > 0) completed.toFloat() / total else 0f
            stats.add(dayLabel to rate)
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        return stats
    }

    suspend fun getMonthlyHeatMap(year: Int, month: Int): Map<Int, Pair<Int, Int>> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val calendar = Calendar.getInstance()
        calendar.set(year, month, 1)
        val maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val result = mutableMapOf<Int, Pair<Int, Int>>()

        for (day in 1..maxDay) {
            calendar.set(year, month, day)
            val dateStr = sdf.format(calendar.time)
            val total = dao.getTaskCountForDate(dateStr)
            val completed = dao.getCompletedCountForDate(dateStr)
            if (total > 0) {
                result[day] = completed to total
            }
        }
        return result
    }

    suspend fun getDailyStatsForRange(days: Int): List<Pair<String, Int>> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val daySdf = SimpleDateFormat("dd", Locale.getDefault())
        val calendar = Calendar.getInstance()
        val stats = mutableListOf<Pair<String, Int>>()

        calendar.add(Calendar.DAY_OF_YEAR, -(days - 1))
        for (i in 0 until days) {
            val dateStr = sdf.format(calendar.time)
            val dayLabel = daySdf.format(calendar.time)
            val completed = dao.getCompletedCountForDate(dateStr)
            stats.add(dayLabel to completed)
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return stats
    }

    suspend fun getPendingCountToday(): Int {
        val today = todayString()
        val total = dao.getTaskCountForDate(today)
        val completed = dao.getCompletedCountForDate(today)
        return total - completed
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
}
