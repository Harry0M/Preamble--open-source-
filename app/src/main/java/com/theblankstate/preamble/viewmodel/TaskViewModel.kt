package com.theblankstate.preamble.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.theblankstate.preamble.data.TaskInputValidator
import com.theblankstate.preamble.data.Task
import com.theblankstate.preamble.notification.TaskNotificationManager
import com.theblankstate.preamble.repository.TaskRepository
import com.theblankstate.preamble.sync.GoogleCalendarManager
import com.theblankstate.preamble.sync.GoogleSyncCoordinator
import com.theblankstate.preamble.sync.GoogleTasksManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    // Shared constraint: only run sync workers when network is available
    private val networkConstraints = androidx.work.Constraints.Builder()
        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
        .build()

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
        val now = System.currentTimeMillis()
        val filtered = if (tagFilter == null) tasks
            else tasks.filter { it.tagList.contains(tagFilter) }
        // Sort snoozed tasks to the bottom instead of hiding them
        filtered.sortedBy { task ->
            if (task.snoozedUntil != null && task.snoozedUntil > now) 1 else 0
        }
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

        // Auto-escalate priority for old rollover tasks on startup
        viewModelScope.launch {
            repository.escalateRolloverPriorities()
        }

        // Subtask count tracking
        viewModelScope.launch {
            todayTasks.flatMapLatest { tasks ->
                val parentIds = tasks.map { it.id }
                android.util.Log.d("TaskSync", "Subtask tracking: todayTasks emitted ${tasks.size} tasks. Parent IDs: $parentIds")
                if (parentIds.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyMap())
                else repository.observeSubtaskStats(parentIds)
            }.collect { stats ->
                android.util.Log.d("TaskSync", "Subtask tracking: received new stats -> $stats")
                _subtaskCounts.value = stats
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
                GoogleSyncCoordinator.syncLinkedData(
                    context = appContext,
                    forceFull = false,
                    isManual = true,
                    isBackground = false,
                    reason = "pull_to_refresh"
                )

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

    fun addTask(title: String, date: String? = null, deadlineTime: String? = null, syncToGoogle: Boolean = false, syncToCalendar: Boolean = false, priority: Int = 0, description: String? = null, tags: String? = null, subtasks: List<String> = emptyList()) {
        val normalizedTitle = TaskInputValidator.normalizeTitle(title)
        val normalizedDescription = TaskInputValidator.normalizeDescription(description)
        if (!TaskInputValidator.isValidTitle(normalizedTitle) || !TaskInputValidator.isValidDescription(normalizedDescription)) return
        viewModelScope.launch {
            val taskDate = date ?: TaskRepository.todayString()
            val now = System.currentTimeMillis()
            val finalTask: com.theblankstate.preamble.data.Task
            if (syncToCalendar && GoogleCalendarManager.isLinked.value) {
                val tempId = java.util.UUID.randomUUID().toString()
                val task = com.theblankstate.preamble.data.Task(
                    id = tempId,
                    title = normalizedTitle,
                    createdDate = taskDate,
                    deadlineTime = deadlineTime,
                    createdTimestamp = now,
                    updatedTimestamp = now,
                    source = "google_calendar",
                    isSyncing = true,
                    priority = priority,
                    description = normalizedDescription,
                    tags = tags,
                    googleCalendarId = "primary"
                )
                repository.insertTask(task)
                finalTask = task
                if (!tags.isNullOrBlank()) repository.saveTagOverride(task.id, tags)
                
                val req = androidx.work.OneTimeWorkRequestBuilder<com.theblankstate.preamble.sync.GoogleCalendarCreationWorker>()
                    .setInputData(androidx.work.Data.Builder().putString("localTaskId", tempId).build())
                    .setConstraints(networkConstraints)
                    .build()
                androidx.work.WorkManager.getInstance(appContext).enqueue(req)
                
            } else if (syncToGoogle && GoogleTasksManager.isLinked.value) {
                val tempId = java.util.UUID.randomUUID().toString()
                val task = com.theblankstate.preamble.data.Task(
                    id = tempId,
                    title = normalizedTitle,
                    createdDate = taskDate,
                    deadlineTime = deadlineTime,
                    createdTimestamp = now,
                    updatedTimestamp = now,
                    source = "google_tasks",
                    isSyncing = true,
                    priority = priority,
                    description = normalizedDescription,
                    tags = tags
                )
                repository.insertTask(task)
                finalTask = task
                if (!tags.isNullOrBlank()) repository.saveTagOverride(task.id, tags)

                val req = androidx.work.OneTimeWorkRequestBuilder<com.theblankstate.preamble.sync.GoogleTaskCreationWorker>()
                    .setInputData(androidx.work.Data.Builder().putString("localTaskId", tempId).build())
                    .setConstraints(networkConstraints)
                    .build()
                androidx.work.WorkManager.getInstance(appContext).enqueue(req)

            } else {
                finalTask = repository.addTask(normalizedTitle, date, deadlineTime, priority, normalizedDescription, tags)
                    ?: return@launch
            }
            if (subtasks.isNotEmpty()) {
                repository.addSubtasks(finalTask.id, subtasks)
                // Optimistic UI insert to bypass the 5-15ms flow observation delay!
                _subtaskCounts.update { current ->
                    current + (finalTask.id to Pair(0, subtasks.size))
                }
            }
            scheduleOrCancelAlarm(finalTask)
            refreshStats()
        }
    }

    // ── Snackbar / Undo Delete support ──
    private val _snackbarEvent = MutableSharedFlow<SnackbarEvent>(extraBufferCapacity = 1)
    val snackbarEvent = _snackbarEvent.asSharedFlow()

    private var pendingDeleteJob: Job? = null
    private var pendingDeleteTask: Task? = null

    data class SnackbarEvent(val message: String, val actionLabel: String? = null, val onAction: (() -> Unit)? = null)

    fun toggleTask(task: Task) {
        viewModelScope.launch {
            // Optimistic: toggle locally FIRST
            repository.toggleTask(task)
            refreshStats()

            // Then sync to Google in background via WorkManager
            val newCompleted = !task.isCompleted
            if (task.source == "google_tasks" && task.id.startsWith("gtask_")) {
                val googleId = task.id.removePrefix("gtask_")
                // Mark as syncing optimistically
                repository.getTaskById(task.id)?.let {
                    repository.updateTask(it.copy(isSyncing = true))
                }
                val req = androidx.work.OneTimeWorkRequestBuilder<com.theblankstate.preamble.sync.GoogleTaskCompletionWorker>()
                    .setInputData(androidx.work.Data.Builder()
                        .putString("taskId", task.id)
                        .putString("googleTaskId", googleId)
                        .putBoolean("completed", newCompleted)
                        .build())
                    .setConstraints(networkConstraints)
                    .build()
                androidx.work.WorkManager.getInstance(appContext).enqueue(req)
            }
            if (task.source == "google_calendar" && task.id.startsWith("gcal_")) {
                val eventId = task.id.removePrefix("gcal_")
                val calendarId = task.googleCalendarId ?: "primary"
                repository.getTaskById(task.id)?.let {
                    repository.updateTask(it.copy(isSyncing = true))
                }
                val req = androidx.work.OneTimeWorkRequestBuilder<com.theblankstate.preamble.sync.GoogleCalendarCompletionWorker>()
                    .setInputData(androidx.work.Data.Builder()
                        .putString("taskId", task.id)
                        .putString("eventId", eventId)
                        .putString("calendarId", calendarId)
                        .putBoolean("completed", newCompleted)
                        .build())
                    .setConstraints(networkConstraints)
                    .build()
                androidx.work.WorkManager.getInstance(appContext).enqueue(req)
            }
        }
    }

    fun deleteTask(task: Task) {
        // Cancel any previous pending delete
        pendingDeleteJob?.cancel()
        pendingDeleteTask?.let { previousTask ->
            // Commit the previous pending delete immediately
            commitDelete(previousTask)
        }

        viewModelScope.launch {
            // Cancel alarm if task had a deadline
            com.theblankstate.preamble.notification.TaskAlarmManager.cancelAlarm(
                appContext, task.id
            )
            // Mark as deleted BEFORE any sync can re-insert it
            repository.markAsDeleted(task.id)
            
            // Delete locally first for Optimistic UI
            repository.deleteTask(task)
            refreshStats()

            // Store for undo
            pendingDeleteTask = task

            // Show snackbar with undo
            _snackbarEvent.tryEmit(SnackbarEvent(
                message = "Task deleted",
                actionLabel = "Undo",
                onAction = { undoDelete() }
            ))

            // Delayed commit: actually fire network deletion after 4 seconds
            pendingDeleteJob = launch {
                delay(4000)
                commitDelete(task)
                pendingDeleteTask = null
            }
        }
    }

    private fun commitDelete(task: Task) {
        viewModelScope.launch {
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

    private fun undoDelete() {
        pendingDeleteJob?.cancel()
        pendingDeleteJob = null
        val task = pendingDeleteTask ?: return
        pendingDeleteTask = null
        viewModelScope.launch {
            repository.clearDeletedId(task.id)
            repository.insertTask(task)
            // Re-schedule alarm if it had one
            scheduleOrCancelAlarm(task)
            refreshStats()
        }
    }

    fun updateTask(task: Task, newTitle: String, newDate: String?, newDeadlineTime: String?, newPriority: Int = task.priority, newDescription: String? = task.description, newTags: String? = task.tags, newRecurrenceType: String? = task.recurrenceType, newRecurrenceInterval: Int = task.recurrenceInterval ?: 1, newRecurrenceDays: String? = task.recurrenceDays, newRecurrenceEndDate: String? = task.recurrenceEndDate) {
        val normalizedTitle = TaskInputValidator.normalizeTitle(newTitle)
        val normalizedDescription = TaskInputValidator.normalizeDescription(newDescription)
        if (!TaskInputValidator.isValidTitle(normalizedTitle) || !TaskInputValidator.isValidDescription(normalizedDescription)) return
        viewModelScope.launch {
            // Cancel old alarm if task had a deadline
            if (task.deadlineTime != null) {
                com.theblankstate.preamble.notification.TaskAlarmManager.cancelAlarm(
                    appContext, task.id
                )
            }
            val updated = task.copy(
                title = normalizedTitle,
                createdDate = newDate ?: task.createdDate,
                deadlineTime = newDeadlineTime,
                updatedTimestamp = System.currentTimeMillis(),
                priority = newPriority,
                description = normalizedDescription,
                tags = newTags,
                recurrenceType = newRecurrenceType,
                recurrenceInterval = if (newRecurrenceType != null) newRecurrenceInterval else null,
                recurrenceDays = if (newRecurrenceType != null) newRecurrenceDays else null,
                recurrenceEndDate = if (newRecurrenceType != null) newRecurrenceEndDate else null
            )
            // Optimistic: update locally FIRST
            repository.updateTask(updated)
            // Save tag override for Google-sourced tasks (persists across syncs + Firebase)
            if (task.source == "google_calendar" || task.source == "google_tasks") {
                repository.saveTagOverride(task.id, newTags)
            }
            scheduleOrCancelAlarm(updated)
            refreshStats()

            // Then sync to Google in background via WorkManager
            if (task.source == "google_tasks" && task.id.startsWith("gtask_")) {
                val googleId = task.id.removePrefix("gtask_")
                repository.updateTask(updated.copy(isSyncing = true))
                val req = androidx.work.OneTimeWorkRequestBuilder<com.theblankstate.preamble.sync.GoogleTaskUpdateWorker>()
                    .setInputData(androidx.work.Data.Builder()
                        .putString("taskId", task.id)
                        .putString("googleTaskId", googleId)
                        .putString("title", normalizedTitle)
                        .putString("date", newDate ?: task.createdDate)
                        .build())
                    .setConstraints(networkConstraints)
                    .build()
                androidx.work.WorkManager.getInstance(appContext).enqueue(req)
            }
            if (task.source == "google_calendar" && task.id.startsWith("gcal_")) {
                val eventId = task.id.removePrefix("gcal_")
                val calendarId = task.googleCalendarId ?: "primary"
                repository.updateTask(updated.copy(isSyncing = true))
                val dataBuilder = androidx.work.Data.Builder()
                    .putString("taskId", task.id)
                    .putString("eventId", eventId)
                    .putString("calendarId", calendarId)
                    .putString("title", normalizedTitle)
                    .putString("date", newDate ?: task.createdDate)
                if (newDeadlineTime != null) dataBuilder.putString("deadlineTime", newDeadlineTime)
                if (normalizedDescription != null) dataBuilder.putString("description", normalizedDescription)
                if (newRecurrenceType != null) {
                    dataBuilder.putString("recurrenceType", newRecurrenceType)
                    dataBuilder.putInt("recurrenceInterval", newRecurrenceInterval)
                    if (newRecurrenceDays != null) dataBuilder.putString("recurrenceDays", newRecurrenceDays)
                    if (newRecurrenceEndDate != null) dataBuilder.putString("recurrenceEndDate", newRecurrenceEndDate)
                }
                if (newTags != null) dataBuilder.putString("tags", newTags)
                val req = androidx.work.OneTimeWorkRequestBuilder<com.theblankstate.preamble.sync.GoogleCalendarUpdateWorker>()
                    .setInputData(dataBuilder.build())
                    .setConstraints(networkConstraints)
                    .build()
                androidx.work.WorkManager.getInstance(appContext).enqueue(req)
            }
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
        tags: String? = null,
        subtasks: List<String> = emptyList()
    ) {
        val normalizedTitle = TaskInputValidator.normalizeTitle(title)
        val normalizedDescription = TaskInputValidator.normalizeDescription(description)
        if (!TaskInputValidator.isValidTitle(normalizedTitle) || !TaskInputValidator.isValidDescription(normalizedDescription)) return
        viewModelScope.launch {
            val taskDate = date ?: TaskRepository.todayString()
            val now = System.currentTimeMillis()
            if (syncToCalendar && GoogleCalendarManager.isLinked.value) {
                val tempId = java.util.UUID.randomUUID().toString()
                val task = com.theblankstate.preamble.data.Task(
                    id = tempId,
                    title = normalizedTitle,
                    createdDate = taskDate,
                    deadlineTime = deadlineTime,
                    createdTimestamp = now,
                    updatedTimestamp = now,
                    source = "google_calendar",
                    isSyncing = true,
                    priority = priority,
                    description = normalizedDescription,
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
                val finalTask = repository.addRecurringTask(
                    normalizedTitle, date, deadlineTime, priority, normalizedDescription,
                    recurrenceType, recurrenceInterval, recurrenceDays, recurrenceEndDate,
                    tags = tags
                )
                if (finalTask == null) return@launch
                if (subtasks.isNotEmpty()) {
                    repository.addSubtasks(finalTask.id, subtasks)
                    // Optimistic update
                    _subtaskCounts.update { current ->
                        current + (finalTask.id to Pair(0, subtasks.size))
                    }
                }
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
        val normalizedTitle = TaskInputValidator.normalizeTitle(title)
        if (!TaskInputValidator.isValidTitle(normalizedTitle)) return
        viewModelScope.launch {
            if (repository.addSubtask(parentId, normalizedTitle) == null) return@launch
            refreshStats()
        }
    }

    fun toggleSubtaskCompletion(subtaskId: String, isCompleted: Boolean) {
        viewModelScope.launch {
            repository.updateSubtaskCompletion(subtaskId, isCompleted)
            refreshStats()
        }
    }

    fun completeAllSubtasks(parentId: String) {
        viewModelScope.launch {
            repository.completeAllSubtasks(parentId)
            refreshStats()
        }
    }

    fun deleteSubtask(subtaskId: String) {
        viewModelScope.launch {
            repository.deleteSubtask(subtaskId)
            refreshStats()
        }
    }

    // ── Snooze methods ──

    fun snoozeTask(taskId: String, durationMs: Long) {
        viewModelScope.launch {
            repository.snoozeTask(taskId, durationMs)
        }
    }

    // ── Copy to Today ──

    fun copyTaskToToday(task: Task) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val todayStr = TaskRepository.todayString()
            val newId = java.util.UUID.randomUUID().toString()

            // Determine source based on original task's source for Google sync
            val syncToCalendar = task.source == "google_calendar" && com.theblankstate.preamble.sync.GoogleCalendarManager.isLinked.value
            val syncToGoogleTasks = task.source == "google_tasks" && com.theblankstate.preamble.sync.GoogleTasksManager.isLinked.value

            val newTask = Task(
                id = newId,
                title = task.title,
                isCompleted = false,
                createdDate = todayStr,
                createdTimestamp = now,
                updatedTimestamp = now,
                deadlineTime = task.deadlineTime,
                source = when {
                    syncToCalendar -> "google_calendar"
                    syncToGoogleTasks -> "google_tasks"
                    else -> "local"
                },
                isSyncing = syncToCalendar || syncToGoogleTasks,
                priority = task.priority,
                description = task.description,
                tags = task.tags,
                // No recurrence — this is a one-off copy
                recurrenceType = null,
                recurrenceInterval = null,
                recurrenceDays = null,
                recurrenceEndDate = null,
                googleCalendarId = if (syncToCalendar) task.googleCalendarId ?: "primary" else null
            )

            repository.insertTask(newTask)
            if (!task.tags.isNullOrBlank()) repository.saveTagOverride(newTask.id, task.tags!!)

            // Copy subtasks
            val childSubtasks = repository.getSubtasksForParentSync(task.id)
            if (childSubtasks.isNotEmpty()) {
                val subtaskTitles = childSubtasks.map { it.title }
                repository.addSubtasks(newId, subtaskTitles)
                _subtaskCounts.update { current ->
                    current + (newId to Pair(0, subtaskTitles.size))
                }
            }

            // Sync to Google Calendar if needed
            if (syncToCalendar) {
                val req = androidx.work.OneTimeWorkRequestBuilder<com.theblankstate.preamble.sync.GoogleCalendarCreationWorker>()
                    .setInputData(androidx.work.Data.Builder().putString("localTaskId", newId).build())
                    .setConstraints(networkConstraints)
                    .build()
                androidx.work.WorkManager.getInstance(appContext).enqueue(req)
            }

            // Sync to Google Tasks if needed
            if (syncToGoogleTasks) {
                val req = androidx.work.OneTimeWorkRequestBuilder<com.theblankstate.preamble.sync.GoogleTaskCreationWorker>()
                    .setInputData(androidx.work.Data.Builder().putString("localTaskId", newId).build())
                    .setConstraints(networkConstraints)
                    .build()
                androidx.work.WorkManager.getInstance(appContext).enqueue(req)
            }

            scheduleOrCancelAlarm(newTask)
            refreshStats()
        }
    }

    // ── Tag methods ──

    fun setTagFilter(tag: String?) {
        _selectedTagFilter.value = tag
    }

    // ── Retry failed sync ──

    fun retrySync(task: Task) {
        if (!task.syncFailed) return
        viewModelScope.launch {
            repository.updateTask(task.copy(syncFailed = false, isSyncing = true))
            if (task.source == "google_tasks" && task.id.startsWith("gtask_")) {
                val googleId = task.id.removePrefix("gtask_")
                // Re-enqueue a completion sync (most common failure case)
                val req = androidx.work.OneTimeWorkRequestBuilder<com.theblankstate.preamble.sync.GoogleTaskCompletionWorker>()
                    .setInputData(androidx.work.Data.Builder()
                        .putString("taskId", task.id)
                        .putString("googleTaskId", googleId)
                        .putBoolean("completed", task.isCompleted)
                        .build())
                    .setConstraints(networkConstraints)
                    .build()
                androidx.work.WorkManager.getInstance(appContext).enqueue(req)
            }
            if (task.source == "google_calendar" && task.id.startsWith("gcal_")) {
                val eventId = task.id.removePrefix("gcal_")
                val calendarId = task.googleCalendarId ?: "primary"
                val req = androidx.work.OneTimeWorkRequestBuilder<com.theblankstate.preamble.sync.GoogleCalendarCompletionWorker>()
                    .setInputData(androidx.work.Data.Builder()
                        .putString("taskId", task.id)
                        .putString("eventId", eventId)
                        .putString("calendarId", calendarId)
                        .putBoolean("completed", task.isCompleted)
                        .build())
                    .setConstraints(networkConstraints)
                    .build()
                androidx.work.WorkManager.getInstance(appContext).enqueue(req)
            }
        }
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
