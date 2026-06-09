package com.theblankstate.preamble.viewmodel

import android.content.Context
import com.theblankstate.preamble.analytics.AnalyticsManager
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
    // Existing
    val totalCompleted: Int = 0,
    val totalTasks: Int = 0,
    val todayCompleted: Int = 0,
    val todayTotal: Int = 0,
    val streak: Int = 0,
    val weeklyStats: List<Pair<String, Float>> = emptyList(),
    val dailyCompleted: List<Pair<String, Int>> = emptyList(),
    val monthlyCompleted: List<Pair<String, Int>> = emptyList(),
    // Productivity Score
    val productivityScore: Int = 0,
    val productivityScoreTrend: Int = 0, // -1, 0, +1
    // Focus Timer
    val todayFocusMinutes: Int = 0,
    val todayFocusSessions: Int = 0,
    val totalFocusHours: Float = 0f,
    val averageDailyFocusMinutes: Int = 0,
    val bestFocusDay: String? = null,
    val bestFocusDayMinutes: Int = 0,
    val topFocusedTasks: List<com.theblankstate.preamble.data.TaskFocusSummary> = emptyList(),
    val weeklyFocusData: List<Pair<String, Int>> = emptyList(),
    // Task Type Breakdown
    val taskTypeBreakdown: List<com.theblankstate.preamble.data.TaskTypeBreakdown> = emptyList(),
    // Rollover Health
    val activeRolloverCount: Int = 0,
    val averageRolloverDaysPending: Float = 0f,
    val oldestRolloverTaskTitle: String? = null,
    val oldestRolloverDays: Int = 0,
    val rolloverCompletionRate: Float = 0f,
    // Streak & Consistency
    val longestStreak: Int = 0,
    val weeklyConsistencyDays: Int = 0,
    val monthlyHeatmap: Map<Int, Float> = emptyMap(),
    val yearlyHeatmap: Map<String, Float> = emptyMap(),
    // Weekly Comparison
    val thisWeekCompleted: Int = 0,
    val lastWeekCompleted: Int = 0,
    // Tag Analytics
    val tagStats: List<com.theblankstate.preamble.data.TagStats> = emptyList(),
    val mostActiveTag: String? = null,
    // Karma / Level System (Todoist-inspired)
    val karmaPoints: Int = 0,
    val karmaLevel: String = "Beginner",
    // Historical comparison
    val yesterdayCompleted: Int = 0,
    val yesterdayTotal: Int = 0,
    val thisMonthCompleted: Int = 0,
    val lastMonthCompleted: Int = 0,
    // Trend Intelligence (computed client-side with regression/EMA)
    val movingAvg7Day: Float = 0f,         // 7-day moving average of completions
    val completionVelocity: Float = 0f,    // linear regression slope: +ve = improving
    val momentumScore: Int = 0,            // EMA-based momentum 0–100
    val consistencyScore: Int = 0,         // 0–100: 100 = perfectly consistent output
    val burnoutRiskScore: Float = 0f,      // 0–1: recent drop vs baseline
    val peakDayOfWeek: String = "",        // e.g. "Thu"
    val weekOverWeekGrowth: Float = 0f,    // fractional e.g. 0.25 = +25%
    val monthOverMonthGrowth: Float = 0f,
    val dailyStatsWithDates: List<Triple<String, Int, Int>> = emptyList(), // yyyy-MM-dd, done, total
    // ═══ NEW: Deep Analytics Fields ═══
    val performanceGrade: String = "C",                     // A/B/C/D/F letter grade
    val completionRateToday: Float = 0f,                    // today's completion rate 0-1
    val completionRate7Day: Float = 0f,                     // 7-day avg completion rate
    val completionRate30Day: Float = 0f,                    // 30-day avg completion rate
    val stdDevDaily: Float = 0f,                            // standard deviation of daily completions
    val forecastNext7: List<Pair<String, Float>> = emptyList(), // predicted completions next 7 days
    val weekdayAvg: Float = 0f,                             // avg weekday completions
    val weekendAvg: Float = 0f,                             // avg weekend completions
    val priorityDistribution: List<Pair<String, Float>> = emptyList(), // priority label to percentage
    val avgTaskAgeDays: Float = 0f,                         // avg days a task stays pending
    val procrastinationIndex: Float = 0f,                   // 0-1: ratio of late completions
    val smartInsights: List<String> = emptyList(),           // generated insight strings
    val completionRateHistory: List<Pair<String, Float>> = emptyList(), // daily completion rates for chart
    val productivityScoreHistory: List<Pair<String, Int>> = emptyList(), // daily scores for chart
    val peakHourLabel: String = "",                          // e.g. "2 PM - 6 PM"
    val hourlyDistribution: List<Pair<String, Int>> = emptyList(), // hour bucket to count
)

class TaskViewModel(
    private val repository: TaskRepository,
    private val appContext: Context
) : ViewModel() {

    private val database = (appContext.applicationContext as com.theblankstate.preamble.PreambleApplication).database
    private val syncMutationDao = database.syncMutationDao()

    private val today = TaskRepository.todayString()

    // ── Admin Tasks (broadcasts) ──
    private val adminTaskRepo = AdminTaskRepository(appContext)
    val adminTasks: StateFlow<List<AdminTask>> = adminTaskRepo.adminTasks

    init {
        refreshAdminTasks()
        viewModelScope.launch {
            repository.getAllHabitTasksFlow().collect { habitTasks ->
                try {
                    val streaks = mutableMapOf<String, TaskRepository.HabitStreakData>()
                    for (task in habitTasks) {
                        streaks[task.id] = repository.getHabitStreakData(task)
                    }
                    _habitStreaks.value = streaks
                } catch (e: Exception) {
                    android.util.Log.e("TaskViewModel", "Failed to update habit streaks reactively", e)
                }
            }
        }
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
        val filtered = if (tagFilter == null) tasks
            else tasks.filter { it.tagList.contains(tagFilter) }
        // Smart ordering: deadline urgency × 5 + priority × 3 + staleness + rollover bonus.
        // Snoozed/completed automatically sink to bottom inside TaskOrderer.
        com.theblankstate.preamble.ai.TaskOrderer.ordered(filtered)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Subtask state
    private val _subtaskCounts = MutableStateFlow<Map<String, Pair<Int, Int>>>(emptyMap())
    val subtaskCounts: StateFlow<Map<String, Pair<Int, Int>>> = _subtaskCounts.asStateFlow()

    // Habit streak cache: taskId → HabitStreakData
    private val _habitStreaks = MutableStateFlow<Map<String, TaskRepository.HabitStreakData>>(emptyMap())
    val habitStreaks: StateFlow<Map<String, TaskRepository.HabitStreakData>> = _habitStreaks.asStateFlow()

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

    /**
     * Fetch tasks for any date — exact mirror of what home screen shows for that date.
     * For today, prefer the [todayTasks] StateFlow (already in memory).
     * For other dates, queries the repository with full rollover + recurring resolution.
     */
    suspend fun fetchTasksForDate(date: String): List<Task> {
        val today = TaskRepository.todayString()
        return if (date == today) {
            todayTasks.value
        } else {
            repository.getTasksForDateWithRecurrence(date)
        }
    }

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
            var isFirstEmission = true
            todayTasks.collect { tasks ->
                // Mark initial load as done on first emission
                if (_isInitialLoad.value) _isInitialLoad.value = false
                val completed = tasks.count { it.isCompleted }
                val total = tasks.size
                _statsState.update { it.copy(todayCompleted = completed, todayTotal = total) }
                // Auto-refresh full stats when tasks change (skip first emission — handled by init refreshStats)
                if (isFirstEmission) {
                    isFirstEmission = false
                } else {
                    refreshStats()
                }
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
            // Run all stat queries in parallel
            val streakDef = async(kotlinx.coroutines.Dispatchers.IO) { repository.calculateStreak() }
            val longestStreakDef = async(kotlinx.coroutines.Dispatchers.IO) { repository.calculateLongestStreak() }
            val weeklyDef = async(kotlinx.coroutines.Dispatchers.IO) { repository.getWeeklyStats() }
            val dailyDef = async(kotlinx.coroutines.Dispatchers.IO) { repository.getDailyStatsForRange(14) }
            val monthlyDef = async(kotlinx.coroutines.Dispatchers.IO) { repository.getDailyStatsForRange(30) }
            val breakdownDef = async(kotlinx.coroutines.Dispatchers.IO) { repository.getTaskBreakdownByType() }
            val rolloverDef = async(kotlinx.coroutines.Dispatchers.IO) { repository.getRolloverHealth() }
            val tagDef = async(kotlinx.coroutines.Dispatchers.IO) { repository.getTagAnalytics() }
            val consistencyDef = async(kotlinx.coroutines.Dispatchers.IO) { repository.getWeeklyConsistency() }
            val weekCompareDef = async(kotlinx.coroutines.Dispatchers.IO) { repository.getWeeklyComparison() }
            val heatmapDef = async(kotlinx.coroutines.Dispatchers.IO) { repository.getMonthlyHeatmap() }
            val yearlyHeatmapDef = async(kotlinx.coroutines.Dispatchers.IO) { repository.getYearlyHeatmap() }
            // Historical comparison & trend intelligence
            val yesterdayDef = async(kotlinx.coroutines.Dispatchers.IO) { repository.getYesterdayStats() }
            val monthlyCompDef = async(kotlinx.coroutines.Dispatchers.IO) { repository.getMonthlyComparison() }
            val dailyWithDatesDef = async(kotlinx.coroutines.Dispatchers.IO) { repository.getDailyStatsWithFullDates(365) }
            // Focus stats
            val todayFocusDef = async(kotlinx.coroutines.Dispatchers.IO) { repository.getTodayFocusMinutes() }
            val todayFocusCountDef = async(kotlinx.coroutines.Dispatchers.IO) { repository.getTodayFocusCount() }
            val totalFocusDef = async(kotlinx.coroutines.Dispatchers.IO) { repository.getTotalFocusHours() }
            val avgFocusDef = async(kotlinx.coroutines.Dispatchers.IO) { repository.getAverageDailyFocusMinutes() }
            val bestDayDef = async(kotlinx.coroutines.Dispatchers.IO) { repository.getBestFocusDayData() }
            val topTasksDef = async(kotlinx.coroutines.Dispatchers.IO) { repository.getTopFocusedTasks() }
            val weeklyFocusDef = async(kotlinx.coroutines.Dispatchers.IO) { repository.getWeeklyFocusData() }
            // NEW: Deep analytics queries
            val hourlyDef = async(kotlinx.coroutines.Dispatchers.IO) { repository.getHourlyCompletionDistribution() }
            val priorityDef = async(kotlinx.coroutines.Dispatchers.IO) { repository.getPriorityDistribution() }
            val avgAgeDef = async(kotlinx.coroutines.Dispatchers.IO) { repository.getAvgTaskAgeDays() }
            val procrastDef = async(kotlinx.coroutines.Dispatchers.IO) { repository.getProcrastinationIndex() }
            val wdweDef = async(kotlinx.coroutines.Dispatchers.IO) { repository.getWeekdayWeekendComparison() }

            val (yesterdayDone, yesterdayTot) = yesterdayDef.await()
            val (thisMonthDone, lastMonthDone) = monthlyCompDef.await()
            val dailyWithDates = dailyWithDatesDef.await()
            val streak = streakDef.await()
            val longestStreak = longestStreakDef.await()
            val weekly = weeklyDef.await()
            val daily = dailyDef.await()
            val monthly = monthlyDef.await()
            val breakdown = breakdownDef.await()
            val rollover = rolloverDef.await()
            val tags = tagDef.await()
            val consistency = consistencyDef.await()
            val (thisWeek, lastWeek) = weekCompareDef.await()
            val heatmap = heatmapDef.await()
            val yearlyHeatmap = yearlyHeatmapDef.await()
            val todayFocus = todayFocusDef.await()
            val todayFocusCount = todayFocusCountDef.await()
            val totalFocus = totalFocusDef.await()
            val avgFocus = avgFocusDef.await()
            val bestDay = bestDayDef.await()
            val topTasks = topTasksDef.await()
            val weeklyFocus = weeklyFocusDef.await()
            // NEW: deep analytics results
            val hourlyDist = hourlyDef.await()
            val priorityDist = priorityDef.await()
            val avgTaskAge = avgAgeDef.await()
            val procrastIdx = procrastDef.await()
            val (weekdayAvg, weekendAvg) = wdweDef.await()

            // Compute productivity score
            val currentState = _statsState.value
            val completionRate = if (currentState.totalTasks > 0)
                currentState.totalCompleted.toFloat() / currentState.totalTasks else 0f
            val streakBonus = (streak.toFloat() / 30f).coerceAtMost(1f)
            val focusRatio = (todayFocus.toFloat() / 120f).coerceAtMost(1f)
            val consistencyRate = consistency.toFloat() / 7f
            val score = ((completionRate * 40f + streakBonus * 20f + focusRatio * 20f + consistencyRate * 20f)).toInt().coerceIn(0, 100)

            // Approximate previous score for trend (simple: compare this vs last week completion)
            val trend = when {
                thisWeek > lastWeek -> 1
                thisWeek < lastWeek -> -1
                else -> 0
            }

            // Karma: tasks completed (10pts) + streak longevity (5pts/day) + focus hours (30pts/hr)
            val karmaPoints = (currentState.totalCompleted * 10 + longestStreak * 5 + (totalFocus * 30).toInt()).coerceAtLeast(0)
            val karmaLevel = when {
                karmaPoints >= 7000 -> "Enlightened"
                karmaPoints >= 4000 -> "Master"
                karmaPoints >= 2000 -> "Expert"
                karmaPoints >= 1000 -> "Productive"
                karmaPoints >= 600  -> "Focused"
                karmaPoints >= 300  -> "Consistent"
                karmaPoints >= 100  -> "Getting Started"
                else                -> "Beginner"
            }

            // Trend intelligence computations
            val raw14 = dailyWithDates.takeLast(14).map { it.second }
            val raw30 = dailyWithDates.takeLast(30).map { it.second }
            val velocity = linearRegressionSlope(raw14)
            val emaVal = ema(raw14)
            val maxEmaBase = raw14.maxOrNull()?.toFloat()?.coerceAtLeast(1f) ?: 1f
            val momentumScoreVal = ((emaVal / maxEmaBase) * 100).toInt().coerceIn(0, 100)
            val consistencyScoreVal = consistencyScore(raw30)
            val burnoutRiskVal = burnoutRisk(dailyWithDates.takeLast(10).map { it.second })
            val peakDay = peakDayOfWeek(dailyWithDates.takeLast(60))
            val movingAvg7 = raw14.takeLast(7).average().toFloat()
            val weekGrowth = if (lastWeek > 0) (thisWeek - lastWeek).toFloat() / lastWeek else 0f
            val monthGrowth = if (lastMonthDone > 0) (thisMonthDone - lastMonthDone).toFloat() / lastMonthDone else 0f

            val bestDayFormatted = bestDay?.let {
                try {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    val displaySdf = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault())
                    displaySdf.format(sdf.parse(it.date)!!)
                } catch (_: Exception) { it.date }
            }

            // ═══ NEW: Deep Analytics Computations ═══

            // Performance Grade (A-F based on score)
            val grade = when {
                score >= 85 -> "A"
                score >= 70 -> "B"
                score >= 55 -> "C"
                score >= 40 -> "D"
                else -> "F"
            }

            // Completion rate history
            val completionRateHist = dailyWithDates.takeLast(30).map { (date, done, total) ->
                date to (if (total > 0) done.toFloat() / total else 0f)
            }

            // Completion rates for different periods
            val todayRate = if (currentState.todayTotal > 0) currentState.todayCompleted.toFloat() / currentState.todayTotal else 0f
            val last7 = dailyWithDates.takeLast(7)
            val rate7 = if (last7.isNotEmpty()) {
                val d = last7.sumOf { it.second }
                val t = last7.sumOf { it.third }.coerceAtLeast(1)
                d.toFloat() / t
            } else 0f
            val last30 = dailyWithDates.takeLast(30)
            val rate30 = if (last30.isNotEmpty()) {
                val d = last30.sumOf { it.second }
                val t = last30.sumOf { it.third }.coerceAtLeast(1)
                d.toFloat() / t
            } else 0f

            // Standard deviation of daily completions
            val stdDev = standardDeviation(raw30)

            // Forecast next 7 days using double exponential smoothing
            val sdfForecast = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val forecastValues = doubleExponentialSmooth(raw30, 7)
            val forecast7 = (1..7).map { i ->
                val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, i) }
                val dateStr = sdfForecast.format(cal.time)
                dateStr to (forecastValues.getOrNull(i - 1) ?: movingAvg7)
            }

            // Peak hour from hourly distribution
            val peakBucket = hourlyDist.maxByOrNull { it.second }
            val peakHourStr = peakBucket?.first ?: ""

            // Productivity score history (approximate from daily data)
            val scoreHist = dailyWithDates.takeLast(30).mapIndexed { idx, (date, done, total) ->
                val r = if (total > 0) done.toFloat() / total else 0f
                val dayScore = (r * 40 + streakBonus * 20 + focusRatio * 20 + consistencyRate * 20).toInt().coerceIn(0, 100)
                date to dayScore
            }

            // Smart insights generation
            val insights = generateSmartInsights(
                streak = streak,
                longestStreak = longestStreak,
                peakDay = peakDay,
                weekdayAvg = weekdayAvg,
                weekendAvg = weekendAvg,
                velocity = velocity,
                burnoutRisk = burnoutRiskVal,
                activeRolloverCount = rollover.activeCount,
                avgRolloverDays = rollover.averageDaysPending,
                todayDone = currentState.todayCompleted,
                todayTotal = currentState.todayTotal,
                procrastIdx = procrastIdx,
                consistency = consistencyScoreVal,
                score = score,
                avgAge = avgTaskAge,
                movingAvg = movingAvg7
            )

            _statsState.update {
                it.copy(
                    streak = streak,
                    longestStreak = longestStreak,
                    weeklyStats = weekly,
                    dailyCompleted = daily,
                    monthlyCompleted = monthly,
                    productivityScore = score,
                    productivityScoreTrend = trend,
                    todayFocusMinutes = todayFocus,
                    todayFocusSessions = todayFocusCount,
                    totalFocusHours = totalFocus,
                    averageDailyFocusMinutes = avgFocus,
                    bestFocusDay = bestDayFormatted,
                    bestFocusDayMinutes = (bestDay?.totalSeconds ?: 0) / 60,
                    topFocusedTasks = topTasks,
                    weeklyFocusData = weeklyFocus,
                    taskTypeBreakdown = breakdown,
                    activeRolloverCount = rollover.activeCount,
                    averageRolloverDaysPending = rollover.averageDaysPending,
                    oldestRolloverTaskTitle = rollover.oldestTaskTitle,
                    oldestRolloverDays = rollover.oldestDaysPending,
                    rolloverCompletionRate = rollover.completionRate,
                    weeklyConsistencyDays = consistency,
                    monthlyHeatmap = heatmap,
                    yearlyHeatmap = yearlyHeatmap,
                    thisWeekCompleted = thisWeek,
                    lastWeekCompleted = lastWeek,
                    tagStats = tags,
                    mostActiveTag = tags.firstOrNull()?.tag,
                    karmaPoints = karmaPoints,
                    karmaLevel = karmaLevel,
                    yesterdayCompleted = yesterdayDone,
                    yesterdayTotal = yesterdayTot,
                    thisMonthCompleted = thisMonthDone,
                    lastMonthCompleted = lastMonthDone,
                    movingAvg7Day = movingAvg7,
                    completionVelocity = velocity,
                    momentumScore = momentumScoreVal,
                    consistencyScore = consistencyScoreVal,
                    burnoutRiskScore = burnoutRiskVal,
                    peakDayOfWeek = peakDay,
                    weekOverWeekGrowth = weekGrowth,
                    monthOverMonthGrowth = monthGrowth,
                    dailyStatsWithDates = dailyWithDates,
                    // NEW deep analytics fields
                    performanceGrade = grade,
                    completionRateToday = todayRate,
                    completionRate7Day = rate7,
                    completionRate30Day = rate30,
                    stdDevDaily = stdDev,
                    forecastNext7 = forecast7,
                    weekdayAvg = weekdayAvg,
                    weekendAvg = weekendAvg,
                    priorityDistribution = priorityDist,
                    avgTaskAgeDays = avgTaskAge,
                    procrastinationIndex = procrastIdx,
                    smartInsights = insights,
                    completionRateHistory = completionRateHist,
                    productivityScoreHistory = scoreHist,
                    peakHourLabel = peakHourStr,
                    hourlyDistribution = hourlyDist,
                )
            }
        }
    }

    fun addTask(title: String, date: String? = null, deadlineTime: String? = null, syncToGoogle: Boolean = false, syncToCalendar: Boolean = false, priority: Int = 0, description: String? = null, tags: String? = null, subtasks: List<String> = emptyList(), isHabit: Boolean = false, isEvent: Boolean = false, eventIcon: String? = null, eventColor: String? = null) {
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
                    googleCalendarId = "primary",
                    isEvent = isEvent,
                    eventIcon = eventIcon,
                    eventColor = eventColor
                )
                repository.insertTask(task)
                finalTask = task
                if (!tags.isNullOrBlank()) repository.saveTagOverride(task.id, tags)
                
                val mutation = com.theblankstate.preamble.data.SyncMutation(
                    taskId = tempId,
                    provider = "GOOGLE_CALENDAR",
                    actionType = "CREATE",
                    payloadJson = com.google.gson.Gson().toJson(task)
                )
                syncMutationDao.insert(mutation)
                com.theblankstate.preamble.sync.GoogleSyncQueueWorker.enqueue(appContext)
                
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
                    tags = tags,
                    isEvent = isEvent,
                    eventIcon = eventIcon,
                    eventColor = eventColor
                )
                repository.insertTask(task)
                finalTask = task
                if (!tags.isNullOrBlank()) repository.saveTagOverride(task.id, tags)

                val mutation = com.theblankstate.preamble.data.SyncMutation(
                    taskId = tempId,
                    provider = "GOOGLE_TASKS",
                    actionType = "CREATE",
                    payloadJson = com.google.gson.Gson().toJson(task)
                )
                syncMutationDao.insert(mutation)
                com.theblankstate.preamble.sync.GoogleSyncQueueWorker.enqueue(appContext)

            } else {
                finalTask = repository.addTask(normalizedTitle, date, deadlineTime, priority, normalizedDescription, tags, isEvent = isEvent, eventIcon = eventIcon, eventColor = eventColor)
                    ?: return@launch
            }
            if (subtasks.isNotEmpty()) {
                repository.addSubtasks(finalTask.id, subtasks)
                // Optimistic UI insert to bypass the 5-15ms flow observation delay!
                _subtaskCounts.update { current ->
                    current + (finalTask.id to Pair(0, subtasks.size))
                }
            }
            // Auto-set default reminder for new tasks without existing reminders
            val taskWithReminder = if (finalTask.remindersJson == null) {
                val defaultReminder = if (deadlineTime != null) {
                    // Tasks with deadline: 10-min-before reminder
                    com.theblankstate.preamble.data.Reminder.DEFAULT
                } else {
                    // All-day tasks (no deadline): 9 AM morning reminder on task date
                    val taskDate = finalTask.createdDate
                    com.theblankstate.preamble.data.Reminder.defaultAllDay(taskDate)
                }
                if (defaultReminder != null) {
                    val updated = finalTask.copy(remindersJson = com.theblankstate.preamble.data.Reminder.toJson(listOf(defaultReminder)))
                    repository.updateTask(updated)
                    updated
                } else finalTask
            } else finalTask
            scheduleOrCancelAlarm(taskWithReminder)
            if (isHabit) {
                repository.toggleHabit(finalTask.id, true)
                refreshHabitStreaks()
            }
            refreshStats()
            invalidateCalendarForDate(taskDate)

            // PostHog: Task create event track karo
            AnalyticsManager.trackTaskCreated(
                category = tags ?: "uncategorized",
                isPriority = priority > 0,
                hasDeadline = deadlineTime != null,
                isRecurring = false
            )
        }
    }

    // ── Snackbar / Undo Delete support ──
    private val _snackbarEvent = MutableSharedFlow<SnackbarEvent>(extraBufferCapacity = 1)
    val snackbarEvent = _snackbarEvent.asSharedFlow()

    sealed class CelebrationEvent {
        object Standard : CelebrationEvent()
        data class Milestone(val count: Int) : CelebrationEvent()
        data class StreakDay(val days: Int) : CelebrationEvent()
        data class PerfectDay(val tasks: Int) : CelebrationEvent()
    }

    private val _celebrationEvent = MutableSharedFlow<CelebrationEvent>(extraBufferCapacity = 2)
    val celebrationEvent = _celebrationEvent.asSharedFlow()

    private var pendingDeleteJob: Job? = null
    private var pendingDeleteTask: Task? = null

    data class SnackbarEvent(val message: String, val actionLabel: String? = null, val onAction: (() -> Unit)? = null)

    private fun emitCelebrationEvent() {
        try {
            val prefs = appContext.getSharedPreferences("preamble_prefs", android.content.Context.MODE_PRIVATE)
            val next = prefs.getInt("celebration_total_count", 0) + 1
            prefs.edit().putInt("celebration_total_count", next).apply()

            val stats = _statsState.value
            val currentStreak = stats.streak
            val streakShownKey = "celebration_streak_day_shown_$currentStreak"
            val streakAlreadyShown = prefs.getBoolean(streakShownKey, false)
            // +1 because this completion hasn't been reflected in stats yet (stats recompute async)
            val effectiveDone = stats.todayCompleted + 1
            val total = stats.todayTotal

            val event = when {
                currentStreak in listOf(3, 7, 14, 21, 30, 60, 100, 200, 365) && !streakAlreadyShown -> {
                    prefs.edit().putBoolean(streakShownKey, true).apply()
                    CelebrationEvent.StreakDay(currentStreak)
                }
                total > 0 && effectiveDone >= total -> CelebrationEvent.PerfectDay(total)
                next % 10 == 0 -> CelebrationEvent.Milestone(next)
                else -> CelebrationEvent.Standard
            }
            _celebrationEvent.tryEmit(event)
        } catch (_: Exception) {
            _celebrationEvent.tryEmit(CelebrationEvent.Standard)
        }
    }

    fun toggleTask(task: Task) {
        viewModelScope.launch {
            // Optimistic: toggle locally FIRST
            repository.toggleTask(task)
            if (task.isHabit) {
                refreshHabitStreaks()
            }
            refreshStats()
            refreshCalendarDate()

            val newCompleted = !task.isCompleted

            // PostHog: Task complete/uncomplete track karo
            if (newCompleted) {
                val daysOld = try {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    val created = sdf.parse(task.createdDate)
                    if (created != null) ((System.currentTimeMillis() - created.time) / 86400000).toInt() else 0
                } catch (_: Exception) { 0 }
                AnalyticsManager.trackTaskCompleted(taskId = task.id, daysOld = daysOld)

                emitCelebrationEvent()
            }

            // Cancel or reschedule alarms based on new completion state
            if (newCompleted) {
                com.theblankstate.preamble.notification.TaskAlarmManager.cancelAllReminders(appContext, task.id)
            } else {
                // Un-completing: reschedule reminders
                val updatedTask = repository.getTaskById(task.id)
                if (updatedTask != null) scheduleOrCancelAlarm(updatedTask)
            }
            if (task.source == "google_tasks" && task.id.startsWith("gtask_")) {
                repository.getTaskById(task.id)?.let {
                    repository.updateTask(it.copy(isSyncing = true))
                }
                val mutation = com.theblankstate.preamble.data.SyncMutation(
                    taskId = task.id,
                    provider = "GOOGLE_TASKS",
                    actionType = "TOGGLE_COMPLETE",
                    payloadJson = ""
                )
                syncMutationDao.insert(mutation)
                com.theblankstate.preamble.sync.GoogleSyncQueueWorker.enqueue(appContext)
            }
            if (task.source == "google_calendar" && task.id.startsWith("gcal_")) {
                repository.getTaskById(task.id)?.let {
                    repository.updateTask(it.copy(isSyncing = true))
                }
                val mutation = com.theblankstate.preamble.data.SyncMutation(
                    taskId = task.id,
                    provider = "GOOGLE_CALENDAR",
                    actionType = "TOGGLE_COMPLETE",
                    payloadJson = ""
                )
                syncMutationDao.insert(mutation)
                com.theblankstate.preamble.sync.GoogleSyncQueueWorker.enqueue(appContext)
            }
        }
    }

    fun toggleHabit(task: com.theblankstate.preamble.data.Task) {
        viewModelScope.launch {
            repository.toggleHabit(task.id, !task.isHabit)
            refreshHabitStreaks()
        }
    }

    fun refreshHabitStreaks() {
        viewModelScope.launch {
            try {
                val habitTasks = repository.getAllHabitTasks()
                val streaks = mutableMapOf<String, TaskRepository.HabitStreakData>()
                for (task in habitTasks) {
                    streaks[task.id] = repository.getHabitStreakData(task)
                }
                _habitStreaks.value = streaks
            } catch (e: Exception) {
                android.util.Log.e("TaskViewModel", "Failed to refresh habit streaks", e)
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

            // PostHog: Task delete event track karo
            AnalyticsManager.trackTaskDeleted(taskId = task.id)

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
                val mutation = com.theblankstate.preamble.data.SyncMutation(
                    taskId = task.id,
                    provider = "GOOGLE_TASKS",
                    actionType = "DELETE",
                    payloadJson = taskJson
                )
                syncMutationDao.insert(mutation)
                com.theblankstate.preamble.sync.GoogleSyncQueueWorker.enqueue(appContext)
            }
            if (task.source == "google_calendar" && task.id.startsWith("gcal_")) {
                val mutation = com.theblankstate.preamble.data.SyncMutation(
                    taskId = task.id,
                    provider = "GOOGLE_CALENDAR",
                    actionType = "DELETE",
                    payloadJson = taskJson
                )
                syncMutationDao.insert(mutation)
                com.theblankstate.preamble.sync.GoogleSyncQueueWorker.enqueue(appContext)
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

    fun updateTask(task: Task, newTitle: String, newDate: String?, newDeadlineTime: String?, newPriority: Int = task.priority, newDescription: String? = task.description, newTags: String? = task.tags, newRecurrenceType: String? = task.recurrenceType, newRecurrenceInterval: Int = task.recurrenceInterval ?: 1, newRecurrenceDays: String? = task.recurrenceDays, newRecurrenceEndDate: String? = task.recurrenceEndDate, newIsEvent: Boolean = task.isEvent, newEventIcon: String? = task.eventIcon, newEventColor: String? = task.eventColor) {
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
                recurrenceEndDate = if (newRecurrenceType != null) newRecurrenceEndDate else null,
                isEvent = newIsEvent,
                eventIcon = newEventIcon,
                eventColor = newEventColor
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
                repository.updateTask(updated.copy(isSyncing = true))
                val mutation = com.theblankstate.preamble.data.SyncMutation(
                    taskId = task.id,
                    provider = "GOOGLE_TASKS",
                    actionType = "UPDATE",
                    payloadJson = ""
                )
                syncMutationDao.insert(mutation)
                com.theblankstate.preamble.sync.GoogleSyncQueueWorker.enqueue(appContext)
            }
            if (task.source == "google_calendar" && task.id.startsWith("gcal_")) {
                repository.updateTask(updated.copy(isSyncing = true))
                val mutation = com.theblankstate.preamble.data.SyncMutation(
                    taskId = task.id,
                    provider = "GOOGLE_CALENDAR",
                    actionType = "UPDATE",
                    payloadJson = ""
                )
                syncMutationDao.insert(mutation)
                com.theblankstate.preamble.sync.GoogleSyncQueueWorker.enqueue(appContext)
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
        subtasks: List<String> = emptyList(),
        isHabit: Boolean = false,
        isEvent: Boolean = false,
        eventIcon: String? = null,
        eventColor: String? = null
    ) {
        val normalizedTitle = TaskInputValidator.normalizeTitle(title)
        val normalizedDescription = TaskInputValidator.normalizeDescription(description)
        if (!TaskInputValidator.isValidTitle(normalizedTitle) || !TaskInputValidator.isValidDescription(normalizedDescription)) return
        viewModelScope.launch {
            val taskDate = date ?: TaskRepository.todayString()
            val now = System.currentTimeMillis()
            var createdTaskId: String? = null
            if (syncToCalendar && GoogleCalendarManager.isLinked.value) {
                val tempId = java.util.UUID.randomUUID().toString()
                createdTaskId = tempId
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
                    recurrenceEndDate = recurrenceEndDate,
                    isEvent = isEvent,
                    eventIcon = eventIcon,
                    eventColor = eventColor
                )
                repository.insertTask(task)
                if (!tags.isNullOrBlank()) repository.saveTagOverride(task.id, tags)

                val mutation = com.theblankstate.preamble.data.SyncMutation(
                    taskId = tempId,
                    provider = "GOOGLE_CALENDAR",
                    actionType = "CREATE",
                    payloadJson = com.google.gson.Gson().toJson(task)
                )
                syncMutationDao.insert(mutation)
                com.theblankstate.preamble.sync.GoogleSyncQueueWorker.enqueue(appContext)

            } else {
                val finalTask = repository.addRecurringTask(
                    normalizedTitle, date, deadlineTime, priority, normalizedDescription,
                    recurrenceType, recurrenceInterval, recurrenceDays, recurrenceEndDate,
                    tags = tags,
                    isEvent = isEvent,
                    eventIcon = eventIcon,
                    eventColor = eventColor
                )
                if (finalTask == null) return@launch
                createdTaskId = finalTask.id
                if (subtasks.isNotEmpty()) {
                    repository.addSubtasks(finalTask.id, subtasks)
                    // Optimistic update
                    _subtaskCounts.update { current ->
                        current + (finalTask.id to Pair(0, subtasks.size))
                    }
                }
            }
            if (isHabit && createdTaskId != null) {
                repository.toggleHabit(createdTaskId, true)
                refreshHabitStreaks()
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
                val mutation = com.theblankstate.preamble.data.SyncMutation(
                    taskId = newId,
                    provider = "GOOGLE_CALENDAR",
                    actionType = "CREATE",
                    payloadJson = com.google.gson.Gson().toJson(newTask)
                )
                syncMutationDao.insert(mutation)
                com.theblankstate.preamble.sync.GoogleSyncQueueWorker.enqueue(appContext)
            }

            // Sync to Google Tasks if needed
            if (syncToGoogleTasks) {
                val mutation = com.theblankstate.preamble.data.SyncMutation(
                    taskId = newId,
                    provider = "GOOGLE_TASKS",
                    actionType = "CREATE",
                    payloadJson = com.google.gson.Gson().toJson(newTask)
                )
                syncMutationDao.insert(mutation)
                com.theblankstate.preamble.sync.GoogleSyncQueueWorker.enqueue(appContext)
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
                val mutation = com.theblankstate.preamble.data.SyncMutation(
                    taskId = task.id,
                    provider = "GOOGLE_TASKS",
                    actionType = "TOGGLE_COMPLETE",
                    payloadJson = ""
                )
                syncMutationDao.insert(mutation)
                com.theblankstate.preamble.sync.GoogleSyncQueueWorker.enqueue(appContext)
            }
            if (task.source == "google_calendar" && task.id.startsWith("gcal_")) {
                val mutation = com.theblankstate.preamble.data.SyncMutation(
                    taskId = task.id,
                    provider = "GOOGLE_CALENDAR",
                    actionType = "TOGGLE_COMPLETE",
                    payloadJson = ""
                )
                syncMutationDao.insert(mutation)
                com.theblankstate.preamble.sync.GoogleSyncQueueWorker.enqueue(appContext)
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

// ── Trend Intelligence Math Helpers ──────────────────────────────────────────

/** Ordinary least-squares slope over [values] indexed 0..n-1. +ve = improving. */
private fun linearRegressionSlope(values: List<Int>): Float {
    val n = values.size
    if (n < 2) return 0f
    val sx = (n * (n - 1) / 2).toFloat()
    val sy = values.sum().toFloat()
    val sxy = values.mapIndexed { i, v -> i.toFloat() * v }.sum()
    val sx2 = (0 until n).sumOf { it * it }.toFloat()
    val d = n * sx2 - sx * sx
    if (d == 0f) return 0f
    return (n * sxy - sx * sy) / d
}

/** Exponential moving average — recent values weighted more heavily (alpha = 0.30). */
private fun ema(values: List<Int>, alpha: Float = 0.30f): Float {
    if (values.isEmpty()) return 0f
    var e = values.first().toFloat()
    for (v in values.drop(1)) e = alpha * v + (1f - alpha) * e
    return e
}

/**
 * Consistency score 0–100.
 * Uses inverse coefficient of variation on non-zero days:
 * low CV (stable output) → high score.
 */
private fun consistencyScore(values: List<Int>): Int {
    val nz = values.filter { it > 0 }
    if (nz.size < 2) return if (nz.isEmpty()) 0 else 80
    val mean = nz.average()
    val variance = nz.sumOf { v -> (v - mean) * (v - mean) } / nz.size
    val cv = Math.sqrt(variance) / mean
    return ((1.0 - cv.coerceAtMost(2.0) / 2.0) * 100.0).toInt().coerceIn(0, 100)
}

/**
 * Burnout risk 0–1.
 * Compares average of last 3 days vs previous 7 days.
 * A sharp recent drop relative to baseline = risk.
 */
private fun burnoutRisk(values: List<Int>): Float {
    if (values.size < 7) return 0f
    val recent3 = values.takeLast(3).average()
    val prev7 = values.dropLast(3).takeLast(7).average()
    if (prev7 < 1.0) return 0f
    return ((prev7 - recent3) / prev7).toFloat().coerceIn(0f, 1f)
}

/**
 * Returns 3-letter abbreviation of the day of week with highest average completions
 * over the provided date-stamped values.
 */
private fun peakDayOfWeek(datedValues: List<Triple<String, Int, Int>>): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    val names = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val totals = IntArray(7)
    val counts = IntArray(7)
    datedValues.forEach { (date, comp, _) ->
        try {
            val cal = java.util.Calendar.getInstance().apply { time = sdf.parse(date)!! }
            val dow = cal.get(java.util.Calendar.DAY_OF_WEEK) - 1
            totals[dow] += comp
            counts[dow]++
        } catch (_: Exception) {}
    }
    val avgs = totals.mapIndexed { i, t -> if (counts[i] > 0) t.toFloat() / counts[i] else 0f }
    val peak = avgs.indexOf(avgs.maxOrNull() ?: 0f)
    return if (peak >= 0 && avgs[peak] > 0f) names[peak] else "—"
}

/** Standard deviation of daily completions (population σ). */
private fun standardDeviation(values: List<Int>): Float {
    if (values.size < 2) return 0f
    val mean = values.average()
    val variance = values.sumOf { v -> (v - mean) * (v - mean) } / values.size
    return Math.sqrt(variance).toFloat()
}

/**
 * Double Exponential Smoothing (Holt's method) for trend-aware forecasting.
 * Returns [horizon] predicted future values.
 * alpha controls level smoothing, beta controls trend smoothing.
 */
private fun doubleExponentialSmooth(
    values: List<Int>,
    horizon: Int,
    alpha: Float = 0.3f,
    beta: Float = 0.1f
): List<Float> {
    if (values.isEmpty()) return List(horizon) { 0f }
    if (values.size == 1) return List(horizon) { values[0].toFloat() }

    var level = values[0].toFloat()
    var trend = (values[1] - values[0]).toFloat()

    for (i in 1 until values.size) {
        val newLevel = alpha * values[i] + (1f - alpha) * (level + trend)
        val newTrend = beta * (newLevel - level) + (1f - beta) * trend
        level = newLevel
        trend = newTrend
    }

    return (1..horizon).map { h -> (level + h * trend).coerceAtLeast(0f) }
}

/**
 * Generates smart insights based on stats data.
 * Returns a list of human-readable insight strings with emoji prefixes.
 */
private fun generateSmartInsights(
    streak: Int,
    longestStreak: Int,
    peakDay: String,
    weekdayAvg: Float,
    weekendAvg: Float,
    velocity: Float,
    burnoutRisk: Float,
    activeRolloverCount: Int,
    avgRolloverDays: Float,
    todayDone: Int,
    todayTotal: Int,
    procrastIdx: Float,
    consistency: Int,
    score: Int,
    avgAge: Float,
    movingAvg: Float
): List<String> {
    val insights = mutableListOf<String>()

    // Streak insights
    if (streak >= 7) insights.add("Amazing ${streak}-day streak — your longest is $longestStreak days")
    else if (streak >= 3) insights.add("${streak}-day streak going — push past your record of $longestStreak")
    else if (streak == 0) insights.add("Start a streak today by completing all your tasks")

    // Peak day insight
    if (peakDay.isNotBlank() && peakDay != "—") {
        if (weekdayAvg > 0 && weekendAvg > 0) {
            val ratio = weekdayAvg / weekendAvg
            if (ratio > 1.5f) insights.add("You're ${String.format("%.0f", (ratio - 1) * 100)}% more productive on weekdays")
            else if (ratio < 0.7f) insights.add("You're ${String.format("%.0f", (1 / ratio - 1) * 100)}% more productive on weekends")
        }
        insights.add("Peak day is $peakDay — schedule important tasks then")
    }

    // Velocity insight
    if (velocity > 0.1f) insights.add("Velocity +${String.format("%.1f", velocity)} tasks/day — trending up")
    else if (velocity < -0.2f) insights.add("Velocity dipping — try smaller, tighter tasks")

    // Burnout risk
    if (burnoutRisk >= 0.65f) insights.add("High burnout risk — consider a lighter day")
    else if (burnoutRisk >= 0.35f) insights.add("Moderate burnout risk — watch your pace")

    // Rollover health
    if (activeRolloverCount > 5) insights.add("$activeRolloverCount pending rollover tasks (avg ${String.format("%.0f", avgRolloverDays)} days)")
    else if (activeRolloverCount > 0) insights.add("$activeRolloverCount rollover tasks — tackle the oldest first")

    // Today's progress
    if (todayTotal > 0) {
        if (todayDone == todayTotal) insights.add("All done for today — clean slate")
        else if (todayDone.toFloat() / todayTotal >= 0.75f) insights.add("Almost there — ${todayTotal - todayDone} tasks left today")
    }

    // Procrastination
    if (procrastIdx > 0.5f) insights.add("${(procrastIdx * 100).toInt()}% of tasks completed after their due date")

    // Consistency
    if (consistency >= 80) insights.add("Exceptional consistency: $consistency/100")
    else if (consistency < 30) insights.add("Consistency $consistency/100 — try 1 task every day")

    // Moving average
    if (movingAvg > 0) insights.add("7-day average: ${String.format("%.1f", movingAvg)} tasks/day")

    return insights.take(6) // Cap at 6 most relevant insights
}
