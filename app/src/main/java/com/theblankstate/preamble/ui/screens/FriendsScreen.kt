package com.theblankstate.preamble.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import kotlin.math.roundToInt
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import coil.compose.AsyncImage
import com.theblankstate.preamble.R
import com.theblankstate.preamble.collab.OutgoingInvite
import com.theblankstate.preamble.collab.Leaderboard
import com.theblankstate.preamble.collab.PageWindow
import com.theblankstate.preamble.collab.PreambleId
import com.theblankstate.preamble.collab.RequestsListOrganizer
import com.theblankstate.preamble.collab.SocialSearch
import com.theblankstate.preamble.repository.Friend
import com.theblankstate.preamble.repository.WorkspaceInvite
import com.theblankstate.preamble.ui.components.LeaderboardEntryRow
import com.theblankstate.preamble.ui.components.ReferralCta
import com.theblankstate.preamble.ui.viewmodels.FriendRemovalImpact
import com.theblankstate.preamble.ui.viewmodels.SocialHubLoadState
import com.theblankstate.preamble.ui.viewmodels.WorkspaceUiState
import com.theblankstate.preamble.ui.viewmodels.WorkspaceViewModel
import com.theblankstate.preamble.viewmodel.TaskViewModel

// A vibrant color palette for the stacked cards inspired by Cardfolio
val CardColors = listOf(
    Color(0xFFA1C6FF), // Light Blue
    Color(0xFFEAB3FF), // Light Purple
    Color(0xFFFFD166), // Yellow
    Color(0xFFFF9E9E), // Light Red/Coral
    Color(0xFF9EE8FF), // Cyan
    Color(0xFFFFC085)  // Orange
)

val RandomBackgrounds = listOf(
    R.drawable.astro, R.drawable.bueno, R.drawable.chaotic_good, R.drawable.chillin,
    R.drawable.chilly, R.drawable.coffee, R.drawable.consumer, R.drawable.cube_leg,
    R.drawable.ecto_plasma, R.drawable.entertainment, R.drawable.experiments, R.drawable.feliz,
    R.drawable.fling, R.drawable.gamestation, R.drawable.groceries, R.drawable.growth,
    R.drawable.jumping_air, R.drawable.kiddo, R.drawable.late_for_class, R.drawable.looking_ahead,
    R.drawable.mask, R.drawable.mechanical_love, R.drawable.meela_pantalones, R.drawable.new_beginnings,
    R.drawable.pacheco, R.drawable.pilot, R.drawable.plants, R.drawable.polka_pup,
    R.drawable.pondering, R.drawable.puppy, R.drawable.reflecting, R.drawable.roboto,
    R.drawable.rogue, R.drawable.runner, R.drawable.waiting, R.drawable.walking_contradiction,
    R.drawable.whoa, R.drawable.wont_stop
)

/**
 * Expressive_Motion_And_Shape (social-hub-redesign Req 1.3): a Material 3 Expressive
 * morphing-shape treatment. Wraps an [androidx.graphics.shapes.Morph] (built from two
 * [RoundedPolygon]s) as a Compose [Shape] so a surface can smoothly morph between the
 * two polygons as [percentage] animates from 0f..1f. An optional [rotation] (degrees)
 * lets the shape slowly spin so the hero card feels "alive" rather than static.
 *
 * Assumes the underlying polygons use the default radius of 1f centered at (0f, 0f),
 * so the matrix scales the unit shape to fill the target [Size].
 */
class MorphPolygonShape(
    private val morph: Morph,
    private val percentage: Float,
    private val rotation: Float = 0f,
) : Shape {
    private val matrix = Matrix()

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        matrix.reset()
        matrix.scale(size.width / 2f, size.height / 2f)
        matrix.translate(1f, 1f)
        if (rotation != 0f) matrix.rotateZ(rotation)

        val path = morph.toPath(progress = percentage).asComposePath()
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

/**
 * Expressive_Motion_And_Shape (social-hub-redesign Req 1.2, 1.3): a tasteful Material 3
 * Expressive press treatment for the surface's primary controls (the top-bar Add/Circles
 * pills, the hero share button). Pressing the control springs it down with a bouncy
 * [spring] and releases it back, so the primary controls feel "alive" and responsive
 * rather than static — without altering the established visual identity. The control must
 * pass the same [interactionSource] to its `clickable` so this modifier can observe presses.
 */
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

/**
 * Friends_Screen: the Friends/Leaderboard destination inside the Circles hub
 * ([SocialHubScreen]). Formerly named `WorkspaceScreen` — renamed because that name described
 * where it lived, not what it shows. Circles management now lives in its own hub tab
 * ([CirclesScreen]) instead of being promoted from inside this screen, which is why the old
 * top-bar "Circles" pill and the CirclesEntryCard header promo are gone: removing that stacked
 * card reclaims significant vertical space so the Friends/Leaderboard list is no longer squeezed
 * off small screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    viewModel: WorkspaceViewModel = viewModel(),
    taskViewModel: TaskViewModel? = null,
    initialInviteId: String? = null,
    onInviteConsumed: () -> Unit = {},
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val friends by viewModel.friends.collectAsState()
    // Requests_List view model: the signed-in user's Outgoing_Invites and Incoming_Invites
    // grouped into separate sections (Req 5.3, 5.5) via RequestsListOrganizer.
    val requestsSections by viewModel.requestsSections.collectAsState()
    // PENDING REQUESTS COUNT (social-hub-redesign Req 5.6): the single source of truth for
    // the on-demand Requests control badge. Collected from the ViewModel's
    // pendingRequestsCount StateFlow (outgoing + incoming) rather than recomputed inline.
    val pendingRequestsCount by viewModel.pendingRequestsCount.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    // Deep-link invite presentation routed through the ViewModel so it is consumed
    // exactly once (Req 7.2, 7.3): the ViewModel exposes the pending Preamble_ID to
    // present (or null) via DeepLinkInviteState.
    val deepLinkInviteToPresent by viewModel.deepLinkInviteToPresent.collectAsState()
    // Friends_Leaderboard (social-engagement Requirements 9.1, 9.2, 9.6), ranked by current
    // Weekly_Window points in the ViewModel. Drives both the LeaderboardSection and the
    // current-week figure that supersedes the legacy friend.productivityPoints display.
    val leaderboard by viewModel.leaderboard.collectAsState()
    // SOCIAL_HUB LOAD STATE (social-hub-redesign Req 1.6, 1.7): Loading until the first
    // friends/leaderboard emission, Loaded once both have emitted, or Error(message) when a
    // list listener fails. Drives the Loading_State skeleton and the retry-able Error_State.
    val socialHubLoadState by viewModel.socialHubLoadState.collectAsState()
    val currentUserUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    val weeklyPointsByUid = remember(leaderboard) { leaderboard.associate { it.uid to it.weeklyPoints } }
    val statsState = taskViewModel?.statsState?.collectAsState()?.value

    val myScore = statsState?.productivityScore ?: 0

    val context = LocalContext.current
    var showAddFriendDialog by remember { mutableStateOf(initialInviteId != null) }
    // REQUESTS_LIST visibility (social-hub-redesign Req 5). Opened on a successful send via
    // the ViewModel's one-shot navigateToRequests event, and also reachable on demand from
    // the top-bar Requests control.
    var showRequestsList by remember { mutableStateOf(false) }
    var targetId by remember { mutableStateOf(initialInviteId ?: "") }
    var friendPendingRemoval by remember { mutableStateOf<Friend?>(null) }
    var removalImpact by remember { mutableStateOf<FriendRemovalImpact?>(null) }
    var transferOwnedTasks by remember { mutableStateOf(false) }

    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Pick random backgrounds on composition
    val heroBackground = remember { RandomBackgrounds.random() }

    // SECTION_ORGANIZER state (social-hub-redesign Req 2.1, 2.5): which pane is selected and a
    // separate LazyListState per pane so the Leaderboard and Friends_List each preserve their
    // own scroll position independently when the user switches between the two areas.
    var selectedSection by remember { mutableStateOf(SocialSection.Leaderboard) }
    val leaderboardState = rememberLazyListState()
    val friendsState = rememberLazyListState()
    // SOCIAL_SEARCH state (social-hub-redesign Req 9.1): a per-pane query so the Leaderboard
    // and the Friends_List each filter independently. The query is applied to the FULL
    // in-memory list before paging (Req 9.6); clearing it restores the unfiltered list (Req 9.4).
    var leaderboardQuery by remember { mutableStateOf("") }
    var friendsQuery by remember { mutableStateOf("") }

    val density = LocalDensity.current
    var headerHeightPx by remember { mutableStateOf(0f) }
    var scrollOffsetPx by remember { mutableStateOf(0f) }

    suspend fun animateScrollTo(target: Float) {
        if (headerHeightPx > 0f) {
            androidx.compose.animation.core.animate(
                initialValue = scrollOffsetPx,
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) { value, _ ->
                scrollOffsetPx = value
            }
        }
    }

    val nestedScrollConnection = remember(headerHeightPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta < 0 && scrollOffsetPx > -headerHeightPx) {
                    val oldOffset = scrollOffsetPx
                    scrollOffsetPx = (scrollOffsetPx + delta).coerceIn(-headerHeightPx, 0f)
                    val consumed = scrollOffsetPx - oldOffset
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta > 0 && scrollOffsetPx < 0f) {
                    val oldOffset = scrollOffsetPx
                    scrollOffsetPx = (scrollOffsetPx + delta).coerceIn(-headerHeightPx, 0f)
                    val consumed = scrollOffsetPx - oldOffset
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val deltaY = available.y
                if (headerHeightPx > 0f && scrollOffsetPx > -headerHeightPx && scrollOffsetPx < 0f) {
                    val target = if (deltaY < -100f || scrollOffsetPx < -headerHeightPx / 2f) {
                        -headerHeightPx
                    } else {
                        0f
                    }
                    animateScrollTo(target)
                    return Velocity(0f, deltaY)
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val deltaY = available.y
                if (headerHeightPx > 0f && deltaY > 100f && scrollOffsetPx < 0f) {
                    animateScrollTo(0f)
                    return Velocity(0f, deltaY)
                }
                return Velocity.Zero
            }
        }
    }
    val requestFriendRemoval: (Friend) -> Unit = { friend ->
        val impact = viewModel.friendRemovalImpact(friend.uid)
        if (impact.requiresResolution) {
            friendPendingRemoval = friend
            removalImpact = impact
            transferOwnedTasks = false
        } else {
            viewModel.removeFriend(friend.uid)
        }
    }

    // Handle Toast/Snackbar for UI state
    LaunchedEffect(uiState) {
        when (uiState) {
            is WorkspaceUiState.Success -> {
                android.widget.Toast.makeText(context, (uiState as WorkspaceUiState.Success).message, android.widget.Toast.LENGTH_SHORT).show()
                viewModel.resetState()
            }
            is WorkspaceUiState.Error -> {
                android.widget.Toast.makeText(context, (uiState as WorkspaceUiState.Error).message, android.widget.Toast.LENGTH_SHORT).show()
                viewModel.resetState()
            }
            else -> {}
        }
    }

    LaunchedEffect(initialInviteId) {
        if (initialInviteId != null) {
            // Hand the deep-linked id to the ViewModel, which normalizes and pre-fills it
            // and surfaces a not-found message if it cannot be resolved (Req 7.2, 7.4).
            viewModel.presentDeepLinkInvite(initialInviteId)
        }
    }

    // Present the deep-linked invite at most once: when the ViewModel exposes a pending
    // id, pre-fill the entry surface and open it, then consume it so re-rendering the
    // Social_Hub never re-presents the same invite (Req 7.3).
    LaunchedEffect(deepLinkInviteToPresent) {
        val pending = deepLinkInviteToPresent
        if (pending != null) {
            targetId = pending
            showAddFriendDialog = true
            viewModel.onInviteConsumed()
            onInviteConsumed()
        }
    }

    // Navigate the user to the Requests_List after a successful send (Req 5.1). The
    // ViewModel emits a one-shot event on navigateToRequests; by the time the list opens the
    // just-sent Outgoing_Invite is already present in requestsSections via the optimistic
    // outgoing mirror, so the user sees it immediately (Req 5.2).
    LaunchedEffect(Unit) {
        viewModel.navigateToRequests.collect {
            showRequestsList = true
        }
    }

    Scaffold(
        modifier = modifier,
        // ZERO INSETS: this screen is now hosted as a tab inside SocialHubScreen, which itself
        // sits inside MainActivity's outer Scaffold content slot — that outer Scaffold already
        // resolves the status-bar and bottom-nav-bar insets once via its own innerPadding
        // (the same pattern HomeScreen uses for its own nested Scaffold). Reserving insets again
        // here would double-count them and push the header/list down with a redundant gap.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            // Custom Cardfolio-style Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onClose != null) {
                        IconButton(onClick = onClose, modifier = Modifier.padding(end = 8.dp)) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                    Text(
                        "Friends",
                        fontWeight = FontWeight.Black,
                        fontSize = 28.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                // "Active" Pill / Add button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val addInteraction = remember { MutableInteractionSource() }
                    // REQUESTS control (social-hub-redesign Req 5): opens the Requests_List
                    // on demand. A badge surfaces the number of pending Outgoing_Invites and
                    // Incoming_Invites so the user can find sent invites without relying on a
                    // toast. The same surface is opened automatically after a successful send.
                    val requestsInteraction = remember { MutableInteractionSource() }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .expressivePressScale(requestsInteraction)
                            .clip(RoundedCornerShape(50))
                            .clickable(
                                interactionSource = requestsInteraction,
                                indication = LocalIndication.current,
                            ) { showRequestsList = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (pendingRequestsCount > 0) {
                                BadgedBox(badge = { Badge { Text("$pendingRequestsCount") } }) {
                                    Icon(Icons.Default.MoveToInbox, contentDescription = "Requests", modifier = Modifier.size(18.dp))
                                }
                            } else {
                                Icon(Icons.Default.MoveToInbox, contentDescription = "Requests", modifier = Modifier.size(18.dp))
                            }
                            Text("Requests", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .expressivePressScale(addInteraction)
                            .clip(RoundedCornerShape(50))
                            .clickable(
                                interactionSource = addInteraction,
                                indication = LocalIndication.current,
                            ) { showAddFriendDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.GroupAdd, contentDescription = "Add Friend", modifier = Modifier.size(18.dp))
                            Text("Add", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                coroutineScope.launch {
                    isRefreshing = true
                    viewModel.refreshData()
                    kotlinx.coroutines.delay(800)
                    isRefreshing = false
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection)
                    .padding(horizontal = 16.dp)
            ) {
                // HEADER AREA (social-hub-redesign Req 1.1): the hero ID card, the
                // Referral_CTA, and the Circles_Entry stay a fixed header at the top of the
                // Social_Hub, preserving the established stacked-card composition. Below it the
                // Section_Organizer switches between the Leaderboard pane and the Friends_List
                // pane, each keeping its own scroll position (Req 2.1, 2.2, 2.5).
                val headerBoxModifier = if (headerHeightPx == 0f) {
                    Modifier.fillMaxWidth()
                } else {
                    val headerHeightDp = with(density) { ((headerHeightPx + scrollOffsetPx) / density.density).dp }
                    Modifier
                        .fillMaxWidth()
                        .height(headerHeightDp)
                        .clipToBounds()
                }
                Box(
                    modifier = headerBoxModifier
                        .pointerInput(headerHeightPx) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    val target = if (scrollOffsetPx < -headerHeightPx / 2f) {
                                        -headerHeightPx
                                    } else {
                                        0f
                                    }
                                    coroutineScope.launch {
                                        animateScrollTo(target)
                                    }
                                },
                                onDragCancel = {
                                    val target = if (scrollOffsetPx < -headerHeightPx / 2f) {
                                        -headerHeightPx
                                    } else {
                                        0f
                                    }
                                    coroutineScope.launch {
                                        animateScrollTo(target)
                                    }
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    scrollOffsetPx = (scrollOffsetPx + dragAmount).coerceIn(-headerHeightPx, 0f)
                                }
                            )
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { layoutCoordinates ->
                                if (headerHeightPx == 0f) {
                                    headerHeightPx = layoutCoordinates.size.height.toFloat()
                                }
                            }
                            .offset { androidx.compose.ui.unit.IntOffset(0, scrollOffsetPx.roundToInt()) },
                        // Tightly pack the cards!
                        verticalArrangement = Arrangement.spacedBy((-12).dp)
                    ) {
                // HERO CARD (My ID)
                run {
                    val heroColor = Color(0xFFD4FF70) // Vibrant Lime Green
                    // Expressive_Motion_And_Shape (Req 1.3): an "alive" morphing badge that
                    // continuously morphs between a star and a rounded hexagon while slowly
                    // rotating. Built on androidx.graphics.shapes Morph/RoundedPolygon. The
                    // lime styling, Preamble_ID, productivity score, and RandomBackgrounds PNG
                    // (Req 1.4, 1.5) are all preserved unchanged below.
                    val morph = remember {
                        Morph(
                            start = RoundedPolygon.star(
                                numVerticesPerRadius = 8,
                                innerRadius = 0.7f,
                                rounding = androidx.graphics.shapes.CornerRounding(0.2f),
                            ),
                            end = RoundedPolygon(
                                numVertices = 6,
                                rounding = androidx.graphics.shapes.CornerRounding(0.3f),
                            ),
                        )
                    }
                    val morphTransition = rememberInfiniteTransition(label = "heroMorph")
                    val morphProgress by morphTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 4000),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "heroMorphProgress",
                    )
                    val morphRotation by morphTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 12000),
                            repeatMode = RepeatMode.Restart,
                        ),
                        label = "heroMorphRotation",
                    )
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp) // More rectangular
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(32.dp),
                        colors = CardDefaults.cardColors(containerColor = heroColor)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Lowermost Layer: Random PNG, taking full card but scaled to show top portion
                            Box(modifier = Modifier.matchParentSize()) {
                                Image(
                                    painter = painterResource(id = heroBackground),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    alignment = Alignment.TopCenter,
                                    modifier = Modifier.fillMaxSize(),
                                    alpha = 0.4f,
                                    colorFilter = ColorFilter.tint(Color.Black, blendMode = BlendMode.SrcAtop)
                                )
                            }

                            // Content on top
                            Column(modifier = Modifier.padding(24.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                                    // Abstract icon — expressive morphing badge (Req 1.3)
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(MorphPolygonShape(morph, morphProgress, morphRotation))
                                            .background(Color.Black),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = heroColor, modifier = Modifier.size(28.dp))
                                    }

                                    // Top right ID overlay
                                    Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.5f)) {
                                        Text(
                                            text = "ID: ${viewModel.myPreambleId}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                                    // Real Productivity Score
                                    Column {
                                        Text(
                                            text = "SCORE",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 12.sp,
                                            color = Color.Black.copy(alpha = 0.6f)
                                        )
                                        Text(
                                            text = "$myScore",
                                            style = TextStyle(
                                                fontSize = 48.sp,
                                                fontWeight = FontWeight.Black,
                                                drawStyle = Stroke(
                                                    miter = 10f,
                                                    width = 4f,
                                                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                                                ),
                                                color = Color.Black
                                            )
                                        )
                                    }

                                    // Bottom right action button — expressive press
                                    // treatment (Req 1.2, 1.3) on the share control.
                                    val shareInteraction = remember { MutableInteractionSource() }
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .expressivePressScale(shareInteraction)
                                            .clip(CircleShape)
                                            .background(Color.Black)
                                            .clickable(
                                                interactionSource = shareInteraction,
                                                indication = LocalIndication.current,
                                            ) {
                                                val shareIntent = Intent().apply {
                                                    action = Intent.ACTION_SEND
                                                    putExtra(Intent.EXTRA_TEXT, "Add me on Preamble! My ID is ${viewModel.myPreambleId} or click here: https://preamble.theblankstate.com/invite/${viewModel.myPreambleId}")
                                                    type = "text/plain"
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Share Preamble ID"))
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = "Share", tint = heroColor, modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // REFERRAL CTA (Growth-loops Req 1). Referral_Reward is disabled in
                // Development_Mode, so the CTA omits the AI-credit claim (Req 8.2).
                run {
                    ReferralCta(
                        inviteLink = viewModel.buildInviteLink(),
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }

                // Circles now has its own first-class tab in the Circles hub (SocialHubScreen)
                // instead of a promo card here, which both removes a whole stacked card's worth
                // of vertical space from this header (fixing the small-screen layout squeeze)
                // and makes Circles permanently visible in the hub's tab row rather than only
                // discoverable after scrolling past the hero card.
                    }
                } // end HEADER AREA

                Spacer(modifier = Modifier.height(8.dp))

                // SECTION_ORGANIZER (social-hub-redesign Req 2): a Material 3 Expressive
                // segmented control splits the Friends_Leaderboard and the Friends_List into
                // two distinct, separately-navigable panes, each with its own LazyListState so
                // their scroll positions are preserved independently (Req 2.1, 2.2, 2.5). The
                // Friends pane is a LazyColumn so only visible rows are composed regardless of
                // total size (Req 2.3); the established card styling is preserved within each
                // pane, and a no-friends empty-state offers an add-friend control (Req 2.4).
                // Captured as a composable lambda so it can be rendered both when the Social_Hub
                // has Loaded and, on a transient Error, BELOW the Error_State banner over the
                // retained data rather than blanking it (Req 1.7).
                val sectionOrganizer: @Composable (Modifier) -> Unit = { sectionModifier ->
                SectionOrganizer(
                    selected = selectedSection,
                    onSelect = { selectedSection = it },
                    leaderboardState = leaderboardState,
                    friendsState = friendsState,
                    leaderboardEntries = leaderboard,
                    friends = friends,
                    leaderboardQuery = leaderboardQuery,
                    onLeaderboardQueryChange = { leaderboardQuery = it },
                    friendsQuery = friendsQuery,
                    onFriendsQueryChange = { friendsQuery = it },
                    modifier = sectionModifier,
                    leaderboardContent = { visibleEntries ->
                        // FRIENDS LEADERBOARD (social-engagement Requirements 9.1, 9.2, 9.6).
                        // Rendered as individual list items over the already-filtered, already-
                        // windowed [visibleEntries] so client-side Paged_Loading bounds the
                        // composition and grows as the user scrolls (social-hub-redesign Req 2.8).
                        item(key = "leaderboard_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB300),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "This Week's Leaderboard",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (leaderboard.size <= 1) {
                            // No-friends empty-state preserved from LeaderboardSection.
                            item(key = "leaderboard_empty") {
                                Text(
                                    text = "Add friends to see how you stack up this week.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        } else {
                            itemsIndexed(
                                visibleEntries,
                                key = { _, entry -> "leaderboard_${entry.uid}" }
                            ) { index, entry ->
                                LeaderboardEntryRow(
                                    rank = index + 1,
                                    entry = entry,
                                    isMe = currentUserUid != null && entry.uid == currentUserUid
                                )
                            }
                        }
                    },
                    friendsContent = { visibleFriends ->
                // INCOMING INVITES were relocated into the Requests_List (Req 5.3); the
                // Friends pane now focuses on the established friends list. The Requests
                // control in the top bar (and post-send navigation) surfaces pending
                // invites with their accept/decline/withdraw actions.

                // FRIENDS LIST — rendered over the already-filtered, already-windowed
                // [visibleFriends] so client-side Paged_Loading bounds the composition and grows
                // as the user scrolls (social-hub-redesign Req 2.7). The established Cardfolio
                // card styling is preserved per row.
                itemsIndexed(visibleFriends, key = { _, it -> "friend_${it.uid}" }) { index, friend ->
                    val cardColor = CardColors[index % CardColors.size]
                    var isExpanded by remember { mutableStateOf(false) }
                    val friendBg = remember(friend.uid) { RandomBackgrounds.random() }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(fadeInSpec = tween(300), fadeOutSpec = tween(300))
                            .animateContentSize(animationSpec = tween(300))
                            .clickable { isExpanded = !isExpanded },
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = cardColor)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.matchParentSize()) {
                                Image(
                                    painter = painterResource(id = friendBg),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    alignment = Alignment.TopCenter,
                                    modifier = Modifier.fillMaxSize(),
                                    alpha = 0.3f,
                                    colorFilter = ColorFilter.tint(Color.Black, blendMode = BlendMode.SrcAtop)
                                )
                            }

                            if (isExpanded) {
                                Column(
                                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // Hero Avatar
                                    AsyncImage(
                                        model = "https://api.dicebear.com/9.x/micah/png?seed=${friend.preambleId}",
                                        contentDescription = "Avatar",
                                        modifier = Modifier
                                            .size(120.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha=0.3f))
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = friend.name,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 28.sp,
                                        color = Color.Black.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        "Preamble ID: ${friend.preambleId}",
                                        color = Color.Black.copy(alpha = 0.6f),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))

                                    // Productivity Points
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = Color.Black.copy(alpha = 0.2f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD166)) // Yellow star
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("${weeklyPointsByUid[friend.uid] ?: 0} Points this week", fontWeight = FontWeight.Bold, color = Color.Black)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(24.dp))

                                    // Actions
                                    Button(
                                        onClick = { requestFriendRemoval(friend) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.1f), contentColor = Color.Black)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Remove Friend")
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.height(80.dp).padding(horizontal = 24.dp).fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Avatar from DiceBear
                                        AsyncImage(
                                            model = "https://api.dicebear.com/9.x/micah/png?seed=${friend.preambleId}",
                                            contentDescription = "Avatar",
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha=0.3f))
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            text = friend.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp,
                                            color = Color.Black.copy(alpha = 0.8f)
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "... ${friend.preambleId}",
                                            color = Color.Black.copy(alpha = 0.5f),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        IconButton(
                                            onClick = { requestFriendRemoval(friend) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove Friend", tint = Color.Black.copy(alpha = 0.4f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // NO-FRIENDS EMPTY STATE (social-hub-redesign Req 2.4): when the signed-in
                // user has no friends, the Friends pane shows an empty-state indication and
                // an "Add a friend" control.
                if (friends.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Expressive empty state
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(32.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                "No friends yet.",
                                fontSize = 24.sp,
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Invite someone to start building your circle.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            // "Add a friend" control (Req 2.4): opens the add-friend entry.
                            Button(
                                onClick = { showAddFriendDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(50)
                            ) {
                                Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add a friend")
                            }
                        }
                    }
                }

                    },
                )
                } // end sectionOrganizer composable lambda

                // LOADING_STATE / ERROR_STATE (social-hub-redesign Req 1.6, 1.7): drive the
                // Social_Hub content area from the ViewModel's derived load state. While Loading
                // render an Expressive skeleton/shimmer instead of a blank surface; on Error
                // render an Expressive Error_State card describing the failure with a retry
                // control that calls retryLoad(), keeping any previously loaded content visible
                // BELOW the banner rather than blanking it; once Loaded render the
                // Section_Organizer as before.
                when (val loadState = socialHubLoadState) {
                    SocialHubLoadState.Loading -> SocialHubSkeleton(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                    is SocialHubLoadState.Error -> Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SocialHubErrorState(
                            message = loadState.message,
                            onRetry = { viewModel.retryLoad() },
                        )
                        // Do not blank previously loaded data on a transient error — keep the
                        // retained leaderboard/friends visible under the banner (Req 1.7).
                        if (friends.isNotEmpty() || leaderboard.isNotEmpty()) {
                            sectionOrganizer(
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                        }
                    }
                    SocialHubLoadState.Loaded -> sectionOrganizer(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }
            }
        }
    }

    if (showAddFriendDialog) {
        // INVITE_ENTRY_EXPERIENCE (social-hub-redesign Req 6): an on-theme entry surface
        // that supersedes the plain add-friend AlertDialog. The id is normalized to
        // uppercase as the user types via PreambleId.normalize (Req 6.3), the send control
        // stays disabled while the normalized id is blank (Req 6.4), and a pre-filled id
        // from a link/deep link is supported because targetId is set by the deep-link
        // LaunchedEffect above (Req 6.2, 7.2).
        InviteEntrySheet(
            value = targetId,
            onValueChange = { targetId = PreambleId.normalize(it) },
            sendEnabled = !PreambleId.isBlank(targetId) && uiState !is WorkspaceUiState.Loading,
            onSend = {
                viewModel.sendInvite(targetId)
                showAddFriendDialog = false
            },
            onDismiss = { showAddFriendDialog = false },
        )
    }

    if (showRequestsList) {
        // REQUESTS_LIST (social-hub-redesign Req 5): an on-theme surface presenting the
        // signed-in user's Outgoing_Invites and Incoming_Invites in grouped sections
        // (outgoing separated from incoming, Req 5.3) using requestsSections. Each
        // Outgoing_Invite is shown with prominence comparable to — and visually distinguished
        // from — incoming invites, including the recipient Preamble_ID and an awaiting-response
        // status (Req 4.2, 4.3), and offers a withdraw action. Incoming invites keep their
        // accept/decline actions. An empty-state shows when there are no invites of either
        // kind (Req 5.5).
        RequestsList(
            sections = requestsSections,
            onAccept = { invite -> viewModel.acceptInvite(invite) },
            onDecline = { inviteId -> viewModel.declineInvite(inviteId) },
            onWithdraw = { outgoing -> viewModel.withdrawInvite(outgoing.targetUid) },
            onDismiss = { showRequestsList = false },
        )
    }

    val pendingFriend = friendPendingRemoval
    val pendingImpact = removalImpact
    if (pendingFriend != null && pendingImpact != null) {
        AlertDialog(
            onDismissRequest = {
                friendPendingRemoval = null
                removalImpact = null
                transferOwnedTasks = false
            },
            title = { Text("Resolve shared tasks first") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "You still share ${pendingImpact.totalTasks} task${if (pendingImpact.totalTasks == 1) "" else "s"} with ${pendingFriend.name}. Choose what should happen before removing this friend."
                    )
                    if (pendingImpact.administeredTasks.isNotEmpty()) {
                        Text(
                            "Tasks you own: ${pendingImpact.administeredTasks.size}. By default, ${pendingFriend.name} will be removed from those tasks and you stay admin.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { transferOwnedTasks = !transferOwnedTasks }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = transferOwnedTasks,
                                onCheckedChange = { transferOwnedTasks = it }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Transfer my owned shared tasks to ${pendingFriend.name} and leave them")
                        }
                    }
                    if (pendingImpact.memberTasks.isNotEmpty()) {
                        Text(
                            "Tasks owned by someone else: ${pendingImpact.memberTasks.size}. You will leave those tasks before the friendship is removed.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resolveTasksAndRemoveFriend(pendingFriend, transferOwnedTasks)
                        friendPendingRemoval = null
                        removalImpact = null
                        transferOwnedTasks = false
                    }
                ) {
                    Text("Resolve & Remove")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        friendPendingRemoval = null
                        removalImpact = null
                        transferOwnedTasks = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * InviteEntrySheet (social-hub-redesign Req 6): the Invite_Entry_Experience. An on-theme
 * Material 3 Expressive [ModalBottomSheet] that supersedes the plain add-friend
 * [AlertDialog], styled consistently with the Social_Hub_Design_Language — a vibrant lime
 * Cardfolio-style header card with a [RandomBackgrounds] PNG and an "alive" morphing-shape
 * badge (Expressive_Motion_And_Shape, Req 6.1, 6.5).
 *
 * The host owns the entered value ([value]) and normalizes it to uppercase as the user
 * types through [onValueChange] (which applies [PreambleId.normalize], Req 6.3); because
 * the host's value is also set by the deep-link LaunchedEffect, opening from a link/deep
 * link pre-fills the field (Req 6.2, 7.2). The send control is enabled only when
 * [sendEnabled] is true — the host derives this from the normalized id being non-blank, so
 * the control stays disabled while the id is blank (Req 6.4). [onSend] sends the invite and
 * [onDismiss] closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InviteEntrySheet(
    value: String,
    onValueChange: (String) -> Unit,
    sendEnabled: Boolean,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val heroColor = Color(0xFFD4FF70) // Vibrant Lime Green, echoing the hero ID card
    val sheetBg = remember { RandomBackgrounds.random() }

    // Expressive_Motion_And_Shape (Req 6.5): an "alive" morphing badge that continuously
    // morphs between a star and a rounded hexagon while slowly rotating, matching the hero
    // card's treatment so the entry surface presents motion consistent with the rest of the
    // Social_Hub.
    val morph = remember {
        Morph(
            start = RoundedPolygon.star(
                numVerticesPerRadius = 8,
                innerRadius = 0.7f,
                rounding = androidx.graphics.shapes.CornerRounding(0.2f),
            ),
            end = RoundedPolygon(
                numVertices = 6,
                rounding = androidx.graphics.shapes.CornerRounding(0.3f),
            ),
        )
    }
    val morphTransition = rememberInfiniteTransition(label = "inviteMorph")
    val morphProgress by morphTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "inviteMorphProgress",
    )
    val morphRotation by morphTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000),
            repeatMode = RepeatMode.Restart,
        ),
        label = "inviteMorphRotation",
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // On-theme lime header card with a RandomBackgrounds PNG and the morphing badge
            // (Req 6.1, 6.5).
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = heroColor),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.matchParentSize()) {
                        Image(
                            painter = painterResource(id = sheetBg),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopCenter,
                            modifier = Modifier.fillMaxSize(),
                            alpha = 0.4f,
                            colorFilter = ColorFilter.tint(Color.Black, blendMode = BlendMode.SrcAtop),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(MorphPolygonShape(morph, morphProgress, morphRotation))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.GroupAdd,
                                contentDescription = null,
                                tint = heroColor,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Text(
                            text = "Add a Friend",
                            fontWeight = FontWeight.Black,
                            fontSize = 28.sp,
                            color = Color.Black,
                        )
                    }
                }
            }

            Text(
                text = "Enter your friend's Preamble ID to send them an invite.",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )

            // The entered Preamble_ID. onValueChange normalizes to uppercase as the user
            // types (Req 6.3).
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("Friend's Preamble ID") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFD4FF70),
                    focusedLabelColor = Color(0xFFD4FF70),
                    cursorColor = Color(0xFFD4FF70)
                )
            )

            // Send control — disabled while the normalized id is blank (Req 6.4). An
            // expressive bouncy press treatment keeps it consistent with the surface's
            // primary controls (Req 6.5).
            val sendInteraction = remember { MutableInteractionSource() }
            Button(
                onClick = onSend,
                enabled = sendEnabled,
                interactionSource = sendInteraction,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .expressivePressScale(sendInteraction),
            ) {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Send Invite", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

/**
 * Section_Organizer areas (social-hub-redesign Req 2): the two distinct, separately-navigable
 * areas of the Social_Hub — the Friends_Leaderboard and the Friends_List.
 */
enum class SocialSection { Leaderboard, Friends }

/**
 * SectionOrganizer (social-hub-redesign Req 2): layers a Material 3 Expressive segmented
 * control onto the existing list composition so the Friends_Leaderboard and the Friends_List
 * become two distinct, separately-navigable panes rather than one continuous undivided scroll
 * (Req 2.1). Because the control switches between the two panes, both stay reachable without
 * scrolling through the entire Friends_List to reach the Leaderboard (Req 2.2).
 *
 * Each pane owns its own [LazyListState] ([leaderboardState], [friendsState]), supplied by the
 * caller and remembered across pane switches, so the panes preserve their scroll positions
 * independently of each other (Req 2.5). Only the selected pane is composed, and each pane is a
 * [LazyColumn] so only the visible rows are held in composition regardless of total list size
 * (Req 2.3). The established Cardfolio card styling is preserved within each pane by rendering
 * the caller-supplied [leaderboardContent] / [friendsContent] item composables unchanged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionOrganizer(
    selected: SocialSection,
    onSelect: (SocialSection) -> Unit,
    leaderboardState: LazyListState,
    friendsState: LazyListState,
    leaderboardEntries: List<Leaderboard.Entry>,
    friends: List<Friend>,
    leaderboardQuery: String,
    onLeaderboardQueryChange: (String) -> Unit,
    friendsQuery: String,
    onFriendsQueryChange: (String) -> Unit,
    leaderboardContent: LazyListScope.(visible: List<Leaderboard.Entry>) -> Unit,
    friendsContent: LazyListScope.(visible: List<Friend>) -> Unit,
    modifier: Modifier = Modifier,
) {
    // SOCIAL_SEARCH (Req 9): filter the FULL in-memory list for each pane BEFORE any paging
    // window is applied, so search covers the entire set rather than only the loaded pages
    // (Req 9.6). A blank query returns the list unchanged, restoring the unfiltered list
    // (Req 9.4) — that behavior lives in the pure SocialSearch.filter.
    val filteredLeaderboard = remember(leaderboardQuery, leaderboardEntries) {
        SocialSearch.filter(leaderboardQuery, leaderboardEntries)
    }
    val filteredFriends = remember(friendsQuery, friends) {
        SocialSearch.filter(friendsQuery, friends)
    }

    // PAGED_LOADING (Req 2.7, 2.8): each pane tracks how many pages it has loaded. The count
    // resets to one page whenever the active query changes (keying remember on the query) so a
    // new search starts from the first page of its own result set.
    var leaderboardPageCount by remember(leaderboardQuery) { mutableStateOf(1) }
    var friendsPageCount by remember(friendsQuery) { mutableStateOf(1) }

    val visibleLeaderboard = PageWindow.visible(filteredLeaderboard, leaderboardPageCount)
    val visibleFriends = PageWindow.visible(filteredFriends, friendsPageCount)

    // Grow the leaderboard window by one page as the user scrolls within PREFETCH_THRESHOLD of
    // the end of the loaded entries, until the whole (filtered) list is shown (Req 2.8).
    LaunchedEffect(leaderboardState, filteredLeaderboard) {
        snapshotFlow { leaderboardState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { lastVisible ->
                val loaded = PageWindow.visible(filteredLeaderboard, leaderboardPageCount).size
                if (loaded < filteredLeaderboard.size &&
                    PageWindow.shouldLoadMore(lastVisible, loaded)
                ) {
                    leaderboardPageCount++
                }
            }
    }
    // Same client-side windowing for the Friends_List pane (Req 2.7).
    LaunchedEffect(friendsState, filteredFriends) {
        snapshotFlow { friendsState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { lastVisible ->
                val loaded = PageWindow.visible(filteredFriends, friendsPageCount).size
                if (loaded < filteredFriends.size &&
                    PageWindow.shouldLoadMore(lastVisible, loaded)
                ) {
                    friendsPageCount++
                }
            }
    }

    Column(modifier = modifier) {
        // BOTTOM-INSET CLEARANCE (Fix 2, updated): FriendsScreen is now hosted inside the
        // Circles hub as nested tab content, sitting inside MainActivity's outer Scaffold
        // content slot. That outer Scaffold's bottomBar (the app's ExpressiveNavigationBar)
        // already reserves the system navigation-bar inset beneath it, and its innerPadding
        // already clears the pill bar's full height for this content — so only a small,
        // fixed comfortable gap is needed here, not another navigationBars inset on top of it.
        val paneBottomPadding = 24.dp
        val options = listOf(SocialSection.Leaderboard, SocialSection.Friends)
        // PERSISTENT, LABELED, ACTIVE-AWARE CONTROL (Req 2.6): the segmented control is rendered
        // in a header Surface that sits above the active pane, OUTSIDE the scrolling LazyColumns,
        // so it stays visible regardless of scroll position. It names both areas
        // ("Leaderboard"/"Friends") and marks the active one via the Material 3 Expressive
        // selected state.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Transparent)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(50))
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                options.forEach { section ->
                    val isSelected = selected == section
                    val tabColor = if (section == SocialSection.Leaderboard) {
                        Color(0xFFFFD166) // Cardfolio Yellow
                    } else {
                        Color(0xFFEAB3FF) // Cardfolio Light Purple
                    }
                    val interactionSource = remember { MutableInteractionSource() }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .expressivePressScale(interactionSource)
                            .clip(RoundedCornerShape(50))
                            .background(if (isSelected) tabColor else Color.Transparent)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) { onSelect(section) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (section == SocialSection.Leaderboard) {
                                    Icons.Default.EmojiEvents
                                } else {
                                    Icons.Default.Group
                                },
                                contentDescription = null,
                                tint = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (section == SocialSection.Leaderboard) "Leaderboard" else "Friends",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Only the selected pane is composed; switching back restores the pane's scroll
        // position because the caller holds each LazyListState across recomposition (Req 2.5).
        // Expressive_Motion (Req 1.2, 1.3): panes slide+fade in the direction of travel via an
        // AnimatedContent transition so switching areas feels alive rather than an abrupt swap.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val direction = if (forward) 1 else -1
                    (slideInHorizontally(animationSpec = tween(400)) { full -> direction * full } +
                        fadeIn(animationSpec = tween(400))) togetherWith
                        (slideOutHorizontally(animationSpec = tween(400)) { full -> -direction * full } +
                            fadeOut(animationSpec = tween(400)))
                },
                label = "sectionPane",
            ) { section ->
                when (section) {
                    SocialSection.Leaderboard -> Column(modifier = Modifier.fillMaxSize()) {
                        // SOCIAL_SEARCH field above the pane (Req 9.1).
                        SocialSearchField(
                            query = leaderboardQuery,
                            onQueryChange = onLeaderboardQueryChange,
                            placeholder = "Search the leaderboard",
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        LazyColumn(
                            state = leaderboardState,
                            modifier = Modifier.fillMaxSize(),
                            // Unconditional bottom clearance >= Bottom_System_Inset (Req 10.3, 10.6).
                            contentPadding = PaddingValues(bottom = paneBottomPadding),
                        ) {
                            // NO-MATCH EMPTY STATE (social-hub-redesign Req 9.5): when the
                            // Social_Search query is non-blank and nothing in the FULL leaderboard
                            // matches, show an Expressive "no matching results" empty-state in this
                            // pane instead of an empty list; the search field above stays visible
                            // so the user can adjust or clear the query.
                            if (leaderboardQuery.isNotBlank() && filteredLeaderboard.isEmpty()) {
                                item(key = "leaderboard_no_match") {
                                    NoMatchEmptyState(query = leaderboardQuery)
                                }
                            } else {
                                leaderboardContent(visibleLeaderboard)
                                // Trailing sentinel that drives/visualizes Paged_Loading (Req 2.8).
                                item(key = "leaderboard_sentinel") {
                                    PagingSentinel(hasMore = visibleLeaderboard.size < filteredLeaderboard.size)
                                }
                            }
                        }
                    }
                    SocialSection.Friends -> Column(modifier = Modifier.fillMaxSize()) {
                        // SOCIAL_SEARCH field above the pane (Req 9.1).
                        SocialSearchField(
                            query = friendsQuery,
                            onQueryChange = onFriendsQueryChange,
                            placeholder = "Search your friends",
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        LazyColumn(
                            state = friendsState,
                            modifier = Modifier.fillMaxSize(),
                            // Preserve the established tightly-stacked Cardfolio look within the pane.
                            verticalArrangement = Arrangement.spacedBy((-12).dp),
                            // Unconditional bottom clearance >= Bottom_System_Inset (Req 10.3, 10.6).
                            contentPadding = PaddingValues(bottom = paneBottomPadding),
                        ) {
                            // NO-MATCH EMPTY STATE (social-hub-redesign Req 9.5): when the
                            // Social_Search query is non-blank and nothing in the FULL friends list
                            // matches, show an Expressive "no matching results" empty-state in this
                            // pane. When the query is blank the no-friends empty-state inside
                            // friendsContent still handles the genuinely-empty friends list (Req 2.4).
                            if (friendsQuery.isNotBlank() && filteredFriends.isEmpty()) {
                                item(key = "friends_no_match") {
                                    NoMatchEmptyState(query = friendsQuery)
                                }
                            } else {
                                friendsContent(visibleFriends)
                                // Trailing sentinel that drives/visualizes Paged_Loading (Req 2.7).
                                item(key = "friends_sentinel") {
                                    PagingSentinel(hasMore = visibleFriends.size < filteredFriends.size)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Social_Search field (social-hub-redesign Req 9.1): a reusable Material 3 Expressive text
 * field rendered above each pane, bound to that pane's query state. It is purely an input —
 * all matching is delegated to the pure [SocialSearch.filter]. A trailing clear control resets
 * the query, which restores the unfiltered list (Req 9.4) since a blank query passes the list
 * through unchanged.
 *
 * Expressive_Motion_And_Shape (Req 1.2, 1.3): rather than reading as a plain default box, the
 * field gives subtle, on-theme feedback as it gains focus — it lifts with a small animated
 * elevation/shadow and its leading search icon tints to the primary color — so the search
 * surface feels alive and consistent with the surface's other Expressive controls, while
 * keeping the established rounded 28.dp expressive shape.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SocialSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(28.dp)
    // Gentle lift on focus so the active search field feels alive rather than static.
    val elevation by animateDpAsState(
        targetValue = if (focused) 6.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "searchFieldElevation",
    )
    val leadingIconTint by animateColorAsState(
        targetValue = if (focused) {
            Color(0xFFD4FF70)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 250),
        label = "searchFieldIconTint",
    )
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = elevation, shape = shape, clip = false),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = leadingIconTint) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = shape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFFD4FF70),
            focusedLabelColor = Color(0xFFD4FF70),
            cursorColor = Color(0xFFD4FF70)
        )
    )
}

/**
 * Trailing Paged_Loading sentinel (social-hub-redesign Req 2.7, 2.8): when more entries remain
 * beyond the current window it shows an Expressive progress indicator (the scroll that brings it
 * into view triggers the next page). When the list is fully loaded it emits nothing — bottom
 * clearance no longer comes from a fixed spacer here but from the unconditional pane
 * contentPadding resolved from the Bottom_System_Inset (Req 10.6, Fix 3), removing the
 * paging-state coupling that previously left the last row behind the chin while scrolling.
 */
@Composable
private fun PagingSentinel(hasMore: Boolean) {
    if (hasMore) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 3.dp
            )
        }
    } else {
        Spacer(modifier = Modifier.height(0.dp))
    }
}

/**
 * SocialHubSkeleton (social-hub-redesign Req 1.6): the Loading_State. While the
 * Friends_List / Friends_Leaderboard have not yet loaded, the Social_Hub content area shows
 * this Expressive skeleton — a segmented-control placeholder, a search-field placeholder, and
 * a column of row placeholders that gently pulse (shimmer) — instead of a blank surface. The
 * hero ID card, Referral_CTA, and Circles_Entry header stay visible above it.
 */
@Composable
private fun SocialHubSkeleton(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeletonShimmer")
    val shimmerAlpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonShimmerAlpha",
    )
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = shimmerAlpha)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Segmented-control placeholder (mirrors the persistent Section_Organizer header).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(placeholderColor)
        )
        // Search-field placeholder.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(placeholderColor)
        )
        // Row placeholders standing in for the leaderboard/friends rows.
        repeat(6) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(placeholderColor)
            )
        }
    }
}

/**
 * SocialHubErrorState (social-hub-redesign Req 1.7): the Error_State. Rendered as an
 * Expressive error card that describes the failure ([message]) and offers a Retry control
 * that calls the ViewModel's retryLoad() to re-subscribe the failed list flow. It is shown as
 * a banner above any retained, previously-loaded content so a transient load failure does not
 * blank the surface.
 */
@Composable
private fun SocialHubErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                Icons.Default.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(32.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Something went wrong",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = message,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                )
            }
            // Retry control (Req 1.7) — expressive bouncy press treatment to match the
            // surface's other primary controls.
            val retryInteraction = remember { MutableInteractionSource() }
            Button(
                onClick = onRetry,
                interactionSource = retryInteraction,
                shape = RoundedCornerShape(50),
                modifier = Modifier.expressivePressScale(retryInteraction),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retry", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * NoMatchEmptyState (social-hub-redesign Req 9.5): shown inside a pane when a Social_Search
 * [query] is non-blank but matches no entries in that pane, so the user sees a clear "no
 * matching results" indication rather than a blank list. The pane's search field stays visible
 * above this state so the query can be adjusted or cleared.
 */
@Composable
private fun NoMatchEmptyState(query: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 48.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "No matches found",
            fontSize = 22.sp,
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Nothing matches \"$query\". Try a different name or Preamble ID.",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * RequestsList (social-hub-redesign Req 4, 5): the Requests_List surface. An on-theme
 * Material 3 Expressive [ModalBottomSheet], styled consistently with the
 * Social_Hub_Design_Language, that presents the signed-in user's Outgoing_Invites and
 * Incoming_Invites in two clearly-separated groups (outgoing shown separately from incoming,
 * Req 5.3) from the pure [RequestsListOrganizer.Sections].
 *
 * Each Outgoing_Invite is rendered with prominence and detail comparable to an
 * Incoming_Invite — the recipient's Preamble_ID and an "Awaiting response" status — while
 * being visually distinguished from incoming invites by a different card treatment and a
 * withdraw action (Req 4.2, 4.3). Incoming invites retain their accept/decline actions.
 * When there are no invites of either kind, an empty-state is shown (Req 5.5).
 *
 * The host opens this surface after a successful send (via the ViewModel's one-shot
 * navigateToRequests event) and on demand from the top-bar Requests control; the just-sent
 * Outgoing_Invite is already present in [sections] when it opens (Req 5.1, 5.2).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RequestsList(
    sections: RequestsListOrganizer.Sections,
    onAccept: (WorkspaceInvite) -> Unit,
    onDecline: (String) -> Unit,
    onWithdraw: (OutgoingInvite) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Requests",
                fontWeight = FontWeight.Black,
                fontSize = 28.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            if (sections.isEmpty) {
                // EMPTY-STATE (Req 5.5): no Outgoing_Invites and no Incoming_Invites.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(32.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.MoveToInbox,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "No pending requests.",
                        fontSize = 24.sp,
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Invites you send and receive will show up here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // OUTGOING group, shown first and separately from incoming (Req 5.3).
                    if (sections.outgoing.isNotEmpty()) {
                        item(key = "requests_header_outgoing") {
                            RequestsSectionHeader(
                                icon = Icons.Default.Outbox,
                                title = "Sent",
                                count = sections.outgoing.size,
                            )
                        }
                        itemsIndexed(
                            sections.outgoing,
                            key = { _, it -> "outgoing_${it.targetUid}" },
                        ) { _, outgoing ->
                            OutgoingInviteCard(
                                invite = outgoing,
                                onWithdraw = { onWithdraw(outgoing) },
                            )
                        }
                    }

                    // INCOMING group, clearly separated from the outgoing group (Req 5.3).
                    if (sections.incoming.isNotEmpty()) {
                        item(key = "requests_header_incoming") {
                            RequestsSectionHeader(
                                icon = Icons.Default.MoveToInbox,
                                title = "Received",
                                count = sections.incoming.size,
                            )
                        }
                        itemsIndexed(
                            sections.incoming,
                            key = { _, it -> "incoming_${it.id}" },
                        ) { _, invite ->
                            IncomingInviteCard(
                                invite = invite,
                                onAccept = { onAccept(invite) },
                                onDecline = { onDecline(invite.id) },
                            )
                        }
                    }

                    item(key = "requests_bottom_spacer") {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

/**
 * A small group header inside the [RequestsList] separating the Outgoing_Invites group from
 * the Incoming_Invites group (Req 5.3), with the number of invites in the group.
 */
@Composable
private fun RequestsSectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    count: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = title,
            fontWeight = FontWeight.Black,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(
                text = "$count",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * OutgoingInviteCard (social-hub-redesign Req 4.2, 4.3): a persistent, prominent
 * representation of a sent Friend_Request. It shows the recipient's Preamble_ID
 * ([OutgoingInvite.targetPreambleId]) and an "Awaiting response" status, with detail and
 * prominence comparable to an [IncomingInviteCard]. It is visually distinguished from
 * incoming invites by a distinct lime accent treatment and an outbound badge, and offers a
 * withdraw action via [onWithdraw]. The established Cardfolio styling and a `RandomBackgrounds`
 * PNG are reused.
 */
@Composable
private fun OutgoingInviteCard(
    invite: OutgoingInvite,
    onWithdraw: () -> Unit,
) {
    // Distinct from the dark-slate incoming card: a deep indigo card with a lime accent so
    // sent invites read clearly apart from received invites (Req 4.3).
    val cardColor = Color(0xFF353551)
    val accent = Color(0xFFD4FF70) // Lime, echoing the hero ID card
    val bg = remember(invite.targetUid) { RandomBackgrounds.random() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.matchParentSize()) {
                Image(
                    painter = painterResource(id = bg),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                    alpha = 0.25f,
                )
            }

            Row(
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        AsyncImage(
                            model = "https://api.dicebear.com/9.x/micah/png?seed=${invite.targetPreambleId}",
                            contentDescription = "Avatar",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.3f)),
                        )
                        // Outbound badge marks this as a sent invite (Req 4.3).
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(accent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.NorthEast,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "To: ${invite.targetPreambleId}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                "Awaiting response",
                                color = accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                // Withdraw action (Req 4.1 lifecycle / 5.4): removes the Outgoing_Invite.
                IconButton(
                    onClick = onWithdraw,
                    modifier = Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Withdraw invite", tint = Color(0xFFFF9E9E))
                }
            }
        }
    }
}

/**
 * IncomingInviteCard (social-hub-redesign Req 4.2, 5.3): a received Friend_Request within the
 * Requests_List incoming group, preserving the established dark-slate Cardfolio styling and
 * the accept/decline actions ([onAccept] / [onDecline]).
 */
@Composable
private fun IncomingInviteCard(
    invite: WorkspaceInvite,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val cardColor = Color(0xFF2C2C2E) // Dark slate for incoming invites (existing styling)
    val inviteBg = remember(invite.id) { RandomBackgrounds.random() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.matchParentSize()) {
                Image(
                    painter = painterResource(id = inviteBg),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                    alpha = 0.3f,
                )
            }

            Row(
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = "https://api.dicebear.com/9.x/micah/png?seed=${invite.senderPreambleId}",
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.3f)),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Invite: ${invite.senderName}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White,
                        )
                        Text("ID: ${invite.senderPreambleId}", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onAccept, modifier = Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape)) {
                        Icon(Icons.Default.Check, contentDescription = "Accept", tint = Color(0xFFD4FF70))
                    }
                    IconButton(onClick = onDecline, modifier = Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape)) {
                        Icon(Icons.Default.Close, contentDescription = "Decline", tint = Color(0xFFFF9E9E))
                    }
                }
            }
        }
    }
}
