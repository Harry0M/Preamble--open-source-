package com.theblankstate.preamble.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theblankstate.preamble.data.PredefinedTags
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.lazy.LazyRow
import com.theblankstate.preamble.util.EventIconHelper
import androidx.compose.ui.unit.sp
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.automirrored.filled.EventNote
import com.theblankstate.preamble.data.Task
import com.theblankstate.preamble.data.TaskInputValidator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Color constants
private val GoogleBlue = Color(0xFF4285F4)
private val GoogleGreen = Color(0xFF34A853)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailSheet(
    task: Task,
    onDismiss: () -> Unit,
    onUpdateTask: (newTitle: String, newDate: String?, newDeadlineTime: String?, newPriority: Int, newDescription: String?, newTags: String?, newRecurrenceType: String?, newRecurrenceInterval: Int, newRecurrenceDays: String?, newRecurrenceEndDate: String?, newIsEvent: Boolean, newEventIcon: String?, newEventColor: String?) -> Unit,
    onDelete: () -> Unit,
    onStartFocus: (() -> Unit)? = null,
    subtasks: List<Task> = emptyList(),
    onAddSubtask: ((String) -> Unit)? = null,
    onToggleSubtask: ((String, Boolean) -> Unit)? = null,
    onDeleteSubtask: ((String) -> Unit)? = null,
    onCompleteAllSubtasks: (() -> Unit)? = null,
    onSnooze: ((Long) -> Unit)? = null,
    onUnsnooze: (() -> Unit)? = null,
    onToggleHabit: ((Task) -> Unit)? = null,
    currentUserUid: String? = null,
    onRemoveCollabMember: ((String) -> Unit)? = null,
    onTransferCollabOwnership: ((String) -> Unit)? = null,
    onLeaveCollabTask: (() -> Unit)? = null,
    onReact: ((String) -> Unit)? = null,
    onNudge: ((String) -> Unit)? = null,
    canNudge: ((String) -> Boolean)? = null,
    nudgeCooldownRemaining: ((String) -> Long)? = null
) {
    var taskTitle by remember { mutableStateOf(task.title) }
    var taskDescription by remember { mutableStateOf(task.description ?: "") }
    var selectedTime by remember { mutableStateOf(task.deadlineTime) }
    var selectedDate by remember { mutableStateOf<String?>(task.createdDate) }
    var selectedPriority by remember { mutableStateOf(task.priority) }
    var selectedTags by remember { mutableStateOf(task.tagList.toSet()) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Recurrence state
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
    var memberPendingRemoval by remember { mutableStateOf<Pair<String, String>?>(null) }
    var memberPendingTransfer by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showLeaveTaskConfirm by remember { mutableStateOf(false) }
    val hasChanges = remember(taskTitle, taskDescription, selectedTime, selectedDate, selectedPriority, selectedTags, recurrenceType, recurrenceInterval, recurrenceDays, recurrenceEndDate, isEvent, eventIcon, eventColor) {
        taskTitle != task.title ||
                taskDescription != (task.description ?: "") ||
                selectedTime != task.deadlineTime ||
                selectedDate != task.createdDate ||
                selectedPriority != task.priority ||
                selectedTags != task.tagList.toSet() ||
                recurrenceType != task.recurrenceType ||
                recurrenceInterval != (task.recurrenceInterval ?: 1) ||
                recurrenceDays != (task.recurrenceDays?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.toSet() ?: emptySet<Int>()) ||
                recurrenceEndDate != task.recurrenceEndDate ||
                isEvent != task.isEvent ||
                eventIcon != task.eventIcon ||
                eventColor != task.eventColor
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
                    if (selectedTags.isNotEmpty()) selectedTags.joinToString(",") else null,
                    recurrenceType,
                    recurrenceInterval,
                    recurrenceDays.takeIf { it.isNotEmpty() }?.joinToString(","),
                    recurrenceEndDate,
                    isEvent,
                    eventIcon,
                    eventColor
                )
            }
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // ═══════════════════════════════════════════════════════════════
            // HEADER
            // ═══════════════════════════════════════════════════════════════
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Edit Task",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (onStartFocus != null) {
                        IconButton(onClick = {
                            onStartFocus()
                            onDismiss()
                        }) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = "Focus Timer",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (!task.isInfoOnly) {
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
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Source badge
            EditSourceBadge(task)

            // Rollover Info Badge
            val daysRolledOver = remember(task.recurrenceType, task.createdDate, task.isCompleted, task.completedDate) {
                if (task.recurrenceType == "rollover") {
                    try {
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        val createdDateObj = sdf.parse(task.createdDate)
                        val targetDateStr = task.completedDate ?: sdf.format(java.util.Date())
                        val targetDate = sdf.parse(targetDateStr)
                        if (createdDateObj != null && targetDate != null && createdDateObj.before(targetDate)) {
                            val diff = targetDate.time - createdDateObj.time
                            (diff / (1000 * 60 * 60 * 24)).toInt()
                        } else null
                    } catch (e: Exception) { null }
                } else null
            }
            if (daysRolledOver != null && daysRolledOver > 0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Refresh, 
                            contentDescription = null, 
                            modifier = Modifier.size(16.dp), 
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (task.isCompleted) "Completed $daysRolledOver days late" else "Pending for $daysRolledOver days",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            // Snooze / Unsnooze for pending rollover tasks (Material Chips)
            if (task.recurrenceType == "rollover" && !task.isCompleted) {
                val isSnoozed = task.snoozedUntil != null && task.snoozedUntil > System.currentTimeMillis()
                if (isSnoozed && onUnsnooze != null) {
                    val snoozedUntilText = remember(task.snoozedUntil) {
                        val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                        sdf.format(java.util.Date(task.snoozedUntil!!))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SuggestionChip(
                            onClick = {
                                onUnsnooze()
                                onDismiss()
                            },
                            label = { Text("Snoozed until $snoozedUntilText") },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = Color(0xFFFFF3E0),
                                labelColor = Color(0xFFE65100),
                                iconContentColor = Color(0xFFE65100)
                            ),
                            border = null
                        )
                    }
                } else if (!isSnoozed && onSnooze != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Snooze:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        listOf(
                            "1 Hour" to 1 * 60 * 60 * 1000L,
                            "4 Hours" to 4 * 60 * 60 * 1000L,
                            "Tomorrow" to 24 * 60 * 60 * 1000L
                        ).forEach { (label, duration) ->
                            SuggestionChip(
                                onClick = {
                                    onSnooze(duration)
                                    onDismiss()
                                },
                                label = { Text(label) },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sync warning
            if (task.syncFailed) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Saved locally. Will sync when online.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ═══════════════════════════════════════════════════════════════
            // TITLE
            // ═══════════════════════════════════════════════════════════════
            EditSection(title = "Title", icon = Icons.Default.Description) {
                OutlinedTextField(
                    value = taskTitle,
                    onValueChange = {
                        if (it.length <= TaskInputValidator.TITLE_MAX_LENGTH) {
                            taskTitle = it
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("What needs to be done?") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ═══════════════════════════════════════════════════════════════
            // ASSIGNED MEMBERS & STATUS
            // ═══════════════════════════════════════════════════════════════
            if (task.collabAdminUid != null || task.collabAssignees.isNotEmpty()) {
                EditSection(title = "Assigned Members & Status", icon = Icons.Default.Group) {
                    // Shared collaborator-list rendering (task 14.1) so the edit sheet and the
                    // read-only tap-to-open sheet cannot drift (Requirements 20, 21). Role
                    // controls are enabled here (showRoleControls = true); the remove/transfer/
                    // leave callbacks route through the existing confirmation dialogs so the
                    // edit sheet's Requirement 13 role behavior is unchanged.
                    CollaboratorMemberList(
                        adminUid = task.collabAdminUid,
                        adminName = task.collabAdminName,
                        assignees = task.collabAssignees,
                        currentUserUid = currentUserUid,
                        showRoleControls = true,
                        onRemoveMember = if (onRemoveCollabMember != null) {
                            { uid ->
                                val name = task.collabAssignees.firstOrNull { it.uid == uid }?.name ?: ""
                                memberPendingRemoval = uid to name
                            }
                        } else null,
                        onTransferOwnership = if (onTransferCollabOwnership != null) {
                            { uid ->
                                val name = task.collabAssignees.firstOrNull { it.uid == uid }?.name ?: ""
                                memberPendingTransfer = uid to name
                            }
                        } else null,
                        onLeave = if (onLeaveCollabTask != null) {
                            { showLeaveTaskConfirm = true }
                        } else null,
                        reactions = task.collabReactions,
                        onReact = onReact,
                        onNudge = onNudge,
                        canNudge = canNudge,
                        nudgeCooldownRemaining = nudgeCooldownRemaining,
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // ═══════════════════════════════════════════════════════════════
            // DATE & TIME
            // ═══════════════════════════════════════════════════════════════
            EditSection(title = "Schedule", icon = Icons.Default.Event) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Date picker
                    Surface(
                        onClick = { showDatePicker = true },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Date",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = selectedDate?.let { formatShortDate(it) } ?: "Not set",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Time picker
                    Surface(
                        onClick = { showTimePicker = true },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Time",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = selectedTime ?: "All day",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Clear time button
                if (selectedTime != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { selectedTime = null }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear time", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ═══════════════════════════════════════════════════════════════
            // PRIORITY
            // ═══════════════════════════════════════════════════════════════
            EditSection(title = "Priority", icon = Icons.Default.Flag) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PriorityOption(
                        label = "None",
                        color = MaterialTheme.colorScheme.outline,
                        isSelected = selectedPriority == 0,
                        onClick = { selectedPriority = 0 },
                        modifier = Modifier.weight(1f)
                    )
                    PriorityOption(
                        label = "Low",
                        color = Color(0xFF388E3C),
                        isSelected = selectedPriority == 1,
                        onClick = { selectedPriority = 1 },
                        modifier = Modifier.weight(1f)
                    )
                    PriorityOption(
                        label = "Medium",
                        color = Color(0xFFF57C00),
                        isSelected = selectedPriority == 2,
                        onClick = { selectedPriority = 2 },
                        modifier = Modifier.weight(1f)
                    )
                    PriorityOption(
                        label = "High",
                        color = Color(0xFFD32F2F),
                        isSelected = selectedPriority == 3,
                        onClick = { selectedPriority = 3 },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ═══════════════════════════════════════════════════════════════
            // SUBTASKS (App-native only)
            // ═══════════════════════════════════════════════════════════════
            SubtaskSection(
                parentTask = task,
                subtasks = subtasks,
                onAddSubtask = { title -> onAddSubtask?.invoke(title) },
                onToggleSubtask = { subtaskId, isCompleted -> onToggleSubtask?.invoke(subtaskId, isCompleted) },
                onDeleteSubtask = { subtaskId -> onDeleteSubtask?.invoke(subtaskId) },
                onCompleteAllSubtasks = { onCompleteAllSubtasks?.invoke() }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ═══════════════════════════════════════════════════════════════
            // DESCRIPTION
            // ═══════════════════════════════════════════════════════════════
            EditSection(title = "Description", icon = Icons.Default.Description) {
                OutlinedTextField(
                    value = taskDescription,
                    onValueChange = {
                        if (it.length <= TaskInputValidator.DESCRIPTION_MAX_LENGTH) {
                            taskDescription = it
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Add notes or details...") },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ═══════════════════════════════════════════════════════════════
            // TAGS
            // ═══════════════════════════════════════════════════════════════
            EditSection(title = "Tags", icon = Icons.Default.LocalOffer) {
                TagPicker(
                    selectedTags = selectedTags,
                    onTagsChanged = { selectedTags = it },
                    lockedTags = buildSet {
                        if (task.source == "google_calendar") add("Google Calendar")
                        if (task.source == "google_tasks") add("Google Tasks")
                    }
                )
            }

            // Rollover toggle
            if (recurrenceType == null || recurrenceType == "rollover") {
                Spacer(modifier = Modifier.height(20.dp))
                EditSection(title = "Rollover", icon = Icons.Default.Refresh) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Keep active until completed",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        androidx.compose.material3.Switch(
                            checked = recurrenceType == "rollover",
                            onCheckedChange = {
                                recurrenceType = if (it) "rollover" else null
                                isHabit = false
                            }
                        )
                    }
                }
            }

            // Recurrence (for template tasks)
            if ((task.isRecurrenceTemplate || task.recurrenceParentId == null) && recurrenceType != "rollover") {
                Spacer(modifier = Modifier.height(20.dp))
                EditSection(title = "Repeat", icon = Icons.Default.Repeat) {
                    RecurrencePicker(
                        recurrenceType = recurrenceType,
                        recurrenceInterval = recurrenceInterval,
                        recurrenceDays = recurrenceDays,
                        recurrenceEndDate = recurrenceEndDate,
                        onRecurrenceTypeChanged = {
                            recurrenceType = it
                            if (it == null) {
                                recurrenceInterval = 1
                                recurrenceDays = emptySet()
                                recurrenceEndDate = null
                                isHabit = false
                            }
                        },
                        onIntervalChanged = { recurrenceInterval = it },
                        onDaysChanged = { recurrenceDays = it },
                        onEndDateChanged = { recurrenceEndDate = it }
                    )
                }
            }

            // Event toggle section (can be any type of task)
            Spacer(modifier = Modifier.height(20.dp))
            EditSection(title = "Custom Event", icon = Icons.AutoMirrored.Filled.EventNote) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isEvent) "Event mode active" else "Designate as event",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        androidx.compose.material3.Switch(
                            checked = isEvent,
                            onCheckedChange = {
                                isEvent = it
                                if (isEvent) {
                                    val colors = listOf(
                                        "#D500F9", "#00E676", "#E91E63", "#FF3D00", "#FFEA00",
                                        "#FF6D00", "#00E5FF", "#FF007F", "#2979FF", "#00C853", "#FF1744"
                                    )
                                    eventColor = colors.random()
                                    eventIcon = "event"
                                }
                            }
                        )
                    }
                    AnimatedVisibility(visible = isEvent) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
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

            // Habit toggle (local recurring/rollover tasks only)
            if (recurrenceType != null && recurrenceType != "rollover" && task.source == "local" && onToggleHabit != null) {
                Spacer(modifier = Modifier.height(20.dp))
                EditSection(title = "Habit", icon = Icons.Default.LocalFireDepartment) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isHabit) "🔥 Habit mode active" else "Track as a habit",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        androidx.compose.material3.Switch(
                            checked = isHabit,
                            onCheckedChange = {
                                isHabit = it
                                onToggleHabit(task)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            // ═══════════════════════════════════════════════════════════════
            // METADATA
            // ═══════════════════════════════════════════════════════════════
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                EditMetadataItem(
                    label = "Created",
                    value = formatDisplayDate(task.createdDate),
                    modifier = Modifier.weight(1f)
                )
                if (task.isCompleted && task.completedTimestamp != null) {
                    EditMetadataItem(
                        label = "Completed",
                        value = formatTimestamp(task.completedTimestamp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Info-only warning
            if (task.isInfoOnly) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "This ${task.eventType ?: "event"} can only be modified in Google Calendar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Space previously held by Subtasks

            // ═══════════════════════════════════════════════════════════════
            // SAVE BUTTON
            // ═══════════════════════════════════════════════════════════════
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
                            recurrenceType,
                            recurrenceInterval,
                            recurrenceDays.takeIf { it.isNotEmpty() }?.joinToString(","),
                            recurrenceEndDate,
                            isEvent,
                            eventIcon,
                            eventColor
                        )
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = taskTitle.trim().isNotBlank() && hasChanges,
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Save Changes",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
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

    memberPendingRemoval?.let { (memberUid, memberName) ->
        AlertDialog(
            onDismissRequest = { memberPendingRemoval = null },
            title = { Text("Remove member?") },
            text = { Text("$memberName will lose access to this shared task. Their completion state will be kept in history but hidden from the active member list.") },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveCollabMember?.invoke(memberUid)
                        memberPendingRemoval = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { memberPendingRemoval = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    memberPendingTransfer?.let { (memberUid, memberName) ->
        AlertDialog(
            onDismissRequest = { memberPendingTransfer = null },
            title = { Text("Transfer ownership?") },
            text = { Text("$memberName will become admin of this shared task. You will stay as a member unless you leave later.") },
            confirmButton = {
                Button(
                    onClick = {
                        onTransferCollabOwnership?.invoke(memberUid)
                        memberPendingTransfer = null
                    }
                ) {
                    Text("Transfer")
                }
            },
            dismissButton = {
                TextButton(onClick = { memberPendingTransfer = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showLeaveTaskConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveTaskConfirm = false },
            title = { Text("Leave shared task?") },
            text = { Text("This task will be removed from your list. Other members will keep their copy and progress.") },
            confirmButton = {
                Button(
                    onClick = {
                        onLeaveCollabTask?.invoke()
                        showLeaveTaskConfirm = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Leave")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveTaskConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

}

// ═══════════════════════════════════════════════════════════════════════════
// Helper Composables
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun EditSourceBadge(task: Task) {
    val (icon, text, color) = when {
        task.isCalendarEvent -> Triple(
            Icons.Default.Event,
            task.calendarName ?: "Google Calendar",
            GoogleBlue
        )
        task.isGoogleTask -> Triple(
            Icons.AutoMirrored.Filled.Assignment,
            "Google Tasks",
            GoogleGreen
        )
        else -> Triple(
            Icons.Default.TaskAlt,
            "Local Task",
            MaterialTheme.colorScheme.primary
        )
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = color
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

@Composable
private fun EditSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 10.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        content()
    }
}

@Composable
private fun PriorityOption(
    label: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = if (isSelected) BorderStroke(2.dp, color) else null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) color else color.copy(alpha = 0.5f))
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EditMetadataItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Helper Functions
// ═══════════════════════════════════════════════════════════════════════════

private fun formatDisplayDate(dateStr: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val displaySdf = SimpleDateFormat("MMM d, yyyy", Locale.US)
        displaySdf.format(sdf.parse(dateStr)!!)
    } catch (_: Exception) {
        dateStr
    }
}

private fun formatShortDate(dateStr: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val displaySdf = SimpleDateFormat("EEE, MMM d", Locale.US)
        displaySdf.format(sdf.parse(dateStr)!!)
    } catch (_: Exception) {
        dateStr
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.US)
    return sdf.format(Date(timestamp))
}
