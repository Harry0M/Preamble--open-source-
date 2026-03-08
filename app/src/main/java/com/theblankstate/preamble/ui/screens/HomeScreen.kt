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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import com.theblankstate.preamble.ui.components.AddTaskSheet
import com.theblankstate.preamble.ui.components.TaskItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin

import android.widget.Toast
import com.theblankstate.preamble.ai.AiChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    tasks: List<Task>,
    pastTasks: Map<String, List<Task>> = emptyMap(),
    streak: Int,
    onAddTask: (title: String, date: String?, deadlineTime: String?) -> Unit,
    onToggleTask: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit,
    aiChatViewModel: AiChatViewModel? = null,
    modifier: Modifier = Modifier
) {
    var showAddSheet by remember { mutableStateOf(false) }
    var taskToDelete by remember { mutableStateOf<Task?>(null) }
    var isVoiceListening by remember { mutableStateOf(false) }
    var voiceText by remember { mutableStateOf("") }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val context = LocalContext.current

    val today = remember {
        SimpleDateFormat("EEEE, dd MMMM", Locale.getDefault()).format(Date())
    }

    // Voice recognizer for FAB
    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }

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
                        onAddTask(spoken, null, null)
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
        speechRecognizer.setRecognitionListener(listener)
        onDispose { speechRecognizer.destroy() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Preamble", style = MaterialTheme.typography.titleLarge)
                            Text(
                                today,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
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
                    // Voice FAB
                    FloatingActionButton(
                        onClick = {
                            if (isVoiceListening) {
                                speechRecognizer.stopListening()
                                isVoiceListening = false
                            } else {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                                }
                                speechRecognizer.startListening(intent)
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Today's progress bar (only when tasks exist)
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

                    items(
                        items = tasks,
                        key = { it.id }
                    ) { task ->
                        TaskItem(
                            task = task,
                            onToggle = { onToggleTask(task) },
                            onDelete = { taskToDelete = task },
                            isEditable = true,
                            modifier = Modifier.animateItem()
                        )
                    }
                } else {
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
                pastTasks.forEach { (date, tasksForDate) ->
                    if (tasksForDate.isNotEmpty()) {
                        item {
                            Text(
                                text = date,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
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

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }

        // Voice wave overlay at the bottom
        AnimatedVisibility(
            visible = isVoiceListening,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
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
            onAddTask = { title, date, deadlineTime ->
                onAddTask(title, date, deadlineTime)
                showAddSheet = false
            }
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

