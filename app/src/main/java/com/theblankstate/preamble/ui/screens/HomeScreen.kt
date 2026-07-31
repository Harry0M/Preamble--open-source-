package com.theblankstate.preamble.ui.screens

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import com.theblankstate.preamble.ui.theme.ThemePreferences
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewHeadline
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.foundation.border
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theblankstate.preamble.data.Task
import com.theblankstate.preamble.data.PredefinedTags
import com.theblankstate.preamble.repository.Friend
import com.theblankstate.preamble.ui.components.AddTaskSheet
import com.theblankstate.preamble.ui.components.HapticConfig
import com.theblankstate.preamble.ui.components.LocalHapticConfig
import com.theblankstate.preamble.ui.components.TaskDetailBottomSheet
import com.theblankstate.preamble.ui.components.TaskDetailSheet
import com.theblankstate.preamble.ui.components.RichDateBadge
import com.theblankstate.preamble.ui.components.RichDateHeader
import com.theblankstate.preamble.ui.components.EisenhowerGrid
import com.theblankstate.preamble.ui.components.SubtaskList
import com.theblankstate.preamble.ui.components.SwipeableTaskItem
import com.theblankstate.preamble.ui.components.TaskItem
import com.theblankstate.preamble.ui.components.FocusTimerSheet
import com.theblankstate.preamble.focus.FocusTimerService
import com.theblankstate.preamble.focus.FocusTimerState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin

import android.widget.Toast
import android.app.Activity
import com.theblankstate.preamble.ai.AiChatViewModel
import com.google.firebase.auth.FirebaseAuth
import android.app.AlarmManager
import android.content.Context
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Timer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult

import androidx.compose.ui.text.font.FontWeight
import com.theblankstate.preamble.data.AdminTask
import com.theblankstate.preamble.ui.components.AdminTaskCard
import com.theblankstate.preamble.ui.components.AdminTaskDetailSheet
import com.theblankstate.preamble.viewmodel.TaskViewModel
import com.theblankstate.preamble.viewmodel.DayPlanState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenFriends: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    pendingRequestsCount: Int = 0,
    pendingAssignmentsCount: Int = 0,
    tasks: List<Task>,
    pastTasks: Map<String, List<Task>> = emptyMap(),
    streak: Int,
    onAddTask: (title: String, date: String?, deadlineTime: String?, syncToGoogle: Boolean, syncToCalendar: Boolean, priority: Int, description: String?, tags: String?, subtasks: List<String>, isHabit: Boolean, isEvent: Boolean, eventIcon: String?, eventColor: String?, assignedToFriends: List<com.theblankstate.preamble.repository.Friend>) -> Unit,
    onToggleTask: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit,
    onToggleHabit: ((Task) -> Unit)? = null,
    onEditTask: ((Task, String, String?, String?, Int, String?, String?, String?, Int, String?, String?, Boolean, String?, String?) -> Unit)? = null,
    onAddRecurringTask: ((title: String, date: String?, deadlineTime: String?, priority: Int, description: String?, recurrenceType: String, recurrenceInterval: Int, recurrenceDays: String?, recurrenceEndDate: String?, syncToCalendar: Boolean, tags: String?, subtasks: List<String>, isHabit: Boolean, isEvent: Boolean, eventIcon: String?, eventColor: String?) -> Unit)? = null,
    onAddTaskPendingParse: ((rawText: String, date: String?, deadlineTime: String?, syncToGoogle: Boolean, syncToCalendar: Boolean, priority: Int, description: String?, tags: String?, subtasks: List<String>, isHabit: Boolean, isEvent: Boolean, eventIcon: String?, eventColor: String?, recurrenceType: String?, recurrenceInterval: Int, recurrenceDays: String?, recurrenceEndDate: String?, userOverrides: String, assignedToFriends: List<com.theblankstate.preamble.repository.Friend>) -> Unit)? = null,
    onSyncGoogle: (() -> Unit)? = null,
    isRefreshing: Boolean = false,
    isBackgroundDeleting: Boolean = false,
    aiChatViewModel: AiChatViewModel? = null,
    searchQuery: String = "",
    searchResults: List<Task> = emptyList(),
    onSearchQueryChanged: (String) -> Unit = {},
    onUpdateTaskPriority: ((Task, Int) -> Unit)? = null,
    subtaskCounts: Map<String, Pair<Int, Int>> = emptyMap(),
    expandedTasks: Set<String> = emptySet(),
    onToggleTaskExpanded: ((String) -> Unit)? = null,
    onAddSubtask: ((String, String) -> Unit)? = null,
    onToggleSubtask: ((String, Boolean) -> Unit)? = null,
    onDeleteSubtask: ((String) -> Unit)? = null,
    onCompleteAllSubtasks: ((String) -> Unit)? = null,
    subtasksProvider: ((String) -> kotlinx.coroutines.flow.Flow<List<Task>>)? = null,
    selectedTagFilter: String? = null,
    onTagFilterChanged: ((String?) -> Unit)? = null,
    isInitialLoad: Boolean = false,
    onUpdateAlarmStatus: (Task, Long?, Boolean) -> Unit = { _, _, _ -> },
    onRetrySync: ((Task) -> Unit)? = null,
    onSnoozeTask: ((String, Long) -> Unit)? = null,
    onUnsnoozeTask: ((String) -> Unit)? = null,
    onCopyTaskToToday: ((Task) -> Unit)? = null,
    onDeleteAllRecurrences: ((Task) -> Unit)? = null,
    onAddReminder: ((Task, com.theblankstate.preamble.data.Reminder) -> Unit)? = null,
    onRemoveReminder: ((Task, Int) -> Unit)? = null,
    snackbarEvent: SharedFlow<TaskViewModel.SnackbarEvent>? = null,
    celebrationEvent: SharedFlow<TaskViewModel.CelebrationEvent>? = null,
    openAddTrigger: Int = 0,
    adminTasks: List<AdminTask> = emptyList(),
    onDismissAdminTask: ((String) -> Unit)? = null,
    onAdminTaskAction: ((String) -> Unit)? = null,
    isSignedIn: Boolean = true,
    habitStreaks: Map<String, com.theblankstate.preamble.repository.TaskRepository.HabitStreakData> = emptyMap(),
    modifier: Modifier = Modifier
) {
    var showAddSheet by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(openAddTrigger) {
        if (openAddTrigger > 0) showAddSheet = true
    }
    var showEisenhowerView by remember { mutableStateOf(false) }
    var showFilterBar by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var taskToDelete by remember { mutableStateOf<Task?>(null) }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }
    var taskToShowDetail by remember { mutableStateOf<Task?>(null) }
    var adminTaskToShow by remember { mutableStateOf<AdminTask?>(null) }
    var showAlarmSheet by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var showFocusSheet by remember { mutableStateOf(false) }
    var focusTaskId by remember { mutableStateOf<String?>(null) }
    var focusTaskTitle by remember { mutableStateOf<String?>(null) }
    var isVoiceListening by remember { mutableStateOf(false) }
    var voiceText by remember { mutableStateOf("") }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val context = LocalContext.current
    val activity = context as? Activity
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    var isLateNightDismissed by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    // ── Track A (AI Plan-My-Day) + Track B (premium gate) wiring ──
    // Reuse the existing app-scoped TaskRepository and the Activity-scoped TaskViewModel
    // (keyed by class within the store, so this is the same instance the rest of the app uses).
    val planApp = remember(context) { context.applicationContext as com.theblankstate.preamble.PreambleApplication }
    val planTaskViewModel: TaskViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = TaskViewModel.Factory(planApp.repository, planApp)
    )
    val dayPlanViewModel: com.theblankstate.preamble.viewmodel.DayPlanViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(
            factory = com.theblankstate.preamble.viewmodel.DayPlanViewModel.Factory(planApp.repository, planTaskViewModel)
        )
    val dayPlanState by dayPlanViewModel.state.collectAsState()
    // Drives the full-screen Planning_Screen overlay (Req 18.1). Owned here; the alive FAB
    // entry point that sets this true is wired by task 13.2.
    var showPlanningScreen by remember { mutableStateOf(false) }
    // Non-null while a locked feature's upsell is being shown (Req 11.1, 11.3).
    var upsellFeature by remember { mutableStateOf<com.theblankstate.preamble.data.PremiumFeature?>(null) }

    // Plan-My-Day entry point: run the Track B gate, then either upsell or request a plan.
    val onPlanMyDay: () -> Unit = {
        val feature = com.theblankstate.preamble.data.PremiumFeature.AI_AUTO_PLANNING
        val unlocked = com.theblankstate.preamble.data.FeatureGate.isUnlocked(context, feature)
        com.theblankstate.preamble.analytics.AnalyticsManager.trackGateEvaluated("AI_AUTO_PLANNING", unlocked) // Req 12.1
        if (unlocked) {
            // Req 20.3 / 18.1: open the Planning_Screen pre-planning setup surface.
            showPlanningScreen = true
        } else {
            // Req 20.1, 20.2: locked ⇒ show the upsell and do NOT open planning or call planDay.
            upsellFeature = feature
            com.theblankstate.preamble.analytics.AnalyticsManager.trackUpsellShown("AI_AUTO_PLANNING") // Req 12.2
        }
    }

    val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val scaleFactor = (screenWidthDp / 360f).coerceIn(0.85f, 1.15f)

    val isCalendarSyncing by com.theblankstate.preamble.sync.GoogleCalendarManager.isSyncing.collectAsState()
    val isManualSyncing by com.theblankstate.preamble.sync.GoogleCalendarManager.isManualSyncing.collectAsState()
    val isTasksSyncing by com.theblankstate.preamble.sync.GoogleTasksManager.isSyncing.collectAsState()
    // Background full sync flag — only shown in UI on very first sync (no prior data)
    val isBgSyncing by com.theblankstate.preamble.sync.GoogleCalendarManager.isBgSyncing.collectAsState()
    val lastCalSyncTime by com.theblankstate.preamble.sync.GoogleCalendarManager.lastSyncTime.collectAsState()
    // Theme + focus timer state hoisted here so they're collected once, not buried inside nested lambdas
    val themeMode by ThemePreferences.themeMode.collectAsState()
    val colorfulCards by ThemePreferences.colorfulCards.collectAsState()
    val isTimelineEnabled by ThemePreferences.timelineUi.collectAsState()
    val focusState by FocusTimerService.state.collectAsState()

    // Personal Mode prefs
    val personalMode    by ThemePreferences.personalMode.collectAsState()
    val pmGreeting      by ThemePreferences.pmGreeting.collectAsState()
    val pmSmartProgress by ThemePreferences.pmSmartProgress.collectAsState()
    val pmLateNight     by ThemePreferences.pmLateNight.collectAsState()
    val pmSmartEmpty    by ThemePreferences.pmSmartEmpty.collectAsState()
    val pmLastTask      by ThemePreferences.pmLastTask.collectAsState()
    val pmStreakWarn    by ThemePreferences.pmStreakWarn.collectAsState()
    val pmBests         by ThemePreferences.pmBests.collectAsState()
    val pmMilestones    by ThemePreferences.pmMilestones.collectAsState()
    val pmSparkle       by ThemePreferences.pmSparkle.collectAsState()
    val pmEasterEgg     by ThemePreferences.pmEasterEgg.collectAsState()
    val pmEndowedProgress by ThemePreferences.pmEndowedProgress.collectAsState()
    val pmVariableRewards by ThemePreferences.pmVariableRewards.collectAsState()

    // Time-aware computed values (stable for the session — doesn't drift mid-use)
    val currentHour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }

    // Fetch friends for the face pile
    val workspaceViewModel: com.theblankstate.preamble.ui.viewmodels.WorkspaceViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val friends by workspaceViewModel.friends.collectAsState()
    // Source the user's Circles for the create-sheet Recipient_Picker (Requirements 28.1, 30.1).
    // CircleViewModel is an AndroidViewModel(application), so the default factory supplies the
    // Application and no explicit factory is needed (mirrors how WorkspaceViewModel is obtained).
    val circleViewModel: com.theblankstate.preamble.ui.viewmodels.CircleViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val circles by circleViewModel.circles.collectAsState()
    // Collected so the optimistic nudged/cooldown state in the detail sheets recomposes
    // when a nudge is sent (social-engagement Requirements 10.4, 12.2).
    val nudgedTargets by workspaceViewModel.nudgedTargets.collectAsState()
    // Home Incoming_Section source: own incoming (pending) collaborative assignments, narrowed by
    // the single source of truth for incoming selection (Requirements 19.1, 19.8).
    val incomingAssignments by workspaceViewModel.incomingAssignments.collectAsState()
    val incomingTasks = com.theblankstate.preamble.collab.IncomingTasks.incoming(incomingAssignments)
    val isLateNight = remember { currentHour >= 23 || currentHour < 5 }
    val timeGreeting = remember {
        val condition = when (currentHour) {
            in 5..11  -> "morning"
            in 12..16 -> "afternoon"
            else      -> "evening"
        }
        // Check Firestore override first, fallback to hardcoded
        val override = com.theblankstate.preamble.repository.PmMessageRepository.getMessage(context, "greeting", condition)
        override?.headline ?: when (currentHour) {
            in 5..8   -> "Good Morning"
            in 9..11  -> "Let's Crush It"
            in 12..16 -> "Afternoon Focus"
            in 17..19 -> "Evening Wind Down"
            in 20..22 -> "Almost Done"
            else      -> "Night Owl Mode"
        }
    }
    val userName = remember {
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName
            ?.split(" ")?.firstOrNull()
    }
    val greetingText = remember(userName, timeGreeting) {
        if (userName != null) "$timeGreeting, $userName" else timeGreeting
    }

    // Today's aggregate stats (needed at top level for LaunchedEffects)
    val todayCompleted = remember(tasks) { tasks.count { it.isCompleted } }
    val todayTotal = tasks.size

    val handleToggleTask: (com.theblankstate.preamble.data.Task) -> Unit = remember(personalMode, pmVariableRewards, onToggleTask, context, haptics) {
        { task ->
            onToggleTask(task)
            if (personalMode && pmVariableRewards && !task.isCompleted) {
                val rand = Math.random()
                if (rand < 0.3) {
                    val cheers = listOf("Great job!", "Keep it up!", "Nice!", "You're doing great!", "Awesome!")
                    android.widget.Toast.makeText(context, cheers.random(), android.widget.Toast.LENGTH_SHORT).show()
                }
                if (rand < 0.5) {
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                }
            }
        }
    }

    // Easter egg state
    var easterEggTaps by remember { mutableStateOf(0) }
    var showEasterEgg by remember { mutableStateOf(false) }

    // Streak milestone detection
    androidx.compose.runtime.LaunchedEffect(streak) {
        if (personalMode && pmMilestones && streak in listOf(7, 14, 30, 50, 100)) {
            val prefs = context.getSharedPreferences("preamble_prefs", android.content.Context.MODE_PRIVATE)
            val key = "streak_milestone_shown_$streak"
            if (!prefs.getBoolean(key, false)) {
                prefs.edit().putBoolean(key, true).apply()
                val msg = when (streak) {
                    7   -> "$streak-day streak! You're building a habit."
                    14  -> "2 weeks strong! Consistency is your superpower."
                    30  -> "30 days! You're unstoppable."
                    50  -> "50-day streak! Most people give up. You didn't."
                    100 -> "100 days. Legendary."
                    else -> "$streak-day streak!"
                }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    // Personal best detection
    androidx.compose.runtime.LaunchedEffect(todayCompleted) {
        if (personalMode && pmBests && todayCompleted > 0 && todayCompleted == todayTotal) {
            val prefs = context.getSharedPreferences("preamble_prefs", android.content.Context.MODE_PRIVATE)
            val prevBest = prefs.getInt("personal_best_tasks_day", 0)
            if (todayTotal > prevBest) {
                prefs.edit().putInt("personal_best_tasks_day", todayTotal).apply()
                Toast.makeText(context, "New record! $todayTotal tasks — your best ever!", Toast.LENGTH_LONG).show()
            }
        }
    }
    val hasSyncedBefore = remember {
        context.getSharedPreferences("PreamblePrefs", android.content.Context.MODE_PRIVATE)
            .getLong("last_sync_time", 0L) > 0L
    }
    // Show progress bar for:
    // 1. Pull-to-refresh (isRefreshing)
    // 2. Background deletion
    // 3. Manual calendar sync (isManualSyncing)
    // 4. Any sync when we've synced before (shows user that bg sync is updating)
    // 5. SPECIAL: Background full sync with no prior data (initial link, lastCalSyncTime==0)
    val showInitialFullSync = isBgSyncing && lastCalSyncTime == 0L
    val showSyncIndicator = isRefreshing || isBackgroundDeleting || isManualSyncing ||
        ((isCalendarSyncing || isTasksSyncing) && hasSyncedBefore) ||
        showInitialFullSync

    // Voice recognizer for FAB — lazily created only when user taps mic
    var speechRecognizerRef by remember { mutableStateOf<SpeechRecognizer?>(null) }

    fun getOrCreateRecognizer(): SpeechRecognizer {
        return speechRecognizerRef ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            speechRecognizerRef = it
        }
    }

    DisposableEffect(Unit) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isVoiceListening = true
                voiceText = "Listening..."
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                isVoiceListening = false
                voiceText = ""
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spoken = matches?.firstOrNull()
                if (!spoken.isNullOrBlank()) {
                    if (aiChatViewModel != null) {
                        aiChatViewModel.processVoiceCommand(spoken) { result ->
                            voiceText = result
                            Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        onAddTask(spoken, null, null, false, false, 0, null, null, emptyList(), false, false, null, null, emptyList())
                        voiceText = "Saved: $spoken"
                    }
                }
                isVoiceListening = false
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    voiceText = matches[0]
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
        // Set listener only when recognizer exists (deferred)
        speechRecognizerRef?.setRecognitionListener(listener)

        // Store listener to apply when recognizer is created
        onDispose {
            speechRecognizerRef?.destroy()
            speechRecognizerRef = null
        }
    }

    // Apply listener whenever recognizer is created
    val recognizerListener = remember {
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isVoiceListening = true
                voiceText = "Listening..."
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                isVoiceListening = false
                voiceText = ""
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spoken = matches?.firstOrNull()
                if (!spoken.isNullOrBlank()) {
                    if (aiChatViewModel != null) {
                        aiChatViewModel.processVoiceCommand(spoken) { result ->
                            voiceText = result
                            Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        onAddTask(spoken, null, null, false, false, 0, null, null, emptyList(), false, false, null, null, emptyList())
                        voiceText = "Saved: $spoken"
                    }
                }
                isVoiceListening = false
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    voiceText = matches[0]
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }


    val snackbarHostState = remember { SnackbarHostState() }

    // Collect snackbar events from ViewModel
    if (snackbarEvent != null) {
        androidx.compose.runtime.LaunchedEffect(snackbarEvent) {
            snackbarEvent.collectLatest { event ->
                val result = snackbarHostState.showSnackbar(
                    message = event.message,
                    actionLabel = event.actionLabel,
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    event.onAction?.invoke()
                }
            }
        }
    }

    // Collect celebration events from ViewModel
    var activeCelebration by remember { mutableStateOf<TaskViewModel.CelebrationEvent?>(null) }
    if (celebrationEvent != null) {
        androidx.compose.runtime.LaunchedEffect(celebrationEvent) {
            celebrationEvent.collectLatest { event ->
                activeCelebration = event
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                CenterAlignedTopAppBar(
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    navigationIcon = {
                        Box {
                            Row(
                                modifier = Modifier
                                    .padding(start = 16.dp)
                                    .clickable(onClick = onOpenSettings),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val displaySeeds = friends.map { it.preambleId } + listOf("dummy_a", "dummy_b")
                                val displayAvatars = displaySeeds.take(2)
                                val extraCount = if (friends.size > 2) friends.size - 2 else 1 // Always show a third circle
                                
                                Box(contentAlignment = Alignment.CenterStart) {
                                    displayAvatars.forEachIndexed { index, seed ->
                                        coil.compose.AsyncImage(
                                            model = "https://api.dicebear.com/9.x/micah/png?seed=$seed",
                                            contentDescription = "Friend Avatar",
                                            modifier = Modifier
                                                .padding(start = (index * 24).dp)
                                                .size(36.dp)
                                                .clip(androidx.compose.foundation.shape.CircleShape)
                                                .background(Color.White)
                                                .border(2.dp, MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.CircleShape)
                                        )
                                    }
                                    Surface(
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .padding(start = (2 * 24).dp)
                                            .size(36.dp)
                                            .border(2.dp, MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.CircleShape)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.MoreHoriz, contentDescription = "More friends", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }

                            val showFriendRequestBadge = pendingRequestsCount > 0
                            val showTaskBadge = pendingAssignmentsCount > 0 && !showFriendRequestBadge

                            if (showFriendRequestBadge || showTaskBadge) {
                                val badgeIcon = if (showFriendRequestBadge) Icons.Default.Person else Icons.AutoMirrored.Filled.Assignment
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 4.dp, y = (-4).dp)
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(Color.Red),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = badgeIcon,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                        }
                    },
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val todayStr = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) }
                            val todayHoliday = tasks.firstOrNull { it.isInfoOnly && it.eventType == "holiday" && it.createdDate == todayStr }?.title

                            val titleClickMod = if (pmEasterEgg) Modifier.clickable {
                                easterEggTaps++
                                if (easterEggTaps >= 7) { 
                                    showEasterEgg = true
                                    easterEggTaps = 0
                                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                }
                            } else Modifier

                            if (personalMode && pmGreeting) {
                                // Personal mode: greeting as primary title, "Preamble" as subtle subtitle
                                Row(
                                    modifier = titleClickMod,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        val titleLargeFontSize = MaterialTheme.typography.titleLarge.fontSize
                                        var greetingFontSize by remember(greetingText) {
                                            mutableStateOf(titleLargeFontSize)
                                        }
                                        androidx.compose.animation.AnimatedContent(
                                            targetState = greetingText,
                                            transitionSpec = {
                                                fadeIn(animationSpec = tween(300)) togetherWith
                                                fadeOut(animationSpec = tween(300))
                                            },
                                            label = "greetingAnimation"
                                        ) { animatedText ->
                                            Text(
                                                text = animatedText,
                                                style = MaterialTheme.typography.titleLarge.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = greetingFontSize
                                                ),
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Clip,
                                                onTextLayout = { result ->
                                                    if (result.hasVisualOverflow && greetingFontSize > androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp)) {
                                                        greetingFontSize = greetingFontSize * 0.85f
                                                    }
                                                }
                                            )
                                        }
                                        Text(
                                            text = "Preamble",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                        )
                                    }
                                } // closes personal mode Row
                            } else {
                                // Classic mode — original layout preserved
                                Row(
                                    modifier = titleClickMod,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Preamble",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        // Alarm icon
                        IconButton(onClick = { showAlarmSheet = true }) {
                            Icon(Icons.Filled.Alarm, contentDescription = "Alarms")
                        }

                        // Streak badge — Material icon + count
                        if (streak > 0) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.LocalFireDepartment,
                                        contentDescription = "Streak",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "$streak",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        // Filter/Views menu (Arrow down)
                        IconButton(onClick = { 
                            showFilterBar = !showFilterBar 
                            if (!showFilterBar) {
                                isSearchActive = false
                                onSearchQueryChanged("")
                            }
                        }) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Toggle views and filters")
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            },
            floatingActionButton = {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Speed Dial FAB: Merges Focus Timer & Voice Input into one expanding tools FAB
                    ToolsSpeedDialFab(
                        focusState = focusState,
                        isVoiceListening = isVoiceListening,
                        onOpenFocus = {
                            focusTaskId = null
                            focusTaskTitle = null
                            showFocusSheet = true
                        },
                        onToggleVoice = {
                            if (isVoiceListening) {
                                speechRecognizerRef?.stopListening()
                                isVoiceListening = false
                            } else {
                                val recognizer = getOrCreateRecognizer()
                                recognizer.setRecognitionListener(recognizerListener)
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                                }
                                recognizer.startListening(intent)
                            }
                        },
                        scaleFactor = scaleFactor
                    )

                    // Plan my day — alive morphing FAB (Track A entry point, gated by Track B).
                    // Req 19.1, 19.2: a Material 3 Expressive morphing-shape FAB that feels alive.
                    PlanMyDayFab(onClick = onPlanMyDay)

                    // Add task FAB
                    ExtendedFloatingActionButton(
                        onClick = { showAddSheet = true },
                        icon = { Icon(Icons.Default.Add, contentDescription = "Add task") },
                        text = { Text("Add Task") },
                        shape = CircleShape
                    )
                }
            }
        ) { padding ->
            val isDarkTheme = isSystemInDarkTheme()
            val isLightMode = themeMode == ThemePreferences.ThemeMode.LIGHT || (themeMode == ThemePreferences.ThemeMode.SYSTEM && !isDarkTheme)

            val cardColors = listOf(
                Color(0xFFE3F2FD), // Blue
                Color(0xFFF3E5F5), // Purple
                Color(0xFFE8F5E9), // Green
                Color(0xFFFFF3E0), // Orange
                Color(0xFFFFEBEE), // Red
                Color(0xFFFFFDE7)  // Yellow
            )

            // Provide haptic config once for all TaskItems — avoids per-item SharedPrefs + system service lookups
            val hapticConfig = remember {
                val prefs = context.getSharedPreferences("preamble_prefs", android.content.Context.MODE_PRIVATE)
                val enabled = prefs.getBoolean("haptic_feedback_enabled", true)
                val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                    vm?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                }
                HapticConfig(enabled, vibrator)
            }
            CompositionLocalProvider(LocalHapticConfig provides hapticConfig) {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (showSyncIndicator) {
                    androidx.compose.material3.LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                    )
                }

                // Signed-out info strip
                AnimatedVisibility(visible = !isSignedIn) {
                    var signedOutBannerDismissed by remember { mutableStateOf(false) }
                    AnimatedVisibility(visible = !signedOutBannerDismissed) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f))
                                .clickable { signedOutBannerDismissed = true }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Sign in to enable AI features and cloud sync",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "dismiss",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                // Late-night care banner
                AnimatedVisibility(
                    visible = personalMode && pmLateNight && isLateNight && !isLateNightDismissed
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.75f))
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.Text(
                                text = run {
                                    val lateOverride = com.theblankstate.preamble.repository.PmMessageRepository.getMessage(context, "late_night", "default")
                                    lateOverride?.headline?.let { h -> if (lateOverride.subtitle != null) "$h ${lateOverride.subtitle}" else h }
                                        ?: "It's late \u2014 don't forget to rest. Your tasks will wait for you."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { isLateNightDismissed = true },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Search bar
                AnimatedVisibility(visible = isSearchActive) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Search tasks...") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge,
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChanged("") }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                    )
                }

                // Filter bar — shown only when filter button is tapped
                if (showFilterBar && onTagFilterChanged != null) {
                    val usedTags = remember(tasks) {
                        val tagIndexMap = PredefinedTags.tags.withIndex()
                            .associate { (i, t) -> t.name.lowercase() to i }
                        tasks.flatMap { task -> task.tagList }
                            .distinct()
                            .sortedBy { tagName -> tagIndexMap[tagName.lowercase()] ?: Int.MAX_VALUE }
                    }
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Search chip — first item
                        item {
                            FilterChip(
                                selected = isSearchActive,
                                onClick = {
                                    isSearchActive = !isSearchActive
                                    if (!isSearchActive) onSearchQueryChanged("")
                                },
                                label = { Text("Search", style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isSearchActive) Icons.Filled.Close else Icons.Filled.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                        }
                        // Eisenhower Matrix chip
                        item {
                            FilterChip(
                                selected = showEisenhowerView,
                                onClick = { showEisenhowerView = !showEisenhowerView },
                                label = { Text("Eisenhower Matrix", style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.GridView,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                        }
                        // Timeline View chip
                        item {
                            FilterChip(
                                selected = isTimelineEnabled,
                                onClick = { ThemePreferences.setTimelineUi(context, !isTimelineEnabled) },
                                label = { Text("Timeline View", style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = {
                                    Icon(
                                        if (isTimelineEnabled) Icons.Filled.ViewHeadline else Icons.Filled.ViewAgenda,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                        }
                        // All chip
                        item {
                            FilterChip(
                                selected = selectedTagFilter == null && !isSearchActive,
                                onClick = {
                                    onTagFilterChanged(null)
                                    isSearchActive = false
                                    onSearchQueryChanged("")
                                },
                                label = { Text("All", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                        // Tag chips
                        items(usedTags) { tagName: String ->
                            FilterChip(
                                selected = selectedTagFilter == tagName,
                                onClick = {
                                    onTagFilterChanged(if (selectedTagFilter == tagName) null else tagName)
                                    isSearchActive = false
                                    onSearchQueryChanged("")
                                },
                                label = { Text(tagName, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(
                                                PredefinedTags.colorForTag(tagName),
                                                shape = CircleShape
                                            )
                                    )
                                }
                            )
                        }
                    }
                }

            if (isSearchActive && searchQuery.isNotBlank()) {
                // Show search results
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            "${searchResults.size} result${if (searchResults.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    items(searchResults, key = { "search_${it.id}" }) { task ->
                        Column(modifier = Modifier.animateContentSize()) {
                            SwipeableTaskItem(
                                task = task,
                                onToggle = { handleToggleTask(task) },
                                onDelete = { taskToDelete = task },
                                onEdit = if (onEditTask != null) {
                                    { taskToEdit = task }
                                } else null,
                                onDetail = { taskToShowDetail = task },
                                onStartFocus = {
                                    focusTaskId = task.id
                                    focusTaskTitle = task.title
                                    showFocusSheet = true
                                },
                                onRetrySync = onRetrySync?.let { retry -> { retry(task) } },
                                isEditable = true,
                                subtaskCount = subtaskCounts[task.id],
                                isExpanded = expandedTasks.contains(task.id),
                                onToggleExpand = onToggleTaskExpanded?.let { { it(task.id) } },
                                habitStreakData = habitStreaks[task.recurrenceParentId ?: task.id]
                            )
                            // Show subtasks when expanded
                            if (expandedTasks.contains(task.id) && subtasksProvider != null) {
                                val subtasks by subtasksProvider(task.id).collectAsState(initial = emptyList())
                                SubtaskList(
                                    subtasks = subtasks,
                                    onToggleSubtask = { subtask -> onToggleSubtask?.invoke(subtask.id, !subtask.isCompleted) },
                                    onAddSubtask = { title -> onAddSubtask?.invoke(task.id, title) },
                                    onDeleteSubtask = { subtask -> onDeleteSubtask?.invoke(subtask.id) }
                                )
                            }
                        }
                    }
                }
            } else if (showEisenhowerView) {
                EisenhowerGrid(
                    tasks = tasks,
                    onToggleTask = handleToggleTask,
                    onUpdatePriority = { task, priority ->
                        onUpdateTaskPriority?.invoke(task, priority)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    onSyncGoogle?.invoke()
                },
                modifier = Modifier
                    .fillMaxSize()
            ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Home Incoming_Section as the first item, above the existing date groups
                // (Requirement 19.1). It renders nothing when there are no incoming tasks (19.8).
                // Accept/Decline reuse the ViewModel's optimistic <200 ms update, pending-only
                // transition guard, and exact-state revert on failure/timeout (19.3-19.7). After
                // accept the task's own status becomes "accepted" so it drops out of the incoming
                // list and falls through to the normal Home list (19.4); after decline it is
                // filtered out entirely (19.5).
                if (incomingTasks.isNotEmpty()) {
                    item(key = "incoming_section") {
                        IncomingSection(
                            incoming = incomingTasks,
                            onAccept = { workspaceViewModel.acceptAssignment(it) },
                            onDecline = { workspaceViewModel.declineAssignment(it) }
                        )
                    }
                }

                // Today's tasks with timeline view
                if (tasks.isNotEmpty()) {
                    item(key = "progress_bar") {
                        val baseCompleted = remember(tasks) { tasks.count { it.isCompleted } }
                        val baseTotal = tasks.size
                        
                        val completed = baseCompleted
                        val total = baseTotal
                        
                        val progress = if (total > 0) completed.toFloat() / total else 0f
                        val primaryColor = MaterialTheme.colorScheme.primary

                        // Smart progress label
                        val progressLabel = if (personalMode && pmSmartProgress && total > 0) {
                            val progressCondition = when {
                                completed == 0   -> "progress_0"
                                progress < 0.25f -> "progress_25"
                                progress < 0.50f -> "progress_50"
                                progress < 0.75f -> "progress_75"
                                progress < 1f    -> "progress_100"
                                else             -> "progress_done"
                            }
                            val pmOverride = com.theblankstate.preamble.repository.PmMessageRepository.getMessage(context, "smart_progress", progressCondition)
                            pmOverride?.headline ?: when {
                                completed == 0              -> "A fresh start awaits"
                                progress < 0.25f            -> "You're warming up"
                                progress < 0.50f            -> "Gaining momentum!"
                                progress < 0.75f            -> "Past the halfway mark"
                                progress < 1f               -> "Almost there, keep going!"
                                else                        -> "All done! You're amazing"
                            }
                        } else "Today's Progress"

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    progressLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "$completed / $total",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = primaryColor
                                )
                            }

                            // Streak at-risk warning
                            if (personalMode && pmStreakWarn && streak > 0 && completed == 0 && total > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.LocalFireDepartment,
                                        contentDescription = null,
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = run {
                                            val streakOverride = com.theblankstate.preamble.repository.PmMessageRepository.getMessage(context, "streak_warn", "default")
                                            streakOverride?.headline?.replace("{streak}", streak.toString())
                                                ?: "$streak-day streak at risk \u2014 complete a task to keep it"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Progress bar with sparkle sheen when at 100%
                            val sparkleVisible = personalMode && pmSparkle && progress >= 1f
                            val sparkleAlpha by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = if (sparkleVisible) 1f else 0f,
                                animationSpec = androidx.compose.animation.core.tween(600),
                                label = "sparkle"
                            )
                            
                            androidx.compose.runtime.LaunchedEffect(sparkleVisible) {
                                if (sparkleVisible) {
                                    kotlinx.coroutines.delay(200) // Wait for bar to fill
                                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                }
                            }
                            // Pulse the sheen when visible
                            val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "shine")
                            val shinePulse by infiniteTransition.animateFloat(
                                initialValue = 0.18f,
                                targetValue = 0.42f,
                                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                                    animation = androidx.compose.animation.core.tween(900),
                                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                                ),
                                label = "shinePulse"
                            )
                            Box {
                                WaveProgressBar(
                                    progress = progress,
                                    modifier = Modifier.fillMaxWidth().height(24.dp),
                                    activeColor = primaryColor,
                                    inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                )
                                if (sparkleAlpha > 0f) {
                                    // Full-width vertical sheen (top-bright → bottom-transparent)
                                    // covers every segment, no border artifact
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(24.dp)
                                            .background(
                                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                                    listOf(
                                                        androidx.compose.ui.graphics.Color.White.copy(alpha = shinePulse * sparkleAlpha),
                                                        androidx.compose.ui.graphics.Color.White.copy(alpha = (shinePulse * 0.3f) * sparkleAlpha),
                                                        androidx.compose.ui.graphics.Color.Transparent
                                                    )
                                                )
                                            )
                                    )
                                }
                            }

                            // Last-task amplification
                            if (personalMode && pmLastTask && total > 0 && (total - completed) == 1) {
                                val lastTaskOverride = com.theblankstate.preamble.repository.PmMessageRepository.getMessage(context, "last_task", "default")
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = lastTaskOverride?.headline ?: "One task away from a perfect day",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = primaryColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Admin broadcast tasks — below progress bar, above regular tasks
                    items(
                        items = adminTasks,
                        key = { "admin_${it.id}" }
                    ) { adminTask ->
                        AdminTaskCard(
                            task = adminTask,
                            onDismiss = { onDismissAdminTask?.invoke(adminTask.id) },
                            onAction = { onAdminTaskAction?.invoke(adminTask.id) },
                            onClick = {
                                if (!adminTask.directRedirect) {
                                    adminTaskToShow = adminTask
                                }
                            }
                        )
                    }

                    if (isTimelineEnabled) {
                        item(key = "timeline_today") {
                            val bgColor = if (isLightMode) {
                                if (colorfulCards) cardColors[0] else MaterialTheme.colorScheme.surfaceVariant
                            } else Color.Transparent

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                                    .padding(vertical = 4.dp)
                            ) {
                                // Timeline Left Column
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(40.dp)
                                        .padding(end = 8.dp, top = 24.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .fillMaxHeight()
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                    )
                                }

                                // Right Column (Card)
                                Card(
                                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                                    colors = CardDefaults.cardColors(containerColor = bgColor)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val todayDateStr = remember {
                                            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                                        }
                                        RichDateBadge(
                                            dateStr = todayDateStr,
                                            isCompact = true,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )

                                        // Today's tasks: show all inside card with key{} for smart recomposition skipping
                                        tasks.forEach { task ->
                                            key(task.id) {
                                                Column(modifier = Modifier.animateContentSize()) {
                                                    SwipeableTaskItem(
                                                        task = task,
                                                        onToggle = { handleToggleTask(task) },
                                                        onDelete = { taskToDelete = task },
                                                        onEdit = if (onEditTask != null) {{ taskToEdit = task }} else null,
                                                        onDetail = { taskToShowDetail = task },
                                                        onStartFocus = {
                                                            focusTaskId = task.id
                                                            focusTaskTitle = task.title
                                                            showFocusSheet = true
                                                        },
                                                        onRetrySync = onRetrySync?.let { retry -> { retry(task) } },
                                                        isEditable = true,
                                                        subtaskCount = subtaskCounts[task.id],
                                                        isExpanded = expandedTasks.contains(task.id),
                                                        onToggleExpand = onToggleTaskExpanded?.let { { it(task.id) } },
                                                        habitStreakData = habitStreaks[task.recurrenceParentId ?: task.id]
                                                    )
                                                    // Show subtasks when expanded
                                                    if (expandedTasks.contains(task.id) && subtasksProvider != null) {
                                                        val subtasks by subtasksProvider(task.id).collectAsState(initial = emptyList())
                                                        SubtaskList(
                                                            subtasks = subtasks,
                                                            onToggleSubtask = { subtask -> onToggleSubtask?.invoke(subtask.id, !subtask.isCompleted) },
                                                            onAddSubtask = { title -> onAddSubtask?.invoke(task.id, title) },
                                                            onDeleteSubtask = { subtask -> onDeleteSubtask?.invoke(subtask.id) }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        items(
                            items = tasks,
                            key = { "today_${it.id}" }
                        ) { task ->
                            Column(modifier = Modifier.animateContentSize()) {
                                SwipeableTaskItem(
                                    task = task,
                                    onToggle = { handleToggleTask(task) },
                                    onDelete = { taskToDelete = task },
                                    onEdit = if (onEditTask != null) {{ taskToEdit = task }} else null,
                                    onDetail = { taskToShowDetail = task },
                                    onStartFocus = {
                                        focusTaskId = task.id
                                        focusTaskTitle = task.title
                                        showFocusSheet = true
                                    },
                                    onRetrySync = onRetrySync?.let { retry -> { retry(task) } },
                                    isEditable = true,
                                    modifier = Modifier.animateItem(),
                                    subtaskCount = subtaskCounts[task.id],
                                    isExpanded = expandedTasks.contains(task.id),
                                    onToggleExpand = onToggleTaskExpanded?.let { { it(task.id) } },
                                    habitStreakData = habitStreaks[task.recurrenceParentId ?: task.id]
                                )
                                // Show subtasks when expanded
                                if (expandedTasks.contains(task.id) && subtasksProvider != null) {
                                    val subtasks by subtasksProvider(task.id).collectAsState(initial = emptyList())
                                    SubtaskList(
                                        subtasks = subtasks,
                                        onToggleSubtask = { subtask -> onToggleSubtask?.invoke(subtask.id, !subtask.isCompleted) },
                                        onAddSubtask = { title -> onAddSubtask?.invoke(task.id, title) },
                                        onDeleteSubtask = { subtask -> onDeleteSubtask?.invoke(subtask.id) }
                                    )
                                }
                            }
                        }
                    }
                } else if (!isInitialLoad) {
                    item {
                        val userName = remember {
                            com.theblankstate.preamble.data.UserProfileStore.load(context).name?.trim()?.takeIf { it.isNotBlank() }
                        }
                        val emptyHeadline: String
                        val emptySub: String
                        if (personalMode && pmSmartEmpty) {
                            val emptyCondition = when {
                                currentHour in 5..11  -> "morning"
                                currentHour in 12..16 -> "afternoon"
                                else                  -> "evening"
                            }
                            val emptyOverride = com.theblankstate.preamble.repository.PmMessageRepository.getMessage(context, "empty_state", emptyCondition)
                            if (emptyOverride != null) {
                                emptyHeadline = emptyOverride.headline
                                emptySub = emptyOverride.subtitle ?: ""
                            } else {
                                val nameTag = userName?.let { ", $it" } ?: ""
                                when {
                                    currentHour in 5..11 -> {
                                        emptyHeadline = "Your day is a blank canvas$nameTag"
                                        emptySub = "What will you create today?"
                                    }
                                    currentHour in 12..16 -> {
                                        emptyHeadline = "Nothing pending$nameTag"
                                        emptySub = "Time to think ahead and plan tomorrow"
                                    }
                                    else -> {
                                        emptyHeadline = "All clear$nameTag"
                                        emptySub = "Enjoy your evening \u2014 you've earned it"
                                    }
                                }
                            }
                        } else {
                            emptyHeadline = userName?.let { "No tasks for today, $it" } ?: "No tasks for today"
                            emptySub = "Tap + to add your first task"
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp)
                        ) {
                            Text(
                                text = emptyHeadline,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = emptySub,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Past tasks — always shown, read-only
                var colorIndex = 1 // Start at 1 because Today might take 0, though we'll use colorIndex for all
                pastTasks.forEach { (date, tasksForDate) ->
                    if (tasksForDate.isNotEmpty()) {
                        if (isTimelineEnabled) {
                            item(key = "timeline_$date") {
                                val bgColor = if (isLightMode) {
                                    if (colorfulCards) cardColors[colorIndex % cardColors.size] else MaterialTheme.colorScheme.surfaceVariant
                                } else Color.Transparent
                                colorIndex++

                                // Smart truncation: show first 3 tasks, expandable for rest
                                val collapsedLimit = 3
                                var isExpanded by remember { mutableStateOf(false) }
                                val visibleTasks = if (isExpanded || tasksForDate.size <= collapsedLimit) {
                                    tasksForDate
                                } else {
                                    tasksForDate.take(collapsedLimit)
                                }
                                val hiddenCount = tasksForDate.size - collapsedLimit

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min)
                                        .padding(vertical = 4.dp)
                                ) {
                                    // Timeline Left Column
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .width(40.dp)
                                            .padding(end = 8.dp, top = 24.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .width(2.dp)
                                                .fillMaxHeight()
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                        )
                                    }

                                    // Right Column (Card)
                                    Card(
                                        modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                                        colors = CardDefaults.cardColors(containerColor = bgColor)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            RichDateBadge(
                                                dateStr = date,
                                                isCompact = true,
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )

                                            // Render only visible tasks with key for skip optimization
                                            visibleTasks.forEach { task ->
                                                key(task.id) {
                                                    TaskItem(
                                                        task = task,
                                                        onToggle = { },
                                                        onDelete = { taskToDelete = task },
                                                        onDetail = { taskToShowDetail = task },
                                                        isEditable = false
                                                    )
                                                }
                                            }

                                            // "Show more" button when collapsed
                                            if (!isExpanded && hiddenCount > 0) {
                                                Text(
                                                    text = "+ $hiddenCount more tasks",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(top = 4.dp)
                                                        .clickable { isExpanded = true },
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            item(key = "header_$date") {
                                RichDateBadge(
                                    dateStr = date,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                                )
                            }

                            items(
                                items = tasksForDate,
                                key = { "past_${date}_${it.id}" }
                            ) { task ->
                                TaskItem(
                                    task = task,
                                    onToggle = { },
                                    onDelete = { taskToDelete = task },
                                    onDetail = { taskToShowDetail = task },
                                    isEditable = false,
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
            } // PullToRefreshBox
            } // else (not searching)
            } // Column
            } // CompositionLocalProvider
        }

        // Voice wave overlay at the bottom — sits in outer Box, on top of Scaffold
        AnimatedVisibility(
            visible = isVoiceListening,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 100.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (voiceText.isNotBlank() && voiceText != "Listening...") {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = voiceText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            maxLines = 1
                        )
                    }
                }
                BottomWaveAnimation(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        com.theblankstate.preamble.ui.components.CelebrationOverlay(
            event = activeCelebration,
            onDone = { activeCelebration = null }
        )
    }

    // ── Track A: full-screen Plan-My-Day overlay (Req 18) ──
    // State-driven overlay following the same pattern as the Friends/Circles full-screen
    // overlays (AnimatedVisibility + BackHandler). Opens immediately while Loading and renders
    // every DayPlanState in place. Closing it resets the ViewModel to Idle. The alive FAB that
    // sets `showPlanningScreen = true` is wired by task 13.2.
    AnimatedVisibility(
        visible = showPlanningScreen,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(initialOffsetY = { it }),
        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(targetOffsetY = { it }),
    ) {
        androidx.activity.compose.BackHandler {
            showPlanningScreen = false
            dayPlanViewModel.reset()
        }
        val closePlanning: () -> Unit = {
            showPlanningScreen = false
            dayPlanViewModel.reset()
        }
        val aiChatScreenVm: com.theblankstate.preamble.ai.AiChatScreenViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
            factory = com.theblankstate.preamble.ai.AiChatScreenViewModel.Factory(
                androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application,
                planTaskViewModel
            )
        )

        com.theblankstate.preamble.ui.screens.PlanningScreen(
            state = dayPlanState,
            onRequestPlan = { customDayEndMinute, userContextPrompt, allowRescheduleFixed -> dayPlanViewModel.requestPlan(customDayEndMinute, userContextPrompt, allowRescheduleFixed) },
            onAccept = { dayPlanViewModel.accept() },
            onDiscard = {
                dayPlanViewModel.discard()
            },
            onRetry = { dayPlanViewModel.retry() },
            onSubmitAdjustment = { text, allowRescheduleFixed -> dayPlanViewModel.submitAdjustment(text, allowRescheduleFixed) },
            onClose = closePlanning,
            aiChatScreenViewModel = aiChatScreenVm,
            modifier = Modifier.fillMaxSize(),
        )
    }

    if (showAddSheet) {
        AddTaskSheet(
            onDismiss = { showAddSheet = false },
            onAddTask = { title, date, deadlineTime, syncToGoogle, syncToCalendar, priority, description, tags, subtasks, isHabit, isEvent, eventIcon, eventColor, assignedToFriends ->
                onAddTask(title, date, deadlineTime, syncToGoogle, syncToCalendar, priority, description, tags, subtasks, isHabit, isEvent, eventIcon, eventColor, assignedToFriends)
                showAddSheet = false
            },
            onAddRecurringTask = if (onAddRecurringTask != null) { { title, date, deadlineTime, priority, description, recurrenceType, recurrenceInterval, recurrenceDays, recurrenceEndDate, syncToCalendar, tags, subtasks, isHabit, isEvent, eventIcon, eventColor ->
                onAddRecurringTask(title, date, deadlineTime, priority, description, recurrenceType, recurrenceInterval, recurrenceDays, recurrenceEndDate, syncToCalendar, tags, subtasks, isHabit, isEvent, eventIcon, eventColor)
                showAddSheet = false
            } } else null,
            aiChatViewModel = aiChatViewModel,
            onAddTaskPendingParse = if (onAddTaskPendingParse != null) { { rawText, date, deadlineTime, syncToGoogle, syncToCalendar, priority, description, tags, subtasks, isHabit, isEvent, eventIcon, eventColor, recurrenceType, recurrenceInterval, recurrenceDays, recurrenceEndDate, userOverrides, assignedToFriends ->
                onAddTaskPendingParse(rawText, date, deadlineTime, syncToGoogle, syncToCalendar, priority, description, tags, subtasks, isHabit, isEvent, eventIcon, eventColor, recurrenceType, recurrenceInterval, recurrenceDays, recurrenceEndDate, userOverrides, assignedToFriends)
                showAddSheet = false
            } } else null,
            friends = friends,
            circles = circles
        )
    }

    if (taskToDelete != null) {
        val isRecurring = taskToDelete?.let {
            (it.isRecurrenceTemplate || it.isRecurrenceInstance) && it.recurrenceType != "rollover"
        } ?: false

        ModalBottomSheet(
            onDismissRequest = { taskToDelete = null },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (isRecurring) "Delete Recurring Task?" else "Delete Task?",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "'${taskToDelete?.title}'",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp),
                    textAlign = TextAlign.Center
                )

                if (isRecurring && onDeleteAllRecurrences != null) {
                    // Recurring task: show options
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                taskToDelete?.let { onDeleteTask(it) }
                                taskToDelete = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Delete Only This Instance")
                        }
                        Button(
                            onClick = {
                                taskToDelete?.let { onDeleteAllRecurrences(it) }
                                taskToDelete = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Delete All Recurrences")
                        }
                        OutlinedButton(
                            onClick = { taskToDelete = null },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel")
                        }
                    }
                } else {
                    // Non-recurring task: simple delete/cancel
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = { taskToDelete = null },
                            modifier = Modifier.weight(1f),
                            shape = CircleShape
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                taskToDelete?.let { onDeleteTask(it) }
                                taskToDelete = null
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            shape = CircleShape
                        ) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }

    // Easter egg dialog
    if (showEasterEgg) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showEasterEgg = false },
            title = { Text("You found it", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Built in the quiet hours, for the ones who keep going.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showEasterEgg = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Active alarms bottom sheet
    if (showAlarmSheet) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val nextAlarm = alarmManager.nextAlarmClock

        // AlarmEntry: one row per upcoming reminder/alarm
        data class AlarmEntry(
            val task: Task,
            val triggerMs: Long,
            val isLegacy: Boolean,   // true = single customAlarmTimeMs / deadlineTime alarm
            val description: String  // e.g. "10 min before" or "5:30 PM"
        )

        val allAlarmEntries = remember(tasks, pastTasks) {
            val allTasks = mutableListOf<Task>()
            allTasks.addAll(tasks)
            pastTasks.values.forEach { allTasks.addAll(it) }
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val now = System.currentTimeMillis()
            val entries = mutableListOf<AlarmEntry>()

            for (task in allTasks) {
                if (task.isCompleted || task.isAlarmPaused) continue

                if (task.hasReminders) {
                    // New multi-reminder system
                    val reminders = task.localReminders
                    for ((index, triggerMs) in task.computeReminderTriggerMs()) {
                        if (triggerMs > now) {
                            val desc = reminders.getOrNull(index)?.displayText()
                                ?: timeFmt.format(Date(triggerMs))
                            entries.add(AlarmEntry(task, triggerMs, isLegacy = false, desc))
                        }
                    }
                    // Also show snoozed alarm if present (set via alarm snooze button)
                    val snoozeMs = task.customAlarmTimeMs
                    if (snoozeMs != null && snoozeMs > now) {
                        entries.add(AlarmEntry(task, snoozeMs, isLegacy = true, "Snoozed → ${timeFmt.format(Date(snoozeMs))}"))
                    }
                } else {
                    // Legacy single-alarm system
                    val triggerMs = task.customAlarmTimeMs ?: run {
                        if (task.deadlineTime == null) null
                        else try { sdf.parse("${task.createdDate} ${task.deadlineTime}")?.time }
                             catch (_: Exception) { null }
                    } ?: continue
                    if (triggerMs > now) {
                        entries.add(AlarmEntry(
                            task, triggerMs, isLegacy = true,
                            timeFmt.format(Date(triggerMs))
                        ))
                    }
                }
            }
            entries.sortedBy { it.triggerMs }
        }

        // Kept for legacy edit controls
        val allTasksWithAlarms = allAlarmEntries.map { it.task }.distinctBy { it.id }

        var taskForTimePicker by remember { mutableStateOf<Task?>(null) }
        var taskInitialTimeForPicker by remember { mutableStateOf<Long>(0L) }
        val contextCurrent = androidx.compose.ui.platform.LocalContext.current

        androidx.compose.runtime.LaunchedEffect(taskForTimePicker) {
            if (taskForTimePicker != null) {
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = taskInitialTimeForPicker }
                android.app.TimePickerDialog(
                    contextCurrent,
                    { _, hour, minute ->
                        val newCal = java.util.Calendar.getInstance().apply {
                            timeInMillis = taskInitialTimeForPicker
                            set(java.util.Calendar.HOUR_OF_DAY, hour)
                            set(java.util.Calendar.MINUTE, minute)
                            set(java.util.Calendar.SECOND, 0)
                        }
                        onUpdateAlarmStatus(taskForTimePicker!!, newCal.timeInMillis, taskForTimePicker!!.isAlarmPaused)
                        taskForTimePicker = null
                    },
                    cal.get(java.util.Calendar.HOUR_OF_DAY),
                    cal.get(java.util.Calendar.MINUTE),
                    false
                ).apply {
                    setOnDismissListener { taskForTimePicker = null }
                    show()
                }
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showAlarmSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    "Scheduled Alarms",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (nextAlarm != null) {
                    val nextTime = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                        .format(Date(nextAlarm.triggerTime))
                    Text(
                        "Next system alarm: $nextTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                if (allAlarmEntries.isEmpty()) {
                    Text(
                        "No upcoming reminders",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val dateFmt = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
                    allAlarmEntries.forEach { entry ->
                        val task = entry.task
                        val triggerMs = entry.triggerMs
                        val timeStr = dateFmt.format(Date(triggerMs))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Alarm,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(
                                    task.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                                if (entry.isLegacy) {
                                    // Legacy alarm: show time with edit/nudge controls
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                            .clickable {
                                                taskInitialTimeForPicker = triggerMs
                                                taskForTimePicker = task
                                            }
                                            .padding(vertical = 2.dp)
                                    ) {
                                        Text(
                                            timeStr,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (task.customAlarmTimeMs != null) {
                                            Text(
                                                " • Edited",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(start = 4.dp)
                                            )
                                        }
                                        Icon(
                                            Icons.Filled.Edit,
                                            contentDescription = "Edit Time",
                                            modifier = Modifier.size(16.dp).padding(start = 4.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else {
                                    // New reminder system: show description + time
                                    Text(
                                        "${entry.description}  •  $timeStr",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (entry.isLegacy) {
                                // Nudge and pause controls for legacy alarms
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { onUpdateAlarmStatus(task, triggerMs - (10 * 60 * 1000L), task.isAlarmPaused) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Text("-10m", style = MaterialTheme.typography.labelSmall)
                                    }
                                    IconButton(
                                        onClick = { onUpdateAlarmStatus(task, triggerMs + (10 * 60 * 1000L), task.isAlarmPaused) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Text("+10m", style = MaterialTheme.typography.labelSmall)
                                    }
                                    androidx.compose.material3.Switch(
                                        checked = !task.isAlarmPaused,
                                        onCheckedChange = { isResumed -> onUpdateAlarmStatus(task, task.customAlarmTimeMs, !isResumed) },
                                        modifier = Modifier.scale(0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Read-only task detail bottom sheet
    taskToShowDetail?.let { snapshotTask ->
        // Derive live task from reactive tasks list so UI updates in real-time (e.g. reminders)
        val liveTask = tasks.find { it.id == snapshotTask.id }
            ?: pastTasks.values.flatten().find { it.id == snapshotTask.id }
            ?: snapshotTask
        val task = liveTask
        val subtasks = subtasksProvider?.invoke(task.id)?.collectAsState(initial = emptyList())?.value ?: emptyList()
        val todayStr = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) }
        val isPast = task.createdDate < todayStr
        // Hide "Copy to Today" if the task is recurring and has recurrence for today
        val hasRecurrenceForToday = isPast && task.recurrenceType != null && task.recurrenceType != "rollover"
        TaskDetailBottomSheet(
            task = task,
            onDismiss = { taskToShowDetail = null },
            onEdit = if (!isPast && onEditTask != null) {{
                taskToEdit = task
                taskToShowDetail = null
            }} else null,
            onDelete = {
                taskToDelete = task
                taskToShowDetail = null
            },
            onStartFocus = if (task.isInfoOnly) null else {{
                focusTaskId = task.id
                focusTaskTitle = task.title
                taskToShowDetail = null
                showFocusSheet = true
            }},
            subtasks = subtasks,
            onAddSubtask = if (!isPast) {{ title -> onAddSubtask?.invoke(task.id, title) }} else null,
            onToggleSubtask = if (!isPast) {{ subtaskId, isCompleted -> onToggleSubtask?.invoke(subtaskId, isCompleted) }} else null,
            onDeleteSubtask = if (!isPast) {{ subtaskId -> onDeleteSubtask?.invoke(subtaskId) }} else null,
            onCompleteAllSubtasks = if (!isPast) {{ onCompleteAllSubtasks?.invoke(task.id) }} else null,
            onSnooze = if (onSnoozeTask != null && task.recurrenceType == "rollover") {
                { duration -> onSnoozeTask(task.id, duration) }
            } else null,
            onUnsnooze = if (onUnsnoozeTask != null && task.snoozedUntil != null && task.snoozedUntil > System.currentTimeMillis()) {
                { onUnsnoozeTask(task.id) }
            } else null,
            onCopyToToday = if (isPast && !hasRecurrenceForToday && onCopyTaskToToday != null) {{
                onCopyTaskToToday(task)
            }} else null,
            onAddReminder = if (!isPast && onAddReminder != null) {{ reminder ->
                onAddReminder(task, reminder)
            }} else null,
            onRemoveReminder = if (!isPast && onRemoveReminder != null) {{ index ->
                onRemoveReminder(task, index)
            }} else null,
            onToggleRollover = if (!isPast && !task.isCompleted && onEditTask != null
                && task.source == "local" && !task.isRecurrenceTemplate
                && task.recurrenceParentId == null
                && (task.recurrenceType == null || task.recurrenceType == "rollover")) {
                {
                    val newType = if (task.recurrenceType == "rollover") null else "rollover"
                    onEditTask(task, task.title, task.createdDate, task.deadlineTime, task.priority, task.description, task.tags, newType, task.recurrenceInterval ?: 1, task.recurrenceDays, task.recurrenceEndDate, task.isEvent, task.eventIcon, task.eventColor)
                }
            } else null,
            isPastTask = isPast,
            habitStreakData = habitStreaks[task.recurrenceParentId ?: task.id],
            currentUserUid = FirebaseAuth.getInstance().currentUser?.uid,
            onReact = { emoji -> workspaceViewModel.updateMyReaction(task, emoji) },
            onNudge = { targetUid -> workspaceViewModel.nudge(task, targetUid) },
            canNudge = { targetUid ->
                nudgedTargets // read so the control recomposes after a nudge
                workspaceViewModel.canNudge(task, targetUid)
            },
            nudgeCooldownRemaining = { targetUid -> workspaceViewModel.nudgeCooldownRemaining(task, targetUid) }
        )
    }

    if (taskToEdit != null && onEditTask != null) {
        val subtasks = subtasksProvider?.invoke(taskToEdit!!.id)?.collectAsState(initial = emptyList())?.value ?: emptyList()
        
        TaskDetailSheet(
            task = taskToEdit!!,
            onDismiss = { taskToEdit = null },
            onUpdateTask = { newTitle, newDate, newDeadlineTime, newPriority, newDescription, newTags, newRecurrenceType, newRecurrenceInterval, newRecurrenceDays, newRecurrenceEndDate, newIsEvent, newEventIcon, newEventColor ->
                onEditTask(taskToEdit!!, newTitle, newDate, newDeadlineTime, newPriority, newDescription, newTags, newRecurrenceType, newRecurrenceInterval, newRecurrenceDays, newRecurrenceEndDate, newIsEvent, newEventIcon, newEventColor)
                taskToEdit = null
            },
            onDelete = {
                taskToDelete = taskToEdit
                taskToEdit = null
            },
            onStartFocus = {
                focusTaskId = taskToEdit!!.id
                focusTaskTitle = taskToEdit!!.title
                taskToEdit = null
                showFocusSheet = true
            },
            subtasks = subtasks,
            onAddSubtask = { title -> onAddSubtask?.invoke(taskToEdit!!.id, title) },
            onToggleSubtask = { subtaskId, isCompleted -> onToggleSubtask?.invoke(subtaskId, isCompleted) },
            onDeleteSubtask = { subtaskId -> onDeleteSubtask?.invoke(subtaskId) },
            onCompleteAllSubtasks = { onCompleteAllSubtasks?.invoke(taskToEdit!!.id) },
            onSnooze = if (onSnoozeTask != null && taskToEdit?.recurrenceType == "rollover") {
                { duration -> onSnoozeTask(taskToEdit!!.id, duration) }
            } else null,
            onUnsnooze = if (onUnsnoozeTask != null && taskToEdit?.snoozedUntil != null && taskToEdit!!.snoozedUntil!! > System.currentTimeMillis()) {
                { onUnsnoozeTask(taskToEdit!!.id) }
            } else null,
            onToggleHabit = onToggleHabit,
            currentUserUid = FirebaseAuth.getInstance().currentUser?.uid,
            onRemoveCollabMember = { memberUid ->
                taskToEdit?.let { workspaceViewModel.removeMember(it, memberUid) }
                taskToEdit = null
            },
            onTransferCollabOwnership = { memberUid ->
                taskToEdit?.let { workspaceViewModel.transferOwnership(it, memberUid) }
                taskToEdit = null
            },
            onLeaveCollabTask = {
                taskToEdit?.let { workspaceViewModel.leaveTask(it) }
                taskToEdit = null
            },
            onReact = { emoji -> taskToEdit?.let { workspaceViewModel.updateMyReaction(it, emoji) } },
            onNudge = { targetUid -> taskToEdit?.let { workspaceViewModel.nudge(it, targetUid) } },
            canNudge = { targetUid ->
                nudgedTargets // read so the control recomposes after a nudge
                taskToEdit?.let { workspaceViewModel.canNudge(it, targetUid) } ?: true
            },
            nudgeCooldownRemaining = { targetUid ->
                taskToEdit?.let { workspaceViewModel.nudgeCooldownRemaining(it, targetUid) } ?: 0L
            }
        )
    }

    if (showFocusSheet) {
        FocusTimerSheet(
            onDismiss = { showFocusSheet = false },
            taskId = focusTaskId,
            taskTitle = focusTaskTitle
        )
    }

    // ── Track B: premium upsell shown when the gate returns locked (Req 11.1, 11.3) ──
    upsellFeature?.let { feature ->
        com.theblankstate.preamble.ui.components.PremiumUpsellSheet(
            feature = feature,
            onDismissRequest = { upsellFeature = null },
        )
    }

    // Admin task detail sheet
    adminTaskToShow?.let { adminTask ->
        AdminTaskDetailSheet(
            task = adminTask,
            onDismiss = { adminTaskToShow = null },
            onAction = {
                onAdminTaskAction?.invoke(adminTask.id)
                adminTaskToShow = null
            }
        )
    }
}

@Composable
fun BottomWaveAnimation(modifier: Modifier = Modifier, color: Color) {
    val transition = rememberInfiniteTransition(label = "bottomWave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val midY = h / 2f
        val barCount = 40
        val barWidth = w / (barCount * 1.5f)

        for (i in 0 until barCount) {
            val x = (i.toFloat() / barCount) * w
            val amplitude = midY * 0.9f
            val barHeight = amplitude * kotlin.math.abs(sin(phase + i * 0.4f)) + 3.dp.toPx()

            drawLine(
                color = color.copy(alpha = 0.3f + 0.7f * (barHeight / (amplitude + 3.dp.toPx()))),
                start = Offset(x + barWidth / 2, midY - barHeight / 2),
                end = Offset(x + barWidth / 2, midY + barHeight / 2),
                strokeWidth = barWidth * 0.5f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun WaveProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    activeColor: Color,
    inactiveColor: Color
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val segmentCount = 20
        val gapRatio = 0.3f
        val totalGaps = (segmentCount - 1) * gapRatio
        val segW = w / (segmentCount + totalGaps)
        val gapW = segW * gapRatio
        val filledSegments = (segmentCount * progress).toInt()
        val cornerR = h / 2f

        for (i in 0 until segmentCount) {
            val x = i * (segW + gapW)
            val isActive = i < filledSegments

            drawRoundRect(
                color = if (isActive) activeColor else inactiveColor,
                topLeft = Offset(x, 0f),
                size = androidx.compose.ui.geometry.Size(segW, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerR, cornerR)
            )
        }
    }
}


/**
 * Home Incoming_Section (Requirement 19).
 *
 * Renders the signed-in user's own incoming (pending) collaborative tasks at the top of the Home
 * list. The section — including its header — is shown only when [incoming] is non-empty
 * (Requirement 19.8); when there are no incoming tasks it emits nothing so the Home list looks
 * exactly as it did before. Each row shows the task title plus an inline Accept and Decline control
 * pair (Requirement 19.2).
 *
 * This composable is intentionally presentation-only: callers pass the already-narrowed incoming
 * list (e.g. `IncomingTasks.incoming(workspaceViewModel.incomingAssignments)`) and the
 * accept/decline callbacks. Wiring into the Home `LazyColumn` and the ViewModel happens separately
 * (task 16.2).
 */
@Composable
private fun IncomingSection(
    incoming: List<Task>,
    onAccept: (Task) -> Unit,
    onDecline: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    // Header (and the whole section) is shown only when there is at least one incoming task (19.8).
    if (incoming.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Incoming",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
        )

        incoming.forEach { task ->
            key(task.id) {
                IncomingTaskCard(
                    task = task,
                    onAccept = { onAccept(task) },
                    onDecline = { onDecline(task) }
                )
            }
        }
    }
}

/**
 * An Incoming_Task_Card (Requirement 27): a pending incoming Collaborative_Task assignment rendered
 * as a normal task card so the user can read its full details, paired with inline Accept / decline
 * controls.
 *
 * The card body reuses the normal [TaskItem] in a non-interactive presentation (`isEditable = false`,
 * no-op toggle/delete, no detail) so the incoming card and a normal task card render the SAME
 * metadata layout — title, deadline time formatted exactly as `TaskItem` formats it, tags, and the
 * priority circle — and therefore cannot drift (27.2). An "Assigned by" attribution is retained
 * from the previous minimal row.
 *
 * Controls (27.3, 27.4, 27.5): a wide filled Accept button (`Modifier.weight(1f)`) is measurably
 * wider than the compact cross (`X`) decline icon button. Both are Material 3 Expressive — alive
 * shape and a press-driven scale motion — consistent with the Social Hub. Accept/Decline call the
 * existing optimistic [WorkspaceViewModel.acceptAssignment] / declineAssignment wiring (27.6–27.8).
 */
@Composable
private fun IncomingTaskCard(
    task: Task,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Reuse the normal task card body so the incoming card and a normal card cannot drift.
            // Non-interactive: isEditable = false disables the completion toggle, suppresses the
            // overflow menu, and (with no onDetail) disables the row click — it reads as a normal
            // card without acting as one.
            TaskItem(
                task = task,
                onToggle = {},
                onDelete = {},
                isEditable = false
            )

            // Attribution preserved from the prior minimal Incoming_Section row.
            if (!task.assignedByName.isNullOrBlank()) {
                Text(
                    text = "Assigned by ${task.assignedByName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Wide Accept control — Material 3 Expressive: alive press-scale motion.
                val acceptInteraction = remember { MutableInteractionSource() }
                val acceptPressed by acceptInteraction.collectIsPressedAsState()
                val acceptScale by animateFloatAsState(
                    targetValue = if (acceptPressed) 0.96f else 1f,
                    animationSpec = spring(dampingRatio = 0.45f, stiffness = 380f),
                    label = "acceptScale"
                )
                Button(
                    onClick = onAccept,
                    interactionSource = acceptInteraction,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .scale(acceptScale)
                ) {
                    Text(
                        text = "Accept",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Compact cross (X) decline control — measurably narrower than Accept.
                val declineInteraction = remember { MutableInteractionSource() }
                val declinePressed by declineInteraction.collectIsPressedAsState()
                val declineScale by animateFloatAsState(
                    targetValue = if (declinePressed) 0.9f else 1f,
                    animationSpec = spring(dampingRatio = 0.45f, stiffness = 380f),
                    label = "declineScale"
                )
                FilledTonalIconButton(
                    onClick = onDecline,
                    interactionSource = declineInteraction,
                    shape = RoundedCornerShape(16.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .scale(declineScale)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Decline"
                    )
                }
            }
        }
    }
}

/**
 * Plan_My_Day_FAB — the alive Material 3 Expressive entry point for Track A (Req 19.1, 19.2).
 *
 * Replaces the old static top-bar `AutoAwesome` icon (Req 19.3). It feels "alive" via a
 * continuously morphing-and-rotating shape (a star ↔ rounded-hexagon [Morph] clipped through
 * the shared [MorphPolygonShape]) plus a gentle expressive scale pulse, echoing the lime hero
 * treatment used by [PlanningScreen] and the Social Hub. Tapping it runs the Track B gate via
 * the host's `onClick`.
 */
@Composable
private fun PlanMyDayFab(onClick: () -> Unit) {
    val lime = Color(0xFFD4FF70) // Vibrant Lime Green hero accent, consistent with PlanningScreen

    val morph = remember {
        Morph(
            start = RoundedPolygon.star(
                numVerticesPerRadius = 8,
                innerRadius = 0.72f,
                rounding = CornerRounding(0.22f),
            ),
            end = RoundedPolygon(
                numVertices = 6,
                rounding = CornerRounding(0.32f),
            ),
        )
    }

    val transition = rememberInfiniteTransition(label = "planFab")
    val morphProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "planFabMorph",
    )
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000),
            repeatMode = RepeatMode.Restart,
        ),
        label = "planFabRotation",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "planFabPulse",
    )

    Box(
        modifier = Modifier
            .size(56.dp)
            .scale(pulse)
            .clip(MorphPolygonShape(morph, morphProgress, rotation))
            .background(lime)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = "Plan my day",
            tint = Color.Black,
            modifier = Modifier.size(26.dp),
        )
    }
}

/**
 * Speed Dial FAB component that consolidates Focus Timer & Voice Input tools into a single
 * expanding FAB button following Material 3 Expressive guidelines.
 */
@Composable
private fun ToolsSpeedDialFab(
    focusState: FocusTimerState,
    isVoiceListening: Boolean,
    onOpenFocus: () -> Unit,
    onToggleVoice: () -> Unit,
    scaleFactor: Float = 1f
) {
    var isExpanded by remember { mutableStateOf(false) }

    val animProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "radial_speed_dial_progress"
    )

    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 135f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "cross_rotation"
    )

    Box(
        contentAlignment = Alignment.BottomEnd
    ) {
        if (animProgress > 0.01f) {
            // 1. Focus Timer Sub-FAB (Straight Left: X = -64.dp)
            val timerInteraction = remember { MutableInteractionSource() }
            Surface(
                onClick = {
                    isExpanded = false
                    onOpenFocus()
                },
                shape = CircleShape,
                color = if (focusState.isRunning)
                    MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.secondaryContainer,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .offset(x = (-64).dp * scaleFactor * animProgress, y = 0.dp)
                    .scale(animProgress)
                    .graphicsLayer(alpha = animProgress)
                    .size(44.dp * scaleFactor)
                    .expressivePressScale(timerInteraction)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (focusState.isRunning) {
                        val mins = focusState.remainingSeconds / 60
                        val secs = focusState.remainingSeconds % 60
                        Text(
                            String.format(Locale.US, "%02d:%02d", mins, secs),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onTertiary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Focus timer",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp * scaleFactor)
                        )
                    }
                }
            }

            // 2. Voice Input Sub-FAB (Up-Left Arc: X = -45.dp, Y = -45.dp)
            val voiceInteraction = remember { MutableInteractionSource() }
            Surface(
                onClick = {
                    isExpanded = false
                    onToggleVoice()
                },
                shape = CircleShape,
                color = if (isVoiceListening)
                    MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.secondaryContainer,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .offset(x = (-45).dp * scaleFactor * animProgress, y = (-45).dp * scaleFactor * animProgress)
                    .scale(animProgress)
                    .graphicsLayer(alpha = animProgress)
                    .size(44.dp * scaleFactor)
                    .expressivePressScale(voiceInteraction)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isVoiceListening) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (isVoiceListening) "Stop" else "Voice Input",
                        tint = if (isVoiceListening) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp * scaleFactor)
                    )
                }
            }
        }

        // Primary Speed Dial Master FAB (Toggles tools & morphs to Cross icon 'X')
        val mainInteraction = remember { MutableInteractionSource() }
        Surface(
            onClick = { isExpanded = !isExpanded },
            shape = CircleShape,
            color = if (isExpanded)
                MaterialTheme.colorScheme.errorContainer
            else if (focusState.isRunning || isVoiceListening)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .size(48.dp * scaleFactor)
                .expressivePressScale(mainInteraction)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.Close else Icons.Default.Widgets,
                    contentDescription = if (isExpanded) "Close tools" else "Tools menu",
                    tint = if (isExpanded)
                        MaterialTheme.colorScheme.onErrorContainer
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .size(22.dp * scaleFactor)
                        .graphicsLayer(rotationZ = rotationAngle)
                )
            }
        }
    }
}

@Composable
private fun Modifier.expressivePressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.92f
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "expressivePressScale"
    )
    return this.scale(scale)
}
