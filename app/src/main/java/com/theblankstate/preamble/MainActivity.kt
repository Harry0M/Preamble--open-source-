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
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.theblankstate.preamble.ui.viewmodels.WorkspaceViewModel
import com.theblankstate.preamble.ui.screens.SocialHubScreen
import com.theblankstate.preamble.ui.screens.SocialHubRoute
import com.theblankstate.preamble.repository.Friend
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Deep link target parsed from the incoming intent (preamble://…). */
    private val _deepLinkTarget = mutableStateOf<String?>(null)

    /** Set true when the launch (or new) intent asks us to open the Weekly Recap. */
    private val _openRecap = mutableStateOf(false)

    /** Set true when VoiceEntryActivity redirects here to ask for mic permission */
    private val _requestMicPermission = mutableStateOf(false)

    private lateinit var inAppUpdateManager: com.theblankstate.preamble.util.InAppUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemePreferences.init(this)
        enableEdgeToEdge()

        inAppUpdateManager = com.theblankstate.preamble.util.InAppUpdateManager(this)
        inAppUpdateManager.checkForUpdate()

        // Parse deep link from launch intent
        _deepLinkTarget.value = parseDeepLink(intent)
        captureReferrerIfPresent(_deepLinkTarget.value)

        // Weekly Recap deep-link from notification
        if (intent.getBooleanExtra(com.theblankstate.preamble.notification.WeeklyRecapReceiver.EXTRA_OPEN_RECAP, false)) {
            _openRecap.value = true
        }

        if (intent.action == "REQUEST_MIC_PERMISSION") {
            _requestMicPermission.value = true
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

        // Schedule Weekly Recap notification for the user's chosen day (reschedules itself after firing)
        try {
            com.theblankstate.preamble.notification.WeeklyRecapScheduler.schedule(this)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to schedule weekly recap", e)
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
                            AnalyticsManager.trackOnboardingComplete(AuthManager.isSignedIn())
                        }
                    )
                } else {
                    val app = application as PreambleApplication
                    val viewModel: TaskViewModel = viewModel(
                        factory = TaskViewModel.Factory(app.repository, app)
                    )
                    val workspaceViewModel: WorkspaceViewModel = viewModel()

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

                    val openRecap by _openRecap
                    var showChangelog by remember {
                        mutableStateOf(prefs.getInt("last_changelog_version", 0) < BuildConfig.VERSION_CODE)
                    }

                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier.fillMaxSize()
                    ) {
                        PreambleApp(
                            viewModel = viewModel,
                            workspaceViewModel = workspaceViewModel,
                            deepLinkTarget = deepLinkTarget,
                            onDeepLinkConsumed = { _deepLinkTarget.value = null },
                            onOpenRecap = { _openRecap.value = true },
                        )
                        if (openRecap) {
                            val statsState = viewModel.statsState.collectAsState().value
                            com.theblankstate.preamble.ui.screens.RecapScreen(
                                statsState = statsState,
                                onDismiss = { _openRecap.value = false },
                                modifier = androidx.compose.ui.Modifier.fillMaxSize()
                            )
                        }
                        if (showChangelog) {
                            com.theblankstate.preamble.ui.components.ChangelogSheet(
                                onDismissRequest = {
                                    showChangelog = false
                                    prefs.edit().putInt("last_changelog_version", BuildConfig.VERSION_CODE).apply()
                                }
                            )
                        }

                        val requestMic by _requestMicPermission
                        if (requestMic) {
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = { _requestMicPermission.value = false },
                                title = { androidx.compose.material3.Text("Microphone Permission Required") },
                                text = { androidx.compose.material3.Text("To use the voice task feature from the notification, please enable the microphone permission in app settings.") },
                                confirmButton = {
                                    androidx.compose.material3.TextButton(onClick = {
                                        _requestMicPermission.value = false
                                        val settingsIntent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = android.net.Uri.fromParts("package", packageName, null)
                                        }
                                        startActivity(settingsIntent)
                                    }) {
                                        androidx.compose.material3.Text("Go to Settings")
                                    }
                                },
                                dismissButton = {
                                    androidx.compose.material3.TextButton(onClick = { _requestMicPermission.value = false }) {
                                        androidx.compose.material3.Text("Cancel")
                                    }
                                }
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
            captureReferrerIfPresent(target)
        }
        if (intent.getBooleanExtra(com.theblankstate.preamble.notification.WeeklyRecapReceiver.EXTRA_OPEN_RECAP, false)) {
            _openRecap.value = true
        }
        if (intent.action == "REQUEST_MIC_PERMISSION") {
            _requestMicPermission.value = true
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
        if (data.scheme == "preamble" || data.scheme == "https") {
            // Handle https://preamble.theblankstate.com/invite/XXXXXX
            if (data.host == "preamble.theblankstate.com" && data.path?.startsWith("/invite/") == true) {
                return "invite/" + data.lastPathSegment
            }
            if (data.scheme == "preamble") {
                val host = data.host ?: return null
                val path = data.path?.trimStart('/') ?: ""
                return if (path.isNotEmpty()) "$host/$path" else host
            }
        }
        return null
    }

    /**
     * Captures the referring Preamble_ID from an `invite/{id}` deep link before an
     * account exists on this device (Requirement 2.1), and records the
     * `referral-invite-opened` funnel event (Requirement 6.2).
     *
     * The referrer id is persisted single-use via [com.theblankstate.preamble.referral.PendingReferrerStore]
     * so it survives until the first account creation, where the attribution is
     * written. When an account already exists there is no pre-signup attribution to
     * retain, so the capture is skipped.
     */
    private fun captureReferrerIfPresent(target: String?) {
        if (target == null || !target.startsWith("invite/")) return
        if (AuthManager.isSignedIn()) return

        val normalizedId = com.theblankstate.preamble.collab.PreambleId
            .normalize(target.removePrefix("invite/"))
        if (normalizedId.isBlank()) return

        com.theblankstate.preamble.referral.PendingReferrerStore.save(this, normalizedId)
        AnalyticsManager.trackReferralInviteOpened()
    }

    override fun onResume() {
        super.onResume()
        if (::inAppUpdateManager.isInitialized) {
            inAppUpdateManager.onResume()
        }
        val prefs = getSharedPreferences("preamble_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("onboarding_done", false)) {
            postNotification()
            autoSyncGoogleData()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::inAppUpdateManager.isInitialized) {
            inAppUpdateManager.onDestroy()
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
    workspaceViewModel: WorkspaceViewModel,
    deepLinkTarget: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
    onOpenRecap: () -> Unit = {},
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var quickAddTrigger by remember { mutableIntStateOf(0) }

    var lastTab by remember { mutableIntStateOf(selectedTab) }
    var lastTabStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // PostHog: Har tab change pe screen view track karo
    // Compose mein traditional Activity nahi hota, toh manually track karna padta hai
    // NOTE: index 4's analytics key stays "WorkspaceScreen"/"workspace_screen" on purpose even
    // though the tab is now labeled "Circles" in the UI (SocialHubScreen) — this keeps existing
    // PostHog funnels/dashboards built on that key intact. Only the user-facing label changed.
    val screenNames = remember { listOf("HomeScreen", "StatsScreen", "CalendarScreen", "AiChatScreen", "WorkspaceScreen", "SettingsScreen") }
    androidx.compose.runtime.LaunchedEffect(selectedTab) {
        val now = System.currentTimeMillis()
        val elapsedSec = (now - lastTabStartTime) / 1000.0

        if (elapsedSec > 0.1) {
            if (lastTab == 1) { // StatsScreen
                AnalyticsManager.trackScreenClosed("stats_screen", elapsedSec)
            } else if (lastTab == 3) { // AiChatScreen
                AnalyticsManager.trackScreenClosed("ai_chat_screen", elapsedSec)
            } else if (lastTab == 4) { // WorkspaceScreen
                AnalyticsManager.trackScreenClosed("workspace_screen", elapsedSec)
            }
        }

        AnalyticsManager.trackScreenView(screenNames[selectedTab])
        if (selectedTab == 1) {
            AnalyticsManager.trackScreenOpened("stats_screen")
        } else if (selectedTab == 3) {
            AnalyticsManager.trackScreenOpened("ai_chat_screen")
        } else if (selectedTab == 4) {
            AnalyticsManager.trackScreenOpened("workspace_screen")
        }

        lastTab = selectedTab
        lastTabStartTime = now
    }

    val tabLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(tabLifecycleOwner, selectedTab) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                val elapsedSec = (System.currentTimeMillis() - lastTabStartTime) / 1000.0
                if (selectedTab == 1) {
                    AnalyticsManager.trackScreenClosed("stats_screen", elapsedSec)
                } else if (selectedTab == 3) {
                    AnalyticsManager.trackScreenClosed("ai_chat_screen", elapsedSec)
                }
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                lastTabStartTime = System.currentTimeMillis()
                if (selectedTab == 1) {
                    AnalyticsManager.trackScreenOpened("stats_screen")
                } else if (selectedTab == 3) {
                    AnalyticsManager.trackScreenOpened("ai_chat_screen")
                }
            }
        }
        tabLifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            tabLifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val tasks by viewModel.todayTasks.collectAsState()
    val pastTasks by viewModel.pastTasks.collectAsState()
    val stats by viewModel.statsState.collectAsState()
    val habitStreaks by viewModel.habitStreaks.collectAsState()
    val density = LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0
    val showBottomBar = !(selectedTab == 3 && isImeVisible)

    val incomingAssignments by workspaceViewModel.incomingAssignments.collectAsState()
    val requestsSections by workspaceViewModel.requestsSections.collectAsState()
    val friends by workspaceViewModel.friends.collectAsState()
    val lastCheckedTimestamp = workspaceViewModel.lastCheckedNotificationsTimestamp

    val pendingRequestsCount = remember(requestsSections, friends, lastCheckedTimestamp) {
        requestsSections.incoming.count { it.timestamp > lastCheckedTimestamp } +
        friends.count { it.addedAt > lastCheckedTimestamp }
    }
    val pendingAssignmentsCount = remember(incomingAssignments, lastCheckedTimestamp) {
        incomingAssignments.count { it.assignmentStatus == "pending" && it.createdTimestamp > lastCheckedTimestamp }
    }
    val socialHubBadgeCount = pendingAssignmentsCount + pendingRequestsCount

    // Holds a one-shot requested landing destination for the Circles hub — set by the avatar
    // shortcut on Home and by deep links, consumed exactly once by SocialHubScreen.
    var socialHubInitialRoute by remember { mutableStateOf<SocialHubRoute?>(null) }

    val expressiveNavItems = remember(socialHubBadgeCount) {
        listOf(
            ExpressiveNavItem("Tasks", Icons.Default.Home),
            ExpressiveNavItem("Stats", Icons.Filled.Analytics),
            ExpressiveNavItem("Calendar", Icons.Default.DateRange),
            // "Circles" (formerly "Workspace"): the single merged entry point for Friends,
            // Leaderboard, Circles, and shared/assigned Tasks (SocialHubScreen).
            ExpressiveNavItem("Circles", Icons.Default.Groups, badgeCount = socialHubBadgeCount),
            ExpressiveNavItem("Settings", Icons.Default.Settings),
        )
    }

    var initialInviteId by remember { mutableStateOf<String?>(null) }
    var showTimerScreen by remember { mutableStateOf(false) }
    var timerTargetTask by remember { mutableStateOf<Pair<String?, String?>?>(null) }

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
                deepLinkTarget == "recap" -> onOpenRecap()
                deepLinkTarget.startsWith("home") -> selectedTab = 0
                deepLinkTarget.startsWith("stats") -> {
                    selectedTab = 1
                    viewModel.refreshStats()
                }
                deepLinkTarget.startsWith("calendar") -> selectedTab = 2
                deepLinkTarget.startsWith("invite/") -> {
                    initialInviteId = deepLinkTarget.removePrefix("invite/")
                    socialHubInitialRoute = SocialHubRoute.Friends
                    selectedTab = 3
                }
                deepLinkTarget == "social" -> {
                    socialHubInitialRoute = SocialHubRoute.Friends
                    selectedTab = 3
                }
                deepLinkTarget.startsWith("task/") -> {
                    socialHubInitialRoute = SocialHubRoute.Tasks
                    selectedTab = 3
                }
                deepLinkTarget.startsWith("timer") -> {
                    showTimerScreen = true
                }
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

    val timerViewModel: com.theblankstate.preamble.ui.viewmodels.TaskTimerViewModel = viewModel(
        factory = com.theblankstate.preamble.ui.viewmodels.TaskTimerViewModel.Factory(
            androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application
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
                onOpenFriends = {
                    socialHubInitialRoute = if (socialHubBadgeCount > 0) SocialHubRoute.Tasks else SocialHubRoute.Friends
                    selectedTab = 4
                },
                onOpenSettings = {
                    selectedTab = 3
                },
                pendingRequestsCount = pendingRequestsCount,
                pendingAssignmentsCount = pendingAssignmentsCount,
                tasks = tasks,
                pastTasks = pastTasks,
                streak = stats.streak,
                onAddTask = { title, date, deadlineTime, syncToGoogle, syncToCalendar, priority, description, tags, subtasks, isHabit, isEvent, eventIcon, eventColor, assignedToFriends ->
                    viewModel.addTask(title, date, deadlineTime, syncToGoogle, syncToCalendar, priority, description, tags, subtasks, isHabit, isEvent, eventIcon, eventColor, assignedToFriends)
                },
                onToggleTask = { viewModel.toggleTask(it) },
                onDeleteTask = { viewModel.deleteTask(it) },
                onToggleHabit = { viewModel.toggleHabit(it) },
                onEditTask = { task, title, date, time, priority, description, tags, recurrenceType, recurrenceInterval, recurrenceDays, recurrenceEndDate, isEvent, eventIcon, eventColor ->
                    viewModel.updateTask(task, title, date, time, priority, description, tags, recurrenceType, recurrenceInterval, recurrenceDays, recurrenceEndDate, isEvent, eventIcon, eventColor)
                },
                onAddRecurringTask = { title, date, deadlineTime, priority, description, recurrenceType, recurrenceInterval, recurrenceDays, recurrenceEndDate, syncToCalendar, tags, subtasks, isHabit, isEvent, eventIcon, eventColor ->
                    viewModel.addRecurringTask(title, date, deadlineTime, priority, description, recurrenceType, recurrenceInterval, recurrenceDays, recurrenceEndDate, syncToCalendar, tags, subtasks, isHabit, isEvent, eventIcon, eventColor)
                },
                onAddTaskPendingParse = { rawText, date, deadlineTime, syncToGoogle, syncToCalendar, priority, description, tags, subtasks, isHabit, isEvent, eventIcon, eventColor, recurrenceType, recurrenceInterval, recurrenceDays, recurrenceEndDate, userOverrides, assignedToFriends ->
                    viewModel.addTaskWithPendingAiParse(rawText, date, deadlineTime, syncToGoogle, syncToCalendar, priority, description, tags, subtasks, isHabit, isEvent, eventIcon, eventColor, recurrenceType, recurrenceInterval, recurrenceDays, recurrenceEndDate, userOverrides, assignedToFriends)
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
                habitStreaks = habitStreaks,
                modifier = Modifier.padding(innerPadding)
            )
            1 -> StatsScreenHost(
                statsState = stats,
                onRefreshStats = { viewModel.refreshStats() },
                onOpenRecap = onOpenRecap,
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
                        isPastTask = isPast,
                        habitStreakData = habitStreaks[task.recurrenceParentId ?: task.id]
                    )
                }

                // Edit task sheet
                if (calendarEditTask != null) {
                    com.theblankstate.preamble.ui.components.EditTaskSheet(
                        task = calendarEditTask!!,
                        onDismiss = { calendarEditTask = null },
                        onUpdateTask = { title, date, time, priority, description, tags, isEvent, eventIcon, eventColor ->
                            viewModel.updateTask(calendarEditTask!!, title, date, time, priority, description, tags, null, 0, null, null, isEvent, eventIcon, eventColor)
                            calendarEditTask = null
                        },
                        onToggleHabit = { viewModel.toggleHabit(it) }
                    )
                }

                // Add task sheet
                if (showCalendarAddSheet) {
                    com.theblankstate.preamble.ui.components.AddTaskSheet(
                        onDismiss = { showCalendarAddSheet = false },
                        onAddTask = { title, date, deadlineTime, syncToGoogle, syncToCalendar, priority, description, tags, subtasks, isHabit, isEvent, eventIcon, eventColor, _ ->
                            viewModel.addTask(title, date, deadlineTime, syncToGoogle, syncToCalendar, priority, description, tags, subtasks, isHabit, isEvent, eventIcon, eventColor)
                            showCalendarAddSheet = false
                        },
                        onAddRecurringTask = { title, date, deadlineTime, priority, description, recurrenceType, recurrenceInterval, recurrenceDays, recurrenceEndDate, syncToCalendar, tags, subtasks, isHabit, isEvent, eventIcon, eventColor ->
                            viewModel.addRecurringTask(title, date, deadlineTime, priority, description, recurrenceType, recurrenceInterval, recurrenceDays, recurrenceEndDate, syncToCalendar, tags, subtasks, isHabit, isEvent, eventIcon, eventColor)
                            showCalendarAddSheet = false
                        },
                        aiChatViewModel = aiChatViewModel
                    )
                }
            }
            3 -> {
                // Circles hub: merges Friends, Leaderboard, Circles, and shared/assigned Tasks
                SocialHubScreen(
                    workspaceViewModel = workspaceViewModel,
                    taskViewModel = viewModel,
                    initialInviteId = initialInviteId,
                    onInviteConsumed = { initialInviteId = null },
                    initialRoute = socialHubInitialRoute,
                    onInitialRouteConsumed = { socialHubInitialRoute = null },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            4 -> SettingsScreen(
                yearlyHeatmap = stats.yearlyHeatmap,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }

    if (showTimerScreen) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showTimerScreen = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            com.theblankstate.preamble.ui.screens.TaskTimerScreen(
                timerViewModel = timerViewModel,
                initialTaskId = timerTargetTask?.first,
                initialTaskTitle = timerTargetTask?.second,
                onBack = { showTimerScreen = false },
                onOpenStats = {
                    showTimerScreen = false
                    selectedTab = 1
                    viewModel.refreshStats()
                }
            )
        }
    }
    // Friends and Circles used to render here as separate full-screen overlays reached only via
    // the Home avatar shortcut / deep links. They're now organized routes inside SocialHubScreen
    // (selectedTab == 4 above), reached the same way (avatar shortcut + deep links still work,
    // see onOpenFriends and the deep-link handling above) but consolidated behind the single
    // "Circles" bottom-nav tab per the merged-navigation redesign.
}
