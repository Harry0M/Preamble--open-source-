package com.theblankstate.preamble.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theblankstate.preamble.data.Task

@Composable
fun EisenhowerGrid(
    tasks: List<Task>,
    onToggleTask: (Task) -> Unit,
    onUpdatePriority: (Task, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val pendingTasks = tasks.filter { !it.isCompleted }
    val q1 = pendingTasks.filter { it.priority == 3 } // Do First
    val q2 = pendingTasks.filter { it.priority == 2 } // Schedule
    val q3 = pendingTasks.filter { it.priority == 1 } // Delegate
    val q4 = pendingTasks.filter { it.priority == 0 } // Eliminate

    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
        // Top row: Q1 + Q2
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            EisenhowerQuadrant(
                title = "Do First",
                subtitle = "Urgent & Important",
                tasks = q1,
                color = MaterialTheme.colorScheme.errorContainer,
                textColor = MaterialTheme.colorScheme.onErrorContainer,
                priority = 3,
                onToggleTask = onToggleTask,
                onUpdatePriority = onUpdatePriority,
                modifier = Modifier.weight(1f).padding(end = 4.dp)
            )
            EisenhowerQuadrant(
                title = "Schedule",
                subtitle = "Important, Not Urgent",
                tasks = q2,
                color = MaterialTheme.colorScheme.primaryContainer,
                textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                priority = 2,
                onToggleTask = onToggleTask,
                onUpdatePriority = onUpdatePriority,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Bottom row: Q3 + Q4
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            EisenhowerQuadrant(
                title = "Delegate",
                subtitle = "Urgent, Not Important",
                tasks = q3,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                textColor = MaterialTheme.colorScheme.onTertiaryContainer,
                priority = 1,
                onToggleTask = onToggleTask,
                onUpdatePriority = onUpdatePriority,
                modifier = Modifier.weight(1f).padding(end = 4.dp)
            )
            EisenhowerQuadrant(
                title = "Eliminate",
                subtitle = "Neither",
                tasks = q4,
                color = MaterialTheme.colorScheme.surfaceVariant,
                textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                priority = 0,
                onToggleTask = onToggleTask,
                onUpdatePriority = onUpdatePriority,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun EisenhowerQuadrant(
    title: String,
    subtitle: String,
    tasks: List<Task>,
    color: Color,
    textColor: Color,
    priority: Int,
    onToggleTask: (Task) -> Unit,
    onUpdatePriority: (Task, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.3f),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = textColor
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = textColor.copy(alpha = 0.7f)
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = color,
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "${tasks.size}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = textColor
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            // Task list
            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tasks",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(tasks, key = { it.id }) { task ->
                        CompactTaskRow(
                            task = task,
                            onToggle = { onToggleTask(task) },
                            textColor = textColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactTaskRow(
    task: Task,
    onToggle: () -> Unit,
    textColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable { onToggle() }
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onToggle,
            modifier = Modifier.size(20.dp)
        ) {
            Icon(
                imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                contentDescription = null,
                tint = textColor.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = task.title,
            style = MaterialTheme.typography.bodySmall.copy(
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
            ),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (task.deadlineTime != null) {
            Text(
                text = task.deadlineTime,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = textColor.copy(alpha = 0.6f)
            )
        }
    }
}
