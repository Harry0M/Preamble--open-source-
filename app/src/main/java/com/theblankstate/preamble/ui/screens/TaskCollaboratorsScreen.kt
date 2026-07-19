package com.theblankstate.preamble.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theblankstate.preamble.collab.CollaboratorView
import com.theblankstate.preamble.collab.TaskReaction
import com.theblankstate.preamble.data.CollabAssigneeStatus
import com.theblankstate.preamble.data.Task
import com.theblankstate.preamble.ui.components.AvatarStatusStyle
import com.theblankstate.preamble.ui.components.MemberAvatar
import com.theblankstate.preamble.ui.components.avatarStatusStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Preamble Cardfolio Soft Color Palette
private val PreambleCardColors = listOf(
    Color(0xFFA1C6FF), // Soft Blue
    Color(0xFFEAB3FF), // Soft Purple
    Color(0xFFFFD166), // Soft Yellow
    Color(0xFFFF9E9E), // Soft Coral
    Color(0xFF9EE8FF), // Soft Cyan
    Color(0xFFFFC085)  // Soft Orange
)

/**
 * Dedicated Task Collaborators Screen complying strictly with `custom-ui-design-guidelines`.
 *
 * Visual Features:
 * - Borderless list layout sitting directly on screen background (like Activity / Notification Center).
 * - 44.dp circular visual anchor on the left using soft pastel accent colors from Preamble CardColors.
 * - Dynamic Responsive Scaling via scaleFactor.
 * - Tactile press scaling (expressivePressScale) on all interactive controls.
 * - Overlapping avatar stacks with negative spacing Arrangement.spacedBy((-12).dp) and thin white borders.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskCollaboratorsScreen(
    task: Task,
    currentUserUid: String?,
    onBack: () -> Unit,
    showRoleControls: Boolean = true,
    onRemoveMember: ((String) -> Unit)? = null,
    onTransferOwnership: ((String) -> Unit)? = null,
    onLeaveTask: (() -> Unit)? = null,
    onReact: ((String) -> Unit)? = null,
    onNudge: ((String) -> Unit)? = null,
    canNudge: ((String) -> Boolean)? = null,
    nudgeCooldownRemaining: ((String) -> Long)? = null,
    modifier: Modifier = Modifier
) {
    // 1. Dynamic Responsive Scaling (custom-ui-design-guidelines Rule #2)
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val scaleFactor = (screenWidth.value / 360f).coerceIn(0.85f, 1.15f)

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var isSearchExpanded by remember { mutableStateOf(false) }

    // Dialog states
    var memberPendingRemoval by remember { mutableStateOf<Pair<String, String>?>(null) }
    var memberPendingTransfer by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showLeaveTaskConfirm by remember { mutableStateOf(false) }

    val adminUid = task.collabAdminUid
    val adminName = task.collabAdminName ?: "Admin"
    val assignees = task.collabAssignees
    val isCurrentUserAdmin = currentUserUid != null && currentUserUid == adminUid
    val isCurrentUserMember = currentUserUid != null && assignees.any { it.uid == currentUserUid }

    val visibleAssignees = remember(assignees) {
        CollaboratorView.visibleMembers(assignees) { it.status }
    }

    val filteredAssignees = remember(visibleAssignees, searchQuery, selectedFilter) {
        visibleAssignees.filter { assignee ->
            val matchesQuery = searchQuery.isBlank() ||
                assignee.name.contains(searchQuery, ignoreCase = true) ||
                assignee.uid.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                "Accepted" -> assignee.status == "accepted" && !assignee.isCompleted
                "Pending" -> assignee.status == "pending"
                "Completed" -> assignee.isCompleted
                else -> true
            }

            matchesQuery && matchesFilter
        }
    }

    val acceptedCount = visibleAssignees.count { it.status == "accepted" && !it.isCompleted }
    val pendingCount = visibleAssignees.count { it.status == "pending" }
    val completedCount = visibleAssignees.count { it.isCompleted }
    val totalCount = (if (adminUid != null) 1 else 0) + visibleAssignees.size

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Collaborators",
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp * scaleFactor,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp * scaleFactor
                        )
                    }
                },
                navigationIcon = {
                    val backInteraction = remember { MutableInteractionSource() }
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.expressivePressScale(backInteraction)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    val searchInteraction = remember { MutableInteractionSource() }
                    IconButton(
                        onClick = { isSearchExpanded = !isSearchExpanded },
                        modifier = Modifier.expressivePressScale(searchInteraction)
                    ) {
                        Icon(
                            imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
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
            // Search Input (Animated expand)
            AnimatedVisibility(visible = isSearchExpanded) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by name...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp * scaleFactor, vertical = 8.dp * scaleFactor),
                    shape = RoundedCornerShape(50),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                    )
                )
            }

            // Hero Summary Section (Preamble Soft Pastel Styling)
            PreambleHeroSummarySection(
                adminName = adminName,
                totalCount = totalCount,
                acceptedCount = acceptedCount,
                pendingCount = pendingCount,
                completedCount = completedCount,
                assignees = visibleAssignees,
                scaleFactor = scaleFactor
            )

            // Filter Chips (Preamble Flat Pill Style)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp * scaleFactor, vertical = 8.dp * scaleFactor),
                horizontalArrangement = Arrangement.spacedBy(8.dp * scaleFactor)
            ) {
                listOf("All", "Accepted", "Pending", "Completed").forEach { filter ->
                    val isSelected = selectedFilter == filter
                    val chipInteraction = remember { MutableInteractionSource() }
                    val count = when (filter) {
                        "Accepted" -> acceptedCount
                        "Pending" -> pendingCount
                        "Completed" -> completedCount
                        else -> totalCount
                    }

                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = { Text("$filter ($count)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = null,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.expressivePressScale(chipInteraction)
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                thickness = 1.dp
            )

            // Minimalist Borderless List Items (custom-ui-design-guidelines Rule #1)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp * scaleFactor)
            ) {
                // Admin Member Item
                if (adminUid != null && (searchQuery.isBlank() || adminName.contains(searchQuery, ignoreCase = true))) {
                    if (selectedFilter == "All" || selectedFilter == "Accepted") {
                        item(key = "admin_row") {
                            PreambleAdminMemberListRow(
                                name = adminName,
                                isCurrentUser = currentUserUid == adminUid,
                                scaleFactor = scaleFactor
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                                thickness = 1.dp,
                                modifier = Modifier.padding(start = 72.dp * scaleFactor)
                            )
                        }
                    }
                }

                // Assignee Member Items
                items(filteredAssignees, key = { it.uid }) { assignee ->
                    PreambleAssigneeMemberListRow(
                        assignee = assignee,
                        isCurrentUser = currentUserUid == assignee.uid,
                        showRoleControls = showRoleControls,
                        canManage = showRoleControls && isCurrentUserAdmin && assignee.uid != currentUserUid,
                        onRemoveMember = { memberPendingRemoval = assignee.uid to assignee.name },
                        onTransferOwnership = { memberPendingTransfer = assignee.uid to assignee.name },
                        onNudge = onNudge,
                        canNudge = canNudge,
                        nudgeCooldownRemaining = nudgeCooldownRemaining,
                        reactions = task.collabReactions,
                        onReact = onReact,
                        scaleFactor = scaleFactor
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(start = 72.dp * scaleFactor)
                    )
                }

                // Leave Task Action (Footer item)
                if (showRoleControls && isCurrentUserMember && !isCurrentUserAdmin && onLeaveTask != null) {
                    item(key = "leave_task_row") {
                        Spacer(modifier = Modifier.height(16.dp * scaleFactor))
                        val leaveInteraction = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp * scaleFactor),
                            contentAlignment = Alignment.Center
                        ) {
                            OutlinedButton(
                                onClick = { showLeaveTaskConfirm = true },
                                shape = RoundedCornerShape(12.dp * scaleFactor),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp * scaleFactor)
                                    .expressivePressScale(leaveInteraction)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PersonRemove,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp * scaleFactor)
                                )
                                Spacer(modifier = Modifier.width(8.dp * scaleFactor))
                                Text(
                                    text = "Leave This Task",
                                    fontSize = 14.sp * scaleFactor,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation Dialogs
    memberPendingRemoval?.let { (uid, name) ->
        AlertDialog(
            onDismissRequest = { memberPendingRemoval = null },
            title = { Text("Remove Member") },
            text = { Text("Remove $name from this task?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveMember?.invoke(uid)
                        memberPendingRemoval = null
                    }
                ) { Text("Remove", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { memberPendingRemoval = null }) { Text("Cancel") }
            }
        )
    }

    memberPendingTransfer?.let { (uid, name) ->
        AlertDialog(
            onDismissRequest = { memberPendingTransfer = null },
            title = { Text("Transfer Ownership") },
            text = { Text("Make $name the primary owner of this task?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onTransferOwnership?.invoke(uid)
                        memberPendingTransfer = null
                    }
                ) { Text("Transfer", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { memberPendingTransfer = null }) { Text("Cancel") }
            }
        )
    }

    if (showLeaveTaskConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveTaskConfirm = false },
            title = { Text("Leave Task") },
            text = { Text("You will no longer receive updates or notifications for this task.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onLeaveTask?.invoke()
                        showLeaveTaskConfirm = false
                        onBack()
                    }
                ) { Text("Leave", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveTaskConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

/** Preamble Hero Summary Section with Overlapping Avatars Stack (Rule #4). */
@Composable
private fun PreambleHeroSummarySection(
    adminName: String,
    totalCount: Int,
    acceptedCount: Int,
    pendingCount: Int,
    completedCount: Int,
    assignees: List<CollabAssigneeStatus>,
    scaleFactor: Float
) {
    val heroColor = PreambleCardColors[0].copy(alpha = 0.2f)

    Surface(
        color = heroColor,
        shape = RoundedCornerShape(16.dp * scaleFactor),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp * scaleFactor, vertical = 8.dp * scaleFactor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp * scaleFactor),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$totalCount Collaborators",
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp * scaleFactor,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp * scaleFactor))
                Text(
                    text = "Admin: $adminName • $acceptedCount active • $pendingCount pending",
                    fontSize = 12.sp * scaleFactor,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            // Overlapping Avatar Stack (custom-ui-design-guidelines Rule #4)
            PreambleOverlappingAvatarStack(
                assignees = assignees,
                adminName = adminName,
                scaleFactor = scaleFactor
            )
        }
    }
}

/** Overlapping Avatar Stack implementing Rule #4: Arrangement.spacedBy((-12).dp) & white border. */
@Composable
private fun PreambleOverlappingAvatarStack(
    assignees: List<CollabAssigneeStatus>,
    adminName: String,
    scaleFactor: Float
) {
    val displayedMembers = remember(assignees, adminName) {
        buildList {
            add(adminName to null)
            assignees.take(3).forEach { add(it.name to it.photoUrl) }
        }.take(3)
    }

    val totalMembers = 1 + assignees.size
    val overflow = (totalMembers - 3).coerceAtLeast(0)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        // Rule #4: Arrangement.spacedBy((-12).dp)
        horizontalArrangement = Arrangement.spacedBy((-12).dp * scaleFactor)
    ) {
        displayedMembers.forEach { (name, photo) ->
            MemberAvatar(
                photoUrl = photo,
                name = name,
                style = AvatarStatusStyle.FILLED,
                shape = CircleShape,
                modifier = Modifier
                    .size(34.dp * scaleFactor)
                    // Rule #4: thin border around each avatar
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
        if (overflow > 0) {
            Box(
                modifier = Modifier
                    .size(34.dp * scaleFactor)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$overflow",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 11.sp * scaleFactor
                )
            }
        }
    }
}

/** Admin Row in Clean Minimalist Borderless List format (Rule #1). */
@Composable
private fun PreambleAdminMemberListRow(
    name: String,
    isCurrentUser: Boolean,
    scaleFactor: Float
) {
    val anchorColor = PreambleCardColors[0] // Soft Blue

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp * scaleFactor, vertical = 10.dp * scaleFactor),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Visual Anchor on Left: 44.dp circular background filled with soft accent color (Rule #1)
        Box(
            modifier = Modifier
                .size(44.dp * scaleFactor)
                .clip(CircleShape)
                .background(anchorColor.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp * scaleFactor)
            )
        }

        Spacer(modifier = Modifier.width(12.dp * scaleFactor))

        // Metadata in Middle: Bold title (16.sp) + secondary label (Rule #1)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isCurrentUser) "$name (You)" else name,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp * scaleFactor,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(2.dp * scaleFactor))
            Text(
                text = "Task Owner • Admin",
                fontSize = 13.sp * scaleFactor,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
        }

        // Actions/Badge on Right
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        ) {
            Text(
                text = "Admin",
                modifier = Modifier.padding(horizontal = 10.dp * scaleFactor, vertical = 4.dp * scaleFactor),
                fontSize = 11.sp * scaleFactor,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** Assignee Member Row in Clean Minimalist Borderless List format (Rule #1). */
@Composable
private fun PreambleAssigneeMemberListRow(
    assignee: CollabAssigneeStatus,
    isCurrentUser: Boolean,
    showRoleControls: Boolean,
    canManage: Boolean,
    onRemoveMember: () -> Unit,
    onTransferOwnership: () -> Unit,
    onNudge: ((String) -> Unit)?,
    canNudge: ((String) -> Boolean)?,
    nudgeCooldownRemaining: ((String) -> Long)?,
    reactions: List<TaskReaction>,
    onReact: ((String) -> Unit)?,
    scaleFactor: Float
) {
    var showMenu by remember { mutableStateOf(false) }

    val isAccepted = assignee.status == "accepted"
    val colorIdx = (assignee.uid.hashCode().coerceAtLeast(0)) % PreambleCardColors.size
    val anchorColor = PreambleCardColors[colorIdx]

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp * scaleFactor, vertical = 10.dp * scaleFactor),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Visual Anchor on Left: 44.dp circular background filled with soft accent color (Rule #1)
        Box(
            modifier = Modifier
                .size(44.dp * scaleFactor)
                .clip(CircleShape)
                .background(anchorColor.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            MemberAvatar(
                photoUrl = assignee.photoUrl,
                name = assignee.name,
                style = avatarStatusStyle(assignee.status),
                shape = CircleShape,
                modifier = Modifier.size(38.dp * scaleFactor)
            )
        }

        Spacer(modifier = Modifier.width(12.dp * scaleFactor))

        // Metadata in Middle: Bold title (16.sp) + secondary label (Rule #1)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isCurrentUser) "${assignee.name} (You)" else assignee.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp * scaleFactor,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (assignee.isCompleted) {
                    Spacer(modifier = Modifier.width(4.dp * scaleFactor))
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Completed",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp * scaleFactor)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp * scaleFactor))
            Text(
                text = when {
                    assignee.isCompleted -> "Completed task"
                    isAccepted -> "Accepted invite"
                    else -> "Pending response"
                },
                fontSize = 13.sp * scaleFactor,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
        }

        // Actions/Buttons on Right
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Nudge Action Button (Small 32.dp rounded button)
            if (!isCurrentUser && assignee.status == "pending" && onNudge != null) {
                val isNudgeAllowed = canNudge?.invoke(assignee.uid) ?: true
                val cooldown = nudgeCooldownRemaining?.invoke(assignee.uid) ?: 0L
                val nudgeInteraction = remember { MutableInteractionSource() }

                Button(
                    onClick = { if (isNudgeAllowed) onNudge(assignee.uid) },
                    enabled = isNudgeAllowed,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 10.dp * scaleFactor, vertical = 4.dp * scaleFactor),
                    shape = RoundedCornerShape(8.dp * scaleFactor),
                    modifier = Modifier
                        .height(32.dp * scaleFactor)
                        .expressivePressScale(nudgeInteraction)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp * scaleFactor)
                    )
                    Spacer(modifier = Modifier.width(4.dp * scaleFactor))
                    Text(
                        text = if (isNudgeAllowed) "Nudge" else "${cooldown}s",
                        fontSize = 12.sp * scaleFactor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Options menu (if admin managing member)
            if (canManage) {
                Box {
                    val menuInteraction = remember { MutableInteractionSource() }
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.expressivePressScale(menuInteraction)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Transfer Ownership") },
                            onClick = {
                                showMenu = false
                                onTransferOwnership()
                            },
                            leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Remove Member", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onRemoveMember()
                            },
                            leadingIcon = { Icon(Icons.Default.PersonRemove, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }
        }
    }
}

/** Expressive Press Scale Modifier implementing Rule #3 of custom-ui-design-guidelines. */
@Composable
private fun Modifier.expressivePressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.92f
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "expressivePressScale"
    )
    return this.scale(scale)
}
