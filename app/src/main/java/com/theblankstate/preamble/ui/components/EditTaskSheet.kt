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
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.LocalFireDepartment
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.lazy.LazyRow
import com.theblankstate.preamble.util.EventIconHelper
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.automirrored.filled.EventNote
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
    onUpdateTask: (newTitle: String, newDate: String?, newDeadlineTime: String?, newPriority: Int, newDescription: String?, newTags: String?, newIsEvent: Boolean, newEventIcon: String?, newEventColor: String?) -> Unit,
    onToggleHabit: ((Task) -> Unit)? = null
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
    var isHabit by remember { mutableStateOf(task.isHabit) }
    var isEvent by remember { mutableStateOf(task.isEvent) }
    var eventIcon by remember { mutableStateOf(task.eventIcon) }
    var eventColor by remember { mutableStateOf(task.eventColor) }
    var showEventDialog by remember { mutableStateOf(false) }
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

            // Event toggle section
            Spacer(modifier = Modifier.height(8.dp))
            val eventBorderColor = try {
                if (isEvent && !eventColor.isNullOrBlank()) Color(android.graphics.Color.parseColor(eventColor))
                else Color.Transparent
            } catch (_: Exception) { Color.Transparent }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isEvent) {
                    try { Color(android.graphics.Color.parseColor(eventColor ?: "#2979FF")).copy(alpha = 0.10f) }
                    catch (_: Exception) { MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) }
                } else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth().border(
                    width = if (isEvent) 1.dp else 0.dp,
                    color = eventBorderColor,
                    shape = RoundedCornerShape(12.dp)
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!isEvent) {
                                    isEvent = true
                                    val colors = listOf(
                                        "#D500F9", "#00E676", "#E91E63", "#FF3D00", "#FFEA00",
                                        "#FF6D00", "#00E5FF", "#FF007F", "#2979FF", "#00C853", "#FF1744"
                                    )
                                    eventColor = colors.random()
                                    eventIcon = "event"
                                } else {
                                    isEvent = false
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.EventNote,
                            contentDescription = "Mark as custom event",
                            tint = if (isEvent) {
                                try { Color(android.graphics.Color.parseColor(eventColor ?: "#2979FF")) }
                                catch (_: Exception) { MaterialTheme.colorScheme.primary }
                            } else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (isEvent) "Event mode active" else "Track as an event",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isEvent) {
                                    try { Color(android.graphics.Color.parseColor(eventColor ?: "#2979FF")) }
                                    catch (_: Exception) { MaterialTheme.colorScheme.primary }
                                } else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    AnimatedVisibility(visible = isEvent) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Event Icon",
                                style = MaterialTheme.typography.titleSmall
                            )
                            val eventIconsList = listOf(
                                "event", "festival", "cake", "flight", "restaurant", "local_bar",
                                "fitness_center", "directions_run", "directions_car", "work", "school",
                                "celebration", "lightbulb", "shopping_cart", "favorite", "medical_services",
                                "sports_esports", "music_note"
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                items(eventIconsList.size) { index ->
                                    val iconName = eventIconsList[index]
                                    val iconVector = EventIconHelper.getIconByName(iconName)
                                    val isSelected = eventIcon == iconName
                                    val selectedColor = try { Color(android.graphics.Color.parseColor(eventColor ?: "#2979FF")) } catch(_: Exception) { MaterialTheme.colorScheme.primary }
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) selectedColor.copy(alpha = 0.15f) else Color.Transparent)
                                            .clickable { eventIcon = iconName },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = iconVector,
                                            contentDescription = iconName,
                                            tint = if (isSelected) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "Event Color",
                                style = MaterialTheme.typography.titleSmall
                            )
                            val colors = listOf(
                                "#D500F9", "#00E676", "#E91E63", "#FF3D00", "#FFEA00",
                                "#FF6D00", "#00E5FF", "#FF007F", "#2979FF", "#00C853", "#FF1744"
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                items(colors.size) { index ->
                                    val colHex = colors[index]
                                    val col = try { Color(android.graphics.Color.parseColor(colHex)) } catch(_: Exception) { Color.Gray }
                                    val isSelected = eventColor == colHex
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(col)
                                            .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                shape = CircleShape
                                            )
                                            .clickable { eventColor = colHex }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Habit toggle section
            if (task.canBeHabit && onToggleHabit != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isHabit) Color(0xFFFF6D00).copy(alpha = 0.10f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                isHabit = !isHabit
                                onToggleHabit(task)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                if (isHabit) Icons.Default.LocalFireDepartment
                                else Icons.Outlined.LocalFireDepartment,
                                contentDescription = "Track as habit",
                                tint = if (isHabit) Color(0xFFFF6D00)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isHabit) "\uD83D\uDD25 Habit mode \u2014 your streak data stays in Preamble"
                            else "Track as a habit",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isHabit) Color(0xFFFF6D00)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (task.recurrenceType != null && task.source != "local") {
                // Friendly message for Google-synced recurring tasks
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.LocalFireDepartment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "To track this as a habit, create it as a local task \u2014 habits aren\u2019t synced to Google yet \uD83D\uDD25",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
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
                            if (selectedTags.isNotEmpty()) selectedTags.joinToString(",") else null,
                            isEvent,
                            eventIcon,
                            eventColor
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
