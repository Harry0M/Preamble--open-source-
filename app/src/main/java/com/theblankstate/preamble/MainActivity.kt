package com.theblankstate.preamble

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.theblankstate.preamble.ai.AiChatViewModel
import com.theblankstate.preamble.notification.TaskNotificationManager
import com.theblankstate.preamble.notification.TaskNotificationService
import com.theblankstate.preamble.ui.screens.AiChatScreen
import com.theblankstate.preamble.ui.screens.CalendarScreen
import com.theblankstate.preamble.ui.screens.HomeScreen
import com.theblankstate.preamble.ui.screens.OnboardingScreen
import com.theblankstate.preamble.ui.screens.SettingsScreen
import com.theblankstate.preamble.ui.screens.StatsScreenHost
import com.theblankstate.preamble.sync.GoogleSyncCoordinator
import com.theblankstate.preamble.ui.theme.PreambleTheme
import com.theblankstate.preamble.ui.theme.ThemePreferences
import com.theblankstate.preamble.auth.AuthManager
import com.theblankstate.preamble.ui.components.ExpressiveNavItem
import com.theblankstate.preamble.ui.components.ExpressiveNavigationBar
import com.theblankstate.preamble.viewmodel.TaskViewModel
import com.theblankstate.preamble.analytics.AnalyticsManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Deep link target parsed from the incoming intent (preamble://…). */
    private val _deepLinkTarget = mutableStateOf<String?>(null)

    /** Set true when the launch (or new) intent asks us to open Weekly Wrapped. */
    private val _openWrapped = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemePreferences.init(this)
        enableEdgeToEdge()

        // Parse deep link from launch intent
        _deepLinkTarget.value = parseDeepLink(intent)

        // Weekly Wrapped deep-link from notification
        if (intent.getBooleanExtra(com.theblankstate.preamble.notification.WeeklyWrappedReceiver.EXTRA_OPEN_WRAPPED, false)) {
            _openWrapped.value = true
        }

        // PostHog: Agar FCM notification se app khula hai, click track karo
        trackCampaignClickIfPresent(intent)

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

        // On aggressive-OEM devices (especially Vivo), battery optimization kills the
        // foreground service within 10–15 min. Request "Unrestricted" on first launch
        // so the OS never targets it. Show only once; user can revisit in Settings.
        if (com.theblankstate.preamble.util.OemHelper.isAggressiveKiller &&
            !com.theblankstate.preamble.util.OemHelper.isBatteryOptimizationIgnored(this) &&
            !prefs.getBoolean("battery_opt_asked", false)) {
            prefs.edit().putBoolean("battery_opt_asked", true).apply()
            try {
                com.theblankstate.preamble.util.OemHelper.requestIgnoreBatteryOptimizations(this)
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Battery opt request failed", e)
            }
        }

        // Schedule Sunday 7pm Weekly Wrapped notification (reschedules itself after firing)
        try {
            com.theblankstate.preamble.notification.WeeklyWrappedScheduler.schedule(this)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to schedule weekly wrapped", e)
        }

        setContent {
            PreambleTheme {
                var showOnboarding by remember { mutableStateOf(!onboardingDone) }
                val deepLinkTarget by _deepLinkTarget

                if (showOnboarding) {
                    OnboardingScreen(
                        onComplete = {
                            prefs.edit().putBoolean("onboarding_done", true).apply()
                            showOnboarding = false
                            requestExactAlarmPermission()
                            postNotification()

                            // PostHog: Onboarding complete event track karo
                            AnalyticsManager.trackOnboardingComplete()
                        }
                    )
                } else {
                    val app = application as PreambleApplication
                    val viewModel: TaskViewModel = viewModel(
                        factory = TaskViewModel.Factory(app.repository, app)
                    )

                    // Profile collected during onboarding now. Back-fill Firestore if user
                    // had already onboarded pre-v2 but is missing the new fields.
                    LaunchedEffect(Unit) {
                        if (AuthManager.isSignedIn()) {
                            val stored = com.theblankstate.preamble.data.UserProfileStore.load(this@MainActivity)
                            if (stored.baselineScore > 0) {
                                com.theblankstate.preamble.data.UserProfileStore.syncToFirestore(stored)
                            }
                            // Entitlement: seed promo if first time, then pull latest from Firestore.
                            com.theblankstate.preamble.data.EntitlementStore.seedPromotionalIfFirstTime(this@MainActivity)
                            com.theblankstate.preamble.data.EntitlementStore.syncFromFirestore(this@MainActivity)
                        }
                    }

                    val openWrapped by _openWrapped
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier.fillMaxSize()
                    ) {
                        PreambleApp(
                            viewModel = viewModel,
                            deepLinkTarget = deepLinkTarget,
                            onDeepLinkConsumed = { _deepLinkTarget.value = null },
                            onOpenWrapped = { _openWrapped.value = true },
                        )
                        if (openWrapped) {
                            val statsState = viewModel.statsState.collectAsState().value
                            com.theblankstate.preamble.ui.screens.WrappedScreen(
                                statsState = statsState,
                                onDismiss = { _openWrapped.value = false },
                                modifier = androidx.compose.ui.Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        parseDeepLink(intent)?.let { target ->
            _deepLinkTarget.value = target
        }
        if (intent.getBooleanExtra(com.theblankstate.preamble.notification.WeeklyWrappedReceiver.EXTRA_OPEN_WRAPPED, false)) {
            _openWrapped.value = true
        }
        // PostHog: Agar FCM notification se naya intent aaya, click track karo
        trackCampaignClickIfPresent(intent)
    }

    /**
     * FCM notification click track karta hai PostHog mein.
     * Admin panel se campaign_id aur variant intent extras mein aate hain.
     */
    private fun trackCampaignClickIfPresent(intent: Intent?) {
        val campaignId = intent?.getStringExtra("campaign_id") ?: return
        if (campaignId == "unknown") return
        val variant = intent.getStringExtra("campaign_variant") ?: "default"
        AnalyticsManager.trackNotificationClicked(
            campaignId = campaignId,
            notificationType = variant
        )
    }

    /**
     * Parse deep link from intent.
     * Supported schemes:
     *   preamble://home
     *   preamble://settings
     *   preamble://settings/theme
     *   preamble://settings/calendar
     *   preamble://settings/notifications
     *   preamble://stats
     *   preamble://calendar
     */
    private fun parseDeepLink(intent: Intent?): String? {
        val data = intent?.data ?: return null
        if (data.scheme != "preamble") return null
        val host = data.host ?: return null
        val path = data.path?.trimStart('/') ?: ""
        return if (path.isNotEmpty()) "$host/$path" else host
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("preamble_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("onboarding_done", false)) {
            postNotification()
            autoSyncGoogleData()
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
        // Called after onboarding completes and POST_NOTIFICATIONS has been granted.
        // Service may already be running but notification was blocked during startup
        // (Android 13+ requires permission before any notification shows).
        // Re-starting here forces a fresh startForeground() with permission now active.
        if (TaskNotificationService.isEnabled(this)) {
            TaskNotificationService.start(this)
        }
    }
}

@Composable
fun PreambleApp(
    viewModel: TaskViewModel,
    deepLinkTarget: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
    onOpenWrapped: () -> Unit = {},
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var quickAddTrigger by remember { mutableIntStateOf(0) }

    // PostHog: Har tab change pe screen view track karo
    // Compose mein traditional Activity nahi hota, toh manually track karna padta hai
    val screenNames = remember { listOf("HomeScreen", "StatsScreen", "CalendarScreen", "AiChatScreen", "SettingsScreen") }
    androidx.compose.runtime.LaunchedEffect(selectedTab) {
        AnalyticsManager.trackScreenView(screenNames[selectedTab])
    }

    val tasks by viewModel.todayTasks.collectAsState()
    val pastTasks by viewModel.pastTasks.collectAsState()
    val stats by viewModel.statsState.collectAsState()
    val density = LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0
    val showBottomBar = !(selectedTab == 3 && isImeVisible)

    val expressiveNavItems = remember {
        listOf(
            ExpressiveNavItem("Tasks", Icons.Default.Home),
            ExpressiveNavItem("Stats", Icons.Filled.Analytics),
            ExpressiveNavItem("Calendar", Icons.Default.DateRange),
            ExpressiveNavItem("AI", Icons.Filled.AutoAwesome),
            ExpressiveNavItem("Settings", Icons.Default.Settings),
        )
    }

    // Handle deep link navigation
    androidx.compose.runtime.LaunchedEffect(deepLinkTarget) {
        if (deepLinkTarget != null) {
            // Refresh admin tasks whenever a deep link brings us back
            viewModel.refreshAdminTasks()
            when {
                deepLinkTarget == "home/add" -> {
                    selectedTab = 0
                    quickAddTrigger += 1
                }
                deepLinkTarget == "wrapped" -> onOpenWrapped()
                deepLinkTarget.startsWith("home") -> selectedTab = 0
                deepLinkTarget.startsWith("stats") -> {
                    selectedTab = 1
                    viewModel.refreshStats()
                }
                deepLinkTarget.startsWith("calendar") -> selectedTab = 2
                deepLinkTarget.startsWith("ai") -> selectedTab = 3
                deepLinkTarget.startsWith("settings") -> selectedTab = 4
            }
            onDeepLinkConsumed()
        }
    }

    // Refresh admin tasks when app resumes (e.g. after tapping a notification)
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAdminTasks()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val aiChatViewModel: AiChatViewModel = viewModel(
        factory = AiChatViewModel.Factory(
            androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application,
            viewModel
        )
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                ExpressiveNavigationBar(
                    items = expressiveNavItems,
                    selectedIndex = selectedTab,
                    onItemSelected = { index ->
                        selectedTab = index
                        if (index == 1) viewModel.refreshStats()
                    },
                )
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
                celebrationEvent = viewModel.celebrationEvent,
                openAddTrigger = quickAddTrigger,
                adminTasks = viewModel.adminTasks.collectAsState().value,
                onDismissAdminTask = { viewModel.dismissAdminTask(it) },
                onAdminTaskAction = { viewModel.adminTaskActioned(it) },
                isSignedIn = AuthManager.isSignedIn(),
                modifier = Modifier.padding(innerPadding)
            )
            1 -> StatsScreenHost(
                statsState = stats,
                onRefreshStats = { viewModel.refreshStats() },
                onOpenWrapped = onOpenWrapped,
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

                // TaskDetailBottomSheet — full detail like HomeScreen; past tasks are view-only
                calendarDetailTask?.let { task ->
                    val todayDate = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()) }
                    val isPast = task.createdDate < todayDate
                    val calSubtasks by viewModel.getSubtasksForTask(task.id)
                        .collectAsState(initial = emptyList())
                    com.theblankstate.preamble.ui.components.TaskDetailBottomSheet(
                        task = task,
                        onDismiss = { calendarDetailTask = null },
                        onEdit = if (!isPast && !task.isInfoOnly) {{ calendarEditTask = task; calendarDetailTask = null }} else null,
                        onDelete = {
                            if (!isPast) viewModel.deleteTask(task)
                            calendarDetailTask = null
                        },
                        subtasks = calSubtasks,
                        onAddSubtask = if (!isPast) { { title -> viewModel.addSubtask(task.id, title) } } else null,
                        onToggleSubtask = if (!isPast) { { subtaskId, isCompleted -> viewModel.toggleSubtaskCompletion(subtaskId, isCompleted) } } else null,
                        onDeleteSubtask = if (!isPast) { { subtaskId -> viewModel.deleteSubtask(subtaskId) } } else null,
                        onCompleteAllSubtasks = if (!isPast) { { viewModel.completeAllSubtasks(task.id) } } else null,
                        onAddReminder = if (!isPast) { { reminder -> viewModel.addReminder(task, reminder) } } else null,
                        onRemoveReminder = if (!isPast) { { idx -> viewModel.removeReminder(task, idx) } } else null,
                        onToggleRollover = if (!isPast && !task.isCompleted && task.source == "local" && !task.isRecurrenceTemplate && task.recurrenceParentId == null) {
                            {
                                val newType = if (task.recurrenceType == "rollover") null else "rollover"
                                viewModel.updateTask(task, task.title, task.createdDate, task.deadlineTime, task.priority, task.description, task.tags, newType, task.recurrenceInterval ?: 1, task.recurrenceDays, task.recurrenceEndDate)
                            }
                        } else null,
                        isPastTask = isPast
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
            3 -> {
                val chatScreenVm: com.theblankstate.preamble.ai.AiChatScreenViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = com.theblankstate.preamble.ai.AiChatScreenViewModel.Factory(
                        androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application,
                        viewModel
                    )
                )
                AiChatScreen(
                    viewModel = chatScreenVm,
                    modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
                )
            }
            4 -> SettingsScreen(
                yearlyHeatmap = stats.yearlyHeatmap,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
