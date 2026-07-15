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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.theblankstate.preamble.ui.viewmodels.CircleUiState
import com.theblankstate.preamble.ui.viewmodels.CircleViewModel
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextOverflow
import com.theblankstate.preamble.repository.Circle
import com.theblankstate.preamble.repository.CircleMember
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import android.content.Context
import androidx.compose.runtime.mutableStateListOf

/**
 * Circles_Screen (shared-circles Requirements 1.1, 2.1, 2.2, 2.4).
 *
 * Lists the Circles the signed-in user belongs to (Circle_Name + Circle_Member count), shows an
 * empty-state with a create control when the user belongs to none, and offers a create-Circle
 * control bound to [CircleViewModel.createCircle]. Tapping a Circle opens [CircleDetailScreen]
 * via [CircleViewModel.openCircle].
 *
 * Reachable as one of the organized routes inside [SocialHubScreen] — the single "Circles"
 * bottom-nav destination that also hosts [FriendsScreen] (Friends/Leaderboard) and
 * [WorkspaceTasksScreen] (shared/assigned tasks), so all social/collaboration features live
 * behind one predictable tap target instead of being split across a bottom-nav tab, an
 * avatar-triggered overlay, and a nested "Circles" pill (Requirement 2.4). [uiState]
 * Error/Success messages are surfaced via Toast like [FriendsScreen] and cleared with
 * [CircleViewModel.resetState].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CirclesScreen(
    viewModel: CircleViewModel = viewModel(),
    onClose: (() -> Unit)? = null,
    registerCreateCircleTrigger: ((() -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val circles by viewModel.circles.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Internal navigation into the Circle_Detail_Screen. Holding the id (not the projection)
    // keeps the open Circle in sync with the live `circles` StateFlow as membership changes.
    var openCircleId by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newCircleName by remember { mutableStateOf("") }

    val initialSuggestions = remember {
        listOf(
            SuggestionCircle("School", Icons.Default.School),
            SuggestionCircle("Work", Icons.Default.Work),
            SuggestionCircle("Family", Icons.Default.Home),
            SuggestionCircle("Friends", Icons.Default.Group),
            SuggestionCircle("Gym", Icons.Default.FitnessCenter),
            SuggestionCircle("Project", Icons.Default.Build),
            SuggestionCircle("Travel", Icons.Default.Flight),
            SuggestionCircle("Shopping", Icons.Default.ShoppingCart)
        )
    }
    var suggestions by remember { mutableStateOf(initialSuggestions) }
    var activeSuggestionOption by remember { mutableStateOf<SuggestionCircle?>(null) }

    val sharedPrefs = remember(context) { context.getSharedPreferences("circles_prefs", Context.MODE_PRIVATE) }
    var pinnedCircleIds by remember {
        mutableStateOf(sharedPrefs.getStringSet("pinned_circles", emptySet()) ?: emptySet())
    }
    val autoPinSuggestedNames = remember { mutableStateListOf<String>() }
    var showOptionsSheetForCircle by remember { mutableStateOf<Circle?>(null) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(circles) {
        circles.forEach { circle ->
            if (circle.name in autoPinSuggestedNames && circle.id !in pinnedCircleIds) {
                val updated = pinnedCircleIds + circle.id
                sharedPrefs.edit().putStringSet("pinned_circles", updated).apply()
                pinnedCircleIds = updated
                autoPinSuggestedNames.remove(circle.name)
            }
        }
    }

    val gridItems = remember(circles, suggestions, pinnedCircleIds) {
        val pinned = circles.filter { it.id in pinnedCircleIds }
        val suggestionsToFill = suggestions.filter { sug ->
            pinned.none { it.name.equals(sug.name, ignoreCase = true) }
        }
        val combined = (pinned.map { GridItem.Real(it) } + suggestionsToFill.map { GridItem.Suggest(it) }).take(8)
        combined
    }

    LaunchedEffect(registerCreateCircleTrigger) {
        registerCreateCircleTrigger?.invoke {
            newCircleName = ""
            showCreateDialog = true
        }
    }

    // Surface UI state via Toast exactly like WorkspaceScreen, then clear it.
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

    val selectedId = openCircleId
    if (selectedId != null) {
        CircleDetailScreen(
            circleId = selectedId,
            viewModel = viewModel,
            onBack = { openCircleId = null },
            modifier = modifier
        )
        return
    }

    Scaffold(
        modifier = modifier,
        topBar = {
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
                        "Circles",
                        fontWeight = FontWeight.Black,
                        fontSize = 28.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (gridItems.isNotEmpty()) {
                    item(key = "suggestions_header_grid") {
                        CircleSuggestionsGrid(
                            gridItems = gridItems,
                            onRealClick = { circle ->
                                viewModel.openCircle(circle.id)
                                openCircleId = circle.id
                            },
                            onRealLongClick = { circle ->
                                showOptionsSheetForCircle = circle
                            },
                            onSuggestClick = { activeSuggestionOption = it }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                if (circles.isEmpty()) {
                    item(key = "empty_circles_state") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp, horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Groups,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "No Circles yet.",
                                fontSize = 20.sp,
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Create a Circle to share one task list with your friends.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = {
                                newCircleName = ""
                                showCreateDialog = true
                            }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Create a Circle")
                            }
                        }
                    }
                } else {
                    item(key = "my_circles_header") {
                        val configuration = LocalConfiguration.current
                        val scaleFactor = (configuration.screenWidthDp / 360f).coerceIn(0.85f, 1.15f)
                        val searchInteraction = remember { MutableInteractionSource() }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSearchActive) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("Search circles...", fontSize = (13 * scaleFactor).sp) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height((40 * scaleFactor).dp),
                                    shape = RoundedCornerShape(12.dp),
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                isSearchActive = false
                                                searchQuery = ""
                                            },
                                            modifier = Modifier.size((24 * scaleFactor).dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Close Search",
                                                modifier = Modifier.size((16 * scaleFactor).dp)
                                            )
                                        }
                                    }
                                )
                            } else {
                                Text(
                                    text = "MY CIRCLES",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = (11 * scaleFactor).sp,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                                
                                if (circles.size > 6) {
                                    IconButton(
                                        onClick = { isSearchActive = true },
                                        modifier = Modifier
                                            .size((28 * scaleFactor).dp)
                                            .expressivePressScale(searchInteraction)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Search Circles",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            modifier = Modifier.size((18 * scaleFactor).dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val filteredCircles = if (searchQuery.isBlank()) {
                        circles
                    } else {
                        circles.filter { it.name.contains(searchQuery, ignoreCase = true) }
                    }

                    itemsIndexed(filteredCircles, key = { _, circle -> "circle_${circle.id}" }) { index, circle ->
                        val iconColor = CardColors[index % CardColors.size]
                        CircleRowItem(
                            circle = circle,
                            iconColor = iconColor,
                            onClick = {
                                viewModel.openCircle(circle.id)
                                openCircleId = circle.id
                            },
                            onLongClick = {
                                showOptionsSheetForCircle = circle
                            }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create a Circle") },
            text = {
                OutlinedTextField(
                    value = newCircleName,
                    onValueChange = { newCircleName = it },
                    label = { Text("Circle name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createCircle(newCircleName)
                        showCreateDialog = false
                    },
                    enabled = newCircleName.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val activeSuggest = activeSuggestionOption
    if (activeSuggest != null) {
        AlertDialog(
            onDismissRequest = { activeSuggestionOption = null },
            title = { Text("Create '${activeSuggest.name}' Circle?") },
            text = { Text("Would you like to create a real '${activeSuggest.name}' circle or remove this suggestion?") },
            confirmButton = {
                Button(
                    onClick = {
                        autoPinSuggestedNames.add(activeSuggest.name)
                        viewModel.createCircle(activeSuggest.name)
                        suggestions = suggestions.filterNot { it.name == activeSuggest.name }
                        activeSuggestionOption = null
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            suggestions = suggestions.filterNot { it.name == activeSuggest.name }
                            activeSuggestionOption = null
                        }
                    ) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { activeSuggestionOption = null }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    val optionsCircle = showOptionsSheetForCircle
    if (optionsCircle != null) {
        val isPinned = optionsCircle.id in pinnedCircleIds
        ModalBottomSheet(
            onDismissRequest = { showOptionsSheetForCircle = null },
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = optionsCircle.name.uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                
                val pinInteraction = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .expressivePressScale(pinInteraction)
                        .clickable(
                            interactionSource = pinInteraction,
                            indication = androidx.compose.foundation.LocalIndication.current
                        ) {
                            val updated = if (isPinned) pinnedCircleIds - optionsCircle.id else pinnedCircleIds + optionsCircle.id
                            sharedPrefs.edit().putStringSet("pinned_circles", updated).apply()
                            pinnedCircleIds = updated
                            showOptionsSheetForCircle = null
                        }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pin Status",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = if (isPinned) "Unpin from Grid" else "Pin to Grid",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                val deleteInteraction = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .expressivePressScale(deleteInteraction)
                        .clickable(
                            interactionSource = deleteInteraction,
                            indication = androidx.compose.foundation.LocalIndication.current
                        ) {
                            if (isPinned) {
                                val updated = pinnedCircleIds - optionsCircle.id
                                sharedPrefs.edit().putStringSet("pinned_circles", updated).apply()
                                pinnedCircleIds = updated
                            }
                            viewModel.deleteCircle(optionsCircle)
                            showOptionsSheetForCircle = null
                        }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Circle",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Delete Circle",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CircleRowItem(
    circle: Circle,
    iconColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .expressivePressScale(interactionSource)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(iconColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Groups,
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = circle.name,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (circle.memberCount == 1) "1 member" else "${circle.memberCount} members",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        MemberAvatarStack(members = circle.members)
    }
}

@Composable
private fun MemberAvatarStack(
    members: List<CircleMember>,
    modifier: Modifier = Modifier
) {
    val visibleMembers = members.take(3)
    val remainingCount = members.size - visibleMembers.size

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((-12).dp)
    ) {
        visibleMembers.forEach { member ->
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = member.name.take(1).uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        if (remainingCount > 0) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$remainingCount",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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

private data class SuggestionCircle(
    val name: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private sealed interface GridItem {
    data class Real(val circle: Circle) : GridItem
    data class Suggest(val suggestion: SuggestionCircle) : GridItem
}

@Composable
private fun CircleSuggestionsGrid(
    gridItems: List<GridItem>,
    onRealClick: (Circle) -> Unit,
    onRealLongClick: (Circle) -> Unit,
    onSuggestClick: (SuggestionCircle) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val scaleFactor = (configuration.screenWidthDp / 360f).coerceIn(0.85f, 1.15f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Text(
            text = "PINNED & SUGGESTIONS",
            fontWeight = FontWeight.Bold,
            fontSize = (11 * scaleFactor).sp,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )

        val rows = remember(gridItems) { gridItems.chunked(4) }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy((16 * scaleFactor).dp)
        ) {
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    rowItems.forEach { item ->
                        when (item) {
                            is GridItem.Real -> {
                                PinnedCircleItem(
                                    circle = item.circle,
                                    scaleFactor = scaleFactor,
                                    onClick = { onRealClick(item.circle) },
                                    onLongClick = { onRealLongClick(item.circle) }
                                )
                            }
                            is GridItem.Suggest -> {
                                SuggestionCircleItem(
                                    suggestion = item.suggestion,
                                    scaleFactor = scaleFactor,
                                    onClick = { onSuggestClick(item.suggestion) }
                                )
                            }
                        }
                    }
                    if (rowItems.size < 4) {
                        repeat(4 - rowItems.size) {
                            Spacer(modifier = Modifier.width((60 * scaleFactor).dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PinnedCircleItem(
    circle: Circle,
    scaleFactor: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colorIndex = circle.name.length
    val bgColor = CardColors[colorIndex % CardColors.size]

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width((68 * scaleFactor).dp)
            .expressivePressScale(interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(
            modifier = Modifier
                .size((52 * scaleFactor).dp)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Groups,
                contentDescription = circle.name,
                tint = Color.Black.copy(alpha = 0.7f),
                modifier = Modifier.size((24 * scaleFactor).dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = circle.name,
            fontWeight = FontWeight.Medium,
            fontSize = (12 * scaleFactor).sp,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SuggestionCircleItem(
    suggestion: SuggestionCircle,
    scaleFactor: Float,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colorIndex = suggestion.name.length
    val bgColor = CardColors[colorIndex % CardColors.size]

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width((68 * scaleFactor).dp)
            .expressivePressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .size((52 * scaleFactor).dp)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = suggestion.icon,
                contentDescription = suggestion.name,
                tint = Color.Black.copy(alpha = 0.7f),
                modifier = Modifier.size((24 * scaleFactor).dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = suggestion.name,
            fontWeight = FontWeight.Medium,
            fontSize = (12 * scaleFactor).sp,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
