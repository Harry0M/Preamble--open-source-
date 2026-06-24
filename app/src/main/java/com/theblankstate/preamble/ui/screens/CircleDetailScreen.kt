package com.theblankstate.preamble.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.theblankstate.preamble.circles.CircleTask
import com.theblankstate.preamble.repository.CircleMember
import com.theblankstate.preamble.repository.CircleTaskModel
import com.theblankstate.preamble.repository.Friend
import com.theblankstate.preamble.ui.viewmodels.CircleUiState
import com.theblankstate.preamble.ui.viewmodels.CircleViewModel

/**
 * Circle_Detail_Screen (shared-circles Requirements 5.4, 6.1, 6.3, 7.4, 9.1, 10.1, 10.3, 11.1,
 * 11.4, 12.1–12.4).
 *
 * Renders one Circle's shared task list (title, Shared_Completion state, Completer name,
 * Circle_Author name) from [CircleViewModel.circleTasks], an add-task control, an empty-state when
 * there are no tasks, a per-task completion toggle, and author/admin-only edit/delete affordances
 * (gated via [CircleTask.classifyEditDelete]).
 *
 * For the Circle_Admin it renders membership controls (rename, delete, add a friend, remove a
 * member). For a non-admin Circle_Member it renders a confirmed Leave control and **no** admin
 * controls. [uiState] messages surface via Toast and are cleared with [CircleViewModel.resetState].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleDetailScreen(
    circleId: String,
    viewModel: CircleViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val circles by viewModel.circles.collectAsState()
    val tasks by viewModel.circleTasks.collectAsState()
    val friends by viewModel.friends.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val currentUid = FirebaseAuth.getInstance().currentUser?.uid
    val circle = circles.firstOrNull { it.id == circleId }
    val isAdmin = circle != null && circle.adminUid == currentUid

    // Dialog state.
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var newTaskTitle by remember { mutableStateOf("") }

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf("") }

    var showDeleteCircleDialog by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showMembersDialog by remember { mutableStateOf(false) }
    var showAddMemberDialog by remember { mutableStateOf(false) }

    var taskBeingEdited by remember { mutableStateOf<CircleTaskModel?>(null) }
    var editTitleValue by remember { mutableStateOf("") }
    var taskPendingDelete by remember { mutableStateOf<CircleTaskModel?>(null) }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is CircleUiState.Success -> {
                android.widget.Toast.makeText(context, state.message, android.widget.Toast.LENGTH_SHORT).show()
                viewModel.resetState()
            }
            is CircleUiState.Error -> {
                android.widget.Toast.makeText(context, state.message, android.widget.Toast.LENGTH_SHORT).show()
                viewModel.resetState()
            }
            else -> {}
        }
    }

    // If the Circle disappears (deleted, removed, or left), return to the list.
    LaunchedEffect(circle == null) {
        if (circle == null) onBack()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.padding(end = 4.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        circle?.name ?: "Circle",
                        fontWeight = FontWeight.Black,
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isAdmin && circle != null) {
                        // Admin-only membership controls (Requirements 3.1, 4.1, 5.1, 7.1).
                        IconButton(onClick = { showMembersDialog = true }) {
                            Icon(Icons.Default.PersonRemove, contentDescription = "Manage members")
                        }
                        IconButton(onClick = {
                            renameValue = circle.name
                            showRenameDialog = true
                        }) {
                            Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "Rename Circle")
                        }
                        IconButton(onClick = { showDeleteCircleDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Circle")
                        }
                    } else if (circle != null) {
                        // Non-admin member: confirmed Leave control only, no admin controls
                        // (Requirements 5.4, 6.1, 6.3, 7.4).
                        IconButton(onClick = { showLeaveDialog = true }) {
                            Icon(Icons.Default.ExitToApp, contentDescription = "Leave Circle")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (tasks.isEmpty()) {
                // Empty-state (Requirement 10.3).
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(32.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Inbox,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "The shared list is empty.",
                        fontSize = 22.sp,
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Add the first shared task for this Circle.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
                ) {
                    items(tasks, key = { "circle_task_${it.id}" }) { task ->
                        val canEditDelete = currentUid != null && circle != null &&
                            CircleTask.classifyEditDelete(
                                taskAuthorUid = task.authorUid,
                                circleAdminUid = circle.adminUid,
                                requesterUid = currentUid
                            ) == CircleTask.EditDeleteDecision.ALLOW

                        CircleTaskRow(
                            task = task,
                            authorName = circle?.members?.firstOrNull { it.uid == task.authorUid }?.name,
                            canEditDelete = canEditDelete,
                            onToggleCompletion = { viewModel.setCompletion(task, !task.isCompleted) },
                            onEdit = {
                                taskBeingEdited = task
                                editTitleValue = task.title
                            },
                            onDelete = { taskPendingDelete = task }
                        )
                    }
                }
            }

            // Add-task control (Requirement 9.1).
            androidx.compose.material3.FloatingActionButton(
                onClick = {
                    newTaskTitle = ""
                    showAddTaskDialog = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add task")
            }
        }
    }

    // ---- Add task dialog (Requirement 9.1, 9.2) ----
    if (showAddTaskDialog) {
        AlertDialog(
            onDismissRequest = { showAddTaskDialog = false },
            title = { Text("Add a shared task") },
            text = {
                OutlinedTextField(
                    value = newTaskTitle,
                    onValueChange = { newTaskTitle = it },
                    label = { Text("Task title") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addTask(circleId, newTaskTitle)
                        showAddTaskDialog = false
                    },
                    enabled = newTaskTitle.isNotBlank()
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddTaskDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ---- Edit task title dialog (Requirements 12.1) ----
    val editingTask = taskBeingEdited
    if (editingTask != null) {
        AlertDialog(
            onDismissRequest = { taskBeingEdited = null },
            title = { Text("Edit task") },
            text = {
                OutlinedTextField(
                    value = editTitleValue,
                    onValueChange = { editTitleValue = it },
                    label = { Text("Task title") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.editTaskTitle(editingTask, editTitleValue)
                        taskBeingEdited = null
                    },
                    enabled = editTitleValue.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { taskBeingEdited = null }) { Text("Cancel") }
            }
        )
    }

    // ---- Delete task confirmation (Requirement 12.3) ----
    val deletingTask = taskPendingDelete
    if (deletingTask != null) {
        AlertDialog(
            onDismissRequest = { taskPendingDelete = null },
            title = { Text("Delete task") },
            text = { Text("Delete \"${deletingTask.title}\" from the shared list?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteTask(deletingTask)
                    taskPendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { taskPendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    // ---- Rename Circle dialog (admin, Requirements 3.1, 3.4) ----
    if (showRenameDialog && circle != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Circle") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    label = { Text("Circle name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameCircle(circle, renameValue)
                        showRenameDialog = false
                    },
                    enabled = renameValue.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ---- Delete Circle confirmation (admin, Requirement 7.1) ----
    if (showDeleteCircleDialog && circle != null) {
        AlertDialog(
            onDismissRequest = { showDeleteCircleDialog = false },
            title = { Text("Delete Circle") },
            text = { Text("Delete \"${circle.name}\" and its shared task list for everyone? This cannot be undone.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteCircle(circle)
                    showDeleteCircleDialog = false
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCircleDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ---- Leave Circle confirmation (non-admin, Requirement 6.1) ----
    if (showLeaveDialog && circle != null) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("Leave Circle") },
            text = { Text("Leave \"${circle.name}\"? You will stop seeing its shared task list.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.leaveCircle(circle)
                    showLeaveDialog = false
                    onBack()
                }) { Text("Leave") }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ---- Manage members dialog (admin, Requirements 4.1, 5.1) ----
    if (showMembersDialog && circle != null) {
        AlertDialog(
            onDismissRequest = { showMembersDialog = false },
            title = { Text("Members") },
            text = {
                Column(modifier = Modifier.heightIn(max = 360.dp)) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(circle.members.filter { it.status == "active" }, key = { it.uid }) { member ->
                            MemberRow(
                                member = member,
                                isAdminMember = member.uid == circle.adminUid,
                                onRemove = { viewModel.removeMember(circle, member.uid) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = {
                        showMembersDialog = false
                        showAddMemberDialog = true
                    }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add a friend")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMembersDialog = false }) { Text("Done") }
            }
        )
    }

    // ---- Add member from friends dialog (admin, Requirements 4.1, 4.2) ----
    if (showAddMemberDialog && circle != null) {
        val addableFriends = friends.filter { it.uid !in circle.memberUids }
        AlertDialog(
            onDismissRequest = { showAddMemberDialog = false },
            title = { Text("Add a friend") },
            text = {
                if (addableFriends.isEmpty()) {
                    Text("All of your friends are already in this Circle.")
                } else {
                    Column(modifier = Modifier.heightIn(max = 360.dp)) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(addableFriends, key = { it.uid }) { friend ->
                                AddFriendRow(
                                    friend = friend,
                                    onAdd = {
                                        viewModel.addMember(circle, friend)
                                        showAddMemberDialog = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddMemberDialog = false }) { Text("Done") }
            }
        )
    }
}

@Composable
private fun CircleTaskRow(
    task: CircleTaskModel,
    authorName: String?,
    canEditDelete: Boolean,
    onToggleCompletion: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Completion toggle (Requirements 11.1, 11.4).
            IconButton(onClick = onToggleCompletion) {
                Icon(
                    imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (task.isCompleted) "Mark not done" else "Mark done",
                    tint = if (task.isCompleted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )
                Spacer(modifier = Modifier.height(2.dp))
                // Shared_Completion state + Completer name, and Circle_Author (Requirement 10.1).
                val subtitle = buildString {
                    if (task.isCompleted) {
                        val completer = task.completedBy?.name
                        append(if (!completer.isNullOrBlank()) "Done by $completer" else "Done")
                    } else {
                        append("Not done")
                    }
                    if (!authorName.isNullOrBlank()) append(" · Added by $authorName")
                }
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Author/admin-only edit/delete affordances (Requirements 12.1–12.4).
            if (canEditDelete) {
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit task",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete task",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: CircleMember,
    isAdminMember: Boolean,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(member.name.ifBlank { member.uid }, fontWeight = FontWeight.Bold)
            if (isAdminMember) {
                Text("Admin", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // The admin cannot be removed via this control (Requirement 5.3).
        if (!isAdminMember) {
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.PersonRemove,
                    contentDescription = "Remove ${member.name}",
                    tint = Color(0xFFD9534F)
                )
            }
        }
    }
}

@Composable
private fun AddFriendRow(
    friend: Friend,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAdd)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(friend.name.ifBlank { friend.preambleId }, fontWeight = FontWeight.Bold)
            if (friend.preambleId.isNotBlank()) {
                Text(friend.preambleId, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.Default.PersonAdd, contentDescription = "Add ${friend.name}", tint = MaterialTheme.colorScheme.primary)
    }
}
