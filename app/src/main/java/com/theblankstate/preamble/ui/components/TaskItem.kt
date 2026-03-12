package com.theblankstate.preamble.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.theblankstate.preamble.data.Task
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TaskItem(
    task: Task,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: (() -> Unit)? = null,
    isEditable: Boolean = true,
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

    val errorColor = MaterialTheme.colorScheme.error
    val errorContainerColor = MaterialTheme.colorScheme.errorContainer
    val onErrorContainerColor = MaterialTheme.colorScheme.onErrorContainer
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainerColor = MaterialTheme.colorScheme.onPrimaryContainer

    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(cardAlpha)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isOverdue) Modifier.background(errorContainerColor.copy(alpha = 0.3f))
                else Modifier
            )
            .clickable(enabled = isEditable) { onToggle() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { if (isEditable) onToggle() },
            modifier = Modifier.padding(end = 4.dp).size(24.dp),
            enabled = isEditable
        ) {
            Icon(
                imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                contentDescription = if (task.isCompleted) "Completed" else "Uncompleted",
                tint = if (task.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
            
            if (task.isCalendarEvent || task.isGoogleTask) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    val iconTint = Color(0xFF4285F4) // Standard Google Blue
                    
                    if (task.isCalendarEvent) {
                        Icon(
                            imageVector = Icons.Default.Event,
                            contentDescription = "Google Calendar Event",
                            modifier = Modifier.size(14.dp),
                            tint = iconTint
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Calendar",
                            style = MaterialTheme.typography.labelSmall,
                            color = iconTint
                        )
                    } else if (task.isGoogleTask) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Assignment,
                            contentDescription = "Google Task",
                            modifier = Modifier.size(14.dp),
                            tint = iconTint
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Google Tasks",
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
                    if (onEdit != null) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                showMenu = false
                                onEdit()
                            }
                        )
                    }
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
