package com.theblankstate.preamble.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theblankstate.preamble.ai.AiMemoryRepository
import com.theblankstate.preamble.ai.AiProcessLogger
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Switch
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberTimePickerState
import com.theblankstate.preamble.ai.AiReminderEngine
import com.theblankstate.preamble.data.AiMemoryEntity
import com.theblankstate.preamble.data.AiProcessLogEntity
import com.theblankstate.preamble.notification.AiReminderScheduler
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiMemoryBottomSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { AiMemoryRepository.get(context) }
    val scope = rememberCoroutineScope()
    val memories by repo.observe().collectAsState(initial = emptyList())
    var nlpInput by remember { mutableStateOf("") }
    var isExtracting by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { runCatching { repo.pullRemote() } }

    // Auto-clear feedback after 3 seconds
    LaunchedEffect(feedback) {
        if (feedback != null) {
            kotlinx.coroutines.delay(3000)
            feedback = null
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    "What AI remembers",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 10.dp).weight(1f)
                )
                if (memories.isNotEmpty()) {
                    TextButton(onClick = { scope.launch { repo.clearAll() } }) {
                        Text("Clear all", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Text(
                "Durable facts extracted from your chats. Injected into every AI request so responses stay personal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            // NLP Input for adding memories
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.TextField(
                        value = nlpInput,
                        onValueChange = { nlpInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                "Tell me about yourself…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        },
                        maxLines = 3,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        colors = androidx.compose.material3.TextFieldDefaults.colors(
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                    )
                    if (isExtracting) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(
                            onClick = {
                                if (nlpInput.isBlank()) return@IconButton
                                val input = nlpInput.trim()
                                nlpInput = ""
                                isExtracting = true
                                scope.launch {
                                    val saved = runCatching {
                                        com.theblankstate.preamble.ai.MemoryExtractor.extract(context, input)
                                    }.getOrDefault(0)
                                    isExtracting = false
                                    feedback = if (saved > 0) "✓ Saved $saved memor${if (saved == 1) "y" else "ies"}"
                                    else "Nothing to save — try something like 'I like dark mode' or 'My name is Raj'"
                                }
                            },
                            enabled = nlpInput.isNotBlank(),
                        ) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = "Save memory",
                                tint = if (nlpInput.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            // Feedback message
            if (feedback != null) {
                Text(
                    feedback!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (feedback!!.startsWith("✓")) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            if (memories.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "No memories yet. Talk to the AI and it'll learn things about you, or type something above.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(memories, key = { it.id }) { mem ->
                        MemoryRow(
                            mem,
                            onDelete = { scope.launch { repo.delete(mem.id) } },
                            onUpdate = { newValue ->
                                scope.launch {
                                    repo.save(mem.key, newValue, mem.category, mem.confidence, source = "user_edit")
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryRow(mem: AiMemoryEntity, onDelete: () -> Unit, onUpdate: (String) -> Unit) {
    var isEditing by remember { mutableStateOf(false) }
    var editValue by remember(mem.value) { mutableStateOf(mem.value) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).clickable { isEditing = !isEditing }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        mem.key,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        " · ${mem.category}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isEditing) {
                    androidx.compose.material3.OutlinedTextField(
                        value = editValue,
                        onValueChange = { editValue = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                if (editValue.isNotBlank() && editValue != mem.value) {
                                    onUpdate(editValue.trim())
                                }
                                isEditing = false
                            }, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = "Save",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    )
                } else {
                    Text(
                        mem.value,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = "Forget",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiProcessLogBottomSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val logger = remember { AiProcessLogger.get(context) }
    val scope = rememberCoroutineScope()
    val logs by logger.observe(limit = 200).collectAsState(initial = emptyList())
    var expandedId by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "AI activity",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (logs.isNotEmpty()) {
                    TextButton(onClick = { scope.launch { logger.clear() } }) {
                        Text("Clear", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Text(
                "Every AI call — what you said, what the model did, how long it took. Stored on-device only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            if (logs.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Nothing yet. As soon as you chat with the AI, activity will appear here.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 540.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(logs, key = { it.id }) { row ->
                        LogRow(row, expanded = expandedId == row.id, onToggle = {
                            expandedId = if (expandedId == row.id) null else row.id
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(row: AiProcessLogEntity, expanded: Boolean, onToggle: () -> Unit) {
    val dateFmt = remember { SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()) }
    val statusTint = if (row.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (row.success) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = statusTint,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    row.op,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp)
                )
                Text(
                    " · ${row.provider}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${row.durationMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                dateFmt.format(Date(row.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                row.input,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
                maxLines = if (expanded) 100 else 2
            )
            if (expanded) {
                if (!row.thought.isNullOrBlank()) {
                    LogField("thought", row.thought)
                }
                if (!row.output.isNullOrBlank()) {
                    LogField("output", row.output)
                }
                if (!row.toolCalls.isNullOrBlank()) {
                    LogField("tool_calls", row.toolCalls)
                }
                if (!row.error.isNullOrBlank()) {
                    LogField("error", row.error, error = true)
                }
            }
            TextButton(
                onClick = onToggle,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(if (expanded) "Hide details" else "Show details", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun LogField(label: String, text: String, error: Boolean = false) {
    Column(Modifier.padding(top = 6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

// ── AI Reminder Settings ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiReminderSettingsCard() {
    val context = LocalContext.current
    val sp = remember { context.getSharedPreferences("preamble_prefs", android.content.Context.MODE_PRIVATE) }

    var enabled by remember { mutableStateOf(AiReminderEngine.isEnabled(context)) }
    var tone by remember { mutableStateOf(AiReminderEngine.getTone(context)) }
    var hour by remember { mutableStateOf(sp.getInt(AiReminderEngine.PREF_HOUR, 10)) }
    var minute by remember { mutableStateOf(sp.getInt(AiReminderEngine.PREF_MINUTE, 0)) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showTonePicker by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Enable toggle
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("AI Reminder", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Daily personalized nudge based on your pending tasks and memory",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        AiReminderScheduler.setEnabled(context, it)
                    }
                )
            }

            if (enabled) {
                HorizontalDivider()

                // Reminder time row
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showTimePicker = true }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Reminder time", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "%02d:%02d".format(hour, minute),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                HorizontalDivider()

                // Tone row
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showTonePicker = true }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Reminder tone", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${tone.emoji} ${tone.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    // Time picker dialog
    if (showTimePicker) {
        val timeState = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Set reminder time") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    hour = timeState.hour
                    minute = timeState.minute
                    AiReminderScheduler.setTime(context, hour, minute)
                    showTimePicker = false
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            }
        )
    }

    // Tone picker bottom sheet
    if (showTonePicker) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showTonePicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Reminder tone", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "How should the AI speak when it reminds you?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                AiReminderEngine.Tone.values().forEach { t ->
                    val selected = tone == t
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth().clickable {
                            tone = t
                            sp.edit().putString(AiReminderEngine.PREF_TONE, t.key).apply()
                            showTonePicker = false
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(t.emoji, style = MaterialTheme.typography.titleLarge)
                            Column {
                                Text(
                                    t.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    toneDescription(t),
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
}

private fun toneDescription(tone: AiReminderEngine.Tone) = when (tone) {
    AiReminderEngine.Tone.EMPATHETIC -> "Warm and understanding. Never pushy."
    AiReminderEngine.Tone.HUMOROUS -> "Witty and playful. Makes you smile."
    AiReminderEngine.Tone.COACH -> "Energetic and motivating. No excuses."
    AiReminderEngine.Tone.BLUNT -> "Short and direct. Zero fluff."
    AiReminderEngine.Tone.RANDOM -> "Different every day — surprise yourself."
}
