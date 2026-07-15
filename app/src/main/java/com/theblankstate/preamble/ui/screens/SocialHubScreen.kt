package com.theblankstate.preamble.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.collectAsState
import com.theblankstate.preamble.ui.viewmodels.WorkspaceUiState
import com.theblankstate.preamble.collab.PreambleId
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.theblankstate.preamble.ui.viewmodels.CircleViewModel
import com.theblankstate.preamble.ui.viewmodels.WorkspaceViewModel
import com.theblankstate.preamble.viewmodel.TaskViewModel

/**
 * The organized, top-level destinations reachable from the single "Circles" bottom-nav tab.
 * Consolidates what used to be three separate entry points — the avatar-triggered Friends
 * overlay, its nested "Circles" pill, and the bottom-nav "Workspace" tab — into one hub so the
 * whole social/collaboration feature area lives behind one predictable tap target instead of
 * being scattered across the app.
 */
enum class SocialHubRoute(val label: String, val icon: ImageVector) {
    Friends("Friends", Icons.Default.Group),
    Circles("Circles", Icons.Default.Groups),
    Tasks("Tasks", Icons.AutoMirrored.Filled.Assignment),
}

/**
 * Circles hub router. Renders a persistent, compact route switcher above whichever destination
 * is active, so Friends (with Leaderboard nested one tap inside it, unchanged), Circles, and
 * shared/assigned Tasks are three clearly separated, individually full-height screens instead of
 * being stacked together on one crowded page.
 *
 * Each destination keeps its existing implementation and ViewModel wiring completely untouched —
 * [FriendsScreen], [CirclesScreen], and [WorkspaceTasksScreen] are reused exactly as they were,
 * so no data flow, Firestore access, or business logic changes. This screen only supplies
 * navigation between them.
 *
 * [initialRoute] lets a caller — a deep link, or the friend-avatars shortcut in [HomeScreen]'s
 * top bar — request a specific landing destination the next time this hub is shown. It is
 * consumed exactly once via [onInitialRouteConsumed] so switching tabs by hand afterwards doesn't
 * keep resetting the route back.
 */
@Composable
fun SocialHubScreen(
    workspaceViewModel: WorkspaceViewModel,
    circleViewModel: CircleViewModel = viewModel(),
    taskViewModel: TaskViewModel? = null,
    initialInviteId: String? = null,
    onInviteConsumed: () -> Unit = {},
    initialRoute: SocialHubRoute? = null,
    onInitialRouteConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var route by remember { mutableStateOf(initialRoute ?: SocialHubRoute.Friends) }
    var previousRoute by remember { mutableStateOf(SocialHubRoute.Friends) }
    var isTabRowVisible by remember { mutableStateOf(true) }

    var showAddFriendDialog by remember { mutableStateOf(false) }
    var targetId by remember { mutableStateOf("") }
    var triggerCreateCircle by remember { mutableStateOf<(() -> Unit)?>(null) }
    val deepLinkInviteToPresent by workspaceViewModel.deepLinkInviteToPresent.collectAsState()
    val uiState by workspaceViewModel.uiState.collectAsState()

    LaunchedEffect(deepLinkInviteToPresent) {
        val pending = deepLinkInviteToPresent
        if (pending != null) {
            targetId = pending
            showAddFriendDialog = true
            workspaceViewModel.onInviteConsumed()
            onInviteConsumed()
        }
    }

    val requestsSections by workspaceViewModel.requestsSections.collectAsState()
    val friends by workspaceViewModel.friends.collectAsState()
    val lastCheckedTimestamp = workspaceViewModel.lastCheckedNotificationsTimestamp

    val pendingRequestsCount = remember(requestsSections, friends, lastCheckedTimestamp) {
        requestsSections.incoming.count { it.timestamp > lastCheckedTimestamp } +
        friends.count { it.addedAt > lastCheckedTimestamp }
    }
    val incomingAssignments by workspaceViewModel.incomingAssignments.collectAsState()
    val pendingAssignmentsCount = remember(incomingAssignments, lastCheckedTimestamp) {
        incomingAssignments.count { it.assignmentStatus == "pending" && it.createdTimestamp > lastCheckedTimestamp }
    }

    LaunchedEffect(initialRoute) {
        if (initialRoute != null) {
            route = initialRoute
            onInitialRouteConsumed()
        }
    }

    LaunchedEffect(route) {
        if (route != SocialHubRoute.Tasks) {
            previousRoute = route
        }
        isTabRowVisible = true
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -10f) {
                    isTabRowVisible = false
                } else if (available.y > 10f) {
                    isTabRowVisible = true
                }
                return Offset.Zero
            }
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val scaleFactor = (screenWidth / 360f).coerceIn(0.85f, 1.15f)

    val buttonSize = (48.dp * scaleFactor)
    val tabHeight = buttonSize - 8.dp
    val spacing = (8.dp * scaleFactor)
    val horizontalPadding = (16.dp * scaleFactor)
    val iconSize = (18.dp * scaleFactor)
    val fontSize = (14.sp * scaleFactor)
    val headerHeight = buttonSize + 24.dp

    val currentHeaderHeight by animateDpAsState(
        targetValue = if (isTabRowVisible && route != SocialHubRoute.Tasks) headerHeight else 0.dp,
        animationSpec = tween(300),
        label = "headerHeight"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        // 1. Content Pane (Friends / Circles / Tasks)
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(currentHeaderHeight))

            Box(modifier = Modifier.weight(1f)) {
                // Expressive_Motion: panes slide+fade in the direction of travel, mirroring the
                // same transition FriendsScreen already uses for its own Leaderboard/Friends
                // toggle, so switching hub destinations feels consistent with the rest of the app.
                AnimatedContent(
                    targetState = route,
                    transitionSpec = {
                        val forward = targetState.ordinal > initialState.ordinal
                        val direction = if (forward) 1 else -1
                        (slideInHorizontally(animationSpec = tween(300)) { full -> direction * full } +
                            fadeIn(animationSpec = tween(300))) togetherWith
                            (slideOutHorizontally(animationSpec = tween(300)) { full -> -direction * full } +
                                fadeOut(animationSpec = tween(300)))
                    },
                    label = "socialHubRoute",
                ) { targetRoute ->
                    when (targetRoute) {
                        SocialHubRoute.Friends -> FriendsScreen(
                            viewModel = workspaceViewModel,
                            taskViewModel = taskViewModel,
                            initialInviteId = initialInviteId,
                            onInviteConsumed = onInviteConsumed,
                            onAddFriendClick = { showAddFriendDialog = true },
                            onClose = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                        SocialHubRoute.Circles -> CirclesScreen(
                            viewModel = circleViewModel,
                            onClose = null,
                            registerCreateCircleTrigger = { triggerCreateCircle = it },
                            modifier = Modifier.fillMaxSize(),
                        )
                        SocialHubRoute.Tasks -> NotificationCenterScreen(
                            viewModel = workspaceViewModel,
                            onClose = { route = previousRoute },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        // 2. The Header Overlay (Pills & Buttons)
        if (route != SocialHubRoute.Tasks) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                // Segmented control for Friends and Circles
                AnimatedVisibility(
                    visible = isTabRowVisible,
                    enter = slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn(animationSpec = tween(300)),
                    exit = slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut(animationSpec = tween(300)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SocialHubRoute.entries.filter { it != SocialHubRoute.Tasks }.forEach { entry ->
                            val isSelected = entry == route
                            val accent = routeAccentColor(entry)
                            val backgroundColor by animateColorAsState(
                                targetValue = if (isSelected) accent else Color.Transparent,
                                label = "hubTab_${entry.name}",
                            )
                            val contentColor = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                            val interactionSource = remember { MutableInteractionSource() }
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(tabHeight)
                                    .clip(RoundedCornerShape(50))
                                    .background(backgroundColor)
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null,
                                    ) { route = entry },
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = entry.icon,
                                    contentDescription = entry.label,
                                    tint = contentColor,
                                    modifier = Modifier.size(iconSize),
                                )
                                Spacer(modifier = Modifier.width(8.dp * scaleFactor))
                                Text(
                                    text = entry.label,
                                    fontWeight = FontWeight.Black,
                                    fontSize = fontSize,
                                    color = contentColor,
                                )
                            }
                        }
                    }
                }

                if (isTabRowVisible) {
                    Spacer(modifier = Modifier.width(spacing))
                }

                // The 2 Circular Buttons Row (Tasks & Add Friend)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    // Separated circular Tasks button with a dotted downward arrow and badge overlay
                    val isTasksSelected = route == SocialHubRoute.Tasks
                    val accent = routeAccentColor(SocialHubRoute.Tasks)
                    val backgroundColor by animateColorAsState(
                        targetValue = if (isTasksSelected) accent else MaterialTheme.colorScheme.surfaceVariant,
                        label = "hubTab_Tasks",
                    )
                    val interactionSource = remember { MutableInteractionSource() }

                    Box {
                        Box(
                            modifier = Modifier
                                .size(buttonSize)
                                .clip(CircleShape)
                                .background(backgroundColor)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                ) { route = SocialHubRoute.Tasks },
                            contentAlignment = Alignment.Center
                        ) {
                            DottedDownwardArrow(
                                color = if (isTasksSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp * scaleFactor)
                            )
                        }

                        val showFriendRequestBadge = pendingRequestsCount > 0
                        val showTaskBadge = pendingAssignmentsCount > 0 && !showFriendRequestBadge

                        if (showFriendRequestBadge || showTaskBadge) {
                            val badgeIcon = if (showFriendRequestBadge) Icons.Default.Person else Icons.AutoMirrored.Filled.Assignment
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 2.dp, y = (-2).dp)
                                    .size(20.dp * scaleFactor)
                                    .clip(CircleShape)
                                    .background(Color.Red),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = badgeIcon,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp * scaleFactor)
                                )
                            }
                        }
                    }

                    // Separated circular Add Friend button (Friends screen context)
                    if (route == SocialHubRoute.Friends) {
                        val addInteraction = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .size(buttonSize)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(
                                    interactionSource = addInteraction,
                                    indication = null,
                                ) { showAddFriendDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GroupAdd,
                                contentDescription = "Add Friend",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(iconSize)
                            )
                        }
                    }
                }
            }
        }

        // 3. Floating Morphing "New" Button for Circles Screen
        val targetXOffset = if (isTabRowVisible) 24.dp else (horizontalPadding + buttonSize + spacing)
        val currentXOffset by animateDpAsState(targetXOffset, animationSpec = tween(300), label = "circleButtonX")
        val paddingY by animateDpAsState(if (isTabRowVisible) 16.dp else 12.dp, animationSpec = tween(300), label = "circleButtonY")
        val targetWidth = if (isTabRowVisible) 76.dp else buttonSize
        val currentWidth by animateDpAsState(targetWidth, animationSpec = tween(300), label = "circleButtonWidth")
        val targetHeight = if (isTabRowVisible) 36.dp else buttonSize
        val currentHeight by animateDpAsState(targetHeight, animationSpec = tween(300), label = "circleButtonHeight")

        val textAlpha by animateFloatAsState(if (isTabRowVisible) 1f else 0f, animationSpec = tween(150), label = "circleButtonTextAlpha")
        val currentIconSize by animateDpAsState(if (isTabRowVisible) 18.dp else iconSize, animationSpec = tween(300), label = "circleButtonIconSize")
        val currentPadHorizontal by animateDpAsState(if (isTabRowVisible) 12.dp else 0.dp, animationSpec = tween(300), label = "circleButtonPad")

        val addCircleInteraction = remember { MutableInteractionSource() }

        AnimatedVisibility(
            visible = route == SocialHubRoute.Circles,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .offset(x = -currentXOffset, y = currentHeaderHeight + paddingY)
                    .size(width = currentWidth, height = currentHeight)
                    .clip(RoundedCornerShape(50))
                    .clickable(
                        interactionSource = addCircleInteraction,
                        indication = null,
                    ) { triggerCreateCircle?.invoke() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = currentPadHorizontal),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create Circle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(currentIconSize)
                    )
                    if (isTabRowVisible && textAlpha > 0.05f) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "New",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp * scaleFactor,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = textAlpha),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }

    if (showAddFriendDialog) {
        InviteEntrySheet(
            value = targetId,
            onValueChange = { targetId = PreambleId.normalize(it) },
            sendEnabled = !PreambleId.isBlank(targetId) && uiState !is WorkspaceUiState.Loading,
            onSend = {
                workspaceViewModel.sendInvite(targetId)
                showAddFriendDialog = false
            },
            onDismiss = { showAddFriendDialog = false },
            viewModel = workspaceViewModel,
        )
    }
}

@Composable
private fun DottedDownwardArrow(color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(24.dp)) {
        val width = size.width
        val height = size.height
        val dashEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)

        // Vertical arrow shaft
        drawLine(
            color = color,
            start = Offset(width / 2f, height / 5f),
            end = Offset(width / 2f, 4f * height / 5f),
            strokeWidth = 2.dp.toPx(),
            pathEffect = dashEffect
        )
        // Arrow head left side
        drawLine(
            color = color,
            start = Offset(width / 2f - width / 5f, 4f * height / 5f - height / 5f),
            end = Offset(width / 2f, 4f * height / 5f),
            strokeWidth = 2.dp.toPx(),
            pathEffect = dashEffect
        )
        // Arrow head right side
        drawLine(
            color = color,
            start = Offset(width / 2f + width / 5f, 4f * height / 5f - height / 5f),
            end = Offset(width / 2f, 4f * height / 5f),
            strokeWidth = 2.dp.toPx(),
            pathEffect = dashEffect
        )
    }
}

/** Reuses the established Cardfolio [CardColors] palette so the tab row's selected pill reads as part of the same design language as the Friends/Circles cards beneath it. */
private fun routeAccentColor(route: SocialHubRoute): Color = when (route) {
    SocialHubRoute.Friends -> CardColors[1] // Light Purple
    SocialHubRoute.Circles -> CardColors[0] // Light Blue
    SocialHubRoute.Tasks -> CardColors[2] // Yellow
}
