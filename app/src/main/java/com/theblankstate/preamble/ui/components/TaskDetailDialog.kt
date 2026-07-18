package com.theblankstate.preamble.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.theblankstate.preamble.data.Task

@Composable
fun TaskDetailDialog(
    task: Task,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header: Source & Type
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val (icon, sourceLabel) = when (task.source) {
                        "google_calendar" -> Icons.Default.Event to (task.calendarName ?: "Google Calendar")
                        "google_tasks" -> Icons.Default.CheckCircle to "Google Tasks"
                        else -> Icons.Default.TaskAlt to "Local Task"
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = sourceLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    if (task.isInfoOnly) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = task.eventType?.replaceFirstChar { it.uppercase() } ?: "Event",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                // Title
                Text(
                    text = task.title.removePrefix("📅 ").trim(),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Date & Time
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = "Date",
                        modifier = Modifier.size(20.dp).padding(top = 2.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        val dateString = if (task.createdDate.isNotBlank()) task.createdDate else "No Date"
                        val timeString = buildString {
                            if (!task.deadlineTime.isNullOrBlank()) append(task.deadlineTime)
                            if (!task.endTime.isNullOrBlank()) append(" - ${task.endTime}")
                            if (isEmpty()) append("All Day")
                        }
                        Text(text = dateString, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = timeString,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Description
                if (!task.description.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.Notes,
                            contentDescription = "Description",
                            modifier = Modifier.size(20.dp).padding(top = 2.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = task.description!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Location
                if (!task.location.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val gmmIntentUri = Uri.parse("geo:0,0?q=${Uri.encode(task.location)}")
                                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                                mapIntent.setPackage("com.google.android.apps.maps")
                                try {
                                    context.startActivity(mapIntent)
                                } catch (e: Exception) {
                                    // Fallback to browser
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=${Uri.encode(task.location)}")))
                                }
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Location",
                            modifier = Modifier.size(20.dp).padding(top = 2.dp),
                            tint = Color(0xFFEA4335)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = task.location!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textDecoration = TextDecoration.Underline
                        )
                    }
                }

                // Meeting Link
                if (!task.meetingLink.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(task.meetingLink))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // Handle missing browser
                                }
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Meeting Link",
                            modifier = Modifier.size(20.dp).padding(top = 2.dp),
                            tint = Color(0xFF1A73E8)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Join Video Meeting",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF1A73E8),
                            textDecoration = TextDecoration.Underline
                        )
                    }
                }
                // Info-only Notice
                if (task.isInfoOnly) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Info",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "This event type (${task.eventType ?: "event"}) cannot be deleted or modified via third-party apps per Google API rules. Please consider managing it directly in Google Calendar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                
                // Done Button
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}
