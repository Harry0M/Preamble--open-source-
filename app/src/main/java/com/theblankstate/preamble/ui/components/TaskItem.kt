package com.theblankstate.preamble.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theblankstate.preamble.data.Task
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskItem(
    task: Task,
    onToggle: () -> Unit,
    onLongClick: () -> Unit,
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
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(cardAlpha)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isOverdue) Modifier.background(errorContainerColor.copy(alpha = 0.3f))
                else Modifier
            )
            .combinedClickable(
                onClick = { onToggle() },
                onLongClick = { onLongClick() }
            )
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = task.isCompleted,
            onCheckedChange = null,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = task.title,
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
            modifier = Modifier.weight(1f)
        )
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
    }
}
