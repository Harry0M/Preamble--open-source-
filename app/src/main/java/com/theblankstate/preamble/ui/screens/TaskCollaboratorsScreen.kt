package com.theblankstate.preamble.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.theblankstate.preamble.collab.Reactions
import com.theblankstate.preamble.collab.TaskReaction
import com.theblankstate.preamble.data.CollabAssigneeStatus
import com.theblankstate.preamble.data.Task
import com.theblankstate.preamble.ui.components.AvatarStatusStyle
import com.theblankstate.preamble.ui.components.MemberAvatar
import com.theblankstate.preamble.ui.components.avatarStatusStyle

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
 * Dedicated Task Collaborators Screen:
 * - Reduced font sizes for a sleek, refined, minimalist look.
 * - Edge-to-edge layout with status bar color matched to app background.
 * - Circular FAB button opening a ModalBottomSheet for reactions.
 * - Per-member reaction badges and clean borderless minimalist list layout complying with `custom-ui-design-guidelines`.
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
    // Dynamic Responsive Scaling
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val scaleFactor = (screenWidth.value / 360f).coerceIn(0.85f, 1.15f)

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showReactionSheet by remember { mutableStateOf(false) }

    // Dialog states
    var memberPendingRemoval by remember { mutableStateOf<Pair<String, String>?>(null) }
    var memberPendingTransfer by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showLeaveTaskConfirm by remember { mutableStateOf(false) }

    val adminUid = task.collabAdminUid
    val adminName = task.collabAdminName ?: "Admin"
    val assignees = task.collabAssignees
    val reactions = task.collabReactions
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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Collaborators",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp * scaleFactor,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp * scaleFactor
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
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(20.dp * scaleFactor)
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
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(20.dp * scaleFactor)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                modifier = Modifier.statusBarsPadding()
            )
        },
        floatingActionButton = {
            if (onReact != null && (isCurrentUserMember || isCurrentUserAdmin)) {
                val fabInteraction = remember { MutableInteractionSource() }
                FloatingActionButton(
                    onClick = { showReactionSheet = true },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(48.dp * scaleFactor)
                        .navigationBarsPadding()
                        .expressivePressScale(fabInteraction)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = "🔥", fontSize = 20.sp * scaleFactor)
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Animated Search Field
            AnimatedVisibility(
                visible = isSearchExpanded,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search collaborator...", fontSize = 12.sp * scaleFactor) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp * scaleFactor)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp * scaleFactor))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp * scaleFactor, vertical = 4.dp * scaleFactor),
                    shape = RoundedCornerShape(50),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)
                    )
                )
            }

            // Ultra-Minimal Hero Header
            PreambleMinimalHeroHeader(
                adminName = adminName,
                totalCount = totalCount,
                acceptedCount = acceptedCount,
                pendingCount = pendingCount,
                completedCount = completedCount,
                assignees = visibleAssignees,
                scaleFactor = scaleFactor
            )

            // Ultra-Minimal Animated Filter Tabs
            PreambleMinimalFilterTabs(
                selectedFilter = selectedFilter,
                onSelectFilter = { selectedFilter = it },
                counts = mapOf(
                    "All" to totalCount,
                    "Accepted" to acceptedCount,
                    "Pending" to pendingCount,
                    "Completed" to completedCount
                ),
                scaleFactor = scaleFactor
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                thickness = 1.dp
            )

            // Animated List Transition
            AnimatedContent(
                targetState = selectedFilter to searchQuery,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                        slideInVertically(animationSpec = spring(stiffness = Spring.StiffnessLow))) togetherWith
                        (fadeOut(animationSpec = tween(150)) + slideOutVertically())
                },
                label = "collaboratorListAnimation",
                modifier = Modifier.weight(1f)
            ) { (_, _) ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 2.dp * scaleFactor, bottom = 72.dp * scaleFactor)
                ) {
                    // Admin Row
                    if (adminUid != null && (searchQuery.isBlank() || adminName.contains(searchQuery, ignoreCase = true))) {
                        if (selectedFilter == "All" || selectedFilter == "Accepted") {
                            item(key = "admin_row") {
                                val adminReaction = reactions.firstOrNull { it.reactorUid == adminUid }?.emoji
                                PreambleAdminMemberListRow(
                                    name = adminName,
                                    isCurrentUser = currentUserUid == adminUid,
                                    reactionEmoji = adminReaction,
                                    scaleFactor = scaleFactor
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(start = 68.dp * scaleFactor)
                                )
                            }
                        }
                    }

                    // Assignee Rows
                    items(filteredAssignees, key = { it.uid }) { assignee ->
                        val memberReaction = reactions.firstOrNull { it.reactorUid == assignee.uid }?.emoji
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
                            reactionEmoji = memberReaction,
                            scaleFactor = scaleFactor
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(start = 68.dp * scaleFactor)
                        )
                    }

                    // Leave Task Button
                    if (showRoleControls && isCurrentUserMember && !isCurrentUserAdmin && onLeaveTask != null) {
                        item(key = "leave_task_row") {
                            Spacer(modifier = Modifier.height(14.dp * scaleFactor))
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
                                        .height(40.dp * scaleFactor)
                                        .expressivePressScale(leaveInteraction)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PersonRemove,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(15.dp * scaleFactor)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp * scaleFactor))
                                    Text(
                                        text = "Leave This Task",
                                        fontSize = 13.sp * scaleFactor,
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
    }

    // Reaction Modal Bottom Sheet
    if (showReactionSheet && onReact != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showReactionSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            PreambleReactionBottomSheetContent(
                reactions = reactions,
                currentUserUid = currentUserUid,
                onReact = { emoji ->
                    onReact(emoji)
                    showReactionSheet = false
                },
                scaleFactor = scaleFactor
            )
        }
    }

    // Confirmation Dialogs
    memberPendingRemoval?.let { (uid, name) ->
        AlertDialog(
            onDismissRequest = { memberPendingRemoval = null },
            title = { Text("Remove Member", fontSize = 16.sp) },
            text = { Text("Remove $name from this task?", fontSize = 13.sp) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveMember?.invoke(uid)
                        memberPendingRemoval = null
                    }
                ) { Text("Remove", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            },
            dismissButton = {
                TextButton(onClick = { memberPendingRemoval = null }) { Text("Cancel", fontSize = 13.sp) }
            }
        )
    }

    memberPendingTransfer?.let { (uid, name) ->
        AlertDialog(
            onDismissRequest = { memberPendingTransfer = null },
            title = { Text("Transfer Ownership", fontSize = 16.sp) },
            text = { Text("Make $name the primary owner of this task?", fontSize = 13.sp) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onTransferOwnership?.invoke(uid)
                        memberPendingTransfer = null
                    }
                ) { Text("Transfer", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            },
            dismissButton = {
                TextButton(onClick = { memberPendingTransfer = null }) { Text("Cancel", fontSize = 13.sp) }
            }
        )
    }

    if (showLeaveTaskConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveTaskConfirm = false },
            title = { Text("Leave Task", fontSize = 16.sp) },
            text = { Text("You will no longer receive updates or notifications for this task.", fontSize = 13.sp) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onLeaveTask?.invoke()
                        showLeaveTaskConfirm = false
                        onBack()
                    }
                ) { Text("Leave", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveTaskConfirm = false }) { Text("Cancel", fontSize = 13.sp) }
            }
        )
    }
}

/** Ultra-Minimal Hero Header with Animated Counters & Overlapping Avatar Stack. */
@Composable
private fun PreambleMinimalHeroHeader(
    adminName: String,
    totalCount: Int,
    acceptedCount: Int,
    pendingCount: Int,
    completedCount: Int,
    assignees: List<CollabAssigneeStatus>,
    scaleFactor: Float
) {
    val animatedTotal by animateIntAsState(targetValue = totalCount, label = "animatedTotal")
    val animatedActive by animateIntAsState(targetValue = acceptedCount, label = "animatedActive")
    val animatedPending by animateIntAsState(targetValue = pendingCount, label = "animatedPending")

    val heroBg = PreambleCardColors[0].copy(alpha = 0.18f)

    Surface(
        color = heroBg,
        shape = RoundedCornerShape(16.dp * scaleFactor),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp * scaleFactor, vertical = 4.dp * scaleFactor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp * scaleFactor, vertical = 12.dp * scaleFactor),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$animatedTotal Team Members",
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp * scaleFactor,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp * scaleFactor))
                Text(
                    text = "Owner: $adminName • $animatedActive active • $animatedPending pending",
                    fontSize = 11.sp * scaleFactor,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            // Overlapping Avatar Stack (Rule #4)
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
        horizontalArrangement = Arrangement.spacedBy((-10).dp * scaleFactor)
    ) {
        displayedMembers.forEach { (name, photo) ->
            MemberAvatar(
                photoUrl = photo,
                name = name,
                style = AvatarStatusStyle.FILLED,
                shape = CircleShape,
                modifier = Modifier
                    .size(32.dp * scaleFactor)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
        if (overflow > 0) {
            Box(
                modifier = Modifier
                    .size(32.dp * scaleFactor)
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
                    fontSize = 10.sp * scaleFactor
                )
            }
        }
    }
}

/** Ultra-Minimal Animated Filter Tabs with Smooth Pill Transitions. */
@Composable
private fun PreambleMinimalFilterTabs(
    selectedFilter: String,
    onSelectFilter: (String) -> Unit,
    counts: Map<String, Int>,
    scaleFactor: Float
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp * scaleFactor, vertical = 4.dp * scaleFactor),
        horizontalArrangement = Arrangement.spacedBy(6.dp * scaleFactor),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf("All", "Accepted", "Pending", "Completed").forEach { filter ->
            val isSelected = selectedFilter == filter
            val tabInteraction = remember { MutableInteractionSource() }
            val count = counts[filter] ?: 0

            val bgAlpha by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0.4f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "tabBgAlpha"
            )

            Surface(
                onClick = { onSelectFilter(filter) },
                shape = RoundedCornerShape(50),
                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = bgAlpha) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = bgAlpha),
                modifier = Modifier.expressivePressScale(tabInteraction)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp * scaleFactor, vertical = 6.dp * scaleFactor),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = filter,
                        fontSize = 12.sp * scaleFactor,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp * scaleFactor))
                    Text(
                        text = "($count)",
                        fontSize = 10.sp * scaleFactor,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/** Admin Row in Clean Minimalist Borderless List format with floating reaction badge (Rule #1). */
@Composable
private fun PreambleAdminMemberListRow(
    name: String,
    isCurrentUser: Boolean,
    reactionEmoji: String?,
    scaleFactor: Float
) {
    val anchorColor = PreambleCardColors[0] // Soft Blue

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp * scaleFactor, vertical = 8.dp * scaleFactor),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Visual Anchor on Left: 44.dp circular background filled with soft accent color (Rule #1)
        Box(contentAlignment = Alignment.BottomEnd) {
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
                    modifier = Modifier.size(20.dp * scaleFactor)
                )
            }
            // Floating reaction badge overlaid on avatar anchor
            if (!reactionEmoji.isNullOrBlank()) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.size(18.dp * scaleFactor)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = reactionEmoji, fontSize = 10.sp * scaleFactor)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp * scaleFactor))

        // Metadata in Middle: Bold title (14.sp) + secondary label (Rule #1)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isCurrentUser) "$name (You)" else name,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp * scaleFactor,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(1.dp * scaleFactor))
            Text(
                text = "Task Owner • Admin",
                fontSize = 11.5.sp * scaleFactor,
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
                modifier = Modifier.padding(horizontal = 8.dp * scaleFactor, vertical = 3.dp * scaleFactor),
                fontSize = 10.sp * scaleFactor,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** Assignee Member Row in Clean Minimalist Borderless List format with floating reaction badge (Rule #1). */
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
    reactionEmoji: String?,
    scaleFactor: Float
) {
    var showMenu by remember { mutableStateOf(false) }

    val isAccepted = assignee.status == "accepted"
    val colorIdx = (assignee.uid.hashCode().coerceAtLeast(0)) % PreambleCardColors.size
    val anchorColor = PreambleCardColors[colorIdx]

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp * scaleFactor, vertical = 8.dp * scaleFactor),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Visual Anchor on Left: 44.dp circular background filled with soft accent color (Rule #1)
        Box(contentAlignment = Alignment.BottomEnd) {
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
            // Floating reaction badge overlaid on avatar anchor
            if (!reactionEmoji.isNullOrBlank()) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.size(18.dp * scaleFactor)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = reactionEmoji, fontSize = 10.sp * scaleFactor)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp * scaleFactor))

        // Metadata in Middle: Bold title (14.sp) + secondary label (Rule #1)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isCurrentUser) "${assignee.name} (You)" else assignee.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp * scaleFactor,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (assignee.isCompleted) {
                    Spacer(modifier = Modifier.width(4.dp * scaleFactor))
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Completed",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp * scaleFactor)
                    )
                }
            }
            Spacer(modifier = Modifier.height(1.dp * scaleFactor))
            Text(
                text = when {
                    assignee.isCompleted -> "Completed task"
                    isAccepted -> "Accepted invite"
                    else -> "Pending response"
                },
                fontSize = 11.5.sp * scaleFactor,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
        }

        // Actions/Buttons on Right
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Nudge Action Button
            if (!isCurrentUser && assignee.status == "pending" && onNudge != null) {
                val isNudgeAllowed = canNudge?.invoke(assignee.uid) ?: true
                val cooldown = nudgeCooldownRemaining?.invoke(assignee.uid) ?: 0L
                val nudgeInteraction = remember { MutableInteractionSource() }

                Button(
                    onClick = { if (isNudgeAllowed) onNudge(assignee.uid) },
                    enabled = isNudgeAllowed,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 8.dp * scaleFactor, vertical = 2.dp * scaleFactor),
                    shape = RoundedCornerShape(8.dp * scaleFactor),
                    modifier = Modifier
                        .height(28.dp * scaleFactor)
                        .expressivePressScale(nudgeInteraction)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp * scaleFactor)
                    )
                    Spacer(modifier = Modifier.width(3.dp * scaleFactor))
                    Text(
                        text = if (isNudgeAllowed) "Nudge" else "${cooldown}s",
                        fontSize = 11.sp * scaleFactor,
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
                        modifier = Modifier
                            .size(32.dp * scaleFactor)
                            .expressivePressScale(menuInteraction)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp * scaleFactor)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Transfer Ownership", fontSize = 13.sp) },
                            onClick = {
                                showMenu = false
                                onTransferOwnership()
                            },
                            leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        DropdownMenuItem(
                            text = { Text("Remove Member", color = MaterialTheme.colorScheme.error, fontSize = 13.sp) },
                            onClick = {
                                showMenu = false
                                onRemoveMember()
                            },
                            leadingIcon = { Icon(Icons.Default.PersonRemove, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
            }
        }
    }
}

/** Content for Reaction Modal Bottom Sheet opened by the FAB button. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreambleReactionBottomSheetContent(
    reactions: List<TaskReaction>,
    currentUserUid: String?,
    onReact: (String) -> Unit,
    scaleFactor: Float
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp * scaleFactor, vertical = 14.dp * scaleFactor)
            .navigationBarsPadding()
    ) {
        Text(
            text = "React to Task",
            fontWeight = FontWeight.Black,
            fontSize = 16.sp * scaleFactor,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Express yourself to your team members",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            fontSize = 11.sp * scaleFactor
        )

        Spacer(modifier = Modifier.height(14.dp * scaleFactor))

        // Interactive 6-Emoji Set
        val myEmoji = reactions.firstOrNull { it.reactorUid == currentUserUid }?.emoji

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Reactions.EMOJI_SET.forEach { emoji ->
                val isSelected = emoji == myEmoji
                val emojiInteraction = remember { MutableInteractionSource() }

                Surface(
                    onClick = { onReact(emoji) },
                    shape = CircleShape,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    },
                    border = if (isSelected) {
                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    } else null,
                    modifier = Modifier
                        .size(40.dp * scaleFactor)
                        .expressivePressScale(emojiInteraction)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = emoji, fontSize = 20.sp * scaleFactor)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp * scaleFactor))

        // Current reactions list in bottom sheet
        if (reactions.isNotEmpty()) {
            Text(
                text = "Current Reactions (${reactions.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp * scaleFactor,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp * scaleFactor))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp * scaleFactor),
                verticalArrangement = Arrangement.spacedBy(4.dp * scaleFactor)
            ) {
                reactions.forEach { reaction ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp * scaleFactor, vertical = 4.dp * scaleFactor),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = reaction.emoji, fontSize = 13.sp * scaleFactor)
                            Spacer(modifier = Modifier.width(4.dp * scaleFactor))
                            Text(
                                text = reaction.reactorName,
                                fontSize = 11.sp * scaleFactor,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
