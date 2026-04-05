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
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.theblankstate.preamble.BuildConfig
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
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
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

// Color constants
private val GoogleBlue = Color(0xFF4285F4)
private val GoogleGreen = Color(0xFF34A853)
private val GoogleRed = Color(0xFFEA4335)
private val GoogleYellow = Color(0xFFFBBC05)

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
    onCopyToToday: (() -> Unit)? = null,
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
            // HEADER: Title + Action Buttons
            // ═══════════════════════════════════════════════════════════════
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Title
                Text(
                    text = task.title.removePrefix("📅 ").trim(),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
                    ),
                    color = if (task.isCompleted)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

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

                    // Edit button (for non-info-only, non-past tasks)
                    if (!task.isInfoOnly && onEdit != null) {
                        IconButton(onClick = onEdit) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Copy to Today button (for past tasks)
                    if (isPastTask && onCopyToToday != null) {
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

                    // Delete button
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ═══════════════════════════════════════════════════════════════
            // SOURCE BADGE
            // ═══════════════════════════════════════════════════════════════
            SourceBadge(task)

            // ═══════════════════════════════════════════════════════════════
            // COPY TO TODAY (past tasks only)
            // ═══════════════════════════════════════════════════════════════
            if (isPastTask && onCopyToToday != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onCopyToToday()
                            onDismiss()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Copy to Today",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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

                // Recurrence
                if (task.googleRecurrenceInfo != null || task.isRecurrenceTemplate) {
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailRow(
                        icon = Icons.Default.Repeat,
                        iconColor = MaterialTheme.colorScheme.tertiary
                    ) {
                        Text(
                            text = task.googleRecurrenceInfo ?: "Repeating",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                    if (daysRolledOver != null && daysRolledOver > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Repeat,
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
                    // Snooze buttons
                    if (!task.isCompleted && onSnooze != null) {
                        Spacer(modifier = Modifier.height(8.dp))
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
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    onSnooze(duration)
                                                    onDismiss()
                                                }
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.AccessTime,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelSmall,
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
            // REMINDERS
            // ═══════════════════════════════════════════════════════════════
            if (task.hasReminders) {
                Spacer(modifier = Modifier.height(16.dp))
                val reminders = parseReminders(task.remindersJson)
                DetailSection(title = "Reminders") {
                    reminders.forEach { reminder ->
                        DetailRow(
                            icon = Icons.Default.Notifications,
                            iconColor = GoogleYellow
                        ) {
                            Text(
                                text = formatReminderTime(reminder),
                                style = MaterialTheme.typography.bodyMedium
                            )
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

            // ═══════════════════════════════════════════════════════════════
            // BANNER ADS
            // ═══════════════════════════════════════════════════════════════
            Spacer(modifier = Modifier.height(24.dp))

            // Ad lifecycle management
            val bannerAdView1 = remember { mutableStateOf<AdView?>(null) }
            val bannerAdView2 = remember { mutableStateOf<AdView?>(null) }
            val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

            androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    when (event) {
                        androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                            bannerAdView1.value?.pause()
                            bannerAdView2.value?.pause()
                        }
                        androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                            bannerAdView1.value?.resume()
                            bannerAdView2.value?.resume()
                        }
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    bannerAdView1.value?.destroy()
                    bannerAdView1.value = null
                    bannerAdView2.value?.destroy()
                    bannerAdView2.value = null
                }
            }

            val adShape = RoundedCornerShape(12.dp)
            val primaryColor = MaterialTheme.colorScheme.primary
            val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
            val surface = MaterialTheme.colorScheme.surface
            val borderColor = primaryColor.copy(alpha = 0.15f)

            // Banner Ad 1
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(adShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.05f),
                                surfaceVariant.copy(alpha = 0.3f)
                            )
                        )
                    )
                    .border(1.dp, borderColor, adShape)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        AdView(ctx).apply {
                            setAdSize(AdSize.MEDIUM_RECTANGLE)
                            adUnitId = BuildConfig.AD_DETAIL_UNIT_ID
                            loadAd(AdRequest.Builder().build())
                            bannerAdView1.value = this
                        }
                    },
                    onRelease = { adView -> adView.destroy() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Banner Ad 2
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(adShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.05f),
                                surfaceVariant.copy(alpha = 0.3f)
                            )
                        )
                    )
                    .border(1.dp, borderColor, adShape)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        AdView(ctx).apply {
                            setAdSize(AdSize.MEDIUM_RECTANGLE)
                            adUnitId = BuildConfig.AD_DETAIL_UNIT_ID
                            loadAd(AdRequest.Builder().build())
                            bannerAdView2.value = this
                        }
                    },
                    onRelease = { adView -> adView.destroy() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Helper Composables
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun SourceBadge(task: Task) {
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
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFE8F5E9),  // Light green top
                            Color(0xFFC8E6C9),  // Slightly darker green
                            Color(0xFFB2DFDB)   // Teal bottom
                        )
                    )
                )
        ) {
            // Decorative map-like grid lines
            androidx.compose.foundation.Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val gridColor = Color(0xFF81C784).copy(alpha = 0.4f)
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
                    color = Color(0xFFFFFFFF).copy(alpha = 0.6f),
                    start = androidx.compose.ui.geometry.Offset(0f, size.height * 0.7f),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height * 0.3f),
                    strokeWidth = 8f
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

            // Location text at bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.9f),
                                Color.White
                            )
                        )
                    )
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = GoogleRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = location,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1F1F1F),
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
        3 -> "High Priority" to Color(0xFFD32F2F)
        2 -> "Medium Priority" to Color(0xFFF57C00)
        1 -> "Low Priority" to Color(0xFF388E3C)
        else -> return
    }

    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Flag,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = color
            )
            Spacer(modifier = Modifier.width(4.dp))
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
        "holiday" -> Triple("Holiday", Icons.Default.WbSunny, Color(0xFF4CAF50))
        "birthday" -> Triple("Birthday", Icons.Default.Cake, Color(0xFFE91E63))
        "focusTime" -> Triple("Focus Time", Icons.Default.Timer, Color(0xFF9C27B0))
        "outOfOffice" -> Triple("Out of Office", Icons.Default.Event, Color(0xFF607D8B))
        else -> return
    }

    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = color
            )
            Spacer(modifier = Modifier.width(4.dp))
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

private fun parseOrganizer(json: String?): Map<String, String>? {
    if (json.isNullOrBlank()) return null
    return try {
        val type = object : TypeToken<Map<String, String>>() {}.type
        Gson().fromJson(json, type)
    } catch (e: Exception) {
        null
    }
}
