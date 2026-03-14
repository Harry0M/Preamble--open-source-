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

    val todayTasks: StateFlow<List<Task>> = repository.getTasksForDate(today)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
                val completed = tasks.count { it.isCompleted }
                val total = tasks.size
                _statsState.update { it.copy(todayCompleted = completed, todayTotal = total) }
            }
        }

        refreshStats()

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
                        val gTasks = GoogleTasksManager.fetchGoogleTasks(appContext)
                        app.repository.syncGoogleTasks(gTasks, GoogleTasksManager.autoDeleteGoogleTasks.value)
                    }
                }
                // Full sync Google Calendar
                if (GoogleCalendarManager.isLinked.value) {
                    val lastSync = GoogleCalendarManager.lastSyncTime.value
                    val now = System.currentTimeMillis()
                    if (now - lastSync > 60_000) {
                        val calEvents = GoogleCalendarManager.fetchCalendarEvents(appContext)
                        app.repository.syncCalendarEvents(calEvents)
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

    fun syncGoogleData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val app = appContext.applicationContext as com.theblankstate.preamble.PreambleApplication
                // Full sync Google Tasks (no updatedAfter filter)
                if (GoogleTasksManager.isLinked.value) {
                    val gTasks = GoogleTasksManager.fetchGoogleTasks(appContext)
                    app.repository.syncGoogleTasks(gTasks, GoogleTasksManager.autoDeleteGoogleTasks.value)
                }
                // Full sync Google Calendar (no updatedAfter filter)
                if (GoogleCalendarManager.isLinked.value) {
                    val calEvents = GoogleCalendarManager.fetchCalendarEvents(appContext)
                    app.repository.syncCalendarEvents(calEvents)
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

    fun addTask(title: String, date: String? = null, deadlineTime: String? = null, syncToGoogle: Boolean = false, priority: Int = 0, description: String? = null) {
        if (title.isBlank()) return
        viewModelScope.launch {
            if (syncToGoogle && GoogleTasksManager.isLinked.value) {
                // Create on Google first, then save locally with Google Task ID
                val googleId = GoogleTasksManager.createGoogleTask(appContext, title, date ?: TaskRepository.todayString())
                if (googleId != null) {
                    val now = System.currentTimeMillis()
                    val task = com.theblankstate.preamble.data.Task(
                        id = "gtask_$googleId",
                        title = title,
                        createdDate = date ?: TaskRepository.todayString(),
                        deadlineTime = deadlineTime,
                        createdTimestamp = now,
                        updatedTimestamp = now,
                        source = "google_tasks",
                        priority = priority,
                        description = description
                    )
                    repository.insertTask(task)
                } else {
                    // Google create failed — save locally only
                    repository.addTask(title, date, deadlineTime, priority, description)
                }
            } else {
                repository.addTask(title, date, deadlineTime, priority, description)
            }
            if (deadlineTime != null) {
                val taskDate = date ?: TaskRepository.todayString()
                com.theblankstate.preamble.notification.TaskAlarmManager.scheduleAlarm(
                    appContext, title, taskDate, deadlineTime
                )
            }
            refreshStats()
        }
    }

    fun toggleTask(task: Task) {
        viewModelScope.launch {
            repository.toggleTask(task)
            // If this is a Google Task, sync completion to Google
            if (task.source == "google_tasks" && task.id.startsWith("gtask_")) {
                val googleId = task.id.removePrefix("gtask_")
                GoogleTasksManager.updateTaskCompletion(
                    appContext, googleId, !task.isCompleted // toggled state
                )
            }
            refreshStats()
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            // Cancel alarm if task had a deadline
            if (task.deadlineTime != null) {
                com.theblankstate.preamble.notification.TaskAlarmManager.cancelAlarm(
                    appContext, task.title, task.createdDate, task.deadlineTime
                )
            }
            repository.deleteTask(task)
            // Delete from Google Tasks if it's a synced task
            if (task.source == "google_tasks" && task.id.startsWith("gtask_")) {
                val googleId = task.id.removePrefix("gtask_")
                GoogleTasksManager.deleteGoogleTask(appContext, googleId)
            }
            refreshStats()
        }
    }

    fun updateTask(task: Task, newTitle: String, newDate: String?, newDeadlineTime: String?, newPriority: Int = task.priority, newDescription: String? = task.description) {
        viewModelScope.launch {
            // Cancel old alarm if task had a deadline
            if (task.deadlineTime != null) {
                com.theblankstate.preamble.notification.TaskAlarmManager.cancelAlarm(
                    appContext, task.title, task.createdDate, task.deadlineTime
                )
            }
            val updated = task.copy(
                title = newTitle,
                createdDate = newDate ?: task.createdDate,
                deadlineTime = newDeadlineTime,
                updatedTimestamp = System.currentTimeMillis(),
                priority = newPriority,
                description = newDescription
            )
            repository.updateTask(updated)
            // Sync title/date edits back to Google Tasks
            if (task.source == "google_tasks" && task.id.startsWith("gtask_")) {
                val googleId = task.id.removePrefix("gtask_")
                GoogleTasksManager.updateGoogleTask(
                    appContext, googleId, newTitle, newDate ?: task.createdDate
                )
            }
            // Schedule new alarm if deadline is set
            if (newDeadlineTime != null) {
                com.theblankstate.preamble.notification.TaskAlarmManager.scheduleAlarm(
                    appContext, newTitle, updated.createdDate, newDeadlineTime
                )
            }
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
        recurrenceEndDate: String? = null
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.addRecurringTask(
                title, date, deadlineTime, priority, description,
                recurrenceType, recurrenceInterval, recurrenceDays, recurrenceEndDate
            )
            triggerRecurrenceGeneration()
            refreshStats()
        }
    }

    private fun triggerRecurrenceGeneration() {
        val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.theblankstate.preamble.recurrence.RecurrenceWorker>().build()
        androidx.work.WorkManager.getInstance(appContext).enqueue(workRequest)
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
