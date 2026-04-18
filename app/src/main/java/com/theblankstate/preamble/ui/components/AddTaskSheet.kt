package com.theblankstate.preamble.ui.components

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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.theblankstate.preamble.data.PredefinedTags
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.theblankstate.preamble.data.TaskInputValidator
import com.theblankstate.preamble.ai.AiChatViewModel
import com.theblankstate.preamble.sync.GoogleCalendarManager
import com.theblankstate.preamble.sync.GoogleTasksManager
import com.theblankstate.preamble.util.NaturalDateParser
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskSheet(
    onDismiss: () -> Unit,
    onAddTask: (title: String, date: String?, deadlineTime: String?, syncToGoogle: Boolean, syncToCalendar: Boolean, priority: Int, description: String?, tags: String?, subtasks: List<String>) -> Unit,
    onAddRecurringTask: ((title: String, date: String?, deadlineTime: String?, priority: Int, description: String?, recurrenceType: String, recurrenceInterval: Int, recurrenceDays: String?, recurrenceEndDate: String?, syncToCalendar: Boolean, tags: String?, subtasks: List<String>) -> Unit)? = null,
    aiChatViewModel: AiChatViewModel? = null
) {
    val context = LocalContext.current
    // Haptic feedback support
    val hapticEnabled = remember {
        context.getSharedPreferences("preamble_prefs", android.content.Context.MODE_PRIVATE)
            .getBoolean("haptic_feedback_enabled", true)
    }
    val vibrator = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        }
    }
    var taskTitle by remember { mutableStateOf("") }
    var taskDescription by remember { mutableStateOf("") }
    var newSubtasks by remember { mutableStateOf(listOf<String>()) }
    var pendingSubtaskTitle by remember { mutableStateOf("") }
    var selectedTime by remember { mutableStateOf<String?>(null) }
    var selectedDate by remember { mutableStateOf<String?>(null) }
    var selectedPriority by remember { mutableStateOf(0) }
    var selectedTags by remember { mutableStateOf<Set<String>>(emptySet()) }
    var syncToGoogle by remember { mutableStateOf(false) }
    var syncToCalendar by remember { mutableStateOf(false) }
    var recurrenceType by remember { mutableStateOf<String?>("rollover") }
    var recurrenceInterval by remember { mutableStateOf(1) }
    var recurrenceDays by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var recurrenceEndDate by remember { mutableStateOf<String?>(null) }
    val googleLinked = GoogleTasksManager.isLinked.collectAsState().value
    val calendarLinked = GoogleCalendarManager.isLinked.collectAsState().value
    var isListening by remember { mutableStateOf(false) }
    var wantsToListen by remember { mutableStateOf(false) }
    var showScheduleSheet by remember { mutableStateOf(false) }
    var showRepeatSheet by remember { mutableStateOf(false) }
    var showDescription by remember { mutableStateOf(false) }
    var showTagsDialog by remember { mutableStateOf(false) }
    var showPriorityDialog by remember { mutableStateOf(false) }
    var showTaskTypeInfo by remember { mutableStateOf(false) }
    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val isToday = selectedDate == null || selectedDate == todayStr

    // Auto-clear rollover when user picks a non-today date
    LaunchedEffect(isToday) {
        if (!isToday && recurrenceType == "rollover") {
            recurrenceType = null
        }
    }
    val focusRequester = remember { FocusRequester() }

    // Lazily create SpeechRecognizer only when user taps mic (saves ~3-5MB)
    var speechRecognizerRef by remember { mutableStateOf<SpeechRecognizer?>(null) }

    fun getOrCreateRecognizer(): SpeechRecognizer {
        return speechRecognizerRef ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            speechRecognizerRef = it
        }
    }

    fun createSpeechIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        }
    }

    val recognizerListener = remember {
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
                wantsToListen = false
            }
            override fun onError(error: Int) {
                isListening = false
                wantsToListen = false
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spoken = matches?.firstOrNull()
                if (!spoken.isNullOrBlank()) {
                    // Use AI to parse date/time from voice input if configured
                    if (aiChatViewModel != null && aiChatViewModel.isConfigured()) {
                        aiChatViewModel.processTaskInput(spoken) { handled ->
                            if (handled) {
                                // AI parsed and added the task — close sheet
                                onDismiss()
                            } else {
                                // AI didn't parse — fill text field for manual add
                                taskTitle = spoken
                            }
                        }
                    } else {
                        taskTitle = spoken
                    }
                }
                isListening = false
                wantsToListen = false
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    taskTitle = matches[0]
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            speechRecognizerRef?.destroy()
            speechRecognizerRef = null
        }
    }

    val saveTask = {
        if (taskTitle.isNotBlank()) {
            val finalSubtasks = newSubtasks.toMutableList()
            if (pendingSubtaskTitle.isNotBlank()) {
                finalSubtasks.add(pendingSubtaskTitle.trim())
            }
            
            val desc = taskDescription.trim().ifBlank { null }
            if (recurrenceType != null && onAddRecurringTask != null) {
                val daysStr = if (recurrenceDays.isNotEmpty()) recurrenceDays.sorted().joinToString(",") else null
                onAddRecurringTask(
                    taskTitle.trim(),
                    selectedDate,
                    selectedTime,
                    selectedPriority,
                    desc,
                    recurrenceType!!,
                    recurrenceInterval,
                    daysStr,
                    recurrenceEndDate,
                    syncToCalendar,
                    if (selectedTags.isNotEmpty()) selectedTags.joinToString(",") else null,
                    finalSubtasks
                )
            } else {
                onAddTask(
                    taskTitle.trim(),
                    selectedDate,
                    selectedTime,
                    syncToGoogle,
                    syncToCalendar,
                    selectedPriority,
                    desc,
                    if (selectedTags.isNotEmpty()) selectedTags.joinToString(",") else null,
                    finalSubtasks
                )
            }
            // Haptic + toast confirmation
            if (hapticEnabled) {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        vibrator?.vibrate(android.os.VibrationEffect.createOneShot(30, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(30)
                    }
                } catch (_: Exception) {}
            }
            android.widget.Toast.makeText(context, "Task added ✓", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            val placeholders = remember {
                listOf(
                    "Try: 'Buy groceries at 5pm !high'",
                    "Try: 'Meeting with John tomorrow'",
                    "Try: 'Call mom tonight #personal'",
                    "Try: 'Workout daily at 7am'"
                )
            }
            var currentPlaceholderIndex by remember { mutableStateOf(0) }
            
            LaunchedEffect(Unit) {
                while(true) {
                    kotlinx.coroutines.delay(3500)
                    currentPlaceholderIndex = (currentPlaceholderIndex + 1) % placeholders.size
                }
            }
            
            // Selected info chips hoisted to the top
            Box(modifier = Modifier.fillMaxWidth().animateContentSize()) {
                if (selectedDate != null || selectedTime != null || (recurrenceType != null && recurrenceType != "rollover") || selectedTags.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp)
                    ) {
                        if (selectedDate != null) {
                            SuggestionChip(
                                onClick = { showScheduleSheet = true },
                                label = { Text(selectedDate!!, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                        if (selectedTime != null) {
                            SuggestionChip(
                                onClick = { showScheduleSheet = true },
                                label = { Text(selectedTime!!, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                        if (recurrenceType != null && recurrenceType != "rollover") {
                            SuggestionChip(
                                onClick = { showRepeatSheet = true },
                                label = { Text(recurrenceType!!, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                        if (selectedTags.isNotEmpty()) {
                            SuggestionChip(
                                onClick = { showTagsDialog = true },
                                label = { Text(if (selectedTags.size == 1) selectedTags.first() else "${selectedTags.size} Tags", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Notation icon showing current task type
                val currentNotationType = when {
                    recurrenceType == "rollover" -> "half_dotted"
                    recurrenceType != null && recurrenceType != "rollover" -> "fully_dotted"
                    else -> "solid"
                }
                NotationIcon(
                    type = currentNotationType,
                    size = 22.dp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp)
                )

                TextField(
                    value = taskTitle,
                    onValueChange = {
                        if (it.length <= TaskInputValidator.TITLE_MAX_LENGTH) {
                            taskTitle = it
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    placeholder = { 
                        androidx.compose.animation.AnimatedContent(
                            targetState = currentPlaceholderIndex,
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(500)) + slideInVertically(animationSpec = tween(500)) { height -> height }) togetherWith
                                (fadeOut(animationSpec = tween(500)) + slideOutVertically(animationSpec = tween(500)) { height -> -height })
                            },
                            label = "placeholderAnim"
                        ) { targetIndex ->
                            Text(
                                text = placeholders[targetIndex],
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                maxLines = 1
                            )
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    textStyle = MaterialTheme.typography.titleLarge,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { saveTask() })
                )
                Spacer(modifier = Modifier.width(8.dp))
                FloatingActionButton(
                    onClick = {
                        if (isListening || wantsToListen) {
                            wantsToListen = false
                            speechRecognizerRef?.stopListening()
                            isListening = false
                        } else {
                            wantsToListen = true
                            val recognizer = getOrCreateRecognizer()
                            recognizer.setRecognitionListener(recognizerListener)
                            recognizer.startListening(createSpeechIntent())
                            isListening = true
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    containerColor = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (isListening) "Stop" else "Voice Input"
                    )
                }
            }

            // Wave animation while listening
            AnimatedVisibility(
                visible = isListening,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                WaveAnimation(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // NLP date/time suggestion chips
            val parsedDateTime = remember(taskTitle) { NaturalDateParser.parse(taskTitle) }
            AnimatedVisibility(visible = parsedDateTime != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (parsedDateTime?.date != null) {
                        SuggestionChip(
                            onClick = {
                                selectedDate = parsedDateTime.date
                                taskTitle = parsedDateTime.cleanedTitle
                            },
                            label = { Text(parsedDateTime.date, style = MaterialTheme.typography.labelSmall) },
                            icon = { Icon(Icons.Default.DateRange, null, Modifier.size(16.dp)) }
                        )
                    }
                    if (parsedDateTime?.time != null) {
                        SuggestionChip(
                            onClick = {
                                selectedTime = parsedDateTime.time
                                taskTitle = parsedDateTime.cleanedTitle
                            },
                            label = { Text(parsedDateTime.time, style = MaterialTheme.typography.labelSmall) },
                            icon = { Icon(Icons.Default.AccessTime, null, Modifier.size(16.dp)) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Task type notation selector row
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Normal (one-day) task notation
                val isNormal = recurrenceType == null
                val isRollover = recurrenceType == "rollover"
                val isRecurring = recurrenceType != null && recurrenceType != "rollover"
                val primaryCol = MaterialTheme.colorScheme.primary
                val mutedCol = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                val surfVariant = MaterialTheme.colorScheme.surfaceVariant

                // One-Day Task button
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            recurrenceType = null
                        }
                        .then(
                            if (isNormal) Modifier.background(surfVariant, CircleShape).padding(horizontal = 12.dp)
                            else Modifier.padding(horizontal = 12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NotationIcon(type = "solid", size = 14.dp, color = if (isNormal) primaryCol else mutedCol)
                        Text(
                            "One-Day",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isNormal) primaryCol else mutedCol
                        )
                    }
                }

                // Rollover (Active) button — only for today
                if (isToday) {
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                recurrenceType = "rollover"
                            }
                            .then(
                                if (isRollover) Modifier.background(surfVariant, CircleShape).padding(horizontal = 12.dp)
                                else Modifier.padding(horizontal = 12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            NotationIcon(type = "half_dotted", size = 14.dp, color = if (isRollover) primaryCol else mutedCol)
                            Text(
                                "Active",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isRollover) primaryCol else mutedCol
                            )
                        }
                    }
                }

                // Recurring button
                if (onAddRecurringTask != null) {
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (isRecurring) {
                                    // Toggle off recurrence
                                    recurrenceType = if (isToday) "rollover" else null
                                    recurrenceInterval = 1
                                    recurrenceDays = emptySet()
                                    recurrenceEndDate = null
                                } else {
                                    showRepeatSheet = true
                                }
                            }
                            .then(
                                if (isRecurring) Modifier.background(surfVariant, CircleShape).padding(horizontal = 12.dp)
                                else Modifier.padding(horizontal = 12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            NotationIcon(type = "fully_dotted", size = 14.dp, color = if (isRecurring) primaryCol else mutedCol)
                            Text(
                                "Repeat",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isRecurring) primaryCol else mutedCol
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Info button
                IconButton(onClick = { showTaskTypeInfo = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Task type info",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Icon row: Clock (schedule), Details, Tags, Priority
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Clock icon - opens schedule sheet (date + time + reminder)
                IconButton(onClick = { showScheduleSheet = true }) {
                    Icon(
                        Icons.Default.AccessTime,
                        contentDescription = "Schedule",
                        tint = if (selectedTime != null || selectedDate != null)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Details icon - toggles description field
                IconButton(onClick = { showDescription = !showDescription }) {
                    Icon(
                        Icons.AutoMirrored.Filled.Notes,
                        contentDescription = "Details",
                        tint = if (taskDescription.isNotBlank())
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Tags icon
                Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                    IconButton(onClick = { showTagsDialog = true }) {
                        Icon(
                            Icons.Default.Label,
                            contentDescription = "Tags",
                            tint = if (selectedTags.isNotEmpty())
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showTagsDialog,
                        onDismissRequest = { showTagsDialog = false },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.widthIn(max = 240.dp).heightIn(max = 300.dp),
                        properties = PopupProperties(focusable = false)
                    ) {
                        PredefinedTags.tags.forEach { tag ->
                            val isSelected = tag.name in selectedTags
                            DropdownMenuItem(
                                text = { Text(tag.name, style = MaterialTheme.typography.bodyMedium) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(tag.color, CircleShape)
                                    )
                                },
                                trailingIcon = {
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                onClick = {
                                    val newTags = if (isSelected) selectedTags - tag.name else selectedTags + tag.name
                                    selectedTags = newTags
                                }
                            )
                        }
                    }
                }

                // Priority icon
                val priorityColorsCache = remember {
                    listOf(
                        Color.Transparent, // unused logic fallback
                        Color(0xFF4CAF50),
                        Color(0xFFFF9800),
                        Color(0xFFF44336)
                    )
                }
                Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                    IconButton(onClick = { showPriorityDialog = true }) {
                        Icon(
                            if (selectedPriority != 0) Icons.Default.Flag else Icons.Outlined.Flag,
                            contentDescription = "Priority",
                            tint = if (selectedPriority != 0) priorityColorsCache[selectedPriority] else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showPriorityDialog,
                        onDismissRequest = { showPriorityDialog = false },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.widthIn(max = 240.dp),
                        properties = PopupProperties(focusable = false)
                    ) {
                        val priorities = listOf(0 to "None (Default)", 1 to "Low Priority", 2 to "Medium Priority", 3 to "High Priority")
                        priorities.forEach { (value, label) ->
                            val isSelected = selectedPriority == value
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        text = label, 
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) priorityColorsCache[value].takeIf { value != 0 } ?: MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface
                                    ) 
                                },
                                leadingIcon = {
                                    Icon(
                                        if (value != 0) Icons.Default.Flag else Icons.Outlined.Flag,
                                        contentDescription = null,
                                        tint = if (value != 0) priorityColorsCache[value] else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingIcon = {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check, 
                                            contentDescription = "Selected", 
                                            tint = if (value != 0) priorityColorsCache[value] else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    selectedPriority = value
                                    showPriorityDialog = false
                                }
                            )
                        }
                    }
                }
            }

            // Description field (toggled by details icon)
            AnimatedVisibility(visible = showDescription) {
                TextField(
                    value = taskDescription,
                    onValueChange = {
                        if (it.length <= TaskInputValidator.DESCRIPTION_MAX_LENGTH) {
                            taskDescription = it
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    placeholder = { Text("Add details (optional)") },
                    minLines = 2,
                    maxLines = 4,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            // Subtasks Section (App-native only)
            if (!syncToGoogle && !syncToCalendar) {
                Spacer(modifier = Modifier.height(8.dp))
                var showAddSubtask by remember { mutableStateOf(false) }
                Text(
                    "Subtasks",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                val keyboardController = LocalSoftwareKeyboardController.current
                
                Column(
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    newSubtasks.forEachIndexed { index, subtask ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Circle,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = subtask,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { 
                                    newSubtasks = newSubtasks.toMutableList().apply { removeAt(index) } 
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    
                    androidx.compose.animation.AnimatedContent(
                        targetState = showAddSubtask,
                        label = "addSubtaskAnim"
                    ) { adding ->
                        if (adding) {
                            AddSubtaskInline(
                                title = pendingSubtaskTitle,
                                onTitleChange = { pendingSubtaskTitle = it },
                                onAdd = {
                                    if (pendingSubtaskTitle.isNotBlank()) {
                                        newSubtasks = newSubtasks + pendingSubtaskTitle.trim()
                                        pendingSubtaskTitle = ""
                                        showAddSubtask = false
                                        keyboardController?.hide()
                                    }
                                },
                                onCancel = {
                                    pendingSubtaskTitle = ""
                                    showAddSubtask = false
                                    keyboardController?.hide()
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            TextButton(
                                onClick = { showAddSubtask = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add Subtask")
                            }
                        }
                    }
                }
            }


            // Sync to Google Calendar toggle (only visible when linked)
            if (calendarLinked) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Sync to Google Calendar",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    androidx.compose.material3.Switch(
                        checked = syncToCalendar,
                        onCheckedChange = {
                            syncToCalendar = it
                            if (it) syncToGoogle = false
                        }
                    )
                }
            }

            // Sync to Google Tasks toggle (only visible when linked and not recurring)
            if (googleLinked) {
                if (recurrenceType == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Sync to Google Tasks",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        androidx.compose.material3.Switch(
                            checked = syncToGoogle,
                            onCheckedChange = {
                                syncToGoogle = it
                                if (it) syncToCalendar = false
                            }
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp).padding(end = 6.dp)
                        )
                        Text(
                            "Google Tasks doesn't support repeating tasks. Use Calendar instead.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Button(
                onClick = { saveTask() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .height(48.dp),
                enabled = taskTitle.trim().isNotBlank(),
                shape = CircleShape
            ) {
                Text("Add")
            }
        }
    }

    // Schedule Dialog - Calendar + optional TimePicker
    if (showScheduleSheet) {
        val cal = Calendar.getInstance()
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val tomorrowMillis = tomorrow.timeInMillis
        var showTimeDialog by remember { mutableStateOf(false) }

        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate?.let {
                try { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it)?.time } catch (_: Exception) { null }
            },
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis >= tomorrowMillis - 86400000L
                }
            }
        )

        DatePickerDialog(
            onDismissRequest = { showScheduleSheet = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        selectedDate = sdf.format(Date(millis))
                    }
                    showScheduleSheet = false
                }) { Text("Done") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showScheduleSheet = false }) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { showTimeDialog = true }) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (selectedTime != null) selectedTime!! else "Set Time")
                    }
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }

        // Separate TimePicker dialog
        if (showTimeDialog) {
            val timePickerState = rememberTimePickerState(
                initialHour = selectedTime?.split(":")?.getOrNull(0)?.toIntOrNull() ?: cal.get(Calendar.HOUR_OF_DAY),
                initialMinute = selectedTime?.split(":")?.getOrNull(1)?.toIntOrNull() ?: cal.get(Calendar.MINUTE),
                is24Hour = true
            )
            DatePickerDialog(
                onDismissRequest = { showTimeDialog = false },
                confirmButton = {
                    TextButton(onClick = {
                        selectedTime = String.format(Locale.US, "%02d:%02d", timePickerState.hour, timePickerState.minute)
                        showTimeDialog = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showTimeDialog = false }) { Text("Cancel") }
                }
            ) {
                TimePicker(
                    state = timePickerState,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }

    // Repeat Bottom Sheet
    if (showRepeatSheet) {
        ModalBottomSheet(
            onDismissRequest = { showRepeatSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Repeat",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                RecurrencePicker(
                    recurrenceType = recurrenceType,
                    recurrenceInterval = recurrenceInterval,
                    recurrenceDays = recurrenceDays,
                    recurrenceEndDate = recurrenceEndDate,
                    onRecurrenceTypeChanged = { recurrenceType = it; if (it == null) { recurrenceInterval = 1; recurrenceDays = emptySet(); recurrenceEndDate = null } },
                    onIntervalChanged = { recurrenceInterval = it },
                    onDaysChanged = { recurrenceDays = it },
                    onEndDateChanged = { recurrenceEndDate = it }
                )
            }
        }
    }

    // Task type info dialog
    if (showTaskTypeInfo) {
        TaskTypeInfoDialog(onDismiss = { showTaskTypeInfo = false })
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
fun WaveAnimation(modifier: Modifier = Modifier, color: Color) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val midY = h / 2f
        val barCount = 30
        val gap = w / barCount
        val barWidth = gap * 0.4f

        for (i in 0 until barCount) {
            val x = i * gap + gap / 2
            val amplitude = midY * 0.8f
            val barH = amplitude * kotlin.math.abs(sin(phase + i * 0.3f)) + 3.dp.toPx()

            drawLine(
                color = color,
                start = Offset(x, midY - barH / 2),
                end = Offset(x, midY + barH / 2),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun PriorityAnimatedSlider(
    selectedPriority: Int,
    onPrioritySelected: (Int) -> Unit
) {
    Spacer(modifier = Modifier.height(16.dp))
    val priorities = remember { listOf(0 to "None", 1 to "Low", 2 to "Medium", 3 to "High") }
    
    val fallbackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val priorityColors = remember {
        listOf(
            fallbackColor,
            Color(0xFF4CAF50), // Low - Green
            Color(0xFFFF9800), // Medium - Orange
            Color(0xFFF44336)  // High - Red
        )
    }
    
    val targetColor = if (selectedPriority in priorityColors.indices) priorityColors[selectedPriority] else fallbackColor
    val animatedColor by androidx.compose.animation.animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(400),
        label = "priorityColor"
    )
    
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Priority", 
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = priorities[selectedPriority].second,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                color = if (selectedPriority == 0) MaterialTheme.colorScheme.onSurfaceVariant else animatedColor
            )
        }
        
        androidx.compose.material3.Slider(
            value = selectedPriority.toFloat(),
            onValueChange = { onPrioritySelected(java.lang.Math.round(it)) },
            valueRange = 0f..3f,
            steps = 2,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = if (selectedPriority == 0) MaterialTheme.colorScheme.onSurfaceVariant else animatedColor,
                activeTrackColor = if (selectedPriority == 0) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else animatedColor.copy(alpha = 0.7f),
                inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun NotationIcon(
    type: String,
    size: androidx.compose.ui.unit.Dp,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val strokeW = 2.dp.toPx()
        val r = (this.size.width - strokeW) / 2f
        when (type) {
            "solid" -> {
                drawCircle(color = color, radius = r)
            }
            "half_dotted" -> {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 180f,
                    useCenter = false,
                    style = Stroke(width = strokeW)
                )
                drawArc(
                    color = color,
                    startAngle = 90f,
                    sweepAngle = 180f,
                    useCenter = false,
                    style = Stroke(
                        width = strokeW,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                    )
                )
            }
            "fully_dotted" -> {
                drawCircle(
                    color = color,
                    radius = r,
                    style = Stroke(
                        width = strokeW,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                    )
                )
            }
        }
    }
}

@Composable
fun TaskTypeInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        },
        title = { Text("Task Types", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(
                    "Choose how your task behaves:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    NotationIcon(type = "solid", size = 22.dp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 2.dp))
                    Column {
                        Text("One-Day Task", style = MaterialTheme.typography.titleSmall)
                        Text("Lives only for its scheduled day. If not completed, it disappears from your active view.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    NotationIcon(type = "half_dotted", size = 22.dp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 2.dp))
                    Column {
                        Text("Active Until Complete", style = MaterialTheme.typography.titleSmall)
                        Text("Rolls over to the next day automatically until you mark it done. Great for tasks you don\u2019t want to forget.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    NotationIcon(type = "fully_dotted", size = 22.dp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 2.dp))
                    Column {
                        Text("Recurring Task", style = MaterialTheme.typography.titleSmall)
                        Text("Repeats on a schedule \u2014 daily, weekly, or custom. Each occurrence is independent.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    )
}
