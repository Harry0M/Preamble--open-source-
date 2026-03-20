package com.theblankstate.preamble.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.shape.CircleShape as FoundationCircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Festival
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theblankstate.preamble.data.PredefinedTags
import com.theblankstate.preamble.data.Task
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskItem(
    task: Task,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDetail: (() -> Unit)? = null,
    onStartPomodoro: (() -> Unit)? = null,
    isEditable: Boolean = true,
    subtaskCount: Pair<Int, Int>? = null,
    isExpanded: Boolean = false,
    onToggleExpand: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val cardAlpha by animateFloatAsState(
        targetValue = if (task.isCompleted) 0.5f else 1f,
        label = "alpha"
    )

    val isOverdue = remember(task) {
        if (task.isCompleted || task.deadlineTime == null) false
        else {
            try {
                val deadlineDateStr = "${task.createdDate} ${task.deadlineTime}"
                val fullSdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                val deadlineDate = fullSdf.parse(deadlineDateStr)
                deadlineDate != null && deadlineDate.before(Date())
            } catch (_: Exception) {
                false
            }
        }
    }

    val isPastDayUncompleted = remember(task.isCompleted, task.createdDate) {
        if (task.isCompleted) false
        else {
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            task.createdDate < todayStr
        }
    }

    val errorColor = MaterialTheme.colorScheme.error
    val errorContainerColor = MaterialTheme.colorScheme.errorContainer
    val onErrorContainerColor = MaterialTheme.colorScheme.onErrorContainer
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainerColor = MaterialTheme.colorScheme.onPrimaryContainer

    var showMenu by remember { mutableStateOf(false) }
    var showDetailDialog by remember { mutableStateOf(false) }

    if (showDetailDialog) {
        TaskDetailDialog(task = task, onDismiss = { showDetailDialog = false })
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(cardAlpha)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isOverdue) errorContainerColor.copy(alpha = 0.3f)
                else Color.Transparent // Explicitly transparent
            )
            .combinedClickable(
                enabled = isEditable,
                onClick = { onDetail?.invoke() ?: run { showDetailDialog = true } },
                onLongClick = { onDetail?.invoke() ?: run { showDetailDialog = true } }
            )
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // For info-only events (holidays, birthdays, etc.) show a styled material icon instead of checkbox
        if (task.isInfoOnly) {
            val icon = when (task.eventType) {
                "holiday" -> Icons.Default.Festival
                "birthday" -> Icons.Default.Cake
                "focusTime" -> Icons.Default.CenterFocusStrong
                "outOfOffice" -> Icons.Default.Flight
                else -> Icons.AutoMirrored.Filled.EventNote
            }
            val tintColor = when (task.eventType) {
                "holiday" -> Color(0xFFE91E63) // Pink/Magenta for festivals
                "birthday" -> Color(0xFFFF9800) // Orange for cake
                "focusTime" -> Color(0xFF9C27B0) // Purple for focus
                else -> MaterialTheme.colorScheme.primary
            }
            Icon(
                imageVector = icon,
                contentDescription = task.eventType ?: "Event",
                tint = tintColor,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(24.dp)
                    .padding(2.dp) // slight padding to make it match Checkbox visual weight
            )
        } else {
            IconButton(
                onClick = { if (isEditable && !task.isSyncing) onToggle() },
                modifier = Modifier.padding(end = 4.dp).size(40.dp), // Increased touch target
                enabled = isEditable && !task.isSyncing
            ) {
                if (task.isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    val iconVector = when {
                        task.isCompleted -> Icons.Default.CheckCircle
                        isPastDayUncompleted -> Icons.Outlined.Cancel
                        else -> Icons.Outlined.Circle
                    }
                    val iconTint = when {
                        task.isCompleted -> MaterialTheme.colorScheme.primary
                        isPastDayUncompleted -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Icon(
                        imageVector = iconVector,
                        contentDescription = if (task.isCompleted) "Completed" else if (isPastDayUncompleted) "Missed" else "Uncompleted",
                        tint = iconTint,
                        modifier = Modifier.size(24.dp) // Kept visual icon size the same
                    )
                }
            }
        }
        
        // Priority dot
        if (task.priority > 0) {
            val priorityColor = when (task.priority) {
                3 -> Color(0xFFEF4444) // Red - High
                2 -> Color(0xFFF97316) // Orange - Medium
                1 -> Color(0xFF3B82F6) // Blue - Low
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(8.dp)
                    .clip(FoundationCircleShape)
                    .background(priorityColor)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            // Strip legacy emoji prefixes for backward compatibility
            val strippedTitle = task.title.removePrefix("📅 ").trim()
            
            Text(
                text = strippedTitle,
                style = MaterialTheme.typography.bodyLarge.copy(
                    textDecoration = if (task.isCompleted) {
                        TextDecoration.LineThrough
                    } else {
                        TextDecoration.None
                    }
                ),
                color = when {
                    task.isCompleted -> MaterialTheme.colorScheme.onSurfaceVariant
                    isOverdue -> errorColor
                    else -> MaterialTheme.colorScheme.onSurface
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // Description preview
            if (!task.description.isNullOrBlank()) {
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Location
            if (!task.location.isNullOrBlank()) {
                Text(
                    text = "📍 ${task.location}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Meeting link indicator
            if (!task.meetingLink.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = "📹",
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Google Meet",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF1A73E8)
                    )
                }
            }

            // Recurrence indicator
            if (task.recurrenceParentId != null || task.isRecurrenceTemplate) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = "Recurring",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (task.isRecurrenceTemplate) "Repeating" else "Recurring",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Subtask count indicator
            if (subtaskCount != null && subtaskCount.second > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickable { onToggleExpand?.invoke() }
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand subtasks",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${subtaskCount.first}/${subtaskCount.second} steps",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LinearProgressIndicator(
                        progress = { subtaskCount.first.toFloat() / subtaskCount.second },
                        modifier = Modifier.weight(1f).padding(start = 8.dp).height(4.dp),
                    )
                }
            }

            // Tag chips
            if (!task.tags.isNullOrBlank()) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    task.tagList.take(3).forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = PredefinedTags.colorForTag(tag).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = PredefinedTags.colorForTag(tag),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (task.tagList.size > 3) {
                        Text(
                            text = "+${task.tagList.size - 3}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (task.isCalendarEvent || task.isGoogleTask) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    val iconTint = if (task.syncFailed) MaterialTheme.colorScheme.error else Color(0xFF4285F4)
                    
                    if (task.isCalendarEvent) {
                        Icon(
                            imageVector = if (task.syncFailed) Icons.Default.CloudOff else Icons.Default.Event,
                            contentDescription = "Google Calendar Event",
                            modifier = Modifier.size(14.dp),
                            tint = iconTint
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        // Show calendar name or event type label
                        val calLabel = when (task.eventType) {
                            "holiday" -> task.calendarName ?: "Holiday"
                            "birthday" -> "Birthdays"
                            "focusTime" -> "Focus Time"
                            "outOfOffice" -> "Out of Office"
                            else -> task.calendarName ?: "Calendar"
                        }
                        Text(
                            text = calLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = iconTint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (task.isGoogleTask) {
                        Icon(
                            imageVector = if (task.syncFailed) Icons.Default.CloudOff else Icons.AutoMirrored.Filled.Assignment,
                            contentDescription = "Google Task",
                            modifier = Modifier.size(14.dp),
                            tint = iconTint
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (task.syncFailed) "Sync failed (Offline)" else "Google Tasks",
                            style = MaterialTheme.typography.labelSmall,
                            color = iconTint
                        )
                    }
                }
            }

            if (task.deletedFromGoogle) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "Deleted from Google Tasks",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
        if (task.deadlineTime != null) {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isOverdue) errorContainerColor
                        else primaryContainerColor
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isOverdue) "⚠ ${task.deadlineTime}" else task.deadlineTime,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    ),
                    color = if (isOverdue) onErrorContainerColor else onPrimaryContainerColor
                )
            }
        }
        if (isEditable) {
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Options",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    if (onDetail != null) {
                        DropdownMenuItem(
                            text = { Text("Details") },
                            onClick = {
                                showMenu = false
                                onDetail()
                            }
                        )
                    }
                    if (onEdit != null) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                showMenu = false
                                onEdit()
                            }
                        )
                    }
                    if (onStartPomodoro != null) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Timer,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Focus Timer")
                                }
                            },
                            onClick = {
                                showMenu = false
                                onStartPomodoro()
                            }
                        )
                    }
                    if (!task.isInfoOnly) {
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableTaskItem(
    task: Task,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDetail: (() -> Unit)? = null,
    onStartPomodoro: (() -> Unit)? = null,
    isEditable: Boolean = true,
    subtaskCount: Pair<Int, Int>? = null,
    isExpanded: Boolean = false,
    onToggleExpand: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onToggle()
                    false // reset immediately
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    false // reset immediately
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    if (!isEditable) {
        TaskItem(
            task = task,
            onToggle = onToggle,
            onDelete = onDelete,
            onEdit = onEdit,
            onDetail = onDetail,
            onStartPomodoro = onStartPomodoro,
            isEditable = false,
            subtaskCount = subtaskCount,
            isExpanded = isExpanded,
            onToggleExpand = onToggleExpand,
            modifier = modifier
        )
        return
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Color(0xFF4CAF50) // Green for complete
                SwipeToDismissBoxValue.EndToStart -> Color(0xFFEF4444) // Red for delete
                else -> Color.Transparent
            }
            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }
            val icon = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Icons.Default.CheckCircle
                SwipeToDismissBoxValue.EndToStart -> Icons.Default.Delete
                else -> Icons.Default.CheckCircle
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .then(
                        if (color != Color.Transparent) Modifier.background(color.copy(alpha = 0.2f))
                        else Modifier
                    )
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                if (direction != SwipeToDismissBoxValue.Settled) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        enableDismissFromStartToEnd = !task.isCompleted && !task.isInfoOnly,
        enableDismissFromEndToStart = !task.isInfoOnly
    ) {
        TaskItem(
            task = task,
            onToggle = onToggle,
            onDelete = onDelete,
            onEdit = onEdit,
            onDetail = onDetail,
            onStartPomodoro = onStartPomodoro,
            isEditable = true,
            subtaskCount = subtaskCount,
            isExpanded = isExpanded,
            onToggleExpand = onToggleExpand
        )
    }
}

@Composable
fun SubtaskList(
    subtasks: List<Task>,
    onToggleSubtask: (Task) -> Unit,
    onAddSubtask: (String) -> Unit,
    onDeleteSubtask: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    var newSubtaskTitle by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 40.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        subtasks.forEach { subtask ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleSubtask(subtask) }
                    .padding(vertical = 4.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = subtask.isCompleted,
                    onCheckedChange = { onToggleSubtask(subtask) },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = subtask.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        textDecoration = if (subtask.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (subtask.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Inline add subtask
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add step",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = newSubtaskTitle,
                onValueChange = { newSubtaskTitle = it },
                placeholder = { Text("Add step...", style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.weight(1f).height(40.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (newSubtaskTitle.isNotBlank()) {
                            onAddSubtask(newSubtaskTitle.trim())
                            newSubtaskTitle = ""
                        }
                    }
                )
            )
        }
    }
}
