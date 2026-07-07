package com.theblankstate.preamble.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
    var route by remember { mutableStateOf(SocialHubRoute.Friends) }
    var isTabRowVisible by remember { mutableStateOf(true) }

    LaunchedEffect(initialRoute) {
        if (initialRoute != null) {
            route = initialRoute
            onInitialRouteConsumed()
        }
    }

    LaunchedEffect(route) {
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        AnimatedVisibility(
            visible = isTabRowVisible,
            enter = expandVertically(animationSpec = tween(250)) + slideInVertically(animationSpec = tween(250)) { -it } + fadeIn(animationSpec = tween(250)),
            exit = shrinkVertically(animationSpec = tween(250)) + slideOutVertically(animationSpec = tween(250)) { -it } + fadeOut(animationSpec = tween(250))
        ) {
            SocialHubTabRow(selected = route, onSelect = { route = it })
        }

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
                        onClose = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                    SocialHubRoute.Circles -> CirclesScreen(
                        viewModel = circleViewModel,
                        onClose = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                    SocialHubRoute.Tasks -> WorkspaceTasksScreen(
                        workspaceViewModel = workspaceViewModel,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/**
 * Compact, persistent pill row that switches between the hub's [SocialHubRoute]s. Deliberately
 * mirrors the app's existing Expressive segmented-control language (the Leaderboard/Friends
 * toggle in `SectionOrganizer`, FriendsScreen.kt) — same pill shape, same selected-state fill
 * from the established [CardColors] palette — so the new router reads as a native part of the
 * social surface rather than a bolted-on addition.
 */
@Composable
private fun SocialHubTabRow(
    selected: SocialHubRoute,
    onSelect: (SocialHubRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SocialHubRoute.entries.forEach { entry ->
            val isSelected = entry == selected
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
                    .height(44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(backgroundColor)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                    ) { onSelect(entry) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = entry.icon,
                    contentDescription = entry.label,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = entry.label,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = contentColor,
                )
            }
        }
    }
}

/** Reuses the established Cardfolio [CardColors] palette so the tab row's selected pill reads as part of the same design language as the Friends/Circles cards beneath it. */
private fun routeAccentColor(route: SocialHubRoute): Color = when (route) {
    SocialHubRoute.Friends -> CardColors[1] // Light Purple
    SocialHubRoute.Circles -> CardColors[0] // Light Blue
    SocialHubRoute.Tasks -> CardColors[2] // Yellow
}
