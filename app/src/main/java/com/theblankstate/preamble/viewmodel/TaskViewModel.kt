package com.theblankstate.preamble.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.theblankstate.preamble.data.AdminTask
import com.theblankstate.preamble.data.TaskInputValidator
import com.theblankstate.preamble.data.Task
import com.theblankstate.preamble.notification.TaskNotificationManager
import com.theblankstate.preamble.repository.AdminTaskRepository
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

    // ── Admin Tasks (broadcasts) ──
    private val adminTaskRepo = AdminTaskRepository(appContext)
    val adminTasks: StateFlow<List<AdminTask>> = adminTaskRepo.adminTasks

    init {
        refreshAdminTasks()
    }

    fun refreshAdminTasks() {
        viewModelScope.launch {
            adminTaskRepo.refresh()
        }
    }

    fun dismissAdminTask(taskId: String) {
        adminTaskRepo.dismissTask(taskId)
    }

    fun adminTaskActioned(taskId: String) {
        adminTaskRepo.markInteracted(taskId)
    }

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
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(emptyList())
            else repository.searchTasks(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private val _selectedDate = MutableStateFlow<String?>(null)

    // Calendar selected-date tasks: uses virtual expansion to show recurring instances
    private val _selectedDateTasks = MutableStateFlow<List<Task>>(emptyList())
    val selectedDateTasks: StateFlow<List<Task>> = _selectedDateTasks.asStateFlow()

    // ══════════════════════════════════════════════════════════════
    // GOOGLE CALENDAR-STYLE DATA ARCHITECTURE
    // • Plain HashMap cache (non-reactive = zero Compose overhead)
    // • Each pager page loads its own data via suspend function
    // • No shared state between pages = no cascade recomposition
    // ══════════════════════════════════════════════════════════════

    @androidx.compose.runtime.Immutable
    data class MonthData(
        val heatMap: Map<Int, Pair<Int, Int>>,
        val tasksByDay: Map<Int, List<Task>>
    )


    // Plain HashMap — NOT reactive. Read/write only on Main thread.
    // Zero Compose overhead. No recomposition triggered by mutations.
    private val _monthCache = HashMap<String, MonthData>()

    // Per-date task cache (also non-reactive)
    private val _dateTaskCache = mutableMapOf<String, List<Task>>()

    // Track current month for auto-refresh on mutations
    private var _heatMapYear = 0
    private var _heatMapMonth = 0

    // Legacy flows for backward compat (used by non-pager views)
    private val _calendarHeatMap = MutableStateFlow<Map<Int, Pair<Int, Int>>>(emptyMap())
    val calendarHeatMap: StateFlow<Map<Int, Pair<Int, Int>>> = _calendarHeatMap.asStateFlow()
    private val _calendarMonthTasks = MutableStateFlow<Map<Int, List<Task>>>(emptyMap())
    val calendarMonthTasks: StateFlow<Map<Int, List<Task>>> = _calendarMonthTasks.asStateFlow()

    // Refresh tick — increments on every cache invalidation.
    // Used as produceState key so pager pages re-load after mutations/sync.
    private val _calendarRefreshTick = MutableStateFlow(0)
    val calendarRefreshTick: StateFlow<Int> = _calendarRefreshTick.asStateFlow()

    // Warm calendar cache on ViewModel creation (Fix #4)
    // By the time user switches to Calendar tab, current month is ready
    // MUST be after _monthCache declaration (Kotlin init order)
    init {
        viewModelScope.launch {
            val cal = java.util.Calendar.getInstance()
            val y = cal.get(java.util.Calendar.YEAR)
            val m = cal.get(java.util.Calendar.MONTH)
            // Set heatmap tracking BEFORE loading, so legacy flows are also updated
            _heatMapYear = y
            _heatMapMonth = m
            android.util.Log.d("CAL_PERF", "init → pre-warming cache for $y-$m")
            loadMonthDataSuspend(y, m)
        }
    }

    /**
     * Synchronous cache lookup. Called during composition for instant initial value.
     * Returns null if not cached (page will then load async).
     */
    fun getCachedMonthData(key: String): MonthData? = _monthCache[key]

    /**
     * Suspend function — called from each page's produceState.
     * Checks cache first (instant), then loads from DB if needed.
     * Each page calls this independently — no shared reactive state.
     */
    suspend fun loadMonthDataSuspend(year: Int, month: Int): MonthData {
        val key = "$year-$month"
        _monthCache[key]?.let {
            android.util.Log.d("CAL_PERF", "loadMonthDataSuspend($key) → CACHE HIT")
            return it
        }
        android.util.Log.d("CAL_PERF", "loadMonthDataSuspend($key) → CACHE MISS → loading...")
        val startTime = System.currentTimeMillis()
        // Fix #3: Combined method — 4 queries instead of 22+
        val (heatMap, tasksByDay) = repository.getMonthDataCombined(year, month)
        val elapsed = System.currentTimeMillis() - startTime
        android.util.Log.d("CAL_PERF", "loadMonthDataSuspend($key) → DB DONE in ${elapsed}ms | ${tasksByDay.values.sumOf { it.size }} tasks")
        val data = MonthData(heatMap, tasksByDay)
        _monthCache[key] = data
        // Update legacy flows if this is the current month
        if (year == _heatMapYear && month == _heatMapMonth) {
            _calendarHeatMap.value = data.heatMap
            _calendarMonthTasks.value = data.tasksByDay
        }
        // Pre-warm adjacent months in background (makes swiping instant)
        preWarmAdjacentMonths(year, month)
        return data
    }

    /**
     * Pre-warm prev/next month caches in background.
     * Only loads if not already cached — zero overhead on cache hit.
     */
    private fun preWarmAdjacentMonths(year: Int, month: Int) {
        val cal = java.util.Calendar.getInstance()
        // Previous month
        cal.set(year, month, 1)
        cal.add(java.util.Calendar.MONTH, -1)
        val prevY = cal.get(java.util.Calendar.YEAR)
        val prevM = cal.get(java.util.Calendar.MONTH)
        // Next month
        cal.set(year, month, 1)
        cal.add(java.util.Calendar.MONTH, 1)
        val nextY = cal.get(java.util.Calendar.YEAR)
        val nextM = cal.get(java.util.Calendar.MONTH)

        if (_monthCache["$prevY-$prevM"] == null) {
            viewModelScope.launch {
                android.util.Log.d("CAL_PERF", "preWarm → loading prev $prevY-$prevM")
                val (h, t) = repository.getMonthDataCombined(prevY, prevM)
                _monthCache["$prevY-$prevM"] = MonthData(h, t)
            }
        }
        if (_monthCache["$nextY-$nextM"] == null) {
            viewModelScope.launch {
                android.util.Log.d("CAL_PERF", "preWarm → loading next $nextY-$nextM")
                val (h, t) = repository.getMonthDataCombined(nextY, nextM)
                _monthCache["$nextY-$nextM"] = MonthData(h, t)
            }
        }
    }

    fun selectDate(date: String?) {
        _selectedDate.value = date
        if (date != null) {
            // L1: per-date cache
            val cached = _dateTaskCache[date]
            if (cached != null) {
                _selectedDateTasks.value = cached
                return
            }
            // L2: extract from month cache (instant — no DB query needed)
            val monthTasks = extractTasksFromMonthCache(date)
            if (monthTasks != null) {
                _dateTaskCache[date] = monthTasks
                if (_dateTaskCache.size > 30) _dateTaskCache.remove(_dateTaskCache.keys.first())
                _selectedDateTasks.value = monthTasks
                return
            }
            // L3: fall back to DB query
            viewModelScope.launch {
                val tasks = repository.getTasksForDateWithRecurrence(date)
                _dateTaskCache[date] = tasks
                if (_dateTaskCache.size > 30) _dateTaskCache.remove(_dateTaskCache.keys.first())
                if (_selectedDate.value == date) _selectedDateTasks.value = tasks
            }
        } else {
            _selectedDateTasks.value = emptyList()
        }
    }

    /**
     * Extract tasks for a specific date from the month cache.
     * Returns sorted task list or null if the month isn't cached.
     */
    private fun extractTasksFromMonthCache(date: String): List<Task>? {
        try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val parsed = sdf.parse(date) ?: return null
            val cal = java.util.Calendar.getInstance().apply { time = parsed }
            val year = cal.get(java.util.Calendar.YEAR)
            val month = cal.get(java.util.Calendar.MONTH)
            val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
            val key = "$year-$month"
            val monthData = _monthCache[key] ?: return null
            val tasks = monthData.tasksByDay[day] ?: emptyList()
            return tasks.sortedWith(
                compareBy<Task> { it.isCompleted }
                    .thenByDescending { it.priority }
                    .thenByDescending { it.createdTimestamp }
            )
        } catch (_: Exception) {
            return null
        }
    }

    /** Re-query calendar data after a mutation (toggle, delete, add) */
    fun refreshCalendarDate() {
        val date = _selectedDate.value ?: return
        android.util.Log.d("CAL_PERF", "refreshCalendarDate($date) → invalidating affected caches")
        _dateTaskCache.remove(date)
        // Invalidate only the affected month (not ALL months)
        try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val parsed = sdf.parse(date)
            if (parsed != null) {
                val cal = java.util.Calendar.getInstance().apply { time = parsed }
                val y = cal.get(java.util.Calendar.YEAR)
                val m = cal.get(java.util.Calendar.MONTH)
                _monthCache.remove("$y-$m")
                android.util.Log.d("CAL_PERF", "refreshCalendarDate → invalidated month $y-$m")
            }
        } catch (_: Exception) {
            _monthCache.clear() // fallback: clear all on parse error
        }
        // Increment tick to force produceState re-run in visible pages
        _calendarRefreshTick.value++
        viewModelScope.launch {
            val tasks = repository.getTasksForDateWithRecurrence(date)
            _dateTaskCache[date] = tasks
            _selectedDateTasks.value = tasks
            if (_heatMapYear > 0) {
                loadMonthDataSuspend(_heatMapYear, _heatMapMonth)
            }
        }
    }

    fun loadHeatMap(year: Int, month: Int) {
        android.util.Log.d("CAL_PERF", "loadHeatMap($year, $month)")
        _heatMapYear = year
        _heatMapMonth = month
        // Each page handles its own data via produceState — no need to load here
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

        // Reactively update selectedDateTasks when today's tasks change in the DB.
        // This ensures isSyncing flag changes (from WorkManager) propagate to CalendarScreen
        // without requiring a manual refresh or app restart.
        // Also invalidates calendar cache when task count OR content changes
        // (handles notification quick-add AND AI parsing updates).
        viewModelScope.launch {
            var prevCount = -1
            var prevContentFingerprint = ""
            _allTodayTasks.collect { freshTodayTasks ->
                if (_selectedDate.value == today) {
                    _selectedDateTasks.value = freshTodayTasks
                }
                // Build a lightweight fingerprint of task content that AI parsing changes:
                // title, date, deadlineTime, isSyncing. When AI refines a task, at least
                // one of these will change (title gets refined, date/time may be set,
                // isSyncing flips false). This is O(n) string concat — negligible for
                // typical daily task counts (5-30 tasks).
                val contentFingerprint = freshTodayTasks.joinToString("|") {
                    "${it.id}:${it.title}:${it.createdDate}:${it.deadlineTime}:${it.isSyncing}"
                }
                val contentChanged = prevContentFingerprint.isNotEmpty() &&
                    contentFingerprint != prevContentFingerprint
                // When task count changes (e.g. notification quick-add) OR task content
                // changes (e.g. AI parsing refines title/date/time), invalidate calendar
                if (prevCount >= 0 && (freshTodayTasks.size != prevCount || contentChanged)) {
                    invalidateAllCalendarCaches()
                }
                prevCount = freshTodayTasks.size
                prevContentFingerprint = contentFingerprint
            }
        }

        // Invalidate calendar caches when Google sync completes (background or foreground).
        // This handles: first-launch population, background WorkManager sync, notification quick-add.
        viewModelScope.launch {
            var prevCalTime = GoogleCalendarManager.lastSyncTime.value
            var prevTaskTime = GoogleTasksManager.lastSyncTime.value
            GoogleCalendarManager.lastSyncTime.collect { newTime ->
                if (newTime > prevCalTime && prevCalTime > 0L) {
                    android.util.Log.d("CAL_PERF", "Calendar sync detected (${newTime - prevCalTime}ms delta) → invalidating caches")
                    invalidateAllCalendarCaches()
                }
                prevCalTime = newTime
            }
        }
        viewModelScope.launch {
            var prevTaskTime = GoogleTasksManager.lastSyncTime.value
            GoogleTasksManager.lastSyncTime.collect { newTime ->
                if (newTime > prevTaskTime && prevTaskTime > 0L) {
                    android.util.Log.d("CAL_PERF", "Tasks sync detected (${newTime - prevTaskTime}ms delta) → invalidating caches")
                    invalidateAllCalendarCaches()
                }
                prevTaskTime = newTime
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
            // Also refresh admin tasks (broadcasts) on pull-to-refresh
            refreshAdminTasks()
            try {
                val app = appContext.applicationContext as com.theblankstate.preamble.PreambleApplication
                val summary = GoogleSyncCoordinator.syncLinkedData(
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

                // Invalidate calendar cache so new synced data shows immediately
                if (summary.performedAnySync) {
                    invalidateAllCalendarCaches()
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
            // Auto-set default 10-min reminder for tasks with a deadline time
            val taskWithReminder = if (deadlineTime != null && finalTask.remindersJson == null) {
                val defaultReminders = listOf(com.theblankstate.preamble.data.Reminder.DEFAULT)
                val updated = finalTask.copy(remindersJson = com.theblankstate.preamble.data.Reminder.toJson(defaultReminders))
                repository.updateTask(updated)
                updated
            } else finalTask
            scheduleOrCancelAlarm(taskWithReminder)
            refreshStats()
            invalidateCalendarForDate(taskDate)
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
            refreshCalendarDate()

            val newCompleted = !task.isCompleted
            // Cancel or reschedule alarms based on new completion state
            if (newCompleted) {
                com.theblankstate.preamble.notification.TaskAlarmManager.cancelAllReminders(appContext, task.id)
            } else {
                // Un-completing: reschedule reminders
                val updatedTask = repository.getTaskById(task.id)
                if (updatedTask != null) scheduleOrCancelAlarm(updatedTask)
            }
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
            // Cancel all reminder alarms for this task
            com.theblankstate.preamble.notification.TaskAlarmManager.cancelAllReminders(
                appContext, task.id
            )
            // Mark as deleted BEFORE any sync can re-insert it
            repository.markAsDeleted(task.id)
            
            // Delete locally first for Optimistic UI
            repository.deleteTask(task)
            refreshStats()
            invalidateCalendarForDate(task.createdDate)

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

    fun deleteAllRecurrences(task: Task) {
        viewModelScope.launch {
            // Find the template: if this task IS the template, use it; otherwise find by recurrenceParentId
            val templateId = if (task.isRecurrenceTemplate) task.id else task.recurrenceParentId
            if (templateId == null) {
                // Not a recurring task, just delete normally
                deleteTask(task)
                return@launch
            }

            // Get all instances of this recurring task
            val instances = repository.getInstancesForTemplate(templateId)
            val template = repository.getTaskById(templateId)

            // Delete all instances
            for (instance in instances) {
                com.theblankstate.preamble.notification.TaskAlarmManager.cancelAllReminders(appContext, instance.id)
                repository.markAsDeleted(instance.id)
                repository.deleteTask(instance)
                commitDelete(instance)
            }

            // Delete the template itself
            if (template != null) {
                com.theblankstate.preamble.notification.TaskAlarmManager.cancelAllReminders(appContext, template.id)
                repository.markAsDeleted(template.id)
                repository.deleteTask(template)
                commitDelete(template)
            }

            refreshStats()
            // Invalidate calendar for the task's date so calendar updates immediately
            val dateToInvalidate = task.createdDate
            invalidateCalendarForDate(dateToInvalidate)

            _snackbarEvent.tryEmit(SnackbarEvent(
                message = "All recurrences deleted",
                actionLabel = null,
                onAction = {}
            ))
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
     * Determines whether reminders should be scheduled. Paused/completed tasks get cancelled.
     * Active reminder alarms are calculated and scheduled via TaskAlarmManager.
     */
    private fun scheduleOrCancelAlarm(task: Task) {
        if (task.isAlarmPaused || task.isCompleted) {
            com.theblankstate.preamble.notification.TaskAlarmManager.cancelAllReminders(appContext, task.id)
            return
        }

        // If task has reminders in remindersJson, use multi-reminder system
        if (task.hasReminders) {
            com.theblankstate.preamble.notification.TaskAlarmManager.scheduleReminders(appContext, task)
            return
        }

        // Legacy fallback: single alarm from customAlarmTimeMs or deadlineTime
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
            com.theblankstate.preamble.notification.TaskAlarmManager.cancelAllReminders(appContext, task.id)
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

    /** Add a reminder to a task (max 5) */
    fun addReminder(task: Task, reminder: com.theblankstate.preamble.data.Reminder) {
        viewModelScope.launch {
            val current = task.localReminders.toMutableList()
            if (current.size >= com.theblankstate.preamble.data.Reminder.MAX_REMINDERS) return@launch
            current.add(reminder)
            val updated = task.copy(
                remindersJson = com.theblankstate.preamble.data.Reminder.toJson(current),
                updatedTimestamp = System.currentTimeMillis()
            )
            repository.updateTask(updated)
            scheduleOrCancelAlarm(updated)
        }
    }

    /** Remove a reminder by index */
    fun removeReminder(task: Task, index: Int) {
        viewModelScope.launch {
            val current = task.localReminders.toMutableList()
            if (index < 0 || index >= current.size) return@launch
            current.removeAt(index)
            val updated = task.copy(
                remindersJson = com.theblankstate.preamble.data.Reminder.toJson(current),
                updatedTimestamp = System.currentTimeMillis()
            )
            repository.updateTask(updated)
            scheduleOrCancelAlarm(updated)
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
            invalidateCalendarForDate(taskDate)
        }
    }

    private fun triggerRecurrenceGeneration() {
        val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.theblankstate.preamble.recurrence.RecurrenceWorker>().build()
        androidx.work.WorkManager.getInstance(appContext).enqueue(workRequest)
    }

    /** Clears ALL month caches and forces calendar pages to reload.
     *  Used after sync or external data changes (e.g. notification quick-add).
     *  Loads fresh data into cache BEFORE incrementing tick — so when pages
     *  re-render, getCachedMonthData() returns fresh data instantly (no flicker). */
    fun invalidateAllCalendarCaches() {
        _monthCache.clear()
        _dateTaskCache.clear()
        android.util.Log.d("CAL_PERF", "invalidateAllCalendarCaches → cleared all, pre-loading before tick bump")
        viewModelScope.launch {
            // Pre-load current month into cache FIRST
            if (_heatMapYear > 0) {
                loadMonthDataSuspend(_heatMapYear, _heatMapMonth)
            }
            // THEN increment tick — pages re-render with initialValue = fresh cached data
            _calendarRefreshTick.value++
            android.util.Log.d("CAL_PERF", "invalidateAllCalendarCaches → tick=${_calendarRefreshTick.value} (data pre-loaded)")
        }
    }

    /** Clears the month cache for [taskDate] and forces calendar pages to reload. */
    private suspend fun invalidateCalendarForDate(taskDate: String) {
        try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val parsed = sdf.parse(taskDate)
            if (parsed != null) {
                val cal = java.util.Calendar.getInstance().apply { time = parsed }
                val y = cal.get(java.util.Calendar.YEAR)
                val m = cal.get(java.util.Calendar.MONTH)
                _monthCache.remove("$y-$m")
                _dateTaskCache.remove(taskDate)
                android.util.Log.d("ROLLOVER", "invalidateCalendarForDate($taskDate) → cleared month $y-$m, tick=${_calendarRefreshTick.value + 1}")
            }
        } catch (_: Exception) {
            _monthCache.clear()
        }
        _calendarRefreshTick.value++
        if (_heatMapYear > 0) loadMonthDataSuspend(_heatMapYear, _heatMapMonth)
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

    fun unsnoozeTask(taskId: String) {
        viewModelScope.launch {
            repository.unsnoozeTask(taskId)
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
