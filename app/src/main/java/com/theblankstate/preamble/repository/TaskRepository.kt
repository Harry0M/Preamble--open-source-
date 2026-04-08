package com.theblankstate.preamble.repository

import android.util.Log
import com.google.gson.Gson
import com.theblankstate.preamble.data.Task
import com.theblankstate.preamble.data.TaskDao
import com.theblankstate.preamble.data.TaskInputValidator
import com.theblankstate.preamble.data.TaskTagOverride
import com.theblankstate.preamble.recurrence.RecurrenceGenerator
import com.theblankstate.preamble.sync.FirebaseTaskSyncManager
import com.theblankstate.preamble.sync.MirrorParitySummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TaskRepository(
    private val dao: TaskDao,
    private val syncManager: FirebaseTaskSyncManager? = null
) {

    // Track recently deleted IDs to prevent sync from re-inserting them
    private val recentlyDeletedIds = mutableSetOf<String>()

    fun markAsDeleted(id: String) {
        synchronized(recentlyDeletedIds) { recentlyDeletedIds.add(id) }
    }

    fun clearDeletedId(id: String) {
        synchronized(recentlyDeletedIds) { recentlyDeletedIds.remove(id) }
    }

    private fun isRecentlyDeleted(id: String): Boolean {
        return synchronized(recentlyDeletedIds) { id in recentlyDeletedIds }
    }

    val tasksFlow: Flow<List<Task>> = dao.getAllTasksFlow()
    
    suspend fun getTaskById(id: String): Task? = dao.getTaskById(id)

    fun getTasksForDate(date: String): Flow<List<Task>> = dao.getTasksByDate(date)

    fun getTasksForDates(dates: List<String>): Flow<List<Task>> = dao.getTasksForDates(dates, dates.maxOrNull() ?: "")

    /**
     * COMBINED: Load heatMap + tasksByDay in a single pass.
     * Uses batch queries (3 total instead of 22+):
     * 1. getTasksForDatesSync → physical tasks (incl. rollover)
     * 2. getAllRecurrenceTemplates → templates
     * 3. getAllRecurrenceInstances → ALL instances at once (no N+1)
     * HeatMap is derived from tasksByDay (includes rollover + virtual instances).
     */
    suspend fun getMonthDataCombined(year: Int, month: Int): Pair<Map<Int, Pair<Int, Int>>, Map<Int, List<Task>>> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val calendar = Calendar.getInstance()
        calendar.set(year, month, 1)
        val maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val dates = (1..maxDay).map { day -> calendar.set(year, month, day); sdf.format(calendar.time) }
        val fromDate = dates.first(); val toDate = dates.last()

        // 2-3 queries total (down from 22+)
        val allTasks = dao.getTasksForDatesSync(dates, toDate)
        android.util.Log.d("ROLLOVER", "getMonthDataCombined($year-${month+1}): loaded ${allTasks.size} tasks total, ${allTasks.count { it.recurrenceType == "rollover" }} rollover (toDate=$toDate)")
        val templates = dao.getAllRecurrenceTemplates().filter {
            it.source != "google_calendar" && it.recurrenceType != "rollover"
        }
        // Scoped query: only load instances for our local templates (not all 2000+)
        val templateIds = templates.map { it.id }
        val instancesByParent = if (templateIds.isNotEmpty()) {
            dao.getRecurrenceInstancesForParents(templateIds).groupBy { it.recurrenceParentId }
        } else emptyMap()

        // ── Compute tasksByDay ──
        val tasksByDay = mutableMapOf<Int, MutableList<Task>>()
        val today = todayString()
        // Track rollover tasks we've already processed (SQL returns them once but we spread across days)
        val processedRolloverIds = mutableSetOf<String>()

        for (task in allTasks) {
            // ── Rollover tasks: sticky single tasks, NOT recurrence templates ──
            // Must be checked BEFORE isRecurrenceTemplate (which returns true for rollover)
            if (task.recurrenceType == "rollover") {
                if (task.id in processedRolloverIds) continue
                processedRolloverIds.add(task.id)
                val displayTask = task.copy(
                    recurrenceType = null, recurrenceInterval = null,
                    recurrenceDays = null, recurrenceEndDate = null
                )
                if (!task.isCompleted) {
                    // Pending rollover: show on every day from creation through today (within this month)
                    // Use string comparison to avoid calendar.time carrying wall-clock hours past midnight
                    var addedDays = 0
                    for (day in 1..maxDay) {
                        calendar.set(year, month, day)
                        val dayStr = sdf.format(calendar.time)
                        if (dayStr >= task.createdDate && dayStr <= today) {
                            tasksByDay.getOrPut(day) { mutableListOf() }.add(displayTask)
                            addedDays++
                        }
                    }
                    android.util.Log.d("ROLLOVER", "getMonthDataCombined: task='${task.title}' createdDate=${task.createdDate} today=$today → spread across $addedDays day(s) in $year-${month+1}")
                } else if (task.completedDate != null) {
                    // Completed rollover: show only on completion date
                    try {
                        val d = sdf.parse(task.completedDate!!); val c = Calendar.getInstance(); c.time = d!!
                        if (c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) == month) {
                            tasksByDay.getOrPut(c.get(Calendar.DAY_OF_MONTH)) { mutableListOf() }.add(displayTask)
                            android.util.Log.d("ROLLOVER", "getMonthDataCombined: completed task='${task.title}' → shown on day ${c.get(Calendar.DAY_OF_MONTH)}")
                        }
                    } catch (_: Exception) { }
                }
                continue
            }

            // ── Recurrence templates: show only on creation date (virtual expansion handles other dates) ──
            if (task.isRecurrenceTemplate) {
                val templateDay = try {
                    val d = sdf.parse(task.createdDate); val c = Calendar.getInstance(); c.time = d!!
                    if (c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) == month) c.get(Calendar.DAY_OF_MONTH) else null
                } catch (_: Exception) { null }
                if (templateDay != null) {
                    tasksByDay.getOrPut(templateDay) { mutableListOf() }.add(task.copy(
                        recurrenceType = null, recurrenceInterval = null, recurrenceDays = null, recurrenceEndDate = null
                    ))
                }
                continue
            }

            // ── Normal tasks (local, Google Calendar, Google Tasks) ──
            val taskDay = try {
                val d = sdf.parse(task.createdDate); val c = Calendar.getInstance(); c.time = d!!
                if (c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) == month) c.get(Calendar.DAY_OF_MONTH) else null
            } catch (_: Exception) { null }
            if (taskDay != null) tasksByDay.getOrPut(taskDay) { mutableListOf() }.add(task)
        }

        // ── Compute virtual recurrence instances + heatMap in same loop ──
        val virtualCounts = mutableMapOf<Int, Int>()
        for (template in templates) {
            val existingDates = (instancesByParent[template.id]?.map { it.createdDate }?.toSet() ?: emptySet()) + template.createdDate
            val virtualDates = RecurrenceGenerator.generateDates(template, fromDate, toDate)
            for (date in virtualDates) {
                if (date in existingDates) continue
                val parsed = sdf.parse(date) ?: continue
                val dayCal = Calendar.getInstance().apply { time = parsed }
                val day = dayCal.get(Calendar.DAY_OF_MONTH)
                virtualCounts[day] = (virtualCounts[day] ?: 0) + 1
                // Also add virtual task to tasksByDay
                val virtualTask = template.copy(
                    id = "vri_${template.id}_$date", createdDate = date,
                    isCompleted = false, completedTimestamp = null, completedDate = null,
                    recurrenceType = null, recurrenceInterval = null, recurrenceDays = null, recurrenceEndDate = null,
                    recurrenceParentId = template.id
                )
                tasksByDay.getOrPut(day) { mutableListOf() }.add(virtualTask)
            }
        }

        // ── Build heatMap directly from tasksByDay (source of truth) ──
        // This is more accurate than getStatsForDates because it includes:
        // - Rollover tasks (which getStatsForDates misses for pending state)
        // - Virtual recurrence instances
        val heatMap = mutableMapOf<Int, Pair<Int, Int>>()
        for (day in 1..maxDay) {
            val dayTasks = tasksByDay[day] ?: continue
            if (dayTasks.isNotEmpty()) {
                val completed = dayTasks.count { it.isCompleted }
                heatMap[day] = completed to dayTasks.size
            }
        }

        return heatMap to tasksByDay
    }

    fun getCompletedCount(): Flow<Int> = dao.getCompletedTasksCount()

    fun getTotalCount(): Flow<Int> = dao.getTotalTasksCount()

    suspend fun addTask(title: String, date: String? = null, deadlineTime: String? = null, priority: Int = 0, description: String? = null, tags: String? = null): Task? {
        val normalizedTitle = TaskInputValidator.normalizeTitle(title)
        val normalizedDescription = TaskInputValidator.normalizeDescription(description)
        if (!TaskInputValidator.isValidTitle(normalizedTitle) || !TaskInputValidator.isValidDescription(normalizedDescription)) {
            return null
        }
        val taskDate = date ?: todayString()
        val now = System.currentTimeMillis()
        val task = Task(
            title = normalizedTitle,
            createdDate = taskDate,
            deadlineTime = deadlineTime,
            createdTimestamp = now,
            updatedTimestamp = now,
            priority = priority,
            description = normalizedDescription,
            tags = tags
        )
        dao.insertTask(task)
        syncManager?.pushTask(task)
        return task
    }

    suspend fun toggleTask(task: Task) {
        val gson = Gson()
        val updatedSubtasks = if (!task.isCompleted) { // becoming completed
            task.subtasks.map { it.copy(isCompleted = true) }
        } else {
            task.subtasks // leave as is when unchecking
        }
        val isBecomingCompleted = !task.isCompleted
        val now = System.currentTimeMillis()
        val updated = task.copy(
            isCompleted = isBecomingCompleted,
            completedTimestamp = if (isBecomingCompleted) now else null,
            completedDate = if (isBecomingCompleted && task.recurrenceType == "rollover") todayString() else null,
            updatedTimestamp = now,
            subtasksJson = gson.toJson(updatedSubtasks),
            // Clear snooze when completing a snoozed task
            snoozedUntil = if (isBecomingCompleted) null else task.snoozedUntil
        )
        dao.updateTask(updated)
        syncManager?.pushTask(updated)

        // When completing a parent task, also complete all child subtask rows
        if (isBecomingCompleted) {
            val childSubtasks = dao.getSubtasksForParentSync(task.id)
            childSubtasks.forEach { subtask ->
                if (!subtask.isCompleted) {
                    val updatedSubtask = subtask.copy(
                        isCompleted = true,
                        completedTimestamp = now,
                        updatedTimestamp = now
                    )
                    dao.updateTask(updatedSubtask)
                    syncManager?.pushTask(updatedSubtask)
                }
            }
        }

        // If this task is a subtask, update parent completion status
        task.parentTaskId?.let { parentId ->
            updateParentTaskCompletion(parentId)
        }
    }

    suspend fun insertTask(task: Task) {
        val normalized = normalizeForStorage(task) ?: return
        dao.insertTask(normalized)
        syncManager?.pushTask(normalized)
    }

    suspend fun updateTask(task: Task) {
        val normalized = normalizeForStorage(task) ?: return
        dao.updateTask(normalized)
        syncManager?.pushTask(normalized)
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

    suspend fun getSubtasksForParentSync(parentId: String): List<Task> = dao.getSubtasksForParentSync(parentId)

    suspend fun getInstancesForTemplate(templateId: String): List<Task> = dao.getInstancesForTemplate(templateId)

    suspend fun addSubtasks(parentId: String, titles: List<String>): List<Task> {
        if (titles.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val parent = dao.getTaskById(parentId) ?: return emptyList()
        
        val subtasks = titles.mapNotNull { title ->
            val normalizedTitle = TaskInputValidator.normalizeTitle(title)
            if (!TaskInputValidator.isValidTitle(normalizedTitle)) null
            else Task(
                title = normalizedTitle,
                createdDate = parent.createdDate,
                createdTimestamp = now,
                updatedTimestamp = now,
                parentTaskId = parentId
            )
        }
        if (subtasks.isNotEmpty()) {
            dao.insertTasks(subtasks)
            subtasks.forEach { syncManager?.pushTask(it) }
        }
        return subtasks
    }

    suspend fun addSubtask(parentId: String, title: String): Task? {
        val results = addSubtasks(parentId, listOf(title))
        return results.firstOrNull()
    }

    suspend fun getSubtaskStats(parentIds: List<String>): Map<String, Pair<Int, Int>> {
        if (parentIds.isEmpty()) return emptyMap()
        return dao.getSubtaskStatsForParents(parentIds).associate {
            it.parentTaskId to (it.completed to it.total)
        }
    }

    fun observeSubtaskStats(parentIds: List<String>): Flow<Map<String, Pair<Int, Int>>> {
        if (parentIds.isEmpty()) return flowOf(emptyMap())
        return dao.observeSubtaskStatsForParents(parentIds).map { list ->
            list.associate { it.parentTaskId to (it.completed to it.total) }
        }
    }

    // ── Subtask completion business logic ──
    
    suspend fun updateSubtaskCompletion(subtaskId: String, isCompleted: Boolean): Task? {
        val subtask = dao.getTaskById(subtaskId) ?: return null
        val updatedSubtask = subtask.copy(
            isCompleted = isCompleted,
            completedTimestamp = if (isCompleted) System.currentTimeMillis() else null,
            updatedTimestamp = System.currentTimeMillis()
        )
        dao.updateTask(updatedSubtask)
        syncManager?.pushTask(updatedSubtask)
        
        // Check if we need to update parent task completion
        subtask.parentTaskId?.let { parentId ->
            updateParentTaskCompletion(parentId)
        }
        
        return updatedSubtask
    }
    
    suspend fun updateParentTaskCompletion(parentId: String) {
        val parent = dao.getTaskById(parentId) ?: return
        val subtasks = dao.getSubtasksForParentSync(parentId)
        
        // Auto-completion rules:
        // 1. If parent is marked completed, all subtasks should be completed
        // 2. If all subtasks are completed, parent should be completed
        // 3. If any subtask is incomplete, parent should be incomplete (unless manually overridden)
        
        val allSubtasksCompleted = subtasks.isNotEmpty() && subtasks.all { it.isCompleted }
        val shouldCompleteParent = allSubtasksCompleted
        
        if (parent.isCompleted != shouldCompleteParent) {
            val now = System.currentTimeMillis()
            val isRollover = parent.recurrenceType == "rollover"
            val updatedParent = parent.copy(
                isCompleted = shouldCompleteParent,
                completedTimestamp = if (shouldCompleteParent) now else null,
                completedDate = if (shouldCompleteParent && isRollover) todayString() else if (!shouldCompleteParent) null else parent.completedDate,
                updatedTimestamp = now
            )
            dao.updateTask(updatedParent)
            syncManager?.pushTask(updatedParent)
        }
    }
    
    suspend fun completeAllSubtasks(parentId: String) {
        val subtasks = dao.getSubtasksForParentSync(parentId)
        val now = System.currentTimeMillis()
        
        subtasks.forEach { subtask ->
            if (!subtask.isCompleted) {
                val updatedSubtask = subtask.copy(
                    isCompleted = true,
                    completedTimestamp = now,
                    updatedTimestamp = now
                )
                dao.updateTask(updatedSubtask)
                syncManager?.pushTask(updatedSubtask)
            }
        }
        
        // Mark parent as completed
        updateParentTaskCompletion(parentId)
    }
    
    suspend fun deleteSubtask(subtaskId: String) {
        val subtask = dao.getTaskById(subtaskId) ?: return
        dao.deleteTask(subtask)
        syncManager?.pushTask(subtask.copy(source = "local", deletedFromGoogle = true))
        
        // Update parent completion status
        subtask.parentTaskId?.let { parentId ->
            updateParentTaskCompletion(parentId)
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
        recurrenceEndDate: String? = null,
        tags: String? = null
    ): Task? {
        val normalizedTitle = TaskInputValidator.normalizeTitle(title)
        val normalizedDescription = TaskInputValidator.normalizeDescription(description)
        if (!TaskInputValidator.isValidTitle(normalizedTitle) || !TaskInputValidator.isValidDescription(normalizedDescription)) {
            return null
        }
        val taskDate = date ?: todayString()
        val now = System.currentTimeMillis()
        val template = Task(
            title = normalizedTitle,
            createdDate = taskDate,
            deadlineTime = deadlineTime,
            createdTimestamp = now,
            updatedTimestamp = now,
            priority = priority,
            description = normalizedDescription,
            recurrenceType = recurrenceType,
            recurrenceInterval = recurrenceInterval,
            recurrenceDays = recurrenceDays,
            recurrenceEndDate = recurrenceEndDate,
            tags = tags
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

    suspend fun verifyFirebaseFirestoreParity(): MirrorParitySummary? {
        return syncManager?.runParityCheckNow()
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
     * Now includes virtual recurring task instances for accurate calendar display.
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
        val today = todayString()

        // Count rollover tasks per day (getStatsForDates misses pending rollover)
        val rolloverCounts = mutableMapOf<Int, Pair<Int, Int>>() // day → (completed, total)
        val allTasks = dao.getTasksForDatesSync(dates, dates.last())
        val processedRolloverIds = mutableSetOf<String>()
        for (task in allTasks) {
            if (task.recurrenceType != "rollover") continue
            if (task.id in processedRolloverIds) continue
            processedRolloverIds.add(task.id)
            if (!task.isCompleted) {
                for (day in 1..maxDay) {
                    calendar.set(year, month, day)
                    val dayStr = sdf.format(calendar.time)
                    if (dayStr >= task.createdDate && dayStr <= today) {
                        val prev = rolloverCounts[day] ?: (0 to 0)
                        rolloverCounts[day] = prev.first to (prev.second + 1)
                    }
                }
            } else if (task.completedDate != null) {
                try {
                    val d = sdf.parse(task.completedDate!!); val c = Calendar.getInstance(); c.time = d!!
                    if (c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) == month) {
                        val day = c.get(Calendar.DAY_OF_MONTH)
                        val prev = rolloverCounts[day] ?: (0 to 0)
                        rolloverCounts[day] = (prev.first + 1) to (prev.second + 1)
                    }
                } catch (_: Exception) { }
            }
        }

        // Virtual expansion: count recurring instances that don't have physical rows
        val templates = dao.getAllRecurrenceTemplates().filter {
            it.source != "google_calendar" && it.recurrenceType != "rollover"
        }
        val templateIds = templates.map { it.id }
        val instancesByParent = if (templateIds.isNotEmpty()) {
            dao.getRecurrenceInstancesForParents(templateIds).groupBy { it.recurrenceParentId }
        } else emptyMap()

        val virtualCounts = mutableMapOf<Int, Int>()
        for (template in templates) {
            val templateExistingDates = (instancesByParent[template.id]?.map { it.createdDate }?.toSet() ?: emptySet()) + template.createdDate
            val virtualDates = RecurrenceGenerator.generateDates(template, dates.first(), dates.last())
            for (date in virtualDates) {
                if (date in templateExistingDates) continue
                val parsed = sdf.parse(date) ?: continue
                val dayCal = Calendar.getInstance().apply { time = parsed }
                val day = dayCal.get(Calendar.DAY_OF_MONTH)
                virtualCounts[day] = (virtualCounts[day] ?: 0) + 1
            }
        }

        val result = mutableMapOf<Int, Pair<Int, Int>>()
        for (day in 1..maxDay) {
            calendar.set(year, month, day)
            val dateStr = sdf.format(calendar.time)
            val stats = statsMap[dateStr]
            val physicalCompleted = stats?.completed ?: 0
            val physicalTotal = stats?.total ?: 0
            val virtualCount = virtualCounts[day] ?: 0
            val rollover = rolloverCounts[day] ?: (0 to 0)
            val totalForDay = physicalTotal + virtualCount + rollover.second
            val completedForDay = physicalCompleted + rollover.first
            if (totalForDay > 0) {
                result[day] = completedForDay to totalForDay
            }
        }
        return result
    }

    // ── Virtual Expansion (Google-style calendar display) ──

    /**
     * Get tasks for a specific date including virtual recurring instances.
     * Used by CalendarScreen for accurate display of recurring tasks.
     * Physical rows are returned as-is; recurring templates generate virtual instances
     * that are merged into the result list.
     */
    suspend fun getTasksForDateWithRecurrence(date: String): List<Task> {
        // 1. Get physical rows for this date
        val pendingTasks = dao.getPendingTasksForDate(date)
        val completedTasks = dao.getTasksByDateSync(date).filter { it.isCompleted }
        val physicalTasks = pendingTasks + completedTasks

        // 2. Get all recurring templates (skip rollover & Google Calendar)
        val templates = dao.getAllRecurrenceTemplates().filter {
            it.source != "google_calendar" && it.recurrenceType != "rollover"
        }
        if (templates.isEmpty()) return physicalTasks

        // 3. Scoped instance query — only for our local templates
        val templateIds = templates.map { it.id }
        val instancesByParent = dao.getRecurrenceInstancesForParents(templateIds)
            .groupBy { it.recurrenceParentId }

        // 4. Generate virtual instances for templates that don't have physical rows
        val virtualInstances = mutableListOf<Task>()
        for (template in templates) {
            // Check if a physical instance already exists for this template + date (in-memory lookup)
            val templateInstances = instancesByParent[template.id] ?: emptyList()
            val alreadyExists = templateInstances.any { it.createdDate == date }
            // Also check if the template itself is on this date
            val isTemplateDate = template.createdDate == date

            if (!alreadyExists && !isTemplateDate) {
                virtualInstances.addAll(
                    RecurrenceGenerator.generateVirtualInstances(template, date, date)
                )
            }
        }

        // 5. Merge: physical tasks first, then virtual instances
        val displayTasks = physicalTasks.mapNotNull { task ->
            when {
                task.recurrenceType == "rollover" -> task.copy(
                    recurrenceType = null, recurrenceInterval = null,
                    recurrenceDays = null, recurrenceEndDate = null
                )
                task.isRecurrenceTemplate && task.createdDate == date -> task.copy(
                    recurrenceType = null, recurrenceInterval = null,
                    recurrenceDays = null, recurrenceEndDate = null
                )
                task.isRecurrenceTemplate -> null
                else -> task
            }
        } + virtualInstances
        return displayTasks.sortedWith(
            compareBy<Task> { it.isCompleted }
                .thenByDescending { it.priority }
                .thenByDescending { it.createdTimestamp }
        )
    }

    /**
     * Materialize a virtual recurring instance into a physical DB row.
     * Called when the user interacts with a virtual instance (e.g. toggle complete).
     * Returns the materialized task if successful, null otherwise.
     */
    suspend fun materializeVirtualInstance(virtualTask: Task): Task? {
        val parsed = RecurrenceGenerator.parseVirtualInstanceId(virtualTask.id) ?: return null
        val (templateId, date) = parsed
        Log.d("Recurrence", "Materialize: templateId=$templateId, date=$date")

        // Check if already materialized
        val existing = dao.getTaskById(virtualTask.id)
        if (existing != null) return existing

        // Also check by parent + date to prevent duplicates
        if (dao.instanceExistsForDate(templateId, date) > 0) {
            return null
        }

        // Create a real task from the virtual instance with a real UUID
        val template = dao.getTaskById(templateId) ?: return null
        val now = System.currentTimeMillis()
        val materializedTask = template.copy(
            id = java.util.UUID.randomUUID().toString(),
            createdDate = date,
            isCompleted = false,
            completedTimestamp = null,
            completedDate = null,
            createdTimestamp = now,
            updatedTimestamp = now,
            recurrenceType = null,
            recurrenceInterval = null,
            recurrenceDays = null,
            recurrenceEndDate = null,
            recurrenceParentId = templateId
        )
        dao.insertTask(materializedTask)
        syncManager?.pushTask(materializedTask)
        return materializedTask
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

    /**
     * Snooze a rollover task for the given duration in milliseconds.
     * The task will be hidden from the daily view until snoozedUntil expires.
     */
    suspend fun snoozeTask(taskId: String, durationMs: Long) {
        val task = dao.getTaskById(taskId) ?: return
        val updated = task.copy(
            snoozedUntil = System.currentTimeMillis() + durationMs,
            updatedTimestamp = System.currentTimeMillis()
        )
        dao.updateTask(updated)
        syncManager?.pushTask(updated)
    }

    /**
     * Remove snooze from a task, making it immediately active again.
     */
    suspend fun unsnoozeTask(taskId: String) {
        val task = dao.getTaskById(taskId) ?: return
        val updated = task.copy(
            snoozedUntil = null,
            updatedTimestamp = System.currentTimeMillis()
        )
        dao.updateTask(updated)
        syncManager?.pushTask(updated)
    }

    /**
     * Auto-escalate priority for pending rollover tasks based on age.
     * 3+ days → at least Low (1), 7+ days → at least Medium (2), 14+ days → High (3).
     * Only bumps up, never lowers a user-set priority.
     */
    suspend fun escalateRolloverPriorities() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = sdf.parse(todayString()) ?: return
        val pendingRollovers = dao.getPendingRolloverTasks()

        for (task in pendingRollovers) {
            val created = sdf.parse(task.createdDate) ?: continue
            val days = ((today.time - created.time) / (1000 * 60 * 60 * 24)).toInt()
            val minPriority = when {
                days >= 14 -> 3 // High
                days >= 7 -> 2  // Medium
                days >= 3 -> 1  // Low
                else -> 0
            }
            if (task.priority < minPriority) {
                val updated = task.copy(
                    priority = minPriority,
                    updatedTimestamp = System.currentTimeMillis()
                )
                dao.updateTask(updated)
                syncManager?.pushTask(updated)
            }
        }
    }

    private fun normalizeForStorage(task: Task): Task? {
        val normalizedTitle = TaskInputValidator.normalizeTitle(task.title)
        val normalizedDescription = TaskInputValidator.normalizeDescription(task.description)
        if (!TaskInputValidator.isValidTitle(normalizedTitle) || !TaskInputValidator.isValidDescription(normalizedDescription)) {
            Log.w(
                TAG,
                "Rejected task write due to length limits: id=${task.id} titleLen=${task.title.length} descLen=${task.description?.length ?: 0}"
            )
            return null
        }
        return task.copy(
            title = normalizedTitle,
            description = normalizedDescription
        )
    }

    companion object {
        private const val TAG = "TaskRepository"

        fun todayString(): String {
            return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        }
    }

    // ── Tag Override Helpers ──

    /**
     * Extract the stable Google ID from an app task ID.
     * "gcal_abc123" → "abc123", "gtask_xyz" → "xyz"
     */
    private fun extractGoogleId(taskId: String): String? {
        return when {
            taskId.startsWith("gcal_") -> taskId.removePrefix("gcal_")
            taskId.startsWith("gtask_") -> taskId.removePrefix("gtask_")
            else -> null
        }
    }

    /**
     * Build the final tags string for a Google-sourced task by:
     * 1. Looking up any user-assigned tags from the tag override store
     * 2. For recurring instances, falling back to the parent event's override
     * 3. Merging existing tags from the local task if present
     */
    private suspend fun buildTagsForGoogleTask(
        taskId: String,
        existingTags: String? = null,
        parentTaskId: String? = null
    ): String {
        val googleId = extractGoogleId(taskId)
        // First try direct lookup, then fall back to parent event ID for recurring instances
        var overrideTags = if (googleId != null) {
            dao.getTagOverride(googleId)?.tags
        } else null

        if (overrideTags == null && parentTaskId != null) {
            val parentGoogleId = extractGoogleId(parentTaskId)
            if (parentGoogleId != null) {
                overrideTags = dao.getTagOverride(parentGoogleId)?.tags
            }
        }

        val tagSet = mutableSetOf<String>()
        // Add override tags (user-assigned, persisted)
        overrideTags?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.let { tagSet.addAll(it) }
        // Add any existing tags from the local task (strip old source tags)
        existingTags?.split(",")?.map { it.trim() }?.filter {
            it.isNotEmpty() && it != "Google Calendar" && it != "Google Tasks"
        }?.let { tagSet.addAll(it) }
        return if (tagSet.isEmpty()) "" else tagSet.joinToString(",")
    }

    /**
     * Save a tag override for a Google-sourced task.
     * Strips the source tag, stores only user-assigned tags.
     * Pushes to Firebase for cross-device sync.
     */
    suspend fun saveTagOverride(taskId: String, tags: String?) {
        val googleId = extractGoogleId(taskId) ?: return
        // Strip source tags, keep only user-assigned tags
        val userTags = tags?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && it != "Google Calendar" && it != "Google Tasks" }
            ?.joinToString(",") ?: ""

        if (userTags.isEmpty()) {
            dao.deleteTagOverride(googleId)
            syncManager?.deleteTagOverride(googleId)
        } else {
            val override = TaskTagOverride(
                googleId = googleId,
                tags = userTags,
                updatedTimestamp = System.currentTimeMillis()
            )
            dao.upsertTagOverride(override)
            syncManager?.pushTagOverride(googleId, userTags)
        }
    }

    // ── Google Calendar Sync ──

    /**
     * Full sync Google Calendar events into the local database.
     * Uses last-write-wins conflict resolution: only upsert if remote is newer.
     * Preserves local completion state, user tags, priority, and description.
     * Removed events are deleted (unless completed locally).
     */
    suspend fun syncCalendarEvents(events: List<Task>) {
        val existingTasks = dao.getAllCalendarTasks().associateBy { it.id }

        // Handle cancelled recurring instances first — delete them from local DB
        val cancelledEvents = events.filter { it.deletedFromGoogle }
        for (cancelled in cancelledEvents) {
            val existing = existingTasks[cancelled.id]
            if (existing != null) {
                dao.deleteTask(existing)
            }
        }

        // Filter to active (non-cancelled) events for normal sync
        val activeEvents = events.filter { !it.deletedFromGoogle }

        // Delete orphaned local master events: if we receive instances of a recurring event,
        // we should remove the original locally-created master event so it doesn't duplicate the instances.
        val parentIdsFromInstances = activeEvents.mapNotNull { it.recurrenceParentId }.toSet()
        if (parentIdsFromInstances.isNotEmpty()) {
            val parentsToRemove = existingTasks.values.filter { it.id in parentIdsFromInstances }
            for (parent in parentsToRemove) {
                dao.deleteTask(parent)
            }
        }

        val existingCalendarIds = dao.getAllCalendarTaskIds().toSet()
        val newEventIds = activeEvents.map { it.id }.toSet()

        val eventsToInsert = activeEvents.filter { !isRecentlyDeleted(it.id) }.mapNotNull { event ->
            val existing = existingTasks[event.id]
            if (existing != null) {
                // Last-write-wins: only update if remote is newer or equal
                if (event.updatedTimestamp >= existing.updatedTimestamp) {
                    event.copy(
                        isCompleted = existing.isCompleted,
                        completedTimestamp = existing.completedTimestamp,
                        tags = buildTagsForGoogleTask(event.id, existing.tags, event.recurrenceParentId),
                        priority = existing.priority,
                        // Preserve local-only fields that Google doesn't have
                        recurrenceType = event.recurrenceType ?: existing.recurrenceType,
                        recurrenceInterval = event.recurrenceInterval ?: existing.recurrenceInterval,
                        recurrenceDays = event.recurrenceDays ?: existing.recurrenceDays,
                        recurrenceEndDate = event.recurrenceEndDate ?: existing.recurrenceEndDate,
                        recurrenceParentId = event.recurrenceParentId ?: existing.recurrenceParentId,
                        parentTaskId = existing.parentTaskId,
                        googleRecurrenceInfo = event.googleRecurrenceInfo ?: existing.googleRecurrenceInfo
                    )
                } else {
                    null // skip — local is newer
                }
            } else {
                // New event — apply tag overrides if any exist
                event.copy(
                    tags = buildTagsForGoogleTask(event.id, parentTaskId = event.recurrenceParentId)
                )
            }
        }

        if (eventsToInsert.isNotEmpty()) {
            dao.insertTasks(eventsToInsert)
        }

        // Delete events that are no longer in the calendar (skip completed ones user marked locally)
        // Also exclude cancelled event IDs from removal check (they're already handled above)
        val cancelledIds = cancelledEvents.map { it.id }.toSet()
        val removedIds = existingCalendarIds - newEventIds - cancelledIds
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

        // Also handle explicit deletedFromGoogle markers from fetch
        val explicitlyDeletedTasks = tasks.filter { it.deletedFromGoogle }
        for (deletedTask in explicitlyDeletedTasks) {
            val existing = existingTasks[deletedTask.id]
            if (existing != null) {
                if (autoDeleteFromApp) {
                    dao.deleteTask(existing)
                    syncManager?.deleteTask(existing.id)
                } else {
                    dao.updateTask(existing.copy(deletedFromGoogle = true, updatedTimestamp = System.currentTimeMillis()))
                }
            }
        }

        // Insert or update tasks with last-write-wins (only processing non-deleted active tasks)
        val activeTasks = tasks.filter { !it.deletedFromGoogle }
        val tasksToInsert = activeTasks.filter { !isRecentlyDeleted(it.id) }.mapNotNull { task ->
            val existing = existingTasks[task.id]
            if (existing != null) {
                if (task.updatedTimestamp >= existing.updatedTimestamp) {
                    task.copy(
                        tags = buildTagsForGoogleTask(task.id, existing.tags, task.recurrenceParentId),
                        priority = existing.priority,
                        description = existing.description ?: task.description,
                        // Preserve local-only fields that Google doesn't have
                        recurrenceType = existing.recurrenceType,
                        recurrenceInterval = existing.recurrenceInterval,
                        recurrenceDays = existing.recurrenceDays,
                        recurrenceEndDate = existing.recurrenceEndDate,
                        recurrenceParentId = existing.recurrenceParentId,
                        parentTaskId = existing.parentTaskId
                    )
                } else {
                    null // skip — local is newer
                }
            } else {
                // New task — apply tag overrides if any exist
                task.copy(
                    tags = buildTagsForGoogleTask(task.id, parentTaskId = task.recurrenceParentId)
                )
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
    suspend fun quickSyncGoogleTasks(tasks: List<Task>, autoDeleteFromApp: Boolean = false) {
        if (tasks.isEmpty()) return
        val existingTasks = dao.getAllGoogleTasks().associateBy { it.id }

        // Handle deleted tasks — remove them or mark them from local DB
        val deletedTasks = tasks.filter { it.deletedFromGoogle }
        for (deleted in deletedTasks) {
            val existing = existingTasks[deleted.id]
            if (existing != null) {
                if (autoDeleteFromApp) {
                    dao.deleteTask(existing)
                    syncManager?.deleteTask(existing.id)
                } else {
                    dao.updateTask(existing.copy(deletedFromGoogle = true, updatedTimestamp = System.currentTimeMillis()))
                }
            }
        }

        // Process active tasks (not deleted)
        val activeTasks = tasks.filter { !it.deletedFromGoogle }
        val tasksToInsert = activeTasks.filter { !isRecentlyDeleted(it.id) }.mapNotNull { task ->
            val existing = existingTasks[task.id]
            if (existing != null) {
                if (task.updatedTimestamp >= existing.updatedTimestamp) {
                    task.copy(
                        tags = buildTagsForGoogleTask(task.id, existing.tags, task.recurrenceParentId),
                        recurrenceType = existing.recurrenceType,
                        recurrenceInterval = existing.recurrenceInterval,
                        recurrenceDays = existing.recurrenceDays,
                        recurrenceEndDate = existing.recurrenceEndDate,
                        recurrenceParentId = existing.recurrenceParentId,
                        parentTaskId = existing.parentTaskId,
                        priority = existing.priority
                    )
                } else {
                    null
                }
            } else {
                // New task — apply tag overrides if any exist
                task.copy(
                    tags = buildTagsForGoogleTask(task.id, parentTaskId = task.recurrenceParentId)
                )
            }
        }
        if (tasksToInsert.isNotEmpty()) {
            dao.insertTasks(tasksToInsert)
        }
    }

    /**
     * Quick sync for calendar events: upsert changed + handle cancelled instances.
     * Used for incremental pull-to-refresh sync.
     * Preserves local completion state and user tags, applies last-write-wins.
     */
    suspend fun quickSyncCalendarEvents(events: List<Task>) {
        if (events.isEmpty()) return
        val existingTasks = dao.getAllCalendarTasks().associateBy { it.id }

        // Handle cancelled recurring instances — delete them from local DB
        val cancelledEvents = events.filter { it.deletedFromGoogle }
        for (cancelled in cancelledEvents) {
            val existing = existingTasks[cancelled.id]
            if (existing != null) {
                dao.deleteTask(existing)
            }
        }

        // Process active (non-cancelled) events
        val activeEvents = events.filter { !it.deletedFromGoogle }

        // Delete orphaned local master events: if we receive instances of a recurring event,
        // we should remove the original locally-created master event so it doesn't duplicate the instances.
        val parentIdsFromInstances = activeEvents.mapNotNull { it.recurrenceParentId }.toSet()
        if (parentIdsFromInstances.isNotEmpty()) {
            val parentsToRemove = existingTasks.values.filter { it.id in parentIdsFromInstances }
            for (parent in parentsToRemove) {
                dao.deleteTask(parent)
            }
        }

        val eventsToInsert = activeEvents.filter { !isRecentlyDeleted(it.id) }.mapNotNull { event ->
            val existing = existingTasks[event.id]
            if (existing != null) {
                if (event.updatedTimestamp >= existing.updatedTimestamp) {
                    event.copy(
                        isCompleted = existing.isCompleted,
                        completedTimestamp = existing.completedTimestamp,
                        tags = buildTagsForGoogleTask(event.id, existing.tags, event.recurrenceParentId),
                        priority = existing.priority,
                        // Preserve local-only fields
                        recurrenceType = event.recurrenceType ?: existing.recurrenceType,
                        recurrenceInterval = event.recurrenceInterval ?: existing.recurrenceInterval,
                        recurrenceDays = event.recurrenceDays ?: existing.recurrenceDays,
                        recurrenceEndDate = event.recurrenceEndDate ?: existing.recurrenceEndDate,
                        recurrenceParentId = event.recurrenceParentId ?: existing.recurrenceParentId,
                        parentTaskId = existing.parentTaskId,
                        googleRecurrenceInfo = event.googleRecurrenceInfo ?: existing.googleRecurrenceInfo
                    )
                } else {
                    null
                }
            } else {
                // New event — apply tag overrides if any exist
                event.copy(
                    tags = buildTagsForGoogleTask(event.id, parentTaskId = event.recurrenceParentId)
                )
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
