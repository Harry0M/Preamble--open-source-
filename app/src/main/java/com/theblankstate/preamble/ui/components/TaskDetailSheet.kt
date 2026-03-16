package com.theblankstate.preamble.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theblankstate.preamble.data.PredefinedTags
import com.theblankstate.preamble.data.Task
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailSheet(
    task: Task,
    onDismiss: () -> Unit,
    onUpdateTask: (newTitle: String, newDate: String?, newDeadlineTime: String?, newPriority: Int, newDescription: String?, newTags: String?) -> Unit,
    onDelete: () -> Unit,
    onStartPomodoro: (() -> Unit)? = null
) {
    var taskTitle by remember { mutableStateOf(task.title) }
    var taskDescription by remember { mutableStateOf(task.description ?: "") }
    var selectedTime by remember { mutableStateOf(task.deadlineTime) }
    var selectedDate by remember { mutableStateOf<String?>(task.createdDate) }
    var selectedPriority by remember { mutableStateOf(task.priority) }
    var selectedTags by remember { mutableStateOf(task.tagList.toSet()) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val hasChanges = remember(taskTitle, taskDescription, selectedTime, selectedDate, selectedPriority, selectedTags) {
        taskTitle != task.title ||
                taskDescription != (task.description ?: "") ||
                selectedTime != task.deadlineTime ||
                selectedDate != task.createdDate ||
                selectedPriority != task.priority ||
                selectedTags != task.tagList.toSet()
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (hasChanges && taskTitle.isNotBlank()) {
                onUpdateTask(
                    taskTitle.trim(),
                    selectedDate,
                    selectedTime,
                    selectedPriority,
                    taskDescription.trim().ifBlank { null },
                    if (selectedTags.isNotEmpty()) selectedTags.joinToString(",") else null
                )
            }
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header row: title + actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Task Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row {
                    if (onStartPomodoro != null) {
                        IconButton(onClick = {
                            onStartPomodoro()
                            onDismiss()
                        }) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = "Focus Timer",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = {
                        onDelete()
                        onDismiss()
                    }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title
            OutlinedTextField(
                value = taskTitle,
                onValueChange = { taskTitle = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Task title") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Date & Time row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = selectedDate?.let {
                            try {
                                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                val displaySdf = SimpleDateFormat("MMM d", Locale.US)
                                displaySdf.format(sdf.parse(it)!!)
                            } catch (_: Exception) { it }
                        } ?: "Date",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                OutlinedButton(
                    onClick = { showTimePicker = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = selectedTime ?: "Time",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // Clear buttons
            if (selectedTime != null || selectedDate != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (selectedTime != null) {
                        TextButton(onClick = { selectedTime = null }) {
                            Text("Clear time", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Priority
            Text(
                "Priority",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
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

            Spacer(modifier = Modifier.height(12.dp))

            // Description
            Text(
                "Description",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            OutlinedTextField(
                value = taskDescription,
                onValueChange = { taskDescription = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Add details...") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Tags
            Text(
                "Tags",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            TagPicker(
                selectedTags = selectedTags,
                onTagsChanged = { selectedTags = it }
            )

            // Recurrence (for template tasks)
            if (task.isRecurrenceTemplate || task.recurrenceParentId == null) {
                var recurrenceType by remember { mutableStateOf(task.recurrenceType) }
                var recurrenceInterval by remember { mutableStateOf(task.recurrenceInterval ?: 1) }
                var recurrenceDays by remember { mutableStateOf(
                    task.recurrenceDays?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.toSet() ?: emptySet()
                ) }
                var recurrenceEndDate by remember { mutableStateOf(task.recurrenceEndDate) }

                Spacer(modifier = Modifier.height(8.dp))
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

            // Source badge
            if (task.isCalendarEvent || task.isGoogleTask) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4285F4))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (task.isCalendarEvent) "Synced from Google Calendar" else "Synced from Google Tasks",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Metadata
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Created: ${formatDisplayDate(task.createdDate)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            if (task.isCompleted && task.completedTimestamp != null) {
                Text(
                    text = "Completed: ${formatTimestamp(task.completedTimestamp)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Save button
            Button(
                onClick = {
                    if (taskTitle.isNotBlank()) {
                        onUpdateTask(
                            taskTitle.trim(),
                            selectedDate,
                            selectedTime,
                            selectedPriority,
                            taskDescription.trim().ifBlank { null },
                            if (selectedTags.isNotEmpty()) selectedTags.joinToString(",") else null
                        )
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = taskTitle.isNotBlank() && hasChanges,
                shape = CircleShape
            ) {
                Text("Save Changes")
            }
        }
    }

    // Time picker dialog
    if (showTimePicker) {
        val initialHour: Int
        val initialMinute: Int
        if (selectedTime != null) {
            val parts = selectedTime!!.split(":")
            initialHour = parts.getOrNull(0)?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.MINUTE)
        } else {
            initialHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            initialMinute = Calendar.getInstance().get(Calendar.MINUTE)
        }
        val timePickerState = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = true
        )
        DatePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedTime = String.format(Locale.US, "%02d:%02d", timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            }
        ) {
            TimePicker(
                state = timePickerState,
                modifier = Modifier.padding(16.dp)
            )
        }
    }

    // Date picker dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = true
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        selectedDate = sdf.format(Date(millis))
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun formatDisplayDate(dateStr: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val displaySdf = SimpleDateFormat("MMM d, yyyy", Locale.US)
        displaySdf.format(sdf.parse(dateStr)!!)
    } catch (_: Exception) {
        dateStr
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy 'at' HH:mm", Locale.US)
    return sdf.format(Date(timestamp))
}
