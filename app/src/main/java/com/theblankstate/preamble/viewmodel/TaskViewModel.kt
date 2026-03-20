package com.theblankstate.preamble.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.theblankstate.preamble.data.Task
import com.theblankstate.preamble.notification.TaskNotificationManager
import com.theblankstate.preamble.repository.TaskRepository
import com.theblankstate.preamble.sync.GoogleCalendarManager
import com.theblankstate.preamble.sync.GoogleTasksManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class StatsState(
    val totalCompleted: Int = 0,
    val totalTasks: Int = 0,
    val todayCompleted: Int = 0,
    val todayTotal: Int = 0,
    val streak: Int = 0,
    val weeklyStats: List<Pair<String, Float>> = emptyList(),
    val dailyCompleted: List<Pair<String, Int>> = emptyList(),
    val monthlyCompleted: List<Pair<String, Int>> = emptyList()
)

class TaskViewModel(
    private val repository: TaskRepository,
    private val appContext: Context
) : ViewModel() {

    private val today = TaskRepository.todayString()

    // Tag filter
    private val _selectedTagFilter = MutableStateFlow<String?>(null)
    val selectedTagFilter: StateFlow<String?> = _selectedTagFilter.asStateFlow()

    // Loading guard: suppress empty-state flash while Room hydrates
    private val _isInitialLoad = MutableStateFlow(true)
    val isInitialLoad: StateFlow<Boolean> = _isInitialLoad.asStateFlow()

    private val _allTodayTasks: StateFlow<List<Task>> = repository.getTasksForDate(today)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayTasks: StateFlow<List<Task>> = combine(
        _allTodayTasks,
        _selectedTagFilter
    ) { tasks, tagFilter ->
        if (tagFilter == null) tasks
        else tasks.filter { it.tagList.contains(tagFilter) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Subtask state
    private val _subtaskCounts = MutableStateFlow<Map<String, Pair<Int, Int>>>(emptyMap())
    val subtaskCounts: StateFlow<Map<String, Pair<Int, Int>>> = _subtaskCounts.asStateFlow()

    private val _expandedTasks = MutableStateFlow<Set<String>>(emptySet())
    val expandedTasks: StateFlow<Set<String>> = _expandedTasks.asStateFlow()

    private val past10Dates = (1..10).map { i ->
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -i)
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
    }

    val pastTasks: StateFlow<Map<String, List<Task>>> = repository.getTasksForDates(past10Dates)
        .map { tasks: List<Task> -> tasks.groupBy { it.createdDate } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    val searchResults: StateFlow<List<Task>> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(emptyList())
            else repository.searchTasks(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private val _selectedDate = MutableStateFlow<String?>(null)
    
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedDateTasks: StateFlow<List<Task>> = _selectedDate
        .flatMapLatest { dateStr: String? -> 
            if (dateStr == null) flowOf(emptyList()) else repository.getTasksForDate(dateStr) 
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectDate(date: String?) {
        _selectedDate.value = date
    }

    // Calendar heat map
    private val _calendarHeatMap = MutableStateFlow<Map<Int, Pair<Int, Int>>>(emptyMap())
    val calendarHeatMap: StateFlow<Map<Int, Pair<Int, Int>>> = _calendarHeatMap.asStateFlow()

    fun loadHeatMap(year: Int, month: Int) {
        viewModelScope.launch {
            _calendarHeatMap.value = repository.getMonthlyHeatMap(year, month)
        }
    }

    private val _statsState = MutableStateFlow(StatsState())
    val statsState: StateFlow<StatsState> = _statsState.asStateFlow()

    // Debounce stats refresh to avoid 730+ queries on rapid task changes
    private var statsRefreshJob: Job? = null

    init {
        viewModelScope.launch {
            combine(
                repository.getCompletedCount(),
                repository.getTotalCount()
            ) { completed, total ->
                completed to total
            }.collect { (completed, total) ->
                _statsState.update { it.copy(totalCompleted = completed, totalTasks = total) }
            }
        }

        viewModelScope.launch {
            todayTasks.collect { tasks ->
                // Mark initial load as done on first emission
                if (_isInitialLoad.value) _isInitialLoad.value = false
                val completed = tasks.count { it.isCompleted }
                val total = tasks.size
                _statsState.update { it.copy(todayCompleted = completed, todayTotal = total) }
            }
        }

        refreshStats()

        // Subtask count tracking
        viewModelScope.launch {
            todayTasks.collect { tasks ->
                val parentIds = tasks.map { it.id }
                _subtaskCounts.value = repository.getSubtaskStats(parentIds)
            }
        }

        // Auto-sync Google data on app launch (with 60s cooldown)
        autoSyncOnLaunch()
    }

    private fun autoSyncOnLaunch() {
        viewModelScope.launch {
            try {
                val app = appContext.applicationContext as com.theblankstate.preamble.PreambleApplication
                // Full sync Google Tasks
                if (GoogleTasksManager.isLinked.value) {
                    val lastSync = GoogleTasksManager.lastSyncTime.value
                    val now = System.currentTimeMillis()
                    if (now - lastSync > 60_000) { // 60s cooldown
                        val gTasks = GoogleTasksManager.fetchGoogleTasks(appContext) { partialTasks ->
                            app.repository.quickSyncGoogleTasks(partialTasks, GoogleTasksManager.autoDeleteGoogleTasks.value)
                        }
                        app.repository.syncGoogleTasks(gTasks, GoogleTasksManager.autoDeleteGoogleTasks.value)
                    }
                }
                // Sync Google Calendar — route based on incremental vs full
                if (GoogleCalendarManager.isLinked.value) {
                    val lastSync = GoogleCalendarManager.lastSyncTime.value
                    val now = System.currentTimeMillis()
                    if (now - lastSync > 60_000) {
                        val calResult = GoogleCalendarManager.fetchCalendarEvents(appContext) { partialEvents ->
                            app.repository.quickSyncCalendarEvents(partialEvents)
                        }
                        if (calResult.isIncremental) {
                            app.repository.quickSyncCalendarEvents(calResult.events)
                        } else {
                            app.repository.syncCalendarEvents(calResult.events)
                        }
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.e("TaskViewModel", "Auto-sync on launch failed", e)
            }
        }
    }

    // ── Pull-to-refresh Google sync ──
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val isBackgroundDeleting: StateFlow<Boolean> = androidx.work.WorkManager.getInstance(appContext)
        .getWorkInfosByTagFlow("background_deletion")
        .map { infos -> infos.any { !it.state.isFinished } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun syncGoogleData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val app = appContext.applicationContext as com.theblankstate.preamble.PreambleApplication

                // Incremental sync Google Tasks (only changes since last sync)
                if (GoogleTasksManager.isLinked.value) {
                    val lastSync = GoogleTasksManager.lastSyncTime.value
                    if (lastSync > 0) {
                        val updatedAfterRfc = com.google.api.client.util.DateTime(lastSync).toStringRfc3339()
                        val gTasks = GoogleTasksManager.fetchGoogleTasks(appContext, updatedAfter = updatedAfterRfc) { partialTasks ->
                            app.repository.quickSyncGoogleTasks(partialTasks, GoogleTasksManager.autoDeleteGoogleTasks.value)
                        }
                        app.repository.quickSyncGoogleTasks(gTasks, GoogleTasksManager.autoDeleteGoogleTasks.value)
                    } else {
                        val gTasks = GoogleTasksManager.fetchGoogleTasks(appContext) { partialTasks ->
                            app.repository.quickSyncGoogleTasks(partialTasks, GoogleTasksManager.autoDeleteGoogleTasks.value)
                        }
                        app.repository.syncGoogleTasks(gTasks, GoogleTasksManager.autoDeleteGoogleTasks.value)
                    }
                }

                // Sync Google Calendar — route based on incremental vs full
                if (GoogleCalendarManager.isLinked.value) {
                    val calResult = GoogleCalendarManager.fetchCalendarEvents(appContext) { partialEvents ->
                        app.repository.quickSyncCalendarEvents(partialEvents)
                    }
                    if (calResult.isIncremental) {
                        app.repository.quickSyncCalendarEvents(calResult.events)
                    } else {
                        app.repository.syncCalendarEvents(calResult.events)
                    }
                }

                // If neither Google service is linked, sync with Firebase instead
                if (!GoogleTasksManager.isLinked.value && !GoogleCalendarManager.isLinked.value) {
                    app.repository.forceSyncFirebase()
                }
            } catch (e: Throwable) {
                android.util.Log.e("TaskViewModel", "Pull-to-refresh sync failed", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun refreshStats() {
        // Debounce: cancel previous job, wait 500ms before running
        statsRefreshJob?.cancel()
        statsRefreshJob = viewModelScope.launch {
            delay(500)
            val streak = repository.calculateStreak()
            val weekly = repository.getWeeklyStats()
            val daily = repository.getDailyStatsForRange(14)
            val monthly = repository.getDailyStatsForRange(30)
            _statsState.update {
                it.copy(
                    streak = streak,
                    weeklyStats = weekly,
                    dailyCompleted = daily,
                    monthlyCompleted = monthly
                )
            }
        }
    }

    fun addTask(title: String, date: String? = null, deadlineTime: String? = null, syncToGoogle: Boolean = false, syncToCalendar: Boolean = false, priority: Int = 0, description: String? = null, tags: String? = null) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val taskDate = date ?: TaskRepository.todayString()
            val now = System.currentTimeMillis()
            val finalTask: com.theblankstate.preamble.data.Task
            if (syncToCalendar && GoogleCalendarManager.isLinked.value) {
                val tempId = java.util.UUID.randomUUID().toString()
                val task = com.theblankstate.preamble.data.Task(
                    id = tempId,
                    title = title,
                    createdDate = taskDate,
                    deadlineTime = deadlineTime,
                    createdTimestamp = now,
                    updatedTimestamp = now,
                    source = "google_calendar",
                    isSyncing = true,
                    priority = priority,
                    description = description,
                    tags = tags,
                    googleCalendarId = "primary"
                )
                repository.insertTask(task)
                finalTask = task
                if (!tags.isNullOrBlank()) repository.saveTagOverride(task.id, tags)
                
                val req = androidx.work.OneTimeWorkRequestBuilder<com.theblankstate.preamble.sync.GoogleCalendarCreationWorker>()
                    .setInputData(androidx.work.Data.Builder().putString("localTaskId", tempId).build())
                    .build()
                androidx.work.WorkManager.getInstance(appContext).enqueue(req)
                
            } else if (syncToGoogle && GoogleTasksManager.isLinked.value) {
                val tempId = java.util.UUID.randomUUID().toString()
                val task = com.theblankstate.preamble.data.Task(
                    id = tempId,
                    title = title,
                    createdDate = taskDate,
                    deadlineTime = deadlineTime,
                    createdTimestamp = now,
                    updatedTimestamp = now,
                    source = "google_tasks",
                    isSyncing = true,
                    priority = priority,
                    description = description,
                    tags = tags
                )
                repository.insertTask(task)
                finalTask = task
                if (!tags.isNullOrBlank()) repository.saveTagOverride(task.id, tags)

                val req = androidx.work.OneTimeWorkRequestBuilder<com.theblankstate.preamble.sync.GoogleTaskCreationWorker>()
                    .setInputData(androidx.work.Data.Builder().putString("localTaskId", tempId).build())
                    .build()
                androidx.work.WorkManager.getInstance(appContext).enqueue(req)

            } else {
                finalTask = repository.addTask(title, date, deadlineTime, priority, description, tags)
            }
            scheduleOrCancelAlarm(finalTask)
            refreshStats()
        }
    }

    fun toggleTask(task: Task) {
        viewModelScope.launch {
            repository.toggleTask(task)
            // If this is a Google Task, sync completion to Google Tasks
            if (task.source == "google_tasks" && task.id.startsWith("gtask_")) {
                val googleId = task.id.removePrefix("gtask_")
                GoogleTasksManager.updateTaskCompletion(
                    appContext, googleId, !task.isCompleted // toggled state
                )
            }
            // If this is a Google Calendar event, sync completion to Google Calendar Extended Properties
            if (task.source == "google_calendar" && task.id.startsWith("gcal_")) {
                val eventId = task.id.removePrefix("gcal_")
                val calendarId = task.googleCalendarId ?: "primary"
                GoogleCalendarManager.updateCalendarEventCompletion(
                    appContext, eventId, calendarId, !task.isCompleted // toggled state
                )
            }
            refreshStats()
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            // Cancel alarm if task had a deadline
            // Kill alarm if present
            com.theblankstate.preamble.notification.TaskAlarmManager.cancelAlarm(
                appContext, task.id
            )
            // Mark as deleted BEFORE any sync can re-insert it
            repository.markAsDeleted(task.id)
            
            // Delete locally first for Optimistic UI
            repository.deleteTask(task)
            refreshStats()

            // Enqueue background network deletion
            val taskJson = com.google.gson.Gson().toJson(task)
            if (task.source == "google_tasks" && task.id.startsWith("gtask_")) {
                val googleId = task.id.removePrefix("gtask_")
                val req = androidx.work.OneTimeWorkRequestBuilder<com.theblankstate.preamble.sync.GoogleTaskDeletionWorker>()
                    .setInputData(androidx.work.Data.Builder()
                        .putString("googleTaskId", googleId)
                        .putString("taskJson", taskJson)
                        .build())
                    .addTag("background_deletion")
                    .build()
                androidx.work.WorkManager.getInstance(appContext).enqueue(req)
            }
            if (task.source == "google_calendar" && task.id.startsWith("gcal_")) {
                val eventId = task.id.removePrefix("gcal_")
                val calendarId = task.googleCalendarId ?: "primary"
                val req = androidx.work.OneTimeWorkRequestBuilder<com.theblankstate.preamble.sync.GoogleCalendarDeletionWorker>()
                    .setInputData(androidx.work.Data.Builder()
                        .putString("eventId", eventId)
                        .putString("calendarId", calendarId)
                        .putString("taskJson", taskJson)
                        .build())
                    .addTag("background_deletion")
                    .build()
                androidx.work.WorkManager.getInstance(appContext).enqueue(req)
            }

            // Clear the deleted marker after a delay (sync window)
            launch {
                kotlinx.coroutines.delay(30_000)
                repository.clearDeletedId(task.id)
            }
        }
    }

    fun updateTask(task: Task, newTitle: String, newDate: String?, newDeadlineTime: String?, newPriority: Int = task.priority, newDescription: String? = task.description, newTags: String? = task.tags, newRecurrenceType: String? = task.recurrenceType, newRecurrenceInterval: Int = task.recurrenceInterval ?: 1, newRecurrenceDays: String? = task.recurrenceDays, newRecurrenceEndDate: String? = task.recurrenceEndDate) {
        viewModelScope.launch {
            // Cancel old alarm if task had a deadline
            if (task.deadlineTime != null) {
                com.theblankstate.preamble.notification.TaskAlarmManager.cancelAlarm(
                    appContext, task.id
                )
            }
            val updated = task.copy(
                title = newTitle,
                createdDate = newDate ?: task.createdDate,
                deadlineTime = newDeadlineTime,
                updatedTimestamp = System.currentTimeMillis(),
                priority = newPriority,
                description = newDescription,
                tags = newTags,
                recurrenceType = newRecurrenceType,
                recurrenceInterval = if (newRecurrenceType != null) newRecurrenceInterval else null,
                recurrenceDays = if (newRecurrenceType != null) newRecurrenceDays else null,
                recurrenceEndDate = if (newRecurrenceType != null) newRecurrenceEndDate else null
            )
            repository.updateTask(updated)
            // Save tag override for Google-sourced tasks (persists across syncs + Firebase)
            if (task.source == "google_calendar" || task.source == "google_tasks") {
                repository.saveTagOverride(task.id, newTags)
            }
            // Sync title/date edits back to Google Tasks
            if (task.source == "google_tasks" && task.id.startsWith("gtask_")) {
                val googleId = task.id.removePrefix("gtask_")
                GoogleTasksManager.updateGoogleTask(
                    appContext, googleId, newTitle, newDate ?: task.createdDate
                )
            }
            // Sync edits back to Google Calendar
            if (task.source == "google_calendar" && task.id.startsWith("gcal_")) {
                val eventId = task.id.removePrefix("gcal_")
                val calendarId = task.googleCalendarId ?: "primary"
                GoogleCalendarManager.updateCalendarEvent(
                    appContext, eventId, calendarId, newTitle,
                    newDate ?: task.createdDate, newDeadlineTime, newDescription,
                    newRecurrenceType,
                    if (newRecurrenceType != null) newRecurrenceInterval else null,
                    if (newRecurrenceType != null) newRecurrenceDays else null,
                    if (newRecurrenceType != null) newRecurrenceEndDate else null,
                    tags = newTags
                )
            }
            scheduleOrCancelAlarm(updated)
            refreshStats()
        }
    }

    fun updateTaskPriority(task: Task, newPriority: Int) {
        viewModelScope.launch {
            val updated = task.copy(
                priority = newPriority,
                updatedTimestamp = System.currentTimeMillis()
            )
            repository.updateTask(updated)
            refreshStats()
        }
    }

    /**
     * Determines whether an alarm should be scheduled. Paused alarms, or alarms with NO deadline NO custom time,
     * are canceled. Active alarms are calculated and securely scheduled into TaskAlarmManager.
     */
    private fun scheduleOrCancelAlarm(task: Task) {
        if (task.isAlarmPaused) {
            com.theblankstate.preamble.notification.TaskAlarmManager.cancelAlarm(appContext, task.id)
            return
        }
        val triggerMs = task.customAlarmTimeMs ?: run {
            if (task.deadlineTime == null) return@run null
            try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                sdf.parse("${task.createdDate} ${task.deadlineTime}")?.time
            } catch (e: Exception) { null }
        }
        
        if (triggerMs != null && triggerMs > System.currentTimeMillis()) {
            com.theblankstate.preamble.notification.TaskAlarmManager.scheduleAlarm(appContext, task.id, task.title, triggerMs)
        } else {
            com.theblankstate.preamble.notification.TaskAlarmManager.cancelAlarm(appContext, task.id)
        }
    }

    fun updateAlarmStatus(task: Task, newCustomAlarmTimeMs: Long?, isPaused: Boolean) {
        viewModelScope.launch {
            val updated = task.copy(
                customAlarmTimeMs = newCustomAlarmTimeMs,
                isAlarmPaused = isPaused,
                updatedTimestamp = System.currentTimeMillis()
            )
            repository.updateTask(updated)
            scheduleOrCancelAlarm(updated)
            refreshStats()
        }
    }

    private fun updateNotification() {
        // Notification auto-updates via TaskNotificationService
    }

    fun addRecurringTask(
        title: String,
        date: String? = null,
        deadlineTime: String? = null,
        priority: Int = 0,
        description: String? = null,
        recurrenceType: String,
        recurrenceInterval: Int = 1,
        recurrenceDays: String? = null,
        recurrenceEndDate: String? = null,
        syncToCalendar: Boolean = false,
        tags: String? = null
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val taskDate = date ?: TaskRepository.todayString()
            val now = System.currentTimeMillis()
            if (syncToCalendar && GoogleCalendarManager.isLinked.value) {
                val tempId = java.util.UUID.randomUUID().toString()
                val task = com.theblankstate.preamble.data.Task(
                    id = tempId,
                    title = title,
                    createdDate = taskDate,
                    deadlineTime = deadlineTime,
                    createdTimestamp = now,
                    updatedTimestamp = now,
                    source = "google_calendar",
                    isSyncing = true,
                    priority = priority,
                    description = description,
                    tags = tags,
                    googleCalendarId = "primary",
                    recurrenceType = recurrenceType,
                    recurrenceInterval = recurrenceInterval,
                    recurrenceDays = recurrenceDays,
                    recurrenceEndDate = recurrenceEndDate
                )
                repository.insertTask(task)
                if (!tags.isNullOrBlank()) repository.saveTagOverride(task.id, tags)

                val req = androidx.work.OneTimeWorkRequestBuilder<com.theblankstate.preamble.sync.GoogleCalendarCreationWorker>()
                    .setInputData(androidx.work.Data.Builder().putString("localTaskId", tempId).build())
                    .build()
                androidx.work.WorkManager.getInstance(appContext).enqueue(req)

            } else {
                repository.addRecurringTask(
                    title, date, deadlineTime, priority, description,
                    recurrenceType, recurrenceInterval, recurrenceDays, recurrenceEndDate,
                    tags = tags
                )
            }
            triggerRecurrenceGeneration()
            refreshStats()
        }
    }

    private fun triggerRecurrenceGeneration() {
        val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.theblankstate.preamble.recurrence.RecurrenceWorker>().build()
        androidx.work.WorkManager.getInstance(appContext).enqueue(workRequest)
    }

    // ── Subtask methods ──

    fun toggleTaskExpanded(taskId: String) {
        _expandedTasks.update { current ->
            if (taskId in current) current - taskId else current + taskId
        }
    }

    fun getSubtasksForTask(taskId: String): Flow<List<Task>> {
        return repository.getSubtasksForParent(taskId)
    }

    fun addSubtask(parentId: String, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.addSubtask(parentId, title)
            val parentIds = todayTasks.value.map { it.id }
            _subtaskCounts.value = repository.getSubtaskStats(parentIds)
            refreshStats()
        }
    }

    // ── Tag methods ──

    fun setTagFilter(tag: String?) {
        _selectedTagFilter.value = tag
    }

    class Factory(
        private val repository: TaskRepository,
        private val appContext: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TaskViewModel(repository, appContext) as T
        }
    }
}
