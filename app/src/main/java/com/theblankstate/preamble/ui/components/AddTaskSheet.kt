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
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.material3.TimePicker
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
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
    onAddTask: (title: String, date: String?, deadlineTime: String?, syncToGoogle: Boolean, syncToCalendar: Boolean, priority: Int, description: String?, tags: String?) -> Unit,
    onAddRecurringTask: ((title: String, date: String?, deadlineTime: String?, priority: Int, description: String?, recurrenceType: String, recurrenceInterval: Int, recurrenceDays: String?, recurrenceEndDate: String?, syncToCalendar: Boolean, tags: String?) -> Unit)? = null,
    aiChatViewModel: AiChatViewModel? = null
) {
    var taskTitle by remember { mutableStateOf("") }
    var taskDescription by remember { mutableStateOf("") }
    var selectedTime by remember { mutableStateOf<String?>(null) }
    var selectedDate by remember { mutableStateOf<String?>(null) }
    var selectedPriority by remember { mutableStateOf(0) }
    var selectedTags by remember { mutableStateOf<Set<String>>(emptySet()) }
    var syncToGoogle by remember { mutableStateOf(false) }
    var syncToCalendar by remember { mutableStateOf(false) }
    var recurrenceType by remember { mutableStateOf<String?>(null) }
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
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current

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
                    if (selectedTags.isNotEmpty()) selectedTags.joinToString(",") else null
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
                    if (selectedTags.isNotEmpty()) selectedTags.joinToString(",") else null
                )
            }
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
            Text(
                text = "New Task",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = taskTitle,
                    onValueChange = { taskTitle = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    placeholder = { Text("What do you need to remember?") },
                    singleLine = true,
                    shape = CircleShape,
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

            // Icon row: Clock (schedule), Repeat, Details, Tags info chips
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

                // Repeat icon - opens repeat sheet
                if (onAddRecurringTask != null) {
                    IconButton(onClick = { showRepeatSheet = true }) {
                        Icon(
                            Icons.Default.Repeat,
                            contentDescription = "Repeat",
                            tint = if (recurrenceType != null)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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

                Spacer(modifier = Modifier.weight(1f))

                // Selected info chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
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
                    if (recurrenceType != null) {
                        SuggestionChip(
                            onClick = { showRepeatSheet = true },
                            label = { Text(recurrenceType!!, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            // Description field (toggled by details icon)
            AnimatedVisibility(visible = showDescription) {
                OutlinedTextField(
                    value = taskDescription,
                    onValueChange = { taskDescription = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    placeholder = { Text("Add details (optional)") },
                    minLines = 2,
                    maxLines = 4,
                    shape = MaterialTheme.shapes.medium
                )
            }

            // Priority selector
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val priorities = listOf(0 to "None", 1 to "Low", 2 to "Medium", 3 to "High")
                priorities.forEach { (value, label) ->
                    FilterChip(
                        selected = selectedPriority == value,
                        onClick = { selectedPriority = value },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Tags picker - single line horizontal scroll
            Spacer(modifier = Modifier.height(8.dp))
            Text("Tags", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TagPickerInline(
                    selectedTags = selectedTags,
                    onTagsChanged = { selectedTags = it }
                )
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

            // Sync to Google Tasks toggle (only visible when linked)
            if (googleLinked) {
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
            }

            Button(
                onClick = { saveTask() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .height(48.dp),
                enabled = taskTitle.isNotBlank(),
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
