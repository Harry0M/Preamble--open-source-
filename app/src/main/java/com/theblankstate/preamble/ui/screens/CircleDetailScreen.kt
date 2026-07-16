package com.theblankstate.preamble.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.geometry.Offset

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
    pendingNotificationsCount: Int = 0,
    onNotificationClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val circles by viewModel.circles.collectAsState()
    val tasks by viewModel.circleTasks.collectAsState()
    val friends by viewModel.friends.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scaleFactor = (LocalConfiguration.current.screenWidthDp / 360f).coerceIn(0.85f, 1.15f)

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
                val backInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(40.dp * scaleFactor)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .expressivePressScale(backInteraction)
                        .clickable(
                            interactionSource = backInteraction,
                            indication = null
                        ) { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp * scaleFactor)
                    )
                }

                Text(
                    text = circle?.name ?: "Circle",
                    fontWeight = FontWeight.Black,
                    fontSize = (18 * scaleFactor).sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isAdmin && circle != null) {
                        val membersInteraction = remember { MutableInteractionSource() }
                        val renameInteraction = remember { MutableInteractionSource() }
                        val deleteInteraction = remember { MutableInteractionSource() }

                        Box(
                            modifier = Modifier
                                .size(40.dp * scaleFactor)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .expressivePressScale(membersInteraction)
                                .clickable(
                                    interactionSource = membersInteraction,
                                    indication = null
                                ) { showMembersDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonRemove,
                                contentDescription = "Manage members",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp * scaleFactor)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(40.dp * scaleFactor)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .expressivePressScale(renameInteraction)
                                .clickable(
                                    interactionSource = renameInteraction,
                                    indication = null
                                ) {
                                    renameValue = circle.name
                                    showRenameDialog = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DriveFileRenameOutline,
                                contentDescription = "Rename Circle",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp * scaleFactor)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(40.dp * scaleFactor)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .expressivePressScale(deleteInteraction)
                                .clickable(
                                    interactionSource = deleteInteraction,
                                    indication = null
                                ) { showDeleteCircleDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Circle",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp * scaleFactor)
                            )
                        }
                    } else if (circle != null) {
                        val leaveInteraction = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .size(40.dp * scaleFactor)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .expressivePressScale(leaveInteraction)
                                .clickable(
                                    interactionSource = leaveInteraction,
                                    indication = null
                                ) { showLeaveDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExitToApp,
                                contentDescription = "Leave Circle",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp * scaleFactor)
                            )
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
            Column(modifier = Modifier.fillMaxSize()) {
                val circleMembers = circle?.members ?: emptyList()
                if (circleMembers.isNotEmpty()) {
                    val membersInteraction = remember { MutableInteractionSource() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                interactionSource = membersInteraction,
                                indication = null
                            ) {
                                showMembersDialog = true
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy((-12).dp)
                        ) {
                            val visibleMembers = circleMembers.take(3)
                            visibleMembers.forEachIndexed { idx, member ->
                                val initials = member.name.take(1).uppercase()
                                val avatarColor = CardColors[idx % CardColors.size]
                                Box(
                                    modifier = Modifier
                                        .size(32.dp * scaleFactor)
                                        .clip(CircleShape)
                                        .background(avatarColor)
                                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = initials,
                                        fontWeight = FontWeight.Black,
                                        fontSize = (12 * scaleFactor).sp,
                                        color = Color.Black
                                    )
                                }
                            }
                            if (circleMembers.size > 3) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp * scaleFactor)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "+${circleMembers.size - 3}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = (10 * scaleFactor).sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp * scaleFactor))

                            Column {
                                Text(
                                    text = if (circleMembers.size == 1) "1 Member" else "${circleMembers.size} Members",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = (14 * scaleFactor).sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Tap to manage members",
                                    fontSize = (11 * scaleFactor).sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }

                        val completedTasks = tasks.count { it.isCompleted }
                        val totalTasks = tasks.size
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "$completedTasks/$totalTasks Done",
                                fontWeight = FontWeight.Bold,
                                fontSize = (11 * scaleFactor).sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
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
        }
    }

        val fabScaleFactor = (LocalConfiguration.current.screenWidthDp / 360f).coerceIn(0.85f, 1.15f)
        val addFabInteraction = remember { MutableInteractionSource() }
        Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .size(56.dp * fabScaleFactor)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .expressivePressScale(addFabInteraction)
                    .clickable(
                        interactionSource = addFabInteraction,
                        indication = null
                    ) {
                        newTaskTitle = ""
                        showAddTaskDialog = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add task",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp * fabScaleFactor)
                )
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

    // ---- Rename Circle bottom sheet (admin, Requirements 3.1, 3.4) ----
    if (showRenameDialog && circle != null) {
        val renameSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showRenameDialog = false },
            sheetState = renameSheetState,
            containerColor = MaterialTheme.colorScheme.background,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "RENAME CIRCLE",
                    fontWeight = FontWeight.Bold,
                    fontSize = (14 * scaleFactor).sp,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(16.dp * scaleFactor))
                
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    placeholder = { Text("Circle name", fontSize = (14 * scaleFactor).sp) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = (14 * scaleFactor).sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((48 * scaleFactor).dp),
                    shape = RoundedCornerShape(12.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp * scaleFactor))
                
                val saveInteraction = remember { MutableInteractionSource() }
                Button(
                    onClick = {
                        viewModel.renameCircle(circle, renameValue)
                        showRenameDialog = false
                    },
                    enabled = renameValue.isNotBlank(),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((48 * scaleFactor).dp)
                        .expressivePressScale(saveInteraction),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = "Save changes",
                        fontWeight = FontWeight.Bold,
                        fontSize = (14 * scaleFactor).sp
                    )
                }
            }
        }
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

    // ---- Manage members bottom sheet (admin, Requirements 4.1, 5.1) ----
    if (showMembersDialog && circle != null) {
        val membersSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showMembersDialog = false },
            sheetState = membersSheetState,
            containerColor = MaterialTheme.colorScheme.background,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 36.dp)
            ) {
                Text(
                    text = "CIRCLE MEMBERS",
                    fontWeight = FontWeight.Bold,
                    fontSize = (14 * scaleFactor).sp,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(12.dp * scaleFactor))
                
                Box(modifier = Modifier.heightIn(max = 240.dp * scaleFactor)) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(circle.members.filter { it.status == "active" }, key = { it.uid }) { member ->
                            MemberRow(
                                member = member,
                                isAdminMember = member.uid == circle.adminUid,
                                onRemove = { viewModel.removeMember(circle, member.uid) }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp * scaleFactor))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp * scaleFactor))
                
                val addFriendInteraction = remember { MutableInteractionSource() }
                Button(
                    onClick = {
                        showMembersDialog = false
                        showAddMemberDialog = true
                    },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((48 * scaleFactor).dp)
                        .expressivePressScale(addFriendInteraction),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = "Add friend",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp * scaleFactor)
                    )
                    Spacer(modifier = Modifier.width(8.dp * scaleFactor))
                    Text(
                        text = "Add a friend",
                        fontWeight = FontWeight.Bold,
                        fontSize = (14 * scaleFactor).sp
                    )
                }
            }
        }
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
    val scaleFactor = (LocalConfiguration.current.screenWidthDp / 360f).coerceIn(0.85f, 1.15f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val toggleInteraction = remember { MutableInteractionSource() }
        val accentColor = CardColors[0] // Soft Blue
        val anchorBg = if (task.isCompleted) accentColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
        val iconColor = if (task.isCompleted) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
        
        Box(
            modifier = Modifier
                .size(44.dp * scaleFactor)
                .clip(CircleShape)
                .background(anchorBg)
                .expressivePressScale(toggleInteraction)
                .clickable(
                    interactionSource = toggleInteraction,
                    indication = null
                ) {
                    onToggleCompletion()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (task.isCompleted) "Mark not done" else "Mark done",
                tint = iconColor,
                modifier = Modifier.size(20.dp * scaleFactor)
            )
        }

        Spacer(modifier = Modifier.width(16.dp * scaleFactor))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                fontWeight = FontWeight.Bold,
                fontSize = (15 * scaleFactor).sp,
                color = MaterialTheme.colorScheme.onSurface,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
            )
            Spacer(modifier = Modifier.height(2.dp))
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
                fontSize = (12 * scaleFactor).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }

        if (canEditDelete) {
            Spacer(modifier = Modifier.width(8.dp * scaleFactor))
            
            val editInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .size(36.dp * scaleFactor)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .expressivePressScale(editInteraction)
                    .clickable(
                        interactionSource = editInteraction,
                        indication = null
                    ) { onEdit() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit task",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp * scaleFactor)
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp * scaleFactor))
            
            val deleteInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .size(36.dp * scaleFactor)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .expressivePressScale(deleteInteraction)
                    .clickable(
                        interactionSource = deleteInteraction,
                        indication = null
                    ) { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete task",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp * scaleFactor)
                )
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
    val scaleFactor = (LocalConfiguration.current.screenWidthDp / 360f).coerceIn(0.85f, 1.15f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Visual Anchor on Left
        val initials = member.name.take(1).uppercase()
        val avatarColor = CardColors[member.uid.hashCode().coerceAtLeast(0) % CardColors.size]
        Box(
            modifier = Modifier
                .size(36.dp * scaleFactor)
                .clip(CircleShape)
                .background(avatarColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                fontWeight = FontWeight.Black,
                fontSize = (13 * scaleFactor).sp,
                color = Color.Black
            )
        }

        Spacer(modifier = Modifier.width(12.dp * scaleFactor))

        // Metadata in Middle
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = member.name.ifBlank { member.uid },
                fontWeight = FontWeight.Bold,
                fontSize = (14 * scaleFactor).sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (isAdminMember) {
                Text(
                    text = "Admin",
                    fontSize = (11 * scaleFactor).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        // Actions/Metadata on Right
        if (!isAdminMember) {
            val removeInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .size(32.dp * scaleFactor)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .expressivePressScale(removeInteraction)
                    .clickable(
                        interactionSource = removeInteraction,
                        indication = null
                    ) { onRemove() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PersonRemove,
                    contentDescription = "Remove member",
                    tint = Color(0xFFD9534F),
                    modifier = Modifier.size(16.dp * scaleFactor)
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

@Composable
private fun Modifier.expressivePressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.92f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "expressivePressScale",
    )
    return this.scale(scale)
}

@Composable
private fun DottedDownwardArrow(color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(24.dp)) {
        val width = size.width
        val height = size.height
        val dashEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)

        drawLine(
            color = color,
            start = Offset(width / 2f, height / 5f),
            end = Offset(width / 2f, 4f * height / 5f),
            strokeWidth = 2.dp.toPx(),
            pathEffect = dashEffect
        )
        drawLine(
            color = color,
            start = Offset(width / 2f - width / 5f, 4f * height / 5f - height / 5f),
            end = Offset(width / 2f, 4f * height / 5f),
            strokeWidth = 2.dp.toPx(),
            pathEffect = dashEffect
        )
        drawLine(
            color = color,
            start = Offset(width / 2f + width / 5f, 4f * height / 5f - height / 5f),
            end = Offset(width / 2f, 4f * height / 5f),
            strokeWidth = 2.dp.toPx(),
            pathEffect = dashEffect
        )
    }
}
