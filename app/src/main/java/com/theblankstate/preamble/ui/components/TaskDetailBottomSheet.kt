package com.theblankstate.preamble.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Canvas
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.theblankstate.preamble.data.PredefinedTags
import com.theblankstate.preamble.data.Task
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// === DESIGN TOKENS === //
private val SpaceXS = 4.dp
private val SpaceSM = 8.dp
private val SpaceMD = 12.dp
private val SpaceLG = 16.dp
private val SpaceXL = 24.dp
private val SpaceXXL = 32.dp

private val RadiusSM = 8.dp
private val RadiusMD = 12.dp
private val RadiusLG = 16.dp
private val RadiusXL = 24.dp
private val RadiusFull = 100.dp

private val MinTouchTarget = 48.dp

// Component Animation Helper
@Composable
private fun Modifier.pressClickEffect(): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.97f else 1f, label = "pressScale", animationSpec = tween(150))
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

// Color constants
private val GoogleBlue = Color(0xFF4285F4)
private val GoogleGreen = Color(0xFF34A853)
private val GoogleRed = Color(0xFFEA4335)
private val GoogleYellow = Color(0xFFFBBC05)
private val CopyTeal = Color(0xFF00897B)

/**
 * Read-only task detail bottom sheet with professional UI.
 * Shows all task information in a clean, organized layout.
 * Use TaskDetailSheet for editing.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskDetailBottomSheet(
    task: Task,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: () -> Unit,
    onStartPomodoro: (() -> Unit)? = null,
    subtasks: List<Task> = emptyList(),
    onAddSubtask: ((String) -> Unit)? = null,
    onToggleSubtask: ((String, Boolean) -> Unit)? = null,
    onDeleteSubtask: ((String) -> Unit)? = null,
    onCompleteAllSubtasks: (() -> Unit)? = null,
    onSnooze: ((Long) -> Unit)? = null,
    onUnsnooze: (() -> Unit)? = null,
    onCopyToToday: (() -> Unit)? = null,
    onAddReminder: ((com.theblankstate.preamble.data.Reminder) -> Unit)? = null,
    onRemoveReminder: ((Int) -> Unit)? = null,
    onToggleRollover: (() -> Unit)? = null,
    isPastTask: Boolean = false
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = SpaceMD)
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
                .animateContentSize(animationSpec = tween(250))
                .padding(horizontal = SpaceXL)
                .padding(bottom = SpaceXXL)
        ) {
            // ═══════════════════════════════════════════════════════════════
            // HEADER: Title + Action Buttons
            // ═══════════════════════════════════════════════════════════════
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Title
                Column(modifier = Modifier.weight(1f)) {
                    val cleanTitle = task.title.removePrefix("📅 ").trim()
                    Text(
                        text = cleanTitle,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
                        ),
                        color = if (task.isCompleted)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        else
                            MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Full title in small text — always visible when headline truncates
                    if (cleanTitle.length > 35) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = cleanTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Action buttons
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Pomodoro timer (for non-info-only tasks)
                    if (!task.isInfoOnly && onStartPomodoro != null) {
                        IconButton(onClick = onStartPomodoro) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = "Start Focus Timer",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Copy to Today button
                    if (isPastTask && onCopyToToday != null && !task.isInfoOnly) {
                        IconButton(onClick = {
                            onCopyToToday()
                            onDismiss()
                        }) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy to Today",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ═══════════════════════════════════════════════════════════════
            // SOURCE BADGE
            // ═══════════════════════════════════════════════════════════════
            SourceBadge(task)



            // ═══════════════════════════════════════════════════════════════
            // TAGS & PRIORITY
            // ═══════════════════════════════════════════════════════════════
            if (task.priority > 0 || task.tagList.isNotEmpty() || task.eventType != null && task.eventType != "default") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    // Priority chip
                    if (task.priority > 0) {
                        PriorityChip(task.priority)
                    }

                    // Event type chip (holiday, birthday, etc.)
                    task.eventType?.let { eventType ->
                        if (eventType != "default") {
                            EventTypeChip(eventType)
                        }
                    }

                    // Tags
                    task.tagList.forEach { tag ->
                        DetailTag(text = tag, color = PredefinedTags.colorForTag(tag))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // ═══════════════════════════════════════════════════════════════
            // DATE & TIME
            // ═══════════════════════════════════════════════════════════════
            DetailSection(title = null) {
                // Date
                DetailRow(
                    icon = Icons.Default.CalendarToday,
                    iconColor = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = formatDisplayDate(task.createdDate),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Time (if not all-day)
                if (task.deadlineTime != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailRow(
                        icon = Icons.Default.AccessTime,
                        iconColor = MaterialTheme.colorScheme.secondary
                    ) {
                        val timeText = buildString {
                            append(task.deadlineTime)
                            if (task.endTime != null) {
                                append(" - ${task.endTime}")
                            }
                        }
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }


            }

            // ═══════════════════════════════════════════════════════════════
            // DESCRIPTION
            // ═══════════════════════════════════════════════════════════════
            if (!task.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                DetailSection(title = "Description") {
                    Text(
                        text = task.description!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // SUBTASKS (App-native only)
            // ═══════════════════════════════════════════════════════════════
            Spacer(modifier = Modifier.height(16.dp))
            SubtaskSection(
                parentTask = task,
                subtasks = subtasks,
                onAddSubtask = { title -> onAddSubtask?.invoke(title) },
                onToggleSubtask = { subtaskId, isCompleted -> onToggleSubtask?.invoke(subtaskId, isCompleted) },
                onDeleteSubtask = { subtaskId -> onDeleteSubtask?.invoke(subtaskId) },
                onCompleteAllSubtasks = { onCompleteAllSubtasks?.invoke() }
            )

            // ═══════════════════════════════════════════════════════════════
            // LOCATION WITH MAP PREVIEW
            // ═══════════════════════════════════════════════════════════════
            if (!task.location.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                DetailSection(title = "Location") {
                    LocationMapPreview(
                        location = task.location!!,
                        onClick = {
                            val gmmIntentUri = Uri.parse("geo:0,0?q=${Uri.encode(task.location)}")
                            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                                setPackage("com.google.android.apps.maps")
                            }
                            try {
                                context.startActivity(mapIntent)
                            } catch (e: Exception) {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW,
                                        Uri.parse("https://maps.google.com/?q=${Uri.encode(task.location)}"))
                                )
                            }
                        }
                    )
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // MEETING LINKS
            // ═══════════════════════════════════════════════════════════════
            if (!task.meetingLink.isNullOrBlank() || !task.conferencePhone.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                DetailSection(title = "Meeting") {
                    // Video meeting link
                    if (!task.meetingLink.isNullOrBlank()) {
                        Surface(
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(task.meetingLink)))
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = GoogleBlue.copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Videocam,
                                    contentDescription = null,
                                    tint = GoogleBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Join Video Meeting",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = GoogleBlue
                                    )
                                    Text(
                                        text = task.meetingLink!!.removePrefix("https://").take(40) + "...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    tint = GoogleBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Phone dial-in
                    if (!task.conferencePhone.isNullOrBlank()) {
                        if (!task.meetingLink.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        DetailRow(
                            icon = Icons.Default.Phone,
                            iconColor = GoogleGreen
                        ) {
                            Text(
                                text = task.conferencePhone!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // ATTENDEES
            // ═══════════════════════════════════════════════════════════════
            if (task.hasAttendees) {
                Spacer(modifier = Modifier.height(16.dp))
                val attendees = parseAttendees(task.attendeesJson)
                DetailSection(title = "Attendees (${attendees.size})") {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        attendees.forEach { attendee ->
                            AttendeeChip(attendee)
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // REMINDERS (interactive)
            // ═══════════════════════════════════════════════════════════════
            if (task.hasReminders || (onAddReminder != null && !task.isInfoOnly)) {
                Spacer(modifier = Modifier.height(16.dp))
                val reminders = task.localReminders
                var showAddReminderMenu by remember { mutableStateOf(false) }
                var showCustomTimePicker by remember { mutableStateOf(false) }

                DetailSection(title = "Reminders") {
                    // Existing reminders with delete button
                    reminders.forEachIndexed { index, reminder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = GoogleYellow
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = reminder.displayText(),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            if (onRemoveReminder != null && !isPastTask) {
                                IconButton(
                                    onClick = { onRemoveReminder(index) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remove reminder",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }

                    // Add reminder button (max 5)
                    if (onAddReminder != null && !isPastTask && reminders.size < com.theblankstate.preamble.data.Reminder.MAX_REMINDERS) {
                        Spacer(modifier = Modifier.height(8.dp))

                        if (showAddReminderMenu) {
                            // Preset options
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        "Add Reminder",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    val now = System.currentTimeMillis()
                                    val hasDeadline = task.deadlineTime != null

                                    if (hasDeadline) {
                                        // ── TASKS WITH DEADLINE ──
                                        val deadlineEpoch = remember(task.createdDate, task.deadlineTime) {
                                            try {
                                                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                                                sdf.parse("${task.createdDate} ${task.deadlineTime}")?.time
                                            } catch (_: Exception) { null }
                                        }
                                        val allBeforePresetsExpired = deadlineEpoch != null && (deadlineEpoch - 5 * 60 * 1000L) <= now

                                        if (!allBeforePresetsExpired) {
                                            // Normal mode: show "X min before" presets, filtering out expired ones
                                            val beforePresets = listOf(
                                                "5 min before" to 5,
                                                "10 min before" to 10,
                                                "15 min before" to 15,
                                                "30 min before" to 30,
                                                "1 hour before" to 60,
                                                "1 day before" to 1440
                                            ).filter { (_, minutes) ->
                                                deadlineEpoch == null || (deadlineEpoch - minutes * 60 * 1000L) > now
                                            }
                                            for (row in beforePresets.chunked(2)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    for ((label, minutes) in row) {
                                                        val alreadyExists = reminders.any { it.minutesBefore == minutes && it.type == "before" }
                                                        Surface(
                                                            shape = RoundedCornerShape(8.dp),
                                                            color = if (alreadyExists)
                                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                                            else
                                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .padding(vertical = 3.dp)
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .clickable(enabled = !alreadyExists) {
                                                                    onAddReminder(com.theblankstate.preamble.data.Reminder(minutesBefore = minutes, type = "before"))
                                                                    showAddReminderMenu = false
                                                                }
                                                        ) {
                                                            Text(
                                                                text = if (alreadyExists) "✓ $label" else label,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                                color = if (alreadyExists)
                                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                                else
                                                                    MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                    }
                                                    if (row.size == 1) {
                                                        Spacer(modifier = Modifier.weight(1f))
                                                    }
                                                }
                                            }
                                        } else {
                                            // Overdue mode: deadline has passed, show "from now" alternatives
                                            Text(
                                                "Deadline passed — remind from now",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(bottom = 6.dp)
                                            )
                                            val fromNowPresets = listOf(
                                                "In 5 min" to 5L,
                                                "In 15 min" to 15L,
                                                "In 30 min" to 30L,
                                                "In 1 hour" to 60L
                                            )
                                            for (row in fromNowPresets.chunked(2)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    for ((label, minutes) in row) {
                                                        Surface(
                                                            shape = RoundedCornerShape(RadiusMD),
                                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .padding(vertical = SpaceXS)
                                                                .clip(RoundedCornerShape(RadiusMD))
                                                                .clickable {
                                                                    val triggerMs = System.currentTimeMillis() + minutes * 60 * 1000L
                                                                    onAddReminder(com.theblankstate.preamble.data.Reminder(epochMs = triggerMs, type = "exact"))
                                                                    showAddReminderMenu = false
                                                                }
                                                                .pressClickEffect()
                                                        ) {
                                                            Box(
                                                                modifier = Modifier.height(MinTouchTarget),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text(
                                                                    text = label,
                                                                    style = MaterialTheme.typography.labelMedium,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    color = MaterialTheme.colorScheme.primary
                                                                )
                                                            }
                                                        }
                                                    }
                                                    if (row.size == 1) {
                                                        Spacer(modifier = Modifier.weight(1f))
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        // ── ALL-DAY TASKS (no deadline) ──
                                        // Show absolute time presets like industry apps (Todoist, Microsoft To Do, TickTick)
                                        Text(
                                            "All-day task — pick a reminder time",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                        // Build smart presets based on current time of day
                                        val cal = java.util.Calendar.getInstance()
                                        val currentHour = cal.get(java.util.Calendar.HOUR_OF_DAY)
                                        val allDayPresets = remember(currentHour) {
                                            val presets = mutableListOf<Pair<String, Long>>()
                                            val c = java.util.Calendar.getInstance()

                                            // "In 1 hour" — always useful
                                            presets.add("In 1 hour" to (System.currentTimeMillis() + 60 * 60 * 1000L))

                                            // "In 3 hours" — always useful
                                            presets.add("In 3 hours" to (System.currentTimeMillis() + 3 * 60 * 60 * 1000L))

                                            // "Today 6 PM" — only if before 6 PM
                                            if (currentHour < 18) {
                                                c.timeInMillis = System.currentTimeMillis()
                                                c.set(java.util.Calendar.HOUR_OF_DAY, 18)
                                                c.set(java.util.Calendar.MINUTE, 0)
                                                c.set(java.util.Calendar.SECOND, 0)
                                                c.set(java.util.Calendar.MILLISECOND, 0)
                                                if (c.timeInMillis > System.currentTimeMillis()) {
                                                    presets.add("Today 6 PM" to c.timeInMillis)
                                                }
                                            }

                                            // "Tomorrow 9 AM" — always useful
                                            c.timeInMillis = System.currentTimeMillis()
                                            c.add(java.util.Calendar.DAY_OF_YEAR, 1)
                                            c.set(java.util.Calendar.HOUR_OF_DAY, 9)
                                            c.set(java.util.Calendar.MINUTE, 0)
                                            c.set(java.util.Calendar.SECOND, 0)
                                            c.set(java.util.Calendar.MILLISECOND, 0)
                                            presets.add("Tomorrow 9 AM" to c.timeInMillis)

                                            presets.toList()
                                        }
                                        for (row in allDayPresets.chunked(2)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                for ((label, epochMs) in row) {
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .padding(vertical = 3.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .clickable {
                                                                onAddReminder(com.theblankstate.preamble.data.Reminder(epochMs = epochMs, type = "exact"))
                                                                showAddReminderMenu = false
                                                            }
                                                    ) {
                                                        Text(
                                                            text = label,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                                if (row.size == 1) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }

                                    // Custom exact time option
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { showCustomTimePicker = true }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Schedule,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.tertiary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                "Custom date & time",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.tertiary
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // "+ Add Reminder" chip
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { showAddReminderMenu = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Notifications,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "+ Add Reminder",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        // Custom time picker dialog
                        if (showCustomTimePicker) {
                            val ctx = LocalContext.current
                            LaunchedEffect(Unit) {
                                // Date picker first, then time picker
                                val now = java.util.Calendar.getInstance()
                                android.app.DatePickerDialog(
                                    ctx,
                                    { _, year, month, day ->
                                        // Now show time picker
                                        android.app.TimePickerDialog(
                                            ctx,
                                            { _, hour, minute ->
                                                val cal = java.util.Calendar.getInstance().apply {
                                                    set(year, month, day, hour, minute, 0)
                                                    set(java.util.Calendar.MILLISECOND, 0)
                                                }
                                                onAddReminder(
                                                    com.theblankstate.preamble.data.Reminder(
                                                        epochMs = cal.timeInMillis,
                                                        type = "exact"
                                                    )
                                                )
                                                showCustomTimePicker = false
                                                showAddReminderMenu = false
                                            },
                                            now.get(java.util.Calendar.HOUR_OF_DAY),
                                            now.get(java.util.Calendar.MINUTE),
                                            false
                                        ).apply {
                                            setOnDismissListener { showCustomTimePicker = false }
                                            show()
                                        }
                                    },
                                    now.get(java.util.Calendar.YEAR),
                                    now.get(java.util.Calendar.MONTH),
                                    now.get(java.util.Calendar.DAY_OF_MONTH)
                                ).apply {
                                    setOnDismissListener { showCustomTimePicker = false }
                                    show()
                                }
                            }
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // ROLLOVER TOGGLE — flip rollover on/off after create
            // ═══════════════════════════════════════════════════════════════
            run {
                val canToggle = onToggleRollover != null &&
                        !task.isCompleted &&
                        !isPastTask &&
                        !task.isRecurrenceTemplate &&
                        task.recurrenceParentId == null &&
                        (task.recurrenceType == null || task.recurrenceType == "rollover") &&
                        task.source == "local"
                if (canToggle) {
                    val isRollover = task.recurrenceType == "rollover"
                    Spacer(modifier = Modifier.height(12.dp))
                    SuggestionChip(
                        onClick = { onToggleRollover?.invoke() },
                        label = {
                            Text(
                                if (isRollover) "Rolling over — tap to make one-time"
                                else "One-time — tap to roll over daily",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // RECURRENCE & PROGRESS
            // ═══════════════════════════════════════════════════════════════
            if ((task.googleRecurrenceInfo != null && !task.isRecurrenceTemplate) ||
                task.isRecurrenceTemplate || task.recurrenceParentId != null ||
                task.recurrenceType == "rollover") {
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Recurrence — rich card for local recurring tasks, simple row for Google
                if (task.googleRecurrenceInfo != null && !task.isRecurrenceTemplate) {
                    // Google Calendar recurrence: show as simple row
                    DetailRow(
                        icon = Icons.Default.Repeat,
                        iconColor = MaterialTheme.colorScheme.tertiary
                    ) {
                        Text(
                            text = task.googleRecurrenceInfo!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (task.isRecurrenceTemplate || task.recurrenceParentId != null) {
                    // Local recurring task (template or instance) — show rich card
                    RecurrenceCard(task = task, startCollapsed = task.recurrenceType == "rollover")
                }

                // Rollover info + snooze
                if (task.recurrenceType == "rollover") {
                    val daysRolledOver = remember(task.createdDate, task.isCompleted, task.completedDate) {
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
                    }
                    if (task.isRecurrenceTemplate || task.recurrenceParentId != null || task.googleRecurrenceInfo != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    RolloverStuckCard(task = task, daysRolledOver = daysRolledOver ?: 0)
                    
                    // Snooze / Unsnooze section
                    val isSnoozed = task.snoozedUntil != null && task.snoozedUntil > System.currentTimeMillis()
                    if (!task.isCompleted && (onSnooze != null || (isSnoozed && onUnsnooze != null))) {
                        Spacer(modifier = Modifier.height(8.dp))
                        if (isSnoozed && onUnsnooze != null) {
                            // Already snoozed — show unsnooze option
                            val snoozedUntilText = remember(task.snoozedUntil) {
                                val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                                sdf.format(java.util.Date(task.snoozedUntil!!))
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFFFF3E0),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Schedule,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                        Spacer(modifier = Modifier.width(SpaceSM))
                                        Text(
                                            "Snoozed until $snoozedUntilText",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(RadiusMD),
                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(RadiusMD))
                                            .clickable {
                                                onUnsnooze()
                                                onDismiss()
                                            }
                                            .pressClickEffect()
                                    ) {
                                        Row(
                                            modifier = Modifier.height(MinTouchTarget),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically,
                                            content = {
                                                Icon(
                                                    Icons.Default.Notifications,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp),
                                                    tint = MaterialTheme.colorScheme.secondary
                                                )
                                                Spacer(modifier = Modifier.width(SpaceSM))
                                                Text(
                                                    "Unsnooze Now",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        } else if (onSnooze != null) {
                            // Not snoozed — show snooze options
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.AccessTime,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "Snooze this task",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(
                                            Triple("1 Hour", "1h", 1 * 60 * 60 * 1000L),
                                            Triple("4 Hours", "4h", 4 * 60 * 60 * 1000L),
                                            Triple("Tomorrow", "1d", 24 * 60 * 60 * 1000L)
                                        ).forEach { (label, _, duration) ->
                                            Surface(
                                                shape = RoundedCornerShape(RadiusMD),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(RadiusMD))
                                                    .clickable {
                                                        onSnooze(duration)
                                                        onDismiss()
                                                    }
                                                    .pressClickEffect()
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier.defaultMinSize(minHeight = MinTouchTarget),
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Icon(
                                                        Icons.Default.AccessTime,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(modifier = Modifier.height(SpaceXS))
                                                    Text(
                                                        text = label,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // ATTACHMENTS
            // ═══════════════════════════════════════════════════════════════
            if (task.hasAttachments) {
                Spacer(modifier = Modifier.height(16.dp))
                val attachments = parseAttachments(task.attachmentsJson)
                DetailSection(title = "Attachments (${attachments.size})") {
                    attachments.forEach { attachment ->
                        Surface(
                            onClick = {
                                attachment["fileUrl"]?.let { url ->
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Attachment,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = attachment["title"] ?: "Attachment",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // LINKS (Open in Google)
            // ═══════════════════════════════════════════════════════════════
            if (!task.htmlLink.isNullOrBlank() || !task.webViewLink.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                DetailSection(title = "Open In") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Google Calendar link
                        if (!task.htmlLink.isNullOrBlank()) {
                            Surface(
                                onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(task.htmlLink)))
                                },
                                shape = RoundedCornerShape(20.dp),
                                color = GoogleBlue.copy(alpha = 0.1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Event,
                                        contentDescription = null,
                                        tint = GoogleBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Google Calendar",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = GoogleBlue
                                    )
                                }
                            }
                        }

                        // Google Tasks link
                        if (!task.webViewLink.isNullOrBlank()) {
                            Surface(
                                onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(task.webViewLink)))
                                },
                                shape = RoundedCornerShape(20.dp),
                                color = GoogleGreen.copy(alpha = 0.1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Assignment,
                                        contentDescription = null,
                                        tint = GoogleGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Google Tasks",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = GoogleGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // METADATA
            // ═══════════════════════════════════════════════════════════════
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Completed status
                if (task.isCompleted && task.completedTimestamp != null) {
                    MetadataText(
                        icon = Icons.Default.TaskAlt,
                        text = "Completed ${formatTimestamp(task.completedTimestamp)}"
                    )
                }

                // Organizer
                task.organizerJson?.let { json ->
                    parseOrganizer(json)?.let { organizer ->
                        val name = organizer["displayName"]?.takeIf { it.isNotBlank() } ?: organizer["email"]
                        if (!name.isNullOrBlank()) {
                            MetadataText(
                                icon = Icons.Default.Person,
                                text = "Organized by $name"
                            )
                        }
                    }
                }

                // Visibility
                if (!task.visibility.isNullOrBlank() && task.visibility != "default") {
                    MetadataText(
                        icon = when (task.visibility) {
                            "private", "confidential" -> Icons.Default.Lock
                            else -> Icons.Default.Public
                        },
                        text = task.visibility!!.replaceFirstChar { it.uppercase() }
                    )
                }

                // Calendar name
                if (!task.calendarName.isNullOrBlank()) {
                    MetadataText(
                        icon = Icons.Default.Event,
                        text = "From: ${task.calendarName}"
                    )
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // ACTIONS (Edit / Delete)
            // ═══════════════════════════════════════════════════════════════
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SpaceMD)
            ) {
                if (!task.isInfoOnly && onEdit != null) {
                    androidx.compose.material3.Button(
                        onClick = { onEdit() },
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = MinTouchTarget),
                        shape = RoundedCornerShape(RadiusFull),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(SpaceSM))
                        Text("Edit", fontWeight = FontWeight.SemiBold)
                    }
                }

                androidx.compose.material3.Button(
                    onClick = { onDelete() },
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = MinTouchTarget),
                    shape = RoundedCornerShape(RadiusFull),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(SpaceSM))
                    Text("Delete", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(SpaceLG))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Helper Composables
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun SourceBadge(task: Task) {
    if (!task.isCalendarEvent && !task.isGoogleTask) return

    val (icon, text, color) = when {
        task.isCalendarEvent -> Triple(
            Icons.Default.Event,
            task.calendarName ?: "Google Calendar",
            GoogleBlue
        )
        else -> Triple(
            Icons.AutoMirrored.Filled.Assignment,
            "Google Tasks",
            GoogleGreen
        )
    }

    Surface(
        shape = RoundedCornerShape(RadiusMD),
        color = color.copy(alpha = 0.12f),
        modifier = Modifier.padding(bottom = 12.dp)
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
private fun LocationMapPreview(
    location: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // Decorative map-like grid lines
            val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
            val roadColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
            
            androidx.compose.foundation.Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                // Horizontal lines
                for (i in 0..6) {
                    val y = (size.height / 7) * i
                    drawLine(
                        color = gridColor,
                        start = androidx.compose.ui.geometry.Offset(0f, y),
                        end = androidx.compose.ui.geometry.Offset(size.width, y),
                        strokeWidth = 1f
                    )
                }
                // Vertical lines
                for (i in 0..8) {
                    val x = (size.width / 9) * i
                    drawLine(
                        color = gridColor,
                        start = androidx.compose.ui.geometry.Offset(x, 0f),
                        end = androidx.compose.ui.geometry.Offset(x, size.height),
                        strokeWidth = 1f
                    )
                }
                // Diagonal "road" line
                drawLine(
                    color = roadColor,
                    start = androidx.compose.ui.geometry.Offset(0f, size.height * 0.7f),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height * 0.3f),
                    strokeWidth = 12f
                )
            }

            // Location pin in center
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Pin shadow
                    Box(
                        modifier = Modifier
                            .size(20.dp, 6.dp)
                            .offset(y = 36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.2f))
                    )
                    // Pin icon
                    Surface(
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                        color = GoogleRed,
                        shadowElevation = 4.dp
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp, 48.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                        }
                    }
                }
            }

            val surfaceColor = MaterialTheme.colorScheme.surface
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                surfaceColor.copy(alpha = 0.9f),
                                surfaceColor
                            )
                        )
                    )
                    .padding(SpaceMD)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = GoogleRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(SpaceSM))
                    Text(
                        text = location,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open in Maps",
                        tint = GoogleBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PriorityChip(priority: Int) {
    val (text, color) = when (priority) {
        3 -> "High Priority" to MaterialTheme.colorScheme.error
        2 -> "Medium Priority" to MaterialTheme.colorScheme.secondary
        1 -> "Low Priority" to MaterialTheme.colorScheme.tertiary
        else -> return
    }

    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = SpaceMD, vertical = SpaceSM),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Flag,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = color
            )
            Spacer(modifier = Modifier.width(SpaceXS))
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
private fun EventTypeChip(eventType: String) {
    val (text, icon, color) = when (eventType) {
        "holiday" -> Triple("Holiday", Icons.Default.WbSunny, MaterialTheme.colorScheme.secondary)
        "birthday" -> Triple("Birthday", Icons.Default.Cake, MaterialTheme.colorScheme.tertiary)
        "focusTime" -> Triple("Focus Time", Icons.Default.Timer, MaterialTheme.colorScheme.primary)
        "outOfOffice" -> Triple("Out of Office", Icons.Default.Event, MaterialTheme.colorScheme.error)
        else -> return
    }

    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = SpaceMD, vertical = SpaceSM),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = color
            )
            Spacer(modifier = Modifier.width(SpaceXS))
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
private fun DetailTag(text: String, color: Color) {
    SuggestionChip(
        onClick = { },
        label = {
            Text(
                text = text,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        },
        shape = CircleShape,
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = color.copy(alpha = 0.12f)
        ),
        border = null
    )
}

@Composable
private fun DetailSection(
    title: String?,
    content: @Composable () -> Unit
) {
    Column {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        content()
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = iconColor
        )
        Spacer(modifier = Modifier.width(12.dp))
        content()
    }
}

@Composable
private fun AttendeeChip(attendee: Map<String, Any>) {
    val email = attendee["email"] as? String ?: ""
    val displayName = attendee["displayName"] as? String
    val responseStatus = attendee["responseStatus"] as? String ?: "needsAction"
    val isOrganizer = attendee["organizer"] as? Boolean ?: false

    val statusColor = when (responseStatus) {
        "accepted" -> GoogleGreen
        "declined" -> GoogleRed
        "tentative" -> GoogleYellow
        else -> Color.Gray
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = displayName?.takeIf { it.isNotBlank() } ?: email.substringBefore("@"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isOrganizer) FontWeight.Bold else FontWeight.Normal
                )
                if (isOrganizer) {
                    Text(
                        text = "Organizer",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataText(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Helper Functions
// ═══════════════════════════════════════════════════════════════════════════

private fun formatDisplayDate(dateStr: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val outputFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        val date = inputFormat.parse(dateStr)
        date?.let { outputFormat.format(it) } ?: dateStr
    } catch (e: Exception) {
        dateStr
    }
}

private fun formatTimestamp(timestamp: Long): String {
    return try {
        val format = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
        format.format(Date(timestamp))
    } catch (e: Exception) {
        ""
    }
}

private fun formatReminderTime(reminder: Map<String, Any>): String {
    val minutes = (reminder["minutes"] as? Number)?.toInt() ?: 10
    val method = reminder["method"] as? String ?: "notification"

    val timeText = when {
        minutes < 60 -> "$minutes minutes before"
        minutes < 24 * 60 -> "${minutes / 60} hour${if (minutes / 60 > 1) "s" else ""} before"
        else -> "${minutes / (24 * 60)} day${if (minutes / (24 * 60) > 1) "s" else ""} before"
    }

    return "$timeText (${method.replaceFirstChar { it.uppercase() }})"
}

private fun parseAttendees(json: String?): List<Map<String, Any>> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
        Gson().fromJson(json, type)
    } catch (e: Exception) {
        emptyList()
    }
}

private fun parseReminders(json: String?): List<Map<String, Any>> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
        Gson().fromJson(json, type)
    } catch (e: Exception) {
        emptyList()
    }
}

private fun parseAttachments(json: String?): List<Map<String, String>> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val type = object : TypeToken<List<Map<String, String>>>() {}.type
        Gson().fromJson(json, type)
    } catch (e: Exception) {
        emptyList()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Rollover "Stuck Task" Card — visual timeline showing how long task is pending
// ─────────────────────────────────────────────────────────────────────────────

private data class RolloverTier(
    val bgColor: androidx.compose.ui.graphics.Color,
    val accentColor: androidx.compose.ui.graphics.Color,
    val emoji: String,
    val headline: String,
    val subline: String
)

@Composable
private fun RolloverStuckCard(task: Task, daysRolledOver: Int) {
    // Urgency tiers mapped to semantic Material 3 colors adapting to dark mode
    val tier = when {
        task.isCompleted -> RolloverTier(
            bgColor = MaterialTheme.colorScheme.tertiaryContainer,
            accentColor = MaterialTheme.colorScheme.tertiary,
            emoji = "✅",
            headline = "Finally done!",
            subline = if (daysRolledOver > 0) "Completed after $daysRolledOver day${if (daysRolledOver > 1) "s" else ""}" else "Completed today"
        )
        daysRolledOver >= 7 -> RolloverTier(
            bgColor = MaterialTheme.colorScheme.errorContainer,
            accentColor = MaterialTheme.colorScheme.error,
            emoji = "🔥",
            headline = "On fire — $daysRolledOver days!",
            subline = "Waiting since ${formatDaysAgoDate(task.createdDate)}"
        )
        daysRolledOver >= 3 -> RolloverTier(
            bgColor = MaterialTheme.colorScheme.surfaceVariant,
            accentColor = Color(0xFFE65100), // Fallback semantic tint
            emoji = "⚠️",
            headline = "Getting overdue",
            subline = "Carrying over for $daysRolledOver day${if (daysRolledOver > 1) "s" else ""} now"
        )
        daysRolledOver >= 1 -> RolloverTier(
            bgColor = MaterialTheme.colorScheme.secondaryContainer,
            accentColor = MaterialTheme.colorScheme.secondary,
            emoji = "⏳",
            headline = "Moved from yesterday",
            subline = "Rolled over $daysRolledOver day${if (daysRolledOver > 1) "s" else ""} ago"
        )
        else -> RolloverTier(
            bgColor = MaterialTheme.colorScheme.primaryContainer,
            accentColor = MaterialTheme.colorScheme.primary,
            emoji = "📌",
            headline = "Active task",
            subline = "Created today — keep going!"
        )
    }

    Surface(
        shape = RoundedCornerShape(RadiusLG),
        color = tier.bgColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(SpaceLG)) {
            // Top row: emoji badge + headline
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(RadiusMD),
                    color = tier.accentColor.copy(alpha = 0.14f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(text = tier.emoji, style = MaterialTheme.typography.titleMedium)
                    }
                }
                Spacer(modifier = Modifier.width(SpaceMD))
                Column {
                    Text(
                        text = tier.headline,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = tier.accentColor
                    )
                    Text(
                        text = tier.subline,
                        style = MaterialTheme.typography.bodySmall,
                        color = tier.accentColor.copy(alpha = 0.75f)
                    )
                }
            }

            // Pending progress bar — shown when task has been pending 1+ days (or completed late)
            if (daysRolledOver >= 1) {
                Spacer(modifier = Modifier.height(12.dp))

                // Cap the scale at 14 days for a full bar; beyond 14 the bar just stays full
                val scaleMax = daysRolledOver.coerceAtLeast(14)
                val rawProgress = daysRolledOver.toFloat() / scaleMax

                var targetProgress by remember { mutableFloatStateOf(0f) }
                LaunchedEffect(daysRolledOver) { targetProgress = rawProgress }
                val animatedProgress by animateFloatAsState(
                    targetValue = targetProgress,
                    animationSpec = tween(durationMillis = 700),
                    label = "rolloverProgress"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.HourglassBottom,
                            contentDescription = null,
                            tint = tier.accentColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Pending duration",
                            style = MaterialTheme.typography.labelSmall,
                            color = tier.accentColor.copy(alpha = 0.7f)
                        )
                    }
                    Text(
                        text = "$daysRolledOver day${if (daysRolledOver > 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = tier.accentColor
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Canvas(
                    modifier = Modifier.fillMaxWidth().height(8.dp)
                ) {
                    val segmentCount = 14
                    val gap = 3.dp.toPx()
                    val segW = (size.width - gap * (segmentCount - 1)) / segmentCount
                    val filledSegs = (segmentCount * animatedProgress).toInt()
                    for (i in 0 until segmentCount) {
                        val x = i * (segW + gap)
                        drawRoundRect(
                            color = if (i < filledSegs) tier.accentColor else tier.accentColor.copy(alpha = 0.15f),
                            topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                            size = androidx.compose.ui.geometry.Size(segW, size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Day 1",
                        style = MaterialTheme.typography.labelSmall,
                        color = tier.accentColor.copy(alpha = 0.45f)
                    )
                    Text(
                        text = if (task.isCompleted) "Completed" else "Today",
                        style = MaterialTheme.typography.labelSmall,
                        color = tier.accentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private fun formatDaysAgoDate(dateStr: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val display = SimpleDateFormat("d MMM", Locale.getDefault())
        display.format(sdf.parse(dateStr)!!)
    } catch (_: Exception) { dateStr }
}

private fun parseOrganizer(json: String?): Map<String, String>? {
    if (json.isNullOrBlank()) return null
    return try {
        val type = object : TypeToken<Map<String, String>>() {}.type
        Gson().fromJson(json, type)
    } catch (e: Exception) {
        null
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Recurrence Card — rich visual block for local recurring tasks
// ─────────────────────────────────────────────────────────────────────────────

private fun formatRecurrencePattern(
    recurrenceType: String?,
    interval: Int?,
    days: String?
): String {
    val n = interval ?: 1
    return when (recurrenceType) {
        "daily" -> if (n == 1) "Every day" else "Every $n days"
        "weekly" -> {
            val dayNames = days?.split(",")?.mapNotNull { d ->
                when (d.trim()) {
                    "2" -> "Mon"; "3" -> "Tue"; "4" -> "Wed"
                    "5" -> "Thu"; "6" -> "Fri"; "7" -> "Sat"; "1" -> "Sun"
                    else -> null
                }
            }
            val dayStr = if (!dayNames.isNullOrEmpty()) " on ${dayNames.joinToString(", ")}" else ""
            if (n == 1) "Every week$dayStr" else "Every $n weeks$dayStr"
        }
        "monthly" -> if (n == 1) "Every month" else "Every $n months"
        "yearly" -> if (n == 1) "Every year" else "Every $n years"
        "custom" -> if (n == 1) "Custom repeat" else "Every $n days (custom)"
        else -> "Repeating"
    }
}

@Composable
private fun RecurrenceCard(task: Task, startCollapsed: Boolean = false) {
    var isExpanded by remember(task.id) { mutableStateOf(!startCollapsed) }

    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val displaySdf = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }

    // Compute progress: elapsed days from createdDate to today relative to endDate
    val progressData = remember(task.createdDate, task.recurrenceEndDate) {
        try {
            val start = sdf.parse(task.createdDate) ?: return@remember null
            val today = sdf.parse(sdf.format(Date())) ?: return@remember null
            val end = task.recurrenceEndDate?.let { sdf.parse(it) }
            val elapsed = ((today.time - start.time) / 86_400_000L).toInt().coerceAtLeast(0)
            if (end != null && end.after(start)) {
                val total = ((end.time - start.time) / 86_400_000L).toInt()
                Triple(elapsed, total, (elapsed.toFloat() / total).coerceIn(0f, 1f))
            } else {
                Triple(elapsed, -1, -1f) // no end date
            }
        } catch (_: Exception) { null }
    }

    val patternText = remember(task.recurrenceType, task.recurrenceInterval, task.recurrenceDays) {
        formatRecurrencePattern(task.recurrenceType, task.recurrenceInterval, task.recurrenceDays)
    }
    val startText = remember(task.createdDate) {
        try { displaySdf.format(sdf.parse(task.createdDate)!!) } catch (_: Exception) { task.createdDate }
    }
    val endText = remember(task.recurrenceEndDate) {
        task.recurrenceEndDate?.let {
            try { displaySdf.format(sdf.parse(it)!!) } catch (_: Exception) { it }
        }
    }

    // Accent color for the recurrence type
    val accentColor = when (task.recurrenceType) {
        "daily" -> Color(0xFF7C3AED)   // Violet
        "weekly" -> Color(0xFF0891B2)  // Cyan
        "monthly" -> Color(0xFF059669) // Emerald
        "yearly" -> Color(0xFFD97706)  // Amber
        else -> Color(0xFF6366F1)      // Indigo
    }

    // Animated progress for smooth appearance
    var targetProgress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(progressData) {
        targetProgress = progressData?.third?.coerceAtLeast(0f) ?: 0f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 800),
        label = "recurrenceProgress"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = accentColor.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row: icon + pattern label
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accentColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Default.EventRepeat,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Recurring Task",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = patternText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor
                    )
                }
                androidx.compose.material3.IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Toggle Expand",
                        tint = accentColor
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(visible = isExpanded) {
                Column {
                    Spacer(modifier = Modifier.height(14.dp))

            // Start & End date row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Start date pill
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accentColor.copy(alpha = 0.12f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "Started",
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor.copy(alpha = 0.7f)
                            )
                            Text(
                                text = startText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = accentColor
                            )
                        }
                    }
                }
                // End date pill (or Ongoing badge)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (endText != null) accentColor.copy(alpha = 0.12f)
                            else Color(0xFF10B981).copy(alpha = 0.12f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (endText != null) Icons.Default.Event else Icons.Default.Repeat,
                            contentDescription = null,
                            tint = if (endText != null) accentColor else Color(0xFF10B981),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = if (endText != null) "Ends" else "Status",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (endText != null) accentColor.copy(alpha = 0.7f)
                                        else Color(0xFF10B981).copy(alpha = 0.7f)
                            )
                            Text(
                                text = endText ?: "Ongoing",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (endText != null) accentColor else Color(0xFF10B981)
                            )
                        }
                    }
                }
            }

            // Progress bar (only if end date exists)
            if (progressData != null && progressData.third >= 0f) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Progress",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%  ·  ${progressData.first}/${progressData.second} days",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                // Segmented progress bar matching the app's WaveProgressBar aesthetic
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                ) {
                    val segmentCount = 20
                    val gap = 3.dp.toPx()
                    val segW = (size.width - gap * (segmentCount - 1)) / segmentCount
                    val filledSegs = (segmentCount * animatedProgress).toInt()
                    for (i in 0 until segmentCount) {
                        val x = i * (segW + gap)
                        drawRoundRect(
                            color = if (i < filledSegs) accentColor else accentColor.copy(alpha = 0.15f),
                            topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                            size = androidx.compose.ui.geometry.Size(segW, size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                        )
                    }
                }
            } else if (progressData != null) {
                // No end date — show elapsed days counter
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (progressData.first == 0) "Started today"
                               else "Running for ${progressData.first} day${if (progressData.first > 1) "s" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
                } // End inner animated Column
            } // End AnimatedVisibility
        }
    }
}
