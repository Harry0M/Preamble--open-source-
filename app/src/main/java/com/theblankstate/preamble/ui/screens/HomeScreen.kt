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
import androidx.compose.ui.draw.clip
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    tasks: List<Task>,
    pastTasks: Map<String, List<Task>> = emptyMap(),
    streak: Int,
    onAddTask: (title: String, date: String?, deadlineTime: String?, syncToGoogle: Boolean, syncToCalendar: Boolean, priority: Int, description: String?, tags: String?) -> Unit,
    onToggleTask: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit,
    onEditTask: ((Task, String, String?, String?, Int, String?, String?, String?, Int, String?, String?) -> Unit)? = null,
    onAddRecurringTask: ((title: String, date: String?, deadlineTime: String?, priority: Int, description: String?, recurrenceType: String, recurrenceInterval: Int, recurrenceDays: String?, recurrenceEndDate: String?, syncToCalendar: Boolean, tags: String?) -> Unit)? = null,
    onSyncGoogle: (() -> Unit)? = null,
    isRefreshing: Boolean = false,
    aiChatViewModel: AiChatViewModel? = null,
    searchQuery: String = "",
    searchResults: List<Task> = emptyList(),
    onSearchQueryChanged: (String) -> Unit = {},
    onUpdateTaskPriority: ((Task, Int) -> Unit)? = null,
    subtaskCounts: Map<String, Pair<Int, Int>> = emptyMap(),
    expandedTasks: Set<String> = emptySet(),
    onToggleTaskExpanded: ((String) -> Unit)? = null,
    onAddSubtask: ((String, String) -> Unit)? = null,
    subtasksProvider: ((String) -> kotlinx.coroutines.flow.Flow<List<Task>>)? = null,
    selectedTagFilter: String? = null,
    onTagFilterChanged: ((String?) -> Unit)? = null,
    isInitialLoad: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showAddSheet by remember { mutableStateOf(false) }
    var showEisenhowerView by remember { mutableStateOf(false) }
    var taskToDelete by remember { mutableStateOf<Task?>(null) }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }
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
                        onAddTask(spoken, null, null, false, false, 0, null, null)
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
                        onAddTask(spoken, null, null, false, false, 0, null, null)
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



    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Preamble", style = MaterialTheme.typography.titleLarge)
                            RichDateHeader(
                                modifier = Modifier.padding(top = 2.dp)
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

                        val isTimelineEnabled = ThemePreferences.timelineUi.collectAsState().value
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
                    val pomodoroState by PomodoroTimerService.state.collectAsState()
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
            val themeMode = ThemePreferences.themeMode.collectAsState().value
            val isDarkTheme = isSystemInDarkTheme()
            val isLightMode = themeMode == ThemePreferences.ThemeMode.LIGHT || (themeMode == ThemePreferences.ThemeMode.SYSTEM && !isDarkTheme)
            val colorfulCards = ThemePreferences.colorfulCards.collectAsState().value

            val cardColors = listOf(
                Color(0xFFE3F2FD), // Blue
                Color(0xFFF3E5F5), // Purple
                Color(0xFFE8F5E9), // Green
                Color(0xFFFFF3E0), // Orange
                Color(0xFFFFEBEE), // Red
                Color(0xFFFFFDE7)  // Yellow
            )

            val isTimelineEnabled = ThemePreferences.timelineUi.collectAsState().value

            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
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

                // Tag filter chips
                if (onTagFilterChanged != null && !isSearchActive) {
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
                        items(PredefinedTags.tags) { tag ->
                            FilterChip(
                                selected = selectedTagFilter == tag.name,
                                onClick = {
                                    onTagFilterChanged(if (selectedTagFilter == tag.name) null else tag.name)
                                },
                                label = { Text(tag.name, style = MaterialTheme.typography.labelSmall) }
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
                    items(searchResults, key = { it.id }) { task ->
                        SwipeableTaskItem(
                            task = task,
                            onToggle = { onToggleTask(task) },
                            onDelete = { taskToDelete = task },
                            onEdit = if (onEditTask != null) {
                                { taskToEdit = task }
                            } else null,
                            onDetail = if (onEditTask != null) {
                                { taskToEdit = task }
                            } else null,
                            onStartPomodoro = {
                                pomodoroTaskId = task.id
                                pomodoroTaskTitle = task.title
                                showPomodoroSheet = true
                            },
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
                                onToggleSubtask = { onToggleTask(it) },
                                onAddSubtask = { title -> onAddSubtask?.invoke(task.id, title) },
                                onDeleteSubtask = { onDeleteTask(it) }
                            )
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
                    item {
                        val completed = tasks.count { it.isCompleted }
                        val total = tasks.size
                        val progress = if (total > 0) completed.toFloat() / total else 0f

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

                    if (isTimelineEnabled) {
                        item {
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
                                                SwipeableTaskItem(
                                                    task = task,
                                                    onToggle = { onToggleTask(task) },
                                                    onDelete = { taskToDelete = task },
                                                    onEdit = if (onEditTask != null) {{ taskToEdit = task }} else null,
                                                    onDetail = if (onEditTask != null) {{ taskToEdit = task }} else null,
                                                    onStartPomodoro = {
                                                        pomodoroTaskId = task.id
                                                        pomodoroTaskTitle = task.title
                                                        showPomodoroSheet = true
                                                    },
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
                                                        onToggleSubtask = { onToggleTask(it) },
                                                        onAddSubtask = { title -> onAddSubtask?.invoke(task.id, title) },
                                                        onDeleteSubtask = { onDeleteTask(it) }
                                                    )
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
                            key = { it.id }
                        ) { task ->
                            Column {
                                SwipeableTaskItem(
                                    task = task,
                                    onToggle = { onToggleTask(task) },
                                    onDelete = { taskToDelete = task },
                                    onEdit = if (onEditTask != null) {{ taskToEdit = task }} else null,
                                    onDetail = if (onEditTask != null) {{ taskToEdit = task }} else null,
                                    onStartPomodoro = {
                                        pomodoroTaskId = task.id
                                        pomodoroTaskTitle = task.title
                                        showPomodoroSheet = true
                                    },
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
                                        onToggleSubtask = { onToggleTask(it) },
                                        onAddSubtask = { title -> onAddSubtask?.invoke(task.id, title) },
                                        onDeleteSubtask = { onDeleteTask(it) }
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
                            item {
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
                                                        onDelete = { },
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
                            item {
                                RichDateBadge(
                                    dateStr = date,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                                )
                            }

                            items(
                                items = tasksForDate,
                                key = { it.id }
                            ) { task ->
                                TaskItem(
                                    task = task,
                                    onToggle = { },
                                    onDelete = { },
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
            onAddTask = { title, date, deadlineTime, syncToGoogle, syncToCalendar, priority, description, tags ->
                onAddTask(title, date, deadlineTime, syncToGoogle, syncToCalendar, priority, description, tags)
                showAddSheet = false
            },
            onAddRecurringTask = if (onAddRecurringTask != null) { { title, date, deadlineTime, priority, description, recurrenceType, recurrenceInterval, recurrenceDays, recurrenceEndDate, syncToCalendar, tags ->
                onAddRecurringTask(title, date, deadlineTime, priority, description, recurrenceType, recurrenceInterval, recurrenceDays, recurrenceEndDate, syncToCalendar, tags)
                showAddSheet = false
            } } else null,
            aiChatViewModel = aiChatViewModel
        )
    }

    if (taskToDelete != null) {
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
                Text(
                    text = "Delete Task?",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = "Are you sure you want to delete '${taskToDelete?.title}'?",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 24.dp),
                    textAlign = TextAlign.Center
                )
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

    // Active alarms bottom sheet
    if (showAlarmSheet) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val nextAlarm = alarmManager.nextAlarmClock
        // Collect all tasks with deadlineTime that are in the future
        val allTasksWithAlarms = remember(tasks, pastTasks) {
            val allTasks = mutableListOf<Task>()
            allTasks.addAll(tasks)
            pastTasks.values.forEach { allTasks.addAll(it) }
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val now = System.currentTimeMillis()
            allTasks.filter { task ->
                task.deadlineTime != null && !task.isCompleted && try {
                    val dt = sdf.parse("${task.createdDate} ${task.deadlineTime}")
                    dt != null && dt.time > now
                } catch (_: Exception) { false }
            }.sortedBy { task ->
                try { sdf.parse("${task.createdDate} ${task.deadlineTime}")?.time ?: 0L } catch (_: Exception) { 0L }
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

                if (allTasksWithAlarms.isEmpty()) {
                    Text(
                        "No upcoming task alarms",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    allTasksWithAlarms.forEach { task ->
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
                                Text(
                                    "${task.createdDate}  ${task.deadlineTime}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (taskToEdit != null && onEditTask != null) {
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
            }
        )
    }

    if (showPomodoroSheet) {
        PomodoroSheet(
            onDismiss = { showPomodoroSheet = false },
            taskId = pomodoroTaskId,
            taskTitle = pomodoroTaskTitle
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

