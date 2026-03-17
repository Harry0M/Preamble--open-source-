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

    suspend fun addTask(title: String, date: String? = null, deadlineTime: String? = null, priority: Int = 0, description: String? = null, tags: String? = null): Task {
        val taskDate = date ?: todayString()
        val now = System.currentTimeMillis()
        val task = Task(
            title = title,
            createdDate = taskDate,
            deadlineTime = deadlineTime,
            createdTimestamp = now,
            updatedTimestamp = now,
            priority = priority,
            description = description,
            tags = tags
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

    suspend fun insertTask(task: Task) {
        dao.insertTask(task)
        syncManager?.pushTask(task)
    }

    suspend fun updateTask(task: Task) {
        dao.updateTask(task)
        syncManager?.pushTask(task)
    }

    suspend fun deleteTask(task: Task) {
        // Cascade delete subtasks if this task has any
        dao.deleteAllSubtasks(task.id)
        dao.deleteTask(task)
        syncManager?.deleteTask(task.id)
    }

    fun searchTasks(query: String): Flow<List<Task>> = dao.searchTasks(query)

    // ── Subtask methods ──

    fun getSubtasksForParent(parentId: String): Flow<List<Task>> = dao.getSubtasksForParent(parentId)

    suspend fun addSubtask(parentId: String, title: String): Task {
        val now = System.currentTimeMillis()
        val parent = dao.getAllTasks().find { it.id == parentId } ?: error("Parent not found")
        val subtask = Task(
            title = title,
            createdDate = parent.createdDate,
            createdTimestamp = now,
            updatedTimestamp = now,
            parentTaskId = parentId
        )
        dao.insertTask(subtask)
        syncManager?.pushTask(subtask)
        return subtask
    }

    suspend fun getSubtaskStats(parentIds: List<String>): Map<String, Pair<Int, Int>> {
        if (parentIds.isEmpty()) return emptyMap()
        return dao.getSubtaskStatsForParents(parentIds).associate {
            it.parentTaskId to (it.completed to it.total)
        }
    }

    suspend fun addRecurringTask(
        title: String,
        date: String? = null,
        deadlineTime: String? = null,
        priority: Int = 0,
        description: String? = null,
        recurrenceType: String,
        recurrenceInterval: Int = 1,
        recurrenceDays: String? = null,
        recurrenceEndDate: String? = null
    ): Task {
        val taskDate = date ?: todayString()
        val now = System.currentTimeMillis()
        val template = Task(
            title = title,
            createdDate = taskDate,
            deadlineTime = deadlineTime,
            createdTimestamp = now,
            updatedTimestamp = now,
            priority = priority,
            description = description,
            recurrenceType = recurrenceType,
            recurrenceInterval = recurrenceInterval,
            recurrenceDays = recurrenceDays,
            recurrenceEndDate = recurrenceEndDate
        )
        dao.insertTask(template)
        syncManager?.pushTask(template)
        return template
    }

    suspend fun deleteRecurringTaskAndInstances(template: Task) {
        val today = todayString()
        val instances = dao.getInstancesForTemplate(template.id)
        for (instance in instances) {
            if (instance.createdDate >= today && !instance.isCompleted) {
                dao.deleteTask(instance)
                syncManager?.deleteTask(instance.id)
            }
        }
        dao.deleteTask(template)
        syncManager?.deleteTask(template.id)
    }

    suspend fun syncNow() {
        syncManager?.syncAllLocalToRemote()
    }

    suspend fun forceSyncFirebase() {
        syncManager?.forceSyncBidirectional()
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

    // ── Tag Merging Helper ──

    private fun mergeTagsWithSource(existingTags: String?, sourceTag: String): String {
        val tagSet = existingTags?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toMutableSet() ?: mutableSetOf()
        tagSet.add(sourceTag)
        return tagSet.joinToString(",")
    }

    // ── Google Calendar Sync ──

    /**
     * Full sync Google Calendar events into the local database.
     * Uses last-write-wins conflict resolution: only upsert if remote is newer.
     * Preserves local completion state, user tags, priority, and description.
     * Removed events are deleted (unless completed locally).
     */
    suspend fun syncCalendarEvents(events: List<Task>) {
        val existingCalendarIds = dao.getAllCalendarTaskIds().toSet()
        val newEventIds = events.map { it.id }.toSet()

        val existingTasks = dao.getAllCalendarTasks().associateBy { it.id }
        val eventsToInsert = events.mapNotNull { event ->
            val existing = existingTasks[event.id]
            if (existing != null) {
                // Last-write-wins: only update if remote is newer or equal
                if (event.updatedTimestamp >= existing.updatedTimestamp) {
                    event.copy(
                        isCompleted = existing.isCompleted,
                        completedTimestamp = existing.completedTimestamp,
                        tags = mergeTagsWithSource(existing.tags, "Google Calendar"),
                        priority = existing.priority
                    )
                } else {
                    null // skip — local is newer
                }
            } else {
                event // new event, insert
            }
        }

        if (eventsToInsert.isNotEmpty()) {
            dao.insertTasks(eventsToInsert)
        }

        // Delete events that are no longer in the calendar (skip completed ones user marked locally)
        val removedIds = existingCalendarIds - newEventIds
        if (removedIds.isNotEmpty()) {
            val toRemove = existingTasks.values.filter { it.id in removedIds && !it.isCompleted }
            for (task in toRemove) {
                dao.deleteTask(task)
            }
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
     * Full sync Google Tasks into the local database.
     * Uses last-write-wins conflict resolution.
     * Tasks deleted from Google are either:
     * - Marked with deletedFromGoogle=true (default) — shown with tag
     * - Auto-deleted from app if autoDeleteGoogleTasks setting is ON
     */
    suspend fun syncGoogleTasks(tasks: List<Task>, autoDeleteFromApp: Boolean = false) {
        val existingIds = dao.getAllGoogleTaskIds().toSet()
        val newIds = tasks.map { it.id }.toSet()
        val existingTasks = dao.getAllGoogleTasks().associateBy { it.id }

        // Insert or update tasks with last-write-wins
        val tasksToInsert = tasks.mapNotNull { task ->
            val existing = existingTasks[task.id]
            if (existing != null) {
                if (task.updatedTimestamp >= existing.updatedTimestamp) {
                    task.copy(
                        tags = mergeTagsWithSource(existing.tags, "Google Tasks"),
                        priority = existing.priority,
                        description = existing.description ?: task.description
                    )
                } else {
                    null // skip — local is newer
                }
            } else {
                task
            }
        }

        if (tasksToInsert.isNotEmpty()) {
            dao.insertTasks(tasksToInsert)
        }

        // Handle tasks removed from Google
        val removedIds = existingIds - newIds
        if (removedIds.isNotEmpty()) {
            for (task in existingTasks.values.filter { it.id in removedIds && !it.deletedFromGoogle }) {
                if (autoDeleteFromApp) {
                    dao.deleteTask(task)
                    syncManager?.deleteTask(task.id)
                } else {
                    dao.updateTask(task.copy(deletedFromGoogle = true, updatedTimestamp = System.currentTimeMillis()))
                }
            }
        }
    }

    /**
     * Quick sync: only upsert changed tasks, no deletion detection.
     * Used for pull-to-refresh for fast incremental sync.
     * Preserves user tags and applies last-write-wins.
     */
    suspend fun quickSyncGoogleTasks(tasks: List<Task>) {
        if (tasks.isEmpty()) return
        val existingTasks = dao.getAllGoogleTasks().associateBy { it.id }
        val tasksToInsert = tasks.mapNotNull { task ->
            val existing = existingTasks[task.id]
            if (existing != null) {
                if (task.updatedTimestamp >= existing.updatedTimestamp) {
                    task.copy(tags = mergeTagsWithSource(existing.tags, "Google Tasks"))
                } else {
                    null
                }
            } else {
                task
            }
        }
        if (tasksToInsert.isNotEmpty()) {
            dao.insertTasks(tasksToInsert)
        }
    }

    /**
     * Quick sync for calendar events: only upsert, no deletion detection.
     * Used for incremental pull-to-refresh sync.
     * Preserves local completion state and user tags, applies last-write-wins.
     */
    suspend fun quickSyncCalendarEvents(events: List<Task>) {
        if (events.isEmpty()) return
        val existingTasks = dao.getAllCalendarTasks().associateBy { it.id }
        val eventsToInsert = events.mapNotNull { event ->
            val existing = existingTasks[event.id]
            if (existing != null) {
                if (event.updatedTimestamp >= existing.updatedTimestamp) {
                    event.copy(
                        isCompleted = existing.isCompleted,
                        completedTimestamp = existing.completedTimestamp,
                        tags = mergeTagsWithSource(existing.tags, "Google Calendar"),
                        priority = existing.priority
                    )
                } else {
                    null
                }
            } else {
                event
            }
        }
        if (eventsToInsert.isNotEmpty()) {
            dao.insertTasks(eventsToInsert)
        }
    }

    /**
     * Remove all Google Tasks from local database (when unlinking).
     */
    suspend fun clearGoogleTasks() {
        dao.deleteAllGoogleTasks()
    }
}
