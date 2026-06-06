package com.theblankstate.preamble.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.theblankstate.preamble.data.Task
import com.theblankstate.preamble.data.TaskInputValidator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTaskSheet(
    task: Task,
    onDismiss: () -> Unit,
    onUpdateTask: (newTitle: String, newDate: String?, newDeadlineTime: String?, newPriority: Int, newDescription: String?, newTags: String?) -> Unit
) {
    var taskTitle by remember { mutableStateOf(task.title) }
    var taskDescription by remember { mutableStateOf(task.description ?: "") }
    var selectedTime by remember { mutableStateOf(task.deadlineTime) }
    var selectedDate by remember { mutableStateOf<String?>(task.createdDate) }
    var selectedPriority by remember { mutableStateOf(task.priority) }
    var selectedTags by remember { mutableStateOf(task.tagList.toSet()) }
    var recurrenceType by remember { mutableStateOf(task.recurrenceType) }
    var recurrenceInterval by remember { mutableStateOf(task.recurrenceInterval ?: 1) }
    var recurrenceDays by remember { mutableStateOf(
        task.recurrenceDays?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.toSet() ?: emptySet()
    ) }
    var recurrenceEndDate by remember { mutableStateOf(task.recurrenceEndDate) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

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
                text = "Edit Task",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = taskTitle,
                onValueChange = {
                    if (it.length <= TaskInputValidator.TITLE_MAX_LENGTH) {
                        taskTitle = it
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                placeholder = { Text("Task title") },
                singleLine = true,
                shape = CircleShape
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    shape = CircleShape,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text(selectedTime ?: "Set Time")
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(
                    onClick = { showDatePicker = true },
                    shape = CircleShape,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text(selectedDate ?: "Set Date")
                }
            }

            // Clear time button
            if (selectedTime != null) {
                TextButton(
                    onClick = { selectedTime = null },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Clear time")
                }
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

            // Description field
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = taskDescription,
                onValueChange = {
                    if (it.length <= TaskInputValidator.DESCRIPTION_MAX_LENGTH) {
                        taskDescription = it
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Add details (optional)") },
                minLines = 2,
                maxLines = 4,
                shape = MaterialTheme.shapes.medium
            )

            // Tags picker
            Spacer(modifier = Modifier.height(8.dp))
            Text("Tags", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 4.dp))
            TagPicker(
                selectedTags = selectedTags,
                onTagsChanged = { selectedTags = it }
            )

            // Recurrence picker (shown for template tasks or editable tasks)
            if (task.isRecurrenceTemplate || task.recurrenceParentId == null) {
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

            Button(
                onClick = {
                    if (taskTitle.trim().isNotBlank()) {
                        onUpdateTask(
                            taskTitle.trim(),
                            selectedDate,
                            selectedTime,
                            selectedPriority,
                            taskDescription.trim().ifBlank { null },
                            if (selectedTags.isNotEmpty()) selectedTags.joinToString(",") else null
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .height(48.dp),
                enabled = taskTitle.trim().isNotBlank(),
                shape = CircleShape
            ) {
                Text("Update")
            }
        }
    }

    if (showTimePicker) {
        val initialHour: Int
        val initialMinute: Int
        if (task.deadlineTime != null) {
            val parts = task.deadlineTime.split(":")
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

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
