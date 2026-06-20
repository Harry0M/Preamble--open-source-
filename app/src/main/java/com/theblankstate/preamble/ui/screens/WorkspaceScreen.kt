package com.theblankstate.preamble.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.theblankstate.preamble.ui.viewmodels.WorkspaceUiState
import com.theblankstate.preamble.ui.viewmodels.WorkspaceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    viewModel: WorkspaceViewModel = viewModel(),
    initialInviteId: String? = null,
    onInviteConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val friends by viewModel.friends.collectAsState()
    val invites by viewModel.invites.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    
    val context = LocalContext.current
    var showAddFriendDialog by remember { mutableStateOf(initialInviteId != null) }
    var targetId by remember { mutableStateOf(initialInviteId ?: "") }
    
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

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
            TopAppBar(
                title = { Text("Workspace", fontWeight = FontWeight.Black, fontSize = 28.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddFriendDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Add Friend")
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
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Profile Card (My ID)
                item {
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                "My Preamble ID", 
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f), 
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    viewModel.myPreambleId, 
                                    fontWeight = FontWeight.Black, 
                                    fontSize = 28.sp, 
                                    letterSpacing = 2.sp
                                )
                                FilledIconButton(
                                    onClick = {
                                        val shareIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, "Add me on Preamble! My ID is ${viewModel.myPreambleId} or click here: https://preamble.theblankstate.com/invite/${viewModel.myPreambleId}")
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Preamble ID"))
                                    },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
                
                // Pending Invites Section
                if (invites.isNotEmpty()) {
                    item {
                        Text(
                            "Pending Invites", 
                            fontWeight = FontWeight.Bold, 
                            fontSize = 16.sp, 
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp).animateItem()
                        )
                    }
                    items(invites, key = { "invite_${it.id}" }) { invite ->
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem(fadeInSpec = tween(300), fadeOutSpec = tween(300)),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Avatar Placeholder
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(invite.senderName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("ID: ${invite.senderPreambleId}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                }
                                Row {
                                    IconButton(onClick = { viewModel.acceptInvite(invite) }) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Accept", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                                    }
                                    IconButton(onClick = { viewModel.declineInvite(invite.id) }) {
                                        Icon(Icons.Default.Cancel, contentDescription = "Decline", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Friends Section
                if (friends.isNotEmpty()) {
                    item {
                        Text(
                            "My Friends", 
                            fontWeight = FontWeight.Bold, 
                            fontSize = 16.sp, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp).animateItem()
                        )
                    }
                }
                
                items(friends, key = { "friend_${it.uid}" }) { friend ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(fadeInSpec = tween(300), fadeOutSpec = tween(300)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                // Avatar Placeholder
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(friend.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                    Text("ID: ${friend.preambleId}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                }
                            }
                            IconButton(onClick = { viewModel.removeFriend(friend.uid) }) {
                                Icon(Icons.Default.PersonRemove, contentDescription = "Remove Friend", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                            }
                        }
                    }
                }

            if (invites.isEmpty() && friends.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Your workspace is empty.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Share your ID or tap the + button to add friends!",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 14.sp
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
}
