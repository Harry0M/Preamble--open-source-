package com.theblankstate.preamble.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theblankstate.preamble.ai.AiChatScreenViewModel
import com.theblankstate.preamble.ai.AiCreditsManager
import com.theblankstate.preamble.data.ChatConversationPreview
import com.theblankstate.preamble.data.ChatMessageEntity
import com.theblankstate.preamble.data.UserProfileStore
import android.app.Activity
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    viewModel: AiChatScreenViewModel,
    modifier: Modifier = Modifier,
) {
    val messages by viewModel.messages.collectAsState()
    val visibleMessages = messages.orEmpty()
    val isHistoryReady by viewModel.isHistoryReady.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val error by viewModel.error.collectAsState()
    val chatModelOverride by viewModel.chatModelOverride.collectAsState()
    val conciseMode by viewModel.conciseMode.collectAsState()
    val dismissedSuggestions by viewModel.dismissedSuggestions.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val currentConversationId by viewModel.currentConversationId.collectAsState()

    var input by remember { mutableStateOf("") }
    var showChatSheet by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val applyChatSoftInputMode = remember(activity) {
        {
            val window = activity?.window
            val stateMode = window?.attributes?.softInputMode
                ?.and(WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE)
                ?: 0
            window?.setSoftInputMode(stateMode or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            Unit
        }
    }
    SideEffect { applyChatSoftInputMode() }

    DisposableEffect(activity, lifecycleOwner) {
        val window = activity?.window
        val previousSoftInputMode = window?.attributes?.softInputMode
        applyChatSoftInputMode()
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START ||
                event == androidx.lifecycle.Lifecycle.Event.ON_RESUME
            ) {
                applyChatSoftInputMode()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            previousSoftInputMode?.let { window?.setSoftInputMode(it) }
        }
    }

    // Credits UI state
    val creditBalance by AiCreditsManager.balance.collectAsState()
    val isAdLoading by AiCreditsManager.isAdLoading.collectAsState()
    val isLoggedIn = FirebaseAuth.getInstance().currentUser != null
    var adMessage by remember { mutableStateOf<String?>(null) }

    // Preload ad and refresh balance when screen opens
    LaunchedEffect(Unit) {
        if (isLoggedIn) {
            AiCreditsManager.preloadAd(context)
            AiCreditsManager.refreshBalance(scope)
        }
    }

    // Auto-scroll to bottom on new message
    LaunchedEffect(visibleMessages.size, isSending) {
        if (visibleMessages.isNotEmpty()) {
            listState.animateScrollToItem(visibleMessages.size - 1)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .imePadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            if (messages == null || (visibleMessages.isEmpty() && !isHistoryReady)) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 44.dp))
            } else if (visibleMessages.isEmpty()) {
                EmptyChatPlaceholder(
                    modifier = Modifier.weight(1f).padding(top = 44.dp),
                    onSuggestion = { suggestion ->
                        input = suggestion
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 52.dp, bottom = 100.dp)
                ) {
                    items(visibleMessages, key = { it.id }) { msg ->
                        MessageRow(
                            msg = msg,
                            dismissedSuggestions = dismissedSuggestions,
                            onApproveSuggestion = { idx, args -> viewModel.approveTaskSuggestion(msg.id, idx, args) },
                            onDismissSuggestion = { idx -> viewModel.dismissSuggestion(msg.id, idx) },
                        )
                    }
                    if (isSending) {
                        item("typing") { TypingIndicator() }
                    }
                }
            }

            if (error != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Row(
                        Modifier.padding(12.dp).clickable { viewModel.clearError() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val errorDisplay = when (error) {
                            "DAILY_LIMIT_REACHED" ->
                                "Daily limit reached. Watch an ad to get more, or switch to Flash Lite (free & unlimited)."
                            else -> error ?: ""
                        }
                        Text(
                            errorDisplay,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "tap to dismiss",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showOptions,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(top = 6.dp, bottom = 74.dp),
                enter = expandVertically(expandFrom = Alignment.Bottom),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 5.dp,
                    shadowElevation = 5.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Credit balance + Watch Ad row
                        if (isLoggedIn) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "⚡",
                                        fontSize = 14.sp,
                                    )
                                    Text(
                                        " $creditBalance credits",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (creditBalance > 0) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.error,
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        val activity = context as? Activity
                                        if (activity != null) {
                                            AiCreditsManager.showAdForCredits(
                                                activity,
                                                onReward = { newBal ->
                                                    adMessage = "✅ +10 credits! Balance: $newBal"
                                                },
                                                onError = { err ->
                                                    adMessage = err
                                                },
                                            )
                                        }
                                    },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        if (isAdLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(12.dp),
                                                strokeWidth = 1.5.dp,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                            )
                                        } else {
                                            Icon(
                                                Icons.Filled.PlayCircle,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                            )
                                        }
                                        Text(
                                            "Watch Ad +10",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    }
                                }
                            }

                            // Ad result message
                            if (adMessage != null) {
                                Text(
                                    adMessage ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (adMessage?.startsWith("✅") == true) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.error,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { adMessage = null }
                                        .padding(vertical = 2.dp),
                                )
                            }
                        }

                        Text(
                            "Model",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            // Auto = Flash (thinking). Flash Lite = explicit lite/free mode.
                            // Mistral models are premium (daily token budget, refillable via ads).
                            val models = listOf(
                                "" to "Auto",
                                "gemini-2.5-flash-lite" to "Flash Lite",
                                "mistral-small-latest" to "Mistral",
                                "mistral-medium-latest" to "Mistral+",
                            )
                            for ((id, label) in models) {
                                val selected = chatModelOverride == id
                                val isPremium = id.contains("mistral")
                                val badge = when {
                                    id.isBlank() || id.contains("flash") -> "FREE"
                                    id.contains("mistral-medium") -> "DAILY"
                                    else -> "DAILY"
                                }

                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.clickable { viewModel.setChatModel(id) },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        )
                                        Text(
                                            badge,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            color = if (!isPremium) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                                    else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f),
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            "Response Style",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for ((enabled, label) in listOf(true to "Concise", false to "Normal")) {
                                val selected = conciseMode == enabled
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.clickable { viewModel.setConciseMode(enabled) },
                                ) {
                                    Text(
                                        label,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        AiChatComposer(
            input = input,
            onInputChange = { input = it },
            showOptions = showOptions,
            onToggleOptions = { showOptions = !showOptions },
            isSending = isSending,
            onFocus = applyChatSoftInputMode,
            onSend = {
                if (input.isNotBlank() && !isSending) {
                    viewModel.send(input.trim())
                    input = ""
                    keyboard?.hide()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )

        AiChatHeader(
            onOpenChats = { showChatSheet = true },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }

    if (showChatSheet) {
        ChatHistorySheet(
            conversations = conversations,
            currentConversationId = currentConversationId,
            onDismiss = { showChatSheet = false },
            onNewChat = {
                viewModel.newConversation()
                showChatSheet = false
            },
            onSelect = { cid ->
                viewModel.selectConversation(cid)
                showChatSheet = false
            },
            onDelete = { cid -> viewModel.deleteConversation(cid) },
        )
    }
}

@Composable
private fun AiChatComposer(
    input: String,
    onInputChange: (String) -> Unit,
    showOptions: Boolean,
    onToggleOptions: () -> Unit,
    isSending: Boolean,
    onFocus: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canSend = input.isNotBlank() && !isSending

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = if (showOptions) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 5.dp,
            shadowElevation = 5.dp,
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .clickable { onToggleOptions() },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Tune,
                    contentDescription = "Options",
                    modifier = Modifier.size(19.dp),
                    tint = if (showOptions) MaterialTheme.colorScheme.onPrimary
                           else MaterialTheme.colorScheme.primary,
                )
            }
        }

        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 5.dp,
            shadowElevation = 5.dp,
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = 46.dp)
                    .padding(start = 15.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) onFocus()
                        },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (canSend) onSend()
                        },
                    ),
                    maxLines = 4,
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (input.isBlank()) {
                                Text(
                                    "Ask anything...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
                                )
                            }
                            innerTextField()
                        }
                    },
                )

                Surface(
                    shape = CircleShape,
                    color = if (canSend) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface,
                    tonalElevation = if (canSend) 2.dp else 0.dp,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable(enabled = canSend) { onSend() },
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            modifier = Modifier.size(17.dp),
                            tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiChatHeader(
    onOpenChats: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                com.theblankstate.preamble.ui.components.NotationIcon(
                    type = "half_dotted",
                    size = 14.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Preamble",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .clickable { onOpenChats() },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.ChatBubbleOutline,
                    contentDescription = "Chats",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatHistorySheet(
    conversations: List<ChatConversationPreview>,
    currentConversationId: String,
    onDismiss: () -> Unit,
    onNewChat: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Chats",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    modifier = Modifier.clickable { onNewChat() },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            "New chat",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            if (conversations.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "No previous chats yet.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(conversations, key = { it.conversationId }) { convo ->
                        ChatConversationRow(
                            convo = convo,
                            selected = convo.conversationId == currentConversationId,
                            onSelect = { onSelect(convo.conversationId) },
                            onDelete = { onDelete(convo.conversationId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatConversationRow(
    convo: ChatConversationPreview,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .clickable { onSelect() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                com.theblankstate.preamble.ui.components.NotationIcon(
                    type = if (selected) "half_dotted" else "solid",
                    size = 16.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    conversationTitle(convo),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${convo.messageCount} messages · ${formatConversationTime(convo.lastTimestamp)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Filled.DeleteForever,
                    contentDescription = "Delete chat",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private fun conversationTitle(convo: ChatConversationPreview): String {
    val content = convo.lastContent?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
    if (content.isNotBlank()) return content.take(54)
    return if (convo.conversationId == "default") "Default chat" else "Untitled chat"
}

private fun formatConversationTime(timestamp: Long): String {
    if (timestamp <= 0L) return "just now"
    return SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(timestamp))
}

@Composable
private fun MessageRow(
    msg: ChatMessageEntity,
    dismissedSuggestions: Set<String> = emptySet(),
    onApproveSuggestion: (Int, Map<String, String>) -> Unit = { _, _ -> },
    onDismissSuggestion: (Int) -> Unit = {},
) {
    when (msg.role) {
        "user" -> UserBubble(msg)
        "assistant" -> AssistantBubble(msg, dismissedSuggestions, onApproveSuggestion, onDismissSuggestion)
        "summary" -> SystemNote("📌 Earlier conversation summarized: ${msg.content.take(180)}")
        else -> SystemNote(msg.content)
    }
}

@Composable
private fun UserBubble(msg: ChatMessageEntity) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
            color = MaterialTheme.colorScheme.primary,
            tonalElevation = 2.dp,
            modifier = Modifier.padding(start = 56.dp),
        ) {
            Text(
                msg.content,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 22.sp,
            )
        }
    }
}

@Composable
private fun AssistantBubble(
    msg: ChatMessageEntity,
    dismissedSuggestions: Set<String>,
    onApproveSuggestion: (Int, Map<String, String>) -> Unit,
    onDismissSuggestion: (Int) -> Unit,
) {
    val context = LocalContext.current

    // Parse toolCalls JSON into typed buckets once
    val thinkingText = remember(msg.toolCalls) { extractThinking(msg.toolCalls) }
    val actionSteps = remember(msg.toolCalls, msg.toolResults) {
        parseToolSteps(msg.toolCalls ?: "", msg.toolResults)
            .filter { it.name !in setOf("thinking", "suggest_task") }
    }
    val suggestions = remember(msg.toolCalls) { extractSuggestions(msg.toolCalls) }

    Column(modifier = Modifier.fillMaxWidth().padding(end = 24.dp)) {

        // 1. Collapsible thinking (like Claude) — only for thinking-capable models
        if (!thinkingText.isNullOrBlank()) {
            ThinkingCollapsible(thinkingText)
            Spacer(Modifier.height(4.dp))
        }

        // 2. Task action chips (add/modify/delete/complete/list)
        if (actionSteps.isNotEmpty()) {
            ThinkingSection(actionSteps)
            Spacer(Modifier.height(8.dp))
        }

        // 3. Main response text
        if (msg.content.isNotBlank()) {
            Text(
                "Preamble",
                modifier = Modifier.padding(bottom = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.6.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f),
            )
            SelectionContainer {
                RichMarkdownText(
                    text = msg.content,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(
                    onClick = {
                        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clip.setPrimaryClip(ClipData.newPlainText("AI Response", msg.content))
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "Copy",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
                msg.modelUsed?.takeIf { it.isNotBlank() }?.let { ModelBadge(it) }
            }
        }

        // 4. Task suggestions (approve / skip) — shown below response
        suggestions.forEachIndexed { idx, args ->
            val key = "${msg.id}:$idx"
            if (key !in dismissedSuggestions) {
                Spacer(Modifier.height(10.dp))
                SuggestionCard(
                    args = args,
                    onApprove = { onApproveSuggestion(idx, args) },
                    onDismiss = { onDismissSuggestion(idx) },
                )
            }
        }
    }
}

@Composable
private fun ModelBadge(model: String) {
    val short = when {
        model.contains("flash-lite") -> "Flash Lite"
        model.contains("gemini-2.5-flash") -> "Flash"
        model.contains("gemini-2.5-pro") -> "Pro"
        model.contains("mistral-medium") -> "Mistral M"
        model.contains("mistral-small") -> "Mistral S"
        model.equals("gemini", ignoreCase = true) -> "Gemini"
        model.equals("mistral", ignoreCase = true) -> "Mistral"
        else -> model.take(20)
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Text(
            short,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontWeight = FontWeight.Medium,
        )
    }
}

// ───────────── Helpers: parse toolCalls JSON into typed buckets ─────────────

private fun extractThinking(toolCallsJson: String?): String? {
    if (toolCallsJson.isNullOrBlank()) return null
    return runCatching {
        val arr = com.google.gson.JsonParser.parseString(toolCallsJson).asJsonArray
        arr.firstOrNull { it.asJsonObject.get("name")?.asString == "thinking" }
            ?.asJsonObject?.getAsJsonObject("args")?.get("text")?.asString
    }.getOrNull()
}

private fun extractSuggestions(toolCallsJson: String?): List<Map<String, String>> {
    if (toolCallsJson.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = com.google.gson.JsonParser.parseString(toolCallsJson).asJsonArray
        arr.filter { it.asJsonObject.get("name")?.asString == "suggest_task" }
            .mapNotNull { el ->
                val argsObj = el.asJsonObject.getAsJsonObject("args") ?: return@mapNotNull null
                argsObj.entrySet().associate { (k, v) ->
                    k to if (v.isJsonPrimitive) v.asString else v.toString()
                }
            }
    }.getOrDefault(emptyList())
}

// ───────────── Thinking / Tool Calls Section ─────────────

private fun toolDisplayName(name: String): String = when (name) {
    "list_tasks" -> "Searched your tasks"
    "add_task" -> "Created a task"
    "modify_task" -> "Updated a task"
    "delete_task" -> "Deleted a task"
    "complete_task" -> "Marked task complete"
    "set_reminder" -> "Set a reminder"
    else -> name.replace("_", " ").replaceFirstChar { it.uppercase() }
}

private fun toolArgsSummary(name: String, args: Map<String, String>): String = when (name) {
    "list_tasks" -> args["date"]?.let { "for $it" } ?: "for today"
    "add_task" -> args["title"]?.let { "\"$it\"" } ?: ""
    "modify_task" -> args["title"]?.let { "\"$it\"" } ?: ""
    "delete_task" -> args["title"]?.let { "\"$it\"" } ?: ""
    "complete_task" -> args["title"]?.let { "\"$it\"" } ?: ""
    "set_reminder" -> args["title"]?.let { "for \"$it\"" } ?: ""
    else -> args.entries.joinToString(", ") { "${it.key}: ${it.value}" }
}

private data class ToolStep(
    val name: String,
    val displayName: String,
    val argsSummary: String,
    val result: String,
    val args: Map<String, String> = emptyMap(),
)

private fun parseToolSteps(toolCallsJson: String, toolResultsJson: String?): List<ToolStep> {
    return runCatching {
        val parsed = com.google.gson.JsonParser.parseString(toolCallsJson)
        if (!parsed.isJsonArray) return@runCatching emptyList()
        val arr = parsed.asJsonArray
        // Try parsing results
        val results = toolResultsJson?.let {
            runCatching {
                val r = com.google.gson.JsonParser.parseString(it)
                if (r.isJsonArray) r.asJsonArray else null
            }.getOrNull()
        }
        arr.mapIndexed { idx, el ->
            val obj = el.asJsonObject
            val fnName = obj.get("name")?.asString ?: "tool"
            val args = mutableMapOf<String, String>()
            obj.getAsJsonObject("args")?.entrySet()?.forEach { (k, v) ->
                args[k] = if (v.isJsonPrimitive) v.asString else v.toString()
            }
            val result = results?.let { r ->
                if (idx < r.size()) {
                    val rObj = r[idx].asJsonObject
                    rObj.get("result")?.asString ?: ""
                } else ""
            } ?: ""
            ToolStep(fnName, toolDisplayName(fnName), toolArgsSummary(fnName, args), result, args)
        }
    }.getOrDefault(emptyList())
}

// Collapsible "Thought for a moment" block — like Claude
@Composable
private fun ThinkingCollapsible(thinkingText: String) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
            )
            Text(
                if (expanded) "Thinking" else "Thought for a moment",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            )
        }
        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            ) {
                SelectionContainer {
                    Text(
                        thinkingText,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp,
                    )
                }
            }
        }
    }
}

// Suggestion card with approve / skip
@Composable
private fun SuggestionCard(
    args: Map<String, String>,
    onApprove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = args["title"] ?: return
    val tags = args["tags"]
    val priority = args["priority"]?.toIntOrNull() ?: 0
    val description = args["description"]

    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(
            "Suggested task",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.padding(bottom = 3.dp),
        )
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!description.isNullOrBlank()) {
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Add task",
                modifier = Modifier.clickable { onApprove() },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Dismiss",
                modifier = Modifier.clickable { onDismiss() },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun ThinkingSection(steps: List<ToolStep>) {
    if (steps.isEmpty()) return

    // Single task action → structured inline card (no expand needed)
    if (steps.size == 1 && steps[0].name in setOf("add_task", "modify_task", "delete_task", "complete_task")) {
        InlineTaskActionRow(steps[0])
        Spacer(Modifier.height(6.dp))
        return
    }

    // Multi-tool or non-task action → borderless collapsible hint
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
            )
            Text(
                steps.joinToString(" · ") { it.displayName }.take(80),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(Modifier.padding(start = 19.dp, bottom = 6.dp)) {
                steps.forEach { step ->
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        )
                        Column {
                            Text(
                                step.displayName + if (step.argsSummary.isNotBlank()) " ${step.argsSummary}" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (step.result.isNotBlank()) {
                                Text(
                                    step.result,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(top = 1.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}

// Inline card for a single task creation/modification/completion
@Composable
private fun InlineTaskActionRow(step: ToolStep) {
    val (actionLabel, dotColor) = when (step.name) {
        "add_task" -> "Added" to MaterialTheme.colorScheme.primary
        "modify_task" -> "Updated" to MaterialTheme.colorScheme.secondary
        "delete_task" -> "Deleted" to MaterialTheme.colorScheme.error
        "complete_task" -> "Completed" to MaterialTheme.colorScheme.tertiary
        else -> step.displayName to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val title = step.args["title"]?.takeIf { it.isNotBlank() }
        ?: step.argsSummary.trim('"').takeIf { it.isNotBlank() }
    val time = step.args["deadline_time"]
    val tagsRaw = step.args["tags"]
    val priority = step.args["priority"]?.toIntOrNull() ?: 0

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(dotColor.copy(alpha = 0.75f), CircleShape)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                actionLabel,
                style = MaterialTheme.typography.labelSmall,
                color = dotColor.copy(alpha = 0.8f),
                fontWeight = FontWeight.SemiBold,
            )
            if (!title.isNullOrBlank()) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!time.isNullOrBlank() || !tagsRaw.isNullOrBlank() || priority > 0) {
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    time?.takeIf { it.isNotBlank() }?.let { TimeCapsule(it) }
                    tagsRaw?.split(",")?.take(3)?.forEach { tag ->
                        val trimmed = tag.trim()
                        if (trimmed.isNotBlank()) TagCapsule(trimmed)
                    }
                    if (priority > 0) PriorityCapsule(priority)
                }
            }
        }
    }
}

@Composable
private fun TimeCapsule(time: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
    ) {
        Text(
            "⏰ $time",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun TagCapsule(tag: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
    ) {
        Text(
            tag,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
private fun PriorityCapsule(priority: Int) {
    val (label, color) = when (priority) {
        3 -> "P3 High" to MaterialTheme.colorScheme.error
        2 -> "P2 Med" to MaterialTheme.colorScheme.primary
        else -> "P1 Low" to MaterialTheme.colorScheme.secondary
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.10f),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SystemNote(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(8.dp),
        )
    }
}

@Composable
private fun TypingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(end = 24.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.theblankstate.preamble.ui.components.NotationIcon(
            type = "half_dotted",
            size = 14.dp,
            color = MaterialTheme.colorScheme.primary,
            spinning = true,
        )
        Text(
            "thinking…",
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyChatPlaceholder(modifier: Modifier = Modifier, onSuggestion: (String) -> Unit) {
    val context = LocalContext.current
    val name = remember { UserProfileStore.load(context).name?.takeIf { it.isNotBlank() } }
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        com.theblankstate.preamble.ui.components.NotationIcon(
            type = "half_dotted",
            size = 52.dp,
            color = MaterialTheme.colorScheme.primary,
            spinning = true,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (name != null) "Hi $name 👋" else "Hi there 👋",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Ask me to plan your day, add tasks, or just chat.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(32.dp))
        data class Suggestion(val text: String, val notationType: String)
        val suggestions = listOf(
            Suggestion("What should I focus on right now?", "solid"),
            Suggestion("Add a gym task for tomorrow 7am", "solid"),
            Suggestion("Move my hospital task to Friday", "half_dotted"),
            Suggestion("Summarize what I got done this week", "fully_dotted"),
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            for (s in suggestions) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    ),
                    modifier = Modifier.fillMaxWidth().clickable { onSuggestion(s.text) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        com.theblankstate.preamble.ui.components.NotationIcon(
                            type = s.notationType,
                            size = 16.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            s.text,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        )
                    }
                }
            }
        }
    }
}

// ───────────── Rich Markdown Text ─────────────

@Composable
private fun RichMarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val lines = text.split("\n")
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (line in lines) {
            // Strip suggestion markers — they are rendered as SuggestionCard, not inline text
            if (line.trimStart().startsWith("[SUGGEST:")) continue
            when {
                line.isBlank() -> Spacer(Modifier.height(4.dp))
                line.startsWith("### ") -> Text(
                    parseInlineMarkdown(line.removePrefix("### "), codeBackground),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
                line.startsWith("## ") -> Text(
                    parseInlineMarkdown(line.removePrefix("## "), codeBackground),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
                line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                    val content = line.trimStart().drop(2)
                    Row(modifier = Modifier.padding(start = 8.dp)) {
                        Text("•  ", color = color, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            parseInlineMarkdown(content, codeBackground),
                            style = MaterialTheme.typography.bodyLarge,
                            color = color,
                        )
                    }
                }
                line.trimStart().matches(Regex("^\\d+\\.\\s.*")) -> {
                    val num = line.trimStart().substringBefore(".")
                    val content = line.trimStart().substringAfter(". ")
                    Row(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            "$num. ",
                            color = color,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            parseInlineMarkdown(content, codeBackground),
                            style = MaterialTheme.typography.bodyLarge,
                            color = color,
                        )
                    }
                }
                else -> Text(
                    parseInlineMarkdown(line, codeBackground),
                    style = MaterialTheme.typography.bodyLarge,
                    color = color,
                )
            }
        }
    }
}

private fun parseInlineMarkdown(text: String, codeBg: Color): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                // **bold**
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else { append(text[i]); i++ }
                }
                // `code`
                text.startsWith("`", i) && !text.startsWith("``", i) -> {
                    val end = text.indexOf("`", i + 1)
                    if (end > i) {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) {
                            append(" ${text.substring(i + 1, end)} ")
                        }
                        i = end + 1
                    } else { append(text[i]); i++ }
                }
                // *italic*
                text.startsWith("*", i) && !text.startsWith("**", i) -> {
                    val end = text.indexOf("*", i + 1)
                    if (end > i) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else { append(text[i]); i++ }
                }
                else -> { append(text[i]); i++ }
            }
        }
    }
}
