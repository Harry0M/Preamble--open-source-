package com.theblankstate.preamble

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.theblankstate.preamble.ai.AiChatViewModel
import com.theblankstate.preamble.notification.TaskNotificationManager
import com.theblankstate.preamble.notification.TaskNotificationService
import com.theblankstate.preamble.ui.screens.CalendarScreen
import com.theblankstate.preamble.ui.screens.HomeScreen
import com.theblankstate.preamble.ui.screens.OnboardingScreen
import com.theblankstate.preamble.ui.screens.SettingsScreen
import com.theblankstate.preamble.ui.screens.StatsScreen
import com.theblankstate.preamble.sync.GoogleSyncCoordinator
import com.theblankstate.preamble.ui.theme.PreambleTheme
import com.theblankstate.preamble.ui.theme.ThemePreferences
import com.theblankstate.preamble.viewmodel.TaskViewModel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemePreferences.init(this)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("preamble_prefs", MODE_PRIVATE)
        val onboardingDone = prefs.getBoolean("onboarding_done", false)

        // Start persistent notification service only if user hasn't disabled it
        try {
            if (TaskNotificationService.isEnabled(this)) {
                TaskNotificationService.start(this)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to start notification service", e)
        }

        setContent {
            PreambleTheme {
                var showOnboarding by remember { mutableStateOf(!onboardingDone) }

                if (showOnboarding) {
                    OnboardingScreen(
                        onComplete = {
                            prefs.edit().putBoolean("onboarding_done", true).apply()
                            showOnboarding = false
                            requestExactAlarmPermission()
                            postNotification()
                        }
                    )
                } else {
                    val app = application as PreambleApplication
                    val viewModel: TaskViewModel = viewModel(
                        factory = TaskViewModel.Factory(app.repository, app)
                    )
                    PreambleApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("preamble_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("onboarding_done", false)) {
            postNotification()
            autoSyncGoogleData()
            com.theblankstate.preamble.ads.AppOpenAdManager.showIfNotShownToday(this)
        }
    }

    /**
     * Efficient auto-sync on resume:
     * - Skips if last sync was within the 60-second cooldown.
     * - Skips incremental sync if no syncToken exists yet (background full sync is in progress
     *   after first link — no point doing an empty incremental call).
     * - Runs in background so UI is not blocked.
     */
    private fun autoSyncGoogleData() {
        val cooldownMs = 60 * 1000L

        lifecycleScope.launch {
            try {
                // If Calendar is linked but no syncToken exists yet, the background full sync
                // is still in progress. Skip the incremental call entirely.
                val calendarLinked = com.theblankstate.preamble.sync.GoogleCalendarManager.isLinked.value
                val hasToken = com.theblankstate.preamble.sync.GoogleCalendarManager
                    .hasAnySyncToken(this@MainActivity)
                if (calendarLinked && !hasToken) {
                    android.util.Log.d(
                        "MainActivity",
                        "onResume sync skipped — no syncToken yet, full sync in progress"
                    )
                    return@launch
                }

                val summary = GoogleSyncCoordinator.syncLinkedDataIfStale(
                    context = this@MainActivity,
                    staleAfterMs = cooldownMs,
                    reason = "activity_resume"
                )
                if (summary != null) {
                    android.util.Log.d(
                        "MainActivity",
                        "Auto-synced ${summary.calendarEvents} calendar events + ${summary.googleTasks} Google tasks"
                    )
                }
            } catch (e: Throwable) {
                android.util.Log.e("MainActivity", "Auto-sync failed", e)
            }
        }
    }

    private fun requestExactAlarmPermission() {
        // USE_EXACT_ALARM is auto-granted for alarm/calendar apps (cannot be revoked by user)
        // No runtime permission request needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(android.app.AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                android.util.Log.w("MainActivity", "USE_EXACT_ALARM not granted — this shouldn't happen for alarm apps")
            }
        }
    }

    private fun postNotification() {
        // Notification is automatically managed by TaskNotificationService
    }
}

@Composable
fun PreambleApp(viewModel: TaskViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tasks by viewModel.todayTasks.collectAsState()
    val pastTasks by viewModel.pastTasks.collectAsState()
    val stats by viewModel.statsState.collectAsState()

    val aiChatViewModel: AiChatViewModel = viewModel(
        factory = AiChatViewModel.Factory(
            androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application,
            viewModel
        )
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .navigationBarsPadding()
            ) {
                NavigationBar(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50)),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Tasks") },
                        label = { Text("Tasks") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                            viewModel.refreshStats()
                        },
                        icon = { Icon(Icons.Filled.Analytics, contentDescription = "Stats") },
                        label = { Text("Stats") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.DateRange, contentDescription = "Calendar") },
                        label = { Text("Calendar") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            0 -> HomeScreen(
                tasks = tasks,
                pastTasks = pastTasks,
                streak = stats.streak,
                onAddTask = { title, date, deadlineTime, syncToGoogle, syncToCalendar, priority, description, tags, subtasks -> viewModel.addTask(title, date, deadlineTime, syncToGoogle, syncToCalendar, priority, description, tags, subtasks) },
                onToggleTask = { viewModel.toggleTask(it) },
                onDeleteTask = { viewModel.deleteTask(it) },
                onEditTask = { task, title, date, time, priority, description, tags, recurrenceType, recurrenceInterval, recurrenceDays, recurrenceEndDate ->
                    viewModel.updateTask(task, title, date, time, priority, description, tags, recurrenceType, recurrenceInterval, recurrenceDays, recurrenceEndDate)
                },
                onAddRecurringTask = { title, date, deadlineTime, priority, description, recurrenceType, recurrenceInterval, recurrenceDays, recurrenceEndDate, syncToCalendar, tags, subtasks ->
                    viewModel.addRecurringTask(title, date, deadlineTime, priority, description, recurrenceType, recurrenceInterval, recurrenceDays, recurrenceEndDate, syncToCalendar, tags, subtasks)
                },
                onSyncGoogle = { viewModel.syncGoogleData() },
                isRefreshing = viewModel.isRefreshing.collectAsState().value,
                isBackgroundDeleting = viewModel.isBackgroundDeleting.collectAsState().value,
                aiChatViewModel = aiChatViewModel,
                searchQuery = viewModel.searchQuery.collectAsState().value,
                searchResults = viewModel.searchResults.collectAsState().value,
                onSearchQueryChanged = { viewModel.setSearchQuery(it) },
                onUpdateTaskPriority = { task, priority -> viewModel.updateTaskPriority(task, priority) },
                subtaskCounts = viewModel.subtaskCounts.collectAsState().value,
                expandedTasks = viewModel.expandedTasks.collectAsState().value,
                onToggleTaskExpanded = { viewModel.toggleTaskExpanded(it) },
                onAddSubtask = { parentId, title -> viewModel.addSubtask(parentId, title) },
                onToggleSubtask = { subtaskId, isCompleted -> viewModel.toggleSubtaskCompletion(subtaskId, isCompleted) },
                onDeleteSubtask = { subtaskId -> viewModel.deleteSubtask(subtaskId) },
                onCompleteAllSubtasks = { parentId -> viewModel.completeAllSubtasks(parentId) },
                subtasksProvider = { taskId -> viewModel.getSubtasksForTask(taskId) },
                selectedTagFilter = viewModel.selectedTagFilter.collectAsState().value,
                onTagFilterChanged = { viewModel.setTagFilter(it) },
                isInitialLoad = viewModel.isInitialLoad.collectAsState().value,
                onUpdateAlarmStatus = { task, newTime, paused -> viewModel.updateAlarmStatus(task, newTime, paused) },
                onRetrySync = { task -> viewModel.retrySync(task) },
                onSnoozeTask = { taskId, duration -> viewModel.snoozeTask(taskId, duration) },
                onUnsnoozeTask = { taskId -> viewModel.unsnoozeTask(taskId) },
                onCopyTaskToToday = { task -> viewModel.copyTaskToToday(task) },
                onDeleteAllRecurrences = { task -> viewModel.deleteAllRecurrences(task) },
                onAddReminder = { task, reminder -> viewModel.addReminder(task, reminder) },
                onRemoveReminder = { task, index -> viewModel.removeReminder(task, index) },
                snackbarEvent = viewModel.snackbarEvent,
                modifier = Modifier.padding(innerPadding)
            )
            1 -> StatsScreen(
                statsState = stats,
                modifier = Modifier.padding(innerPadding)
            )
            2 -> {
                var calendarDetailTask by remember { mutableStateOf<com.theblankstate.preamble.data.Task?>(null) }
                var showCalendarAddSheet by remember { mutableStateOf(false) }
                var calendarEditTask by remember { mutableStateOf<com.theblankstate.preamble.data.Task?>(null) }

                CalendarScreen(
                    selectedDateTasks = viewModel.selectedDateTasks.collectAsState().value,
                    heatMap = viewModel.calendarHeatMap.collectAsState().value,
                    monthTasksByDay = viewModel.calendarMonthTasks.collectAsState().value,
                    getCachedMonthData = { key -> viewModel.getCachedMonthData(key) },
                    loadMonthData = { y, m -> viewModel.loadMonthDataSuspend(y, m) },
                    refreshTick = viewModel.calendarRefreshTick.collectAsState().value,
                    onDateSelected = { viewModel.selectDate(it) },
                    onMonthChanged = { year, month -> viewModel.loadHeatMap(year, month) },
                    onToggleTask = { viewModel.toggleTask(it) },
                    onDeleteTask = { viewModel.deleteTask(it) },
                    onTaskDetail = { calendarDetailTask = it },
                    onAddTask = { showCalendarAddSheet = true },
                    modifier = Modifier.padding(innerPadding)
                )

                // TaskDetailBottomSheet — with edit support (past tasks read-only)
                calendarDetailTask?.let { task ->
                    val todayDate = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()) }
                    val isPast = task.createdDate < todayDate
                    com.theblankstate.preamble.ui.components.TaskDetailBottomSheet(
                        task = task,
                        onDismiss = { calendarDetailTask = null },
                        onEdit = if (!isPast && !task.isInfoOnly) {{ calendarEditTask = task; calendarDetailTask = null }} else null,
                        onDelete = {
                            if (!isPast) {
                                viewModel.deleteTask(task)
                            }
                            calendarDetailTask = null
                        }
                    )
                }

                // Edit task sheet
                if (calendarEditTask != null) {
                    com.theblankstate.preamble.ui.components.EditTaskSheet(
                        task = calendarEditTask!!,
                        onDismiss = { calendarEditTask = null },
                        onUpdateTask = { title, date, time, priority, description, tags ->
                            viewModel.updateTask(calendarEditTask!!, title, date, time, priority, description, tags, null, 0, null, null)
                            calendarEditTask = null
                        }
                    )
                }

                // Add task sheet
                if (showCalendarAddSheet) {
                    com.theblankstate.preamble.ui.components.AddTaskSheet(
                        onDismiss = { showCalendarAddSheet = false },
                        onAddTask = { title, date, deadlineTime, syncToGoogle, syncToCalendar, priority, description, tags, subtasks ->
                            viewModel.addTask(title, date, deadlineTime, syncToGoogle, syncToCalendar, priority, description, tags, subtasks)
                            showCalendarAddSheet = false
                        },
                        onAddRecurringTask = { title, date, deadlineTime, priority, description, recurrenceType, recurrenceInterval, recurrenceDays, recurrenceEndDate, syncToCalendar, tags, subtasks ->
                            viewModel.addRecurringTask(title, date, deadlineTime, priority, description, recurrenceType, recurrenceInterval, recurrenceDays, recurrenceEndDate, syncToCalendar, tags, subtasks)
                            showCalendarAddSheet = false
                        },
                        aiChatViewModel = aiChatViewModel
                    )
                }
            }
            3 -> SettingsScreen(
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
