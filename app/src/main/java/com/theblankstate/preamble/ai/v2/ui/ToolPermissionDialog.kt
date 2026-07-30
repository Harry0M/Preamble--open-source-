package com.theblankstate.preamble.ai.v2.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theblankstate.preamble.ai.v2.V2ToolCallInfo
import com.theblankstate.preamble.ai.v2.V2ToolResult

/**
 * Displays a permission dialog for a single tool call.
 *
 * Shows the tool's category (read/write) with an appropriate icon,
 * a description of the action, and the target data being accessed or modified.
 * The user can grant or deny the tool call.
 *
 * Requirements: 7.1, 7.3, 7.6, 7.8
 */
@Composable
fun ToolPermissionDialog(
    toolCall: V2ToolCallInfo,
    onGrant: () -> Unit,
    onDeny: () -> Unit,
) {
    val isWrite = toolCall.category.equals("write", ignoreCase = true)
    val categoryIcon = if (isWrite) Icons.Filled.Edit else Icons.Filled.Visibility
    val categoryLabel = if (isWrite) "Write" else "Read"
    val categoryColor = if (isWrite) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    AlertDialog(
        onDismissRequest = { /* Requirement 7.8: no dismiss without explicit choice */ },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = categoryIcon,
                    contentDescription = "$categoryLabel permission",
                    tint = categoryColor,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Tool Permission",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Category badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(categoryColor.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = categoryLabel.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = categoryColor,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Description
                Text(
                    text = toolCall.description.ifBlank { toolCall.name },
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Target data
                if (toolCall.targetData.isNotBlank()) {
                    Text(
                        text = "Target",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = toolCall.targetData,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(8.dp),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text("Allow")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDeny) {
                Text("Deny")
            }
        },
    )
}

/**
 * Result of processing a queue of tool permission dialogs.
 */
data class ToolPermissionResult(
    val results: List<V2ToolResult>,
)

/**
 * Manages a queue of pending tool permission dialogs, presenting them sequentially.
 *
 * When [pendingToolCalls] is non-empty, this composable displays a [ToolPermissionDialog]
 * for the first item in the queue. As the user grants or denies each call, the next one
 * is shown until the queue is exhausted. Once all tool calls have been resolved,
 * [onAllResolved] is invoked with the collected results.
 *
 * If [pendingToolCalls] is empty, nothing is rendered.
 *
 * Requirements: 7.1, 7.3, 7.6, 7.8
 */
@Composable
fun ToolPermissionHandler(
    pendingToolCalls: List<V2ToolCallInfo>,
    onAllResolved: (List<V2ToolResult>) -> Unit,
) {
    if (pendingToolCalls.isEmpty()) return

    var currentIndex by remember(pendingToolCalls) { mutableStateOf(0) }
    var resolvedResults by remember(pendingToolCalls) { mutableStateOf(listOf<V2ToolResult>()) }

    if (currentIndex < pendingToolCalls.size) {
        val currentCall = pendingToolCalls[currentIndex]

        ToolPermissionDialog(
            toolCall = currentCall,
            onGrant = {
                val result = V2ToolResult(
                    name = currentCall.name,
                    result = "",
                    granted = true,
                )
                val updatedResults = resolvedResults + result
                resolvedResults = updatedResults

                val nextIndex = currentIndex + 1
                if (nextIndex >= pendingToolCalls.size) {
                    onAllResolved(updatedResults)
                } else {
                    currentIndex = nextIndex
                }
            },
            onDeny = {
                val result = V2ToolResult(
                    name = currentCall.name,
                    result = "",
                    granted = false,
                )
                val updatedResults = resolvedResults + result
                resolvedResults = updatedResults

                val nextIndex = currentIndex + 1
                if (nextIndex >= pendingToolCalls.size) {
                    onAllResolved(updatedResults)
                } else {
                    currentIndex = nextIndex
                }
            },
        )
    }
}
