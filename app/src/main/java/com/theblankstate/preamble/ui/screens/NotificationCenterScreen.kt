package com.theblankstate.preamble.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.theblankstate.preamble.collab.OutgoingInvite
import com.theblankstate.preamble.collab.RequestsListOrganizer
import com.theblankstate.preamble.data.Task
import com.theblankstate.preamble.repository.WorkspaceInvite
import com.theblankstate.preamble.ui.viewmodels.WorkspaceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCenterScreen(
    viewModel: WorkspaceViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf("All") }
    val filters = listOf("All", "Incoming", "Outgoing")

    val incomingTasks by viewModel.incomingAssignments.collectAsState()
    val outgoingTasks by viewModel.outgoingAssignments.collectAsState()
    val requestsSections by viewModel.requestsSections.collectAsState()

    // Extract friend requests lists
    val incomingRequests = requestsSections.incoming
    val outgoingRequests = requestsSections.outgoing

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Activity",
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Filter Chips (Flat style)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filters.forEach { filter ->
                    val isSelected = selectedFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = null,
                        shape = RoundedCornerShape(50)
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                thickness = 1.dp
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                // Show Friend Requests
                if (selectedFilter == "All" || selectedFilter == "Incoming") {
                    if (incomingRequests.isNotEmpty()) {
                        item(key = "hdr_incoming_req") {
                            NotificationSubHeader("Friend Requests")
                        }
                        items(incomingRequests, key = { "in_req_${it.id}" }) { invite ->
                            ActivityNotificationRow(
                                avatarSeed = invite.senderPreambleId,
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append(invite.senderName)
                                    }
                                    append(" sent you a friend request.")
                                },
                                actions = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(
                                            onClick = {
                                                viewModel.acceptInvite(invite)
                                                Toast.makeText(context, "Request accepted ✓", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("Confirm", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.declineInvite(invite.id)
                                                Toast.makeText(context, "Request deleted", Toast.LENGTH_SHORT).show()
                                            },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("Delete", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                if (selectedFilter == "All" || selectedFilter == "Outgoing") {
                    if (outgoingRequests.isNotEmpty()) {
                        item(key = "hdr_outgoing_req") {
                            NotificationSubHeader("Sent Requests")
                        }
                        items(outgoingRequests, key = { "out_req_${it.targetUid}" }) { invite ->
                            ActivityNotificationRow(
                                avatarSeed = invite.targetPreambleId,
                                text = buildAnnotatedString {
                                    append("You requested to follow ")
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append(invite.targetPreambleId)
                                    }
                                    append(".")
                                },
                                actions = {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.withdrawInvite(invite.targetUid)
                                            Toast.makeText(context, "Request recalled", Toast.LENGTH_SHORT).show()
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Withdraw", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            )
                        }
                    }
                }

                // Show Task Assignments
                if (selectedFilter == "All" || selectedFilter == "Incoming") {
                    if (incomingTasks.isNotEmpty()) {
                        item(key = "hdr_incoming_tasks") {
                            NotificationSubHeader("Incoming Tasks")
                        }
                        items(incomingTasks, key = { "in_task_${it.id}" }) { task ->
                            val status = task.assignmentStatus ?: "pending"
                            ActivityNotificationRow(
                                avatarSeed = task.assignedByName ?: "Friend",
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append(task.assignedByName ?: "A friend")
                                    }
                                    append(" assigned you a task: ")
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                                        append(task.title)
                                    }
                                    append(".")
                                },
                                actions = {
                                    if (status == "pending") {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Button(
                                                onClick = {
                                                    viewModel.acceptAssignment(task)
                                                    Toast.makeText(context, "Task accepted", Toast.LENGTH_SHORT).show()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text("Accept", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.declineAssignment(task)
                                                    Toast.makeText(context, "Task declined", Toast.LENGTH_SHORT).show()
                                                },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text("Decline", fontSize = 12.sp)
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = if (task.isCompleted) "Completed ✓" else "Active",
                                            color = if (task.isCompleted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                if (selectedFilter == "All" || selectedFilter == "Outgoing") {
                    if (outgoingTasks.isNotEmpty()) {
                        item(key = "hdr_outgoing_tasks") {
                            NotificationSubHeader("Assigned Tasks")
                        }
                        items(outgoingTasks, key = { "out_task_${it.id}" }) { task ->
                            val status = task.assignmentStatus ?: "pending"
                            ActivityNotificationRow(
                                avatarSeed = task.assignedToName ?: "Friend",
                                text = buildAnnotatedString {
                                    append("You assigned ")
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                                        append(task.title)
                                    }
                                    append(" to ")
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append(task.assignedToName ?: "Friend")
                                    }
                                    append(".")
                                },
                                actions = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = status.replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (task.isCompleted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (status == "pending") {
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.recallAssignment(task)
                                                    Toast.makeText(context, "Assignment recalled", Toast.LENGTH_SHORT).show()
                                                },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text("Cancel", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                // Empty state if no notifications match the filter
                val isIncomingEmpty = incomingTasks.isEmpty() && incomingRequests.isEmpty()
                val isOutgoingEmpty = outgoingTasks.isEmpty() && outgoingRequests.isEmpty()
                val isEmpty = when (selectedFilter) {
                    "All" -> isIncomingEmpty && isOutgoingEmpty
                    "Incoming" -> isIncomingEmpty
                    "Outgoing" -> isOutgoingEmpty
                    else -> true
                }

                if (isEmpty) {
                    item(key = "empty_notification_state") {
                        Box(
                            modifier = Modifier
                                .fillParentMaxHeight(0.7f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "No activity yet",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationSubHeader(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
fun ActivityNotificationRow(
    avatarSeed: String,
    text: androidx.compose.ui.text.AnnotatedString,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Circular Avatar
        AsyncImage(
            model = "https://api.dicebear.com/9.x/micah/png?seed=$avatarSeed",
            contentDescription = "Avatar",
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Notification Text
        Text(
            text = text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Inline actions
        actions()
    }
}
