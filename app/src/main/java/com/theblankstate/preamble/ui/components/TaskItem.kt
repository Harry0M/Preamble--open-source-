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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.platform.LocalContext
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
    onRetrySync: (() -> Unit)? = null,
    isEditable: Boolean = true,
    subtaskCount: Pair<Int, Int>? = null,
    isExpanded: Boolean = false,
    onToggleExpand: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Read haptic preference
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
    val isSnoozed = remember(task.snoozedUntil) {
        task.snoozedUntil != null && task.snoozedUntil > System.currentTimeMillis()
    }
    val snoozeTimeText = remember(task.snoozedUntil, isSnoozed) {
        if (isSnoozed && task.snoozedUntil != null) {
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            sdf.format(Date(task.snoozedUntil))
        } else null
    }
    val cardAlpha by animateFloatAsState(
        targetValue = when {
            isSnoozed -> 0.4f
            task.isCompleted -> 0.5f
            else -> 1f
        },
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

    val daysRolledOver = remember(task.recurrenceType, task.createdDate, task.isCompleted, task.completedDate) {
        if (task.recurrenceType == "rollover") {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val createdDate = sdf.parse(task.createdDate)
                val targetDateStr = task.completedDate ?: sdf.format(Date())
                val targetDate = sdf.parse(targetDateStr)
                if (createdDate != null && targetDate != null && createdDate.before(targetDate)) {
                    val diff = targetDate.time - createdDate.time
                    (diff / (1000 * 60 * 60 * 24)).toInt()
                } else null
            } catch (e: Exception) { null }
        } else null
    }

    // Escalating urgency: 0 = normal, 1 = warm (3+ days), 2 = hot (7+ days)
    val rolloverUrgency = remember(daysRolledOver, task.isCompleted) {
        if (task.isCompleted || daysRolledOver == null) 0
        else when {
            daysRolledOver >= 7 -> 2
            daysRolledOver >= 3 -> 1
            else -> 0
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
                when {
                    isOverdue -> errorContainerColor.copy(alpha = 0.3f)
                    rolloverUrgency == 2 -> errorContainerColor.copy(alpha = 0.2f)
                    rolloverUrgency == 1 -> Color(0xFFFFF3E0).copy(alpha = 0.5f)
                    else -> Color.Transparent
                }
            )
            .combinedClickable(
                enabled = isEditable || onDetail != null,
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
                onClick = {
                    if (isEditable && !task.isSyncing) {
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
                        onToggle()
                    }
                },
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
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { subtaskCount.first.toFloat() / subtaskCount.second },
                        modifier = Modifier.weight(1f).padding(end = 8.dp).height(4.dp),
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onToggleExpand?.invoke() }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${subtaskCount.first}/${subtaskCount.second} steps",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand subtasks",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Snoozed indicator chip
            if (isSnoozed && snoozeTimeText != null) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Snoozed until $snoozeTimeText",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Rollover chip with escalating urgency colors
            if (daysRolledOver != null && daysRolledOver > 0) {
                val chipColor = when {
                    task.isCompleted -> MaterialTheme.colorScheme.tertiaryContainer
                    rolloverUrgency == 2 -> MaterialTheme.colorScheme.errorContainer
                    rolloverUrgency == 1 -> Color(0xFFFFF3E0)
                    else -> MaterialTheme.colorScheme.tertiaryContainer
                }
                val chipTextColor = when {
                    task.isCompleted -> MaterialTheme.colorScheme.onTertiaryContainer
                    rolloverUrgency == 2 -> MaterialTheme.colorScheme.onErrorContainer
                    rolloverUrgency == 1 -> Color(0xFFE65100)
                    else -> MaterialTheme.colorScheme.onTertiaryContainer
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = chipColor,
                    modifier = Modifier.padding(top = 4.dp, bottom = if (task.tags.isNullOrBlank()) 0.dp else 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp), tint = chipTextColor)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (task.isCompleted) "Completed $daysRolledOver days late" else "${daysRolledOver}d pending",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = chipTextColor
                        )
                    }
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
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .then(
                            if (task.syncFailed && onRetrySync != null) {
                                Modifier.clickable {
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
                                    onRetrySync()
                                }
                            } else Modifier
                        )
                ) {
                    val iconTint = if (task.syncFailed) MaterialTheme.colorScheme.error else Color(0xFF4285F4)
                    
                    if (task.isCalendarEvent) {
                        Icon(
                            imageVector = if (task.syncFailed) Icons.Default.CloudOff else if (task.isSyncing) Icons.Default.Sync else Icons.Default.Event,
                            contentDescription = "Google Calendar Event",
                            modifier = Modifier.size(14.dp),
                            tint = iconTint
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        // Show calendar name or event type label
                        val calLabel = when {
                            task.syncFailed -> "Tap to retry"
                            task.isSyncing -> "Syncing..."
                            else -> when (task.eventType) {
                                "holiday" -> task.calendarName ?: "Holiday"
                                "birthday" -> "Birthdays"
                                "focusTime" -> "Focus Time"
                                "outOfOffice" -> "Out of Office"
                                else -> task.calendarName ?: "Calendar"
                            }
                        }
                        Text(
                            text = calLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = iconTint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (task.syncFailed) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry sync",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else if (task.isGoogleTask) {
                        Icon(
                            imageVector = if (task.syncFailed) Icons.Default.CloudOff else if (task.isSyncing) Icons.Default.Sync else Icons.AutoMirrored.Filled.Assignment,
                            contentDescription = "Google Task",
                            modifier = Modifier.size(14.dp),
                            tint = iconTint
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (task.syncFailed) "Tap to retry" else if (task.isSyncing) "Syncing..." else "Google Tasks",
                            style = MaterialTheme.typography.labelSmall,
                            color = iconTint
                        )
                        if (task.syncFailed) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry sync",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
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
    onRetrySync: (() -> Unit)? = null,
    isEditable: Boolean = true,
    subtaskCount: Pair<Int, Int>? = null,
    isExpanded: Boolean = false,
    onToggleExpand: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    false // reset immediately
                }
                else -> false
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
            onRetrySync = onRetrySync,
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
                SwipeToDismissBoxValue.EndToStart -> Color(0xFFEF4444) // Red for delete
                else -> Color.Transparent
            }
            val alignment = when (direction) {
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }
            val icon = when (direction) {
                SwipeToDismissBoxValue.EndToStart -> Icons.Default.Delete
                else -> Icons.Default.Delete
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
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = !task.isInfoOnly
    ) {
        TaskItem(
            task = task,
            onToggle = onToggle,
            onDelete = onDelete,
            onEdit = onEdit,
            onDetail = onDetail,
            onStartPomodoro = onStartPomodoro,
            onRetrySync = onRetrySync,
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
                IconButton(
                    onClick = { onToggleSubtask(subtask) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (subtask.isCompleted) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = if (subtask.isCompleted) "Completed" else "Uncompleted",
                        tint = if (subtask.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
                }
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

        // Inline add subtask using AddSubtaskInline
        AddSubtaskInline(
            title = newSubtaskTitle,
            onTitleChange = { newSubtaskTitle = it },
            onAdd = {
                if (newSubtaskTitle.isNotBlank()) {
                    onAddSubtask(newSubtaskTitle.trim())
                    newSubtaskTitle = ""
                }
            },
            onCancel = {
                newSubtaskTitle = ""
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 8.dp)
        )
    }
}
