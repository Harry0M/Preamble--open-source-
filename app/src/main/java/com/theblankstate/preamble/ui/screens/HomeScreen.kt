package com.theblankstate.preamble.ui.screens

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewHeadline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import com.theblankstate.preamble.data.Task
import com.theblankstate.preamble.data.PredefinedTags
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
import com.theblankstate.preamble.ui.components.PomodoroSheet
import com.theblankstate.preamble.pomodoro.PomodoroTimerService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin

import android.widget.Toast
import android.app.Activity
import com.theblankstate.preamble.ai.AiChatViewModel
import android.app.AlarmManager
import android.content.Context
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.GridView
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
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    tasks: List<Task>,
    pastTasks: Map<String, List<Task>> = emptyMap(),
    streak: Int,
    onAddTask: (title: String, date: String?, deadlineTime: String?, syncToGoogle: Boolean, syncToCalendar: Boolean, priority: Int, description: String?, tags: String?, subtasks: List<String>) -> Unit,
    onToggleTask: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit,
    onEditTask: ((Task, String, String?, String?, Int, String?, String?, String?, Int, String?, String?) -> Unit)? = null,
    onAddRecurringTask: ((title: String, date: String?, deadlineTime: String?, priority: Int, description: String?, recurrenceType: String, recurrenceInterval: Int, recurrenceDays: String?, recurrenceEndDate: String?, syncToCalendar: Boolean, tags: String?, subtasks: List<String>) -> Unit)? = null,
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
    adminTasks: List<AdminTask> = emptyList(),
    onDismissAdminTask: ((String) -> Unit)? = null,
    onAdminTaskAction: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showAddSheet by remember { mutableStateOf(false) }
    var showEisenhowerView by remember { mutableStateOf(false) }
    var taskToDelete by remember { mutableStateOf<Task?>(null) }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }
    var taskToShowDetail by remember { mutableStateOf<Task?>(null) }
    var adminTaskToShow by remember { mutableStateOf<AdminTask?>(null) }
    var showAlarmSheet by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var showPomodoroSheet by remember { mutableStateOf(false) }
    var pomodoroTaskId by remember { mutableStateOf<String?>(null) }
    var pomodoroTaskTitle by remember { mutableStateOf<String?>(null) }
    var isVoiceListening by remember { mutableStateOf(false) }
    var voiceText by remember { mutableStateOf("") }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val context = LocalContext.current
    val activity = context as? Activity

    val isCalendarSyncing by com.theblankstate.preamble.sync.GoogleCalendarManager.isSyncing.collectAsState()
    val isManualSyncing by com.theblankstate.preamble.sync.GoogleCalendarManager.isManualSyncing.collectAsState()
    val isTasksSyncing by com.theblankstate.preamble.sync.GoogleTasksManager.isSyncing.collectAsState()
    // Background full sync flag — only shown in UI on very first sync (no prior data)
    val isBgSyncing by com.theblankstate.preamble.sync.GoogleCalendarManager.isBgSyncing.collectAsState()
    val lastCalSyncTime by com.theblankstate.preamble.sync.GoogleCalendarManager.lastSyncTime.collectAsState()
    // Theme + pomodoro state hoisted here so they're collected once, not buried inside nested lambdas
    val themeMode by ThemePreferences.themeMode.collectAsState()
    val colorfulCards by ThemePreferences.colorfulCards.collectAsState()
    val isTimelineEnabled by ThemePreferences.timelineUi.collectAsState()
    val pomodoroState by PomodoroTimerService.state.collectAsState()
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
                        onAddTask(spoken, null, null, false, false, 0, null, null, emptyList())
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
                        onAddTask(spoken, null, null, false, false, 0, null, null, emptyList())
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

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            val todayStr = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) }
                            val todayHoliday = tasks.firstOrNull { it.isInfoOnly && it.eventType == "holiday" && it.createdDate == todayStr }?.title

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Preamble", style = MaterialTheme.typography.titleLarge)
                                if (todayHoliday != null) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = todayHoliday,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
                            }

                            RichDateHeader(
                                modifier = Modifier.padding(top = 2.dp),
                                externalFestival = null
                            )
                        }
                    },
                    actions = {
                        // Search icon
                        IconButton(onClick = {
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) onSearchQueryChanged("")
                        }) {
                            Icon(
                                imageVector = if (isSearchActive) Icons.Filled.Close else Icons.Filled.Search,
                                contentDescription = if (isSearchActive) "Close Search" else "Search"
                            )
                        }

                        // Alarm icon — shows all active alarms
                        IconButton(onClick = { showAlarmSheet = true }) {
                            Icon(
                                imageVector = Icons.Filled.Alarm,
                                contentDescription = "Alarms"
                            )
                        }

                        // Eisenhower Matrix toggle
                        IconButton(onClick = { showEisenhowerView = !showEisenhowerView }) {
                            Icon(
                                imageVector = Icons.Filled.GridView,
                                contentDescription = "Eisenhower Matrix",
                                tint = if (showEisenhowerView) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(
                            onClick = { ThemePreferences.setTimelineUi(context, !isTimelineEnabled) }
                        ) {
                            Icon(
                                imageVector = if (isTimelineEnabled) Icons.Filled.ViewHeadline else Icons.Filled.ViewAgenda,
                                contentDescription = "Toggle Timeline View"
                            )
                        }

                        if (streak > 0) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    text = "\uD83D\uDD25 $streak",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
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
                    // Pomodoro FAB
                    FloatingActionButton(
                        onClick = {
                            pomodoroTaskId = null
                            pomodoroTaskTitle = null
                            showPomodoroSheet = true
                        },
                        shape = CircleShape,
                        containerColor = if (pomodoroState.isRunning)
                            MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        if (pomodoroState.isRunning) {
                            val mins = pomodoroState.remainingSeconds / 60
                            val secs = pomodoroState.remainingSeconds % 60
                            Text(
                                String.format(Locale.US, "%02d:%02d", mins, secs),
                                style = MaterialTheme.typography.labelSmall
                            )
                        } else {
                            Icon(Icons.Default.Timer, contentDescription = "Pomodoro")
                        }
                    }

                    // Voice FAB with lock badge
                    Box {
                    FloatingActionButton(
                        onClick = {
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
                        shape = CircleShape,
                        containerColor = if (isVoiceListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(
                            imageVector = if (isVoiceListening) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = if (isVoiceListening) "Stop" else "Voice Input"
                        )
                    }
                    }

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

                // Tag filter chips — show only tags that exist on user's tasks
                if (onTagFilterChanged != null && !isSearchActive) {
                    // Collect unique tags from today's tasks — O(n+m) via pre-built index map
                    val usedTags = remember(tasks) {
                        val tagIndexMap = PredefinedTags.tags.withIndex()
                            .associate { (i, t) -> t.name.lowercase() to i }
                        tasks.flatMap { task -> task.tagList }
                            .distinct()
                            .sortedBy { tagName -> tagIndexMap[tagName.lowercase()] ?: Int.MAX_VALUE }
                    }
                    if (usedTags.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedTagFilter == null,
                                    onClick = { onTagFilterChanged(null) },
                                    label = { Text("All", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                            items(usedTags) { tagName: String ->
                                FilterChip(
                                    selected = selectedTagFilter == tagName,
                                    onClick = {
                                        onTagFilterChanged(if (selectedTagFilter == tagName) null else tagName)
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
                                onToggle = { onToggleTask(task) },
                                onDelete = { taskToDelete = task },
                                onEdit = if (onEditTask != null) {
                                    { taskToEdit = task }
                                } else null,
                                onDetail = { taskToShowDetail = task },
                                onStartPomodoro = {
                                    pomodoroTaskId = task.id
                                    pomodoroTaskTitle = task.title
                                    showPomodoroSheet = true
                                },
                                onRetrySync = onRetrySync?.let { retry -> { retry(task) } },
                                isEditable = true,
                                subtaskCount = subtaskCounts[task.id],
                                isExpanded = expandedTasks.contains(task.id),
                                onToggleExpand = onToggleTaskExpanded?.let { { it(task.id) } }
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
                    onToggleTask = onToggleTask,
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
                // Today's tasks with timeline view
                if (tasks.isNotEmpty()) {
                    item(key = "progress_bar") {
                        val completed = remember(tasks) { tasks.count { it.isCompleted } }
                        val total = tasks.size
                        val progress = remember(tasks) { if (total > 0) completed.toFloat() / total else 0f }

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
                                    "Today's Progress",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "$completed / $total",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            WaveProgressBar(
                                progress = progress,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp),
                                activeColor = MaterialTheme.colorScheme.primary,
                                inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            )
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
                                                        onToggle = { onToggleTask(task) },
                                                        onDelete = { taskToDelete = task },
                                                        onEdit = if (onEditTask != null) {{ taskToEdit = task }} else null,
                                                        onDetail = { taskToShowDetail = task },
                                                        onStartPomodoro = {
                                                            pomodoroTaskId = task.id
                                                            pomodoroTaskTitle = task.title
                                                            showPomodoroSheet = true
                                                        },
                                                        onRetrySync = onRetrySync?.let { retry -> { retry(task) } },
                                                        isEditable = true,
                                                        subtaskCount = subtaskCounts[task.id],
                                                        isExpanded = expandedTasks.contains(task.id),
                                                        onToggleExpand = onToggleTaskExpanded?.let { { it(task.id) } }
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
                                    onToggle = { onToggleTask(task) },
                                    onDelete = { taskToDelete = task },
                                    onEdit = if (onEditTask != null) {{ taskToEdit = task }} else null,
                                    onDetail = { taskToShowDetail = task },
                                    onStartPomodoro = {
                                        pomodoroTaskId = task.id
                                        pomodoroTaskTitle = task.title
                                        showPomodoroSheet = true
                                    },
                                    onRetrySync = onRetrySync?.let { retry -> { retry(task) } },
                                    isEditable = true,
                                    modifier = Modifier.animateItem(),
                                    subtaskCount = subtaskCounts[task.id],
                                    isExpanded = expandedTasks.contains(task.id),
                                    onToggleExpand = onToggleTaskExpanded?.let { { it(task.id) } }
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
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp)
                        ) {
                            Text(
                                text = "No tasks for today",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap + to add your first task",
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
    }

    if (showAddSheet) {
        AddTaskSheet(
            onDismiss = { showAddSheet = false },
            onAddTask = { title, date, deadlineTime, syncToGoogle, syncToCalendar, priority, description, tags, subtasks ->
                onAddTask(title, date, deadlineTime, syncToGoogle, syncToCalendar, priority, description, tags, subtasks)
                showAddSheet = false
            },
            onAddRecurringTask = if (onAddRecurringTask != null) { { title, date, deadlineTime, priority, description, recurrenceType, recurrenceInterval, recurrenceDays, recurrenceEndDate, syncToCalendar, tags, subtasks ->
                onAddRecurringTask(title, date, deadlineTime, priority, description, recurrenceType, recurrenceInterval, recurrenceDays, recurrenceEndDate, syncToCalendar, tags, subtasks)
                showAddSheet = false
            } } else null,
            aiChatViewModel = aiChatViewModel
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
    taskToShowDetail?.let { task ->
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
            onStartPomodoro = if (task.isInfoOnly) null else {{
                pomodoroTaskId = task.id
                pomodoroTaskTitle = task.title
                taskToShowDetail = null
                showPomodoroSheet = true
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
            isPastTask = isPast
        )
    }

    if (taskToEdit != null && onEditTask != null) {
        val subtasks = subtasksProvider?.invoke(taskToEdit!!.id)?.collectAsState(initial = emptyList())?.value ?: emptyList()
        
        TaskDetailSheet(
            task = taskToEdit!!,
            onDismiss = { taskToEdit = null },
            onUpdateTask = { newTitle, newDate, newDeadlineTime, newPriority, newDescription, newTags, newRecurrenceType, newRecurrenceInterval, newRecurrenceDays, newRecurrenceEndDate ->
                onEditTask(taskToEdit!!, newTitle, newDate, newDeadlineTime, newPriority, newDescription, newTags, newRecurrenceType, newRecurrenceInterval, newRecurrenceDays, newRecurrenceEndDate)
                taskToEdit = null
            },
            onDelete = {
                taskToDelete = taskToEdit
                taskToEdit = null
            },
            onStartPomodoro = {
                pomodoroTaskId = taskToEdit!!.id
                pomodoroTaskTitle = taskToEdit!!.title
                taskToEdit = null
                showPomodoroSheet = true
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
            } else null
        )
    }

    if (showPomodoroSheet) {
        PomodoroSheet(
            onDismiss = { showPomodoroSheet = false },
            taskId = pomodoroTaskId,
            taskTitle = pomodoroTaskTitle
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
    val transition = rememberInfiniteTransition(label = "wave")
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

