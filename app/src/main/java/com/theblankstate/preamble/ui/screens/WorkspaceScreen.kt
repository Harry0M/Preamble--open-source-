package com.theblankstate.preamble.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.theblankstate.preamble.R
import com.theblankstate.preamble.repository.Friend
import com.theblankstate.preamble.ui.components.LeaderboardSection
import com.theblankstate.preamble.ui.components.ReferralCta
import com.theblankstate.preamble.ui.viewmodels.FriendRemovalImpact
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    viewModel: WorkspaceViewModel = viewModel(),
    taskViewModel: TaskViewModel? = null,
    initialInviteId: String? = null,
    onInviteConsumed: () -> Unit = {},
    onClose: (() -> Unit)? = null,
    onOpenCircles: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val friends by viewModel.friends.collectAsState()
    val invites by viewModel.invites.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    // Friends_Leaderboard (social-engagement Requirements 9.1, 9.2, 9.6), ranked by current
    // Weekly_Window points in the ViewModel. Drives both the LeaderboardSection and the
    // current-week figure that supersedes the legacy friend.productivityPoints display.
    val leaderboard by viewModel.leaderboard.collectAsState()
    val currentUserUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    val weeklyPointsByUid = remember(leaderboard) { leaderboard.associate { it.uid to it.weeklyPoints } }
    val statsState = taskViewModel?.statsState?.collectAsState()?.value
    
    val myScore = statsState?.productivityScore ?: 0
    
    val context = LocalContext.current
    var showAddFriendDialog by remember { mutableStateOf(initialInviteId != null) }
    var targetId by remember { mutableStateOf(initialInviteId ?: "") }
    var friendPendingRemoval by remember { mutableStateOf<Friend?>(null) }
    var removalImpact by remember { mutableStateOf<FriendRemovalImpact?>(null) }
    var transferOwnedTasks by remember { mutableStateOf(false) }
    
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    // Pick random backgrounds on composition
    val heroBackground = remember { RandomBackgrounds.random() }
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
            targetId = initialInviteId
            showAddFriendDialog = true
            onInviteConsumed()
        }
    }

    Scaffold(
        modifier = modifier,
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
                    if (onOpenCircles != null) {
                        // Entry into Shared Circles from the friends/workspace area
                        // (shared-circles Requirement 2.4).
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onOpenCircles() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Groups, contentDescription = "Circles", modifier = Modifier.size(18.dp))
                                Text("Circles", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { showAddFriendDialog = true }
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                // Tightly pack the cards!
                verticalArrangement = Arrangement.spacedBy((-12).dp)
            ) {
                // HERO CARD (My ID)
                item {
                    val heroColor = Color(0xFFD4FF70) // Vibrant Lime Green
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp) // More rectangular
                            .padding(bottom = 16.dp) 
                            .animateItem(),
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
                                    // Abstract icon
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
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
                                    
                                    // Bottom right action button
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black)
                                            .clickable {
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
                
                // REFERRAL CTA (Growth-loops Req 1): both sides get 50 credits.
                item {
                    ReferralCta(
                        inviteLink = viewModel.buildInviteLink(),
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }

                // PENDING INVITES
                itemsIndexed(invites, key = { _, it -> "invite_${it.id}" }) { _, invite ->
                    val cardColor = Color(0xFF2C2C2E) // Dark slate for invites
                    val inviteBg = remember(invite.id) { RandomBackgrounds.random() }
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp) // Fixed height to create the "slab" look
                            .animateItem(fadeInSpec = tween(300), fadeOutSpec = tween(300)),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = cardColor)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.matchParentSize()) {
                                Image(
                                    painter = painterResource(id = inviteBg),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    alignment = Alignment.TopCenter,
                                    modifier = Modifier.fillMaxSize(),
                                    alpha = 0.3f
                                )
                            }
                            
                            Row(
                                modifier = Modifier.padding(horizontal = 24.dp).fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(
                                        model = "https://api.dicebear.com/9.x/micah/png?seed=${invite.senderPreambleId}",
                                        contentDescription = "Avatar",
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha=0.3f))
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            text = "Invite: ${invite.senderName}", 
                                            fontWeight = FontWeight.Bold, 
                                            fontSize = 18.sp,
                                            color = Color.White
                                        )
                                        Text("ID: ${invite.senderPreambleId}", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconButton(onClick = { viewModel.acceptInvite(invite) }, modifier = Modifier.background(Color.White.copy(alpha=0.1f), CircleShape)) {
                                        Icon(Icons.Default.Check, contentDescription = "Accept", tint = Color(0xFFD4FF70))
                                    }
                                    IconButton(onClick = { viewModel.declineInvite(invite.id) }, modifier = Modifier.background(Color.White.copy(alpha=0.1f), CircleShape)) {
                                        Icon(Icons.Default.Close, contentDescription = "Decline", tint = Color(0xFFFF9E9E))
                                    }
                                }
                            }
                        }
                    }
                }

                // FRIENDS LEADERBOARD (social-engagement Requirements 9.1, 9.2, 9.6)
                item(key = "leaderboard_section") {
                    LeaderboardSection(
                        entries = leaderboard,
                        currentUserUid = currentUserUid,
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(300),
                            fadeOutSpec = tween(300)
                        )
                    )
                }

                // FRIENDS LIST
                itemsIndexed(friends, key = { _, it -> "friend_${it.uid}" }) { index, friend ->
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

                if (invites.isEmpty() && friends.isEmpty()) {
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
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                "It's quiet here.",
                                fontSize = 24.sp,
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Tap Add to start your workspace.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(80.dp)) // Padding for bottom nav
                }
            }
        }
    }

    if (showAddFriendDialog) {
        AlertDialog(
            onDismissRequest = { showAddFriendDialog = false },
            title = { Text("Add Friend") },
            text = {
                OutlinedTextField(
                    value = targetId,
                    onValueChange = { targetId = it.uppercase() },
                    label = { Text("Friend's Preamble ID") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.sendInvite(targetId)
                        showAddFriendDialog = false
                    },
                    enabled = targetId.isNotBlank() && uiState !is WorkspaceUiState.Loading
                ) {
                    Text("Send Invite")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFriendDialog = false }) {
                    Text("Cancel")
                }
            }
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
