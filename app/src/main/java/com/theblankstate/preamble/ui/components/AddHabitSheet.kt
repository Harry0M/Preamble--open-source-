package com.theblankstate.preamble.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.theblankstate.preamble.data.TaskInputValidator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddHabitSheet(
    onDismiss: () -> Unit,
    onAddHabit: (title: String, frequency: String, targetDays: String?, type: String, deadlineDate: String?, reminderTime: String?, description: String?, tags: String?, interval: Int, timesPerWeek: Int?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf(TextFieldValue("")) }
    var description by remember { mutableStateOf(TextFieldValue("")) }
    var selectedFrequency by remember { mutableStateOf("daily") }  // daily, weekly, custom, times_per_week
    var selectedDays by remember { mutableStateOf(setOf<Int>()) }  // 1=Mon..7=Sun for weekly
    var customInterval by remember { mutableIntStateOf(2) }  // every N days for custom
    var timesPerWeek by remember { mutableIntStateOf(3) }  // for "X times per week"
    var habitType by remember { mutableStateOf("continuous") }  // continuous or deadline
    var deadlineDate by remember { mutableStateOf<String?>(null) }
    var reminderTime by remember { mutableStateOf<String?>(null) }
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    var showDeadlinePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // ── Header ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "New Habit",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Title ──
            OutlinedTextField(
                value = title,
                onValueChange = {
                    if (it.text.length <= TaskInputValidator.TITLE_MAX_LENGTH) title = it
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Habit name (e.g., Drink water, Read 30 min)") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Frequency ──
            Text(
                "Frequency",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val frequencies = listOf(
                    "daily" to "Daily",
                    "weekly" to "Weekly",
                    "custom" to "Custom",
                    "times_per_week" to "X per week"
                )
                frequencies.forEach { (value, label) ->
                    FilterChip(
                        selected = selectedFrequency == value,
                        onClick = { selectedFrequency = value },
                        label = { Text(label) },
                        leadingIcon = if (selectedFrequency == value) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Weekly: Day picker ──
            AnimatedVisibility(
                visible = selectedFrequency == "weekly",
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Text(
                        "Select days",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
                        val dayValues = listOf(2, 3, 4, 5, 6, 7, 1)  // Calendar.MONDAY=2...SUNDAY=1
                        dayLabels.zip(dayValues).forEach { (label, dayNum) ->
                            val isSelected = dayNum in selectedDays
                            Surface(
                                onClick = {
                                    selectedDays = if (isSelected) selectedDays - dayNum else selectedDays + dayNum
                                },
                                shape = CircleShape,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // ── Custom: Interval picker ──
            AnimatedVisibility(
                visible = selectedFrequency == "custom",
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Text(
                        "Every $customInterval days",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Slider(
                        value = customInterval.toFloat(),
                        onValueChange = { customInterval = it.toInt() },
                        valueRange = 2f..30f,
                        steps = 27,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // ── Times per week ──
            AnimatedVisibility(
                visible = selectedFrequency == "times_per_week",
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Text(
                        "$timesPerWeek times per week",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Slider(
                        value = timesPerWeek.toFloat(),
                        onValueChange = { timesPerWeek = it.toInt() },
                        valueRange = 1f..7f,
                        steps = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Habit Type ──
            Text(
                "Habit Duration",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = habitType == "continuous",
                    onClick = { habitType = "continuous"; deadlineDate = null },
                    label = { Text("Ongoing") },
                    leadingIcon = if (habitType == "continuous") {
                        { Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                FilterChip(
                    selected = habitType == "deadline",
                    onClick = { habitType = "deadline" },
                    label = { Text("Until date") },
                    leadingIcon = if (habitType == "deadline") {
                        { Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                )
            }

            // Deadline date picker
            AnimatedVisibility(
                visible = habitType == "deadline",
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        onClick = { showDeadlinePicker = true },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (deadlineDate != null) "End: ${formatHabitDate(deadlineDate!!)}" else "Set end date",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (deadlineDate != null) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Reminder Time ──
            Surface(
                onClick = { showTimePicker = true },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (reminderTime != null) "Reminder: $reminderTime" else "Add reminder (optional)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (reminderTime != null) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (reminderTime != null) {
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            "Clear",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { reminderTime = null }
                                .padding(4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Description ──
            OutlinedTextField(
                value = description,
                onValueChange = {
                    if (it.text.length <= TaskInputValidator.DESCRIPTION_MAX_LENGTH) description = it
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Notes (optional)") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Tags ──
            Text("Tags", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 4.dp))
            TagPicker(
                selectedTags = selectedTags,
                onTagsChanged = { selectedTags = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Create Button ──
            Button(
                onClick = {
                    if (title.text.trim().isNotBlank()) {
                        val targetDays = when (selectedFrequency) {
                            "weekly" -> selectedDays.sorted().joinToString(",")
                            else -> null
                        }
                        val interval = when (selectedFrequency) {
                            "custom" -> customInterval
                            else -> 1
                        }
                        val tpw = if (selectedFrequency == "times_per_week") timesPerWeek else null
                        onAddHabit(
                            title.text.trim(),
                            selectedFrequency,
                            targetDays,
                            habitType,
                            if (habitType == "deadline") deadlineDate else null,
                            reminderTime,
                            description.text.trim().takeIf { it.isNotEmpty() },
                            selectedTags.joinToString(",").takeIf { selectedTags.isNotEmpty() },
                            interval,
                            tpw
                        )
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = title.text.trim().isNotBlank() &&
                    (selectedFrequency != "weekly" || selectedDays.isNotEmpty()),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create Habit", fontWeight = FontWeight.SemiBold)
            }
        }
    }

    // ── Deadline Date Picker Dialog ──
    if (showDeadlinePicker) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = deadlineDate?.let {
                try { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it)?.time?.plus(12 * 60 * 60 * 1000) }
                catch (_: Exception) { null }
            } ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDeadlinePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { millis ->
                        deadlineDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))
                    }
                    showDeadlinePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    deadlineDate = null
                    showDeadlinePicker = false
                }) { Text("Clear") }
            }
        ) {
            DatePicker(state = dateState)
        }
    }

    // ── Reminder Time Picker Dialog ──
    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = reminderTime?.split(":")?.getOrNull(0)?.toIntOrNull() ?: 9,
            initialMinute = reminderTime?.split(":")?.getOrNull(1)?.toIntOrNull() ?: 0,
            is24Hour = true
        )
        DatePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    reminderTime = String.format(Locale.US, "%02d:%02d", timeState.hour, timeState.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    reminderTime = null
                    showTimePicker = false
                }) { Text("Clear") }
            }
        ) {
            TimePicker(state = timeState)
        }
    }
}

private fun formatHabitDate(dateStr: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val displaySdf = SimpleDateFormat("EEE, MMM d, yyyy", Locale.US)
        displaySdf.format(sdf.parse(dateStr)!!)
    } catch (_: Exception) {
        dateStr
    }
}
