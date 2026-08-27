package com.theblankstate.preamble.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import com.theblankstate.preamble.ui.screens.MorphPolygonShape
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiChatScreen(
    viewModel: AiChatScreenViewModel,
    showInternalHeader: Boolean = false,
    externalShowChatSheet: Boolean = false,
    onDismissChatSheet: () -> Unit = {},
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val messages by viewModel.messages.collectAsState()
    val visibleMessages = messages.orEmpty()
    val isHistoryReady by viewModel.isHistoryReady.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val error by viewModel.error.collectAsState()
    val dismissedSuggestions by viewModel.dismissedSuggestions.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val currentConversationId by viewModel.currentConversationId.collectAsState()

    var input by remember { mutableStateOf("") }
    var editingMessageId by remember { mutableStateOf<String?>(null) }
    var showChatSheet by remember { mutableStateOf(false) }

    LaunchedEffect(externalShowChatSheet) {
        if (externalShowChatSheet) {
            showChatSheet = true
            onDismissChatSheet()
        }
    }
    val tokensRemaining by viewModel.tokensRemaining.collectAsState()
    val isDailyLimitReached = error == "DAILY_LIMIT_REACHED" || tokensRemaining == 0
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val showScrollToBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && lastVisible < info.totalItemsCount - 1
        }
    }

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

    // Instant jump to bottom when opening or loading conversation (no animation lag)
    LaunchedEffect(currentConversationId, isHistoryReady) {
        editingMessageId = null
        input = ""
        if (visibleMessages.isNotEmpty()) {
            listState.scrollToItem(visibleMessages.size - 1)
        }
    }

    // Smooth scroll to bottom only when actively sending a new message
    LaunchedEffect(visibleMessages.size, isSending) {
        if (visibleMessages.isNotEmpty() && isSending) {
            listState.animateScrollToItem(visibleMessages.size - 1)
        }
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topHeaderOffset = if (showInternalHeader) statusBarTop + 60.dp else 44.dp

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
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = topHeaderOffset))
            } else if (visibleMessages.isEmpty()) {
                EmptyChatPlaceholder(
                    modifier = Modifier.weight(1f).padding(top = topHeaderOffset),
                    onSuggestion = { suggestion ->
                        input = suggestion
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = topHeaderOffset, bottom = 96.dp)
                ) {
                    items(visibleMessages, key = { it.id }) { msg ->
                        MessageRow(
                            msg = msg,
                            dismissedSuggestions = dismissedSuggestions,
                            onEditUserMessage = { message ->
                                editingMessageId = message.id
                                input = message.content
                                viewModel.clearError()
                            },
                            onApproveSuggestion = { idx, args -> viewModel.approveTaskSuggestion(msg.id, idx, args) },
                            onDismissSuggestion = { idx -> viewModel.dismissSuggestion(msg.id, idx) },
                        )
                    }
                    if (isSending) {
                        item("typing") { TypingIndicator() }
                    }
                }
            }
        }

        if (error != null && error != "DAILY_LIMIT_REACHED") {
            AiChatErrorBanner(
                error = error!!,
                onDismiss = { viewModel.clearError() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(
                        bottom = when {
                            editingMessageId != null -> 112.dp
                            else -> 70.dp
                        }
                    ),
            )
        }

        AnimatedVisibility(
            visible = showScrollToBottom,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp)
                .padding(
                    bottom = when {
                        isDailyLimitReached -> 88.dp
                        error != null && error != "DAILY_LIMIT_REACHED" -> 128.dp
                        editingMessageId != null -> 118.dp
                        else -> 78.dp
                    }
                ),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable {
                        scope.launch {
                            val lastIndex = listState.layoutInfo.totalItemsCount - 1
                            if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
                        }
                    },
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.ExpandMore,
                        contentDescription = "Go to bottom",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
        }

        val isImeVisible = WindowInsets.isImeVisible
        if (isDailyLimitReached) {
            AiChatDailyLimitCard(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = if (isImeVisible) 8.dp else 12.dp),
            )
        } else {
            AiChatComposer(
                input = input,
                onInputChange = { input = it },
                isEditing = editingMessageId != null,
                onCancelEdit = {
                    editingMessageId = null
                    input = ""
                },
                isSending = isSending,
                onFocus = applyChatSoftInputMode,
                onSend = {
                    if (input.isNotBlank() && !isSending) {
                        val text = input.trim()
                        val editingId = editingMessageId
                        if (editingId != null) {
                            viewModel.editAndResend(editingId, text)
                        } else {
                            viewModel.send(text)
                        }
                        editingMessageId = null
                        input = ""
                        keyboard?.hide()
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = if (isImeVisible) 8.dp else 12.dp),
            )
        }

        if (showInternalHeader) {
            AiChatHeader(
                onOpenChats = { showChatSheet = true },
                onClose = onClose,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding(),
            )
        }
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
private fun AiChatErrorBanner(
    error: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val errorDisplay = when (error) {
        "DAILY_LIMIT_REACHED" ->
            "Daily limit reached. Watch an ad to get more, or switch to Flash Lite (free & unlimited)."
        "Sign in to use AI features." ->
            "Sign in to unlock AI chat. Go to Settings → Sign in with Google."
        else -> error
    }

    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            Modifier
                .clickable { onDismiss() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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

@Composable
private fun AiChatDailyLimitCard(
    modifier: Modifier = Modifier,
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val scaleFactor = (screenWidth.value / 360f).coerceIn(0.85f, 1.15f)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp * scaleFactor),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp * scaleFactor, vertical = 12.dp * scaleFactor),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp * scaleFactor),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(36.dp * scaleFactor)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = "Daily limit reached",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp * scaleFactor)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Daily limit reached",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp * scaleFactor,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Daily message limit over, continue tomorrow.",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.5.sp * scaleFactor,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun AiChatComposer(
    input: String,
    onInputChange: (String) -> Unit,
    isEditing: Boolean,
    onCancelEdit: () -> Unit,
    isSending: Boolean,
    onFocus: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val scaleFactor = (screenWidth.value / 360f).coerceIn(0.85f, 1.15f)

    val canSend = input.isNotBlank() && !isSending
    val sendInteraction = remember { MutableInteractionSource() }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp * scaleFactor),
    ) {
        if (isEditing) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier.align(Alignment.End),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp * scaleFactor, vertical = 6.dp * scaleFactor),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp * scaleFactor),
                ) {
                    Text(
                        "Editing question",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp * scaleFactor,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        "Cancel",
                        modifier = Modifier.clickable { onCancelEdit() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp * scaleFactor,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // Text Input Bar with Pill Surface & Expressive Send Button
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = 46.dp * scaleFactor)
                    .padding(start = 16.dp * scaleFactor, end = 6.dp * scaleFactor, top = 4.dp * scaleFactor, bottom = 4.dp * scaleFactor),
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
                        fontSize = 13.5.sp * scaleFactor
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
                                    fontSize = 13.5.sp * scaleFactor,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
                                )
                            }
                            innerTextField()
                        }
                    },
                )

                Surface(
                    onClick = { if (canSend) onSend() },
                    enabled = canSend,
                    shape = CircleShape,
                    color = if (canSend) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .padding(start = 6.dp * scaleFactor)
                        .size(36.dp * scaleFactor)
                        .expressivePressScale(sendInteraction)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            modifier = Modifier.size(16.dp * scaleFactor),
                            tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
                        )
                    }
                }
            }
        }
    }
}

private fun solidThemeColor(color: Color, surface: Color): Color =
    color.compositeOver(surface).copy(alpha = 1f)

private fun readableThemeColor(background: Color, preferred: Color): Color {
    val solidPreferred = preferred.compositeOver(background).copy(alpha = 1f)
    val contrast = kotlin.math.abs(background.luminance() - solidPreferred.luminance())
    if (contrast >= 0.32f) return solidPreferred
    return if (background.luminance() > 0.55f) Color.Black else Color.White
}

@Composable
private fun AiChatHeader(
    onOpenChats: () -> Unit,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (onClose != null) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable { onClose() },
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
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
                        "Preamble AI",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
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
    onEditUserMessage: (ChatMessageEntity) -> Unit = {},
    onApproveSuggestion: (Int, Map<String, String>) -> Unit = { _, _ -> },
    onDismissSuggestion: (Int) -> Unit = {},
) {
    when (msg.role) {
        "user" -> UserBubble(msg, onEdit = { onEditUserMessage(msg) })
        "assistant" -> AssistantBubble(msg, dismissedSuggestions, onApproveSuggestion, onDismissSuggestion)
        "summary" -> SystemNote("📌 Earlier conversation summarized: ${msg.content.take(180)}")
        else -> SystemNote(msg.content)
    }
}

@Composable
private fun UserBubble(
    msg: ChatMessageEntity,
    onEdit: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
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
        Row(
            modifier = Modifier
                .padding(top = 2.dp, end = 4.dp)
                .clickable { onEdit() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            )
            Text(
                "Edit",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                fontWeight = FontWeight.SemiBold,
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
    val renderBlocks = remember(msg.renderBlocksJson) { parseRenderBlocks(msg.renderBlocksJson) }
    val renderBlocksIncludeSuggestions = remember(renderBlocks) {
        renderBlocks.any { it.type == "suggested_task" }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(end = 24.dp)) {

        // 1. Collapsible thinking (like Claude) — only for thinking-capable models
        if (!thinkingText.isNullOrBlank()) {
            ThinkingCollapsible(thinkingText)
            Spacer(Modifier.height(4.dp))
        }

        // 2. Task action chips (add/modify/delete/complete/list)
        if (renderBlocks.isEmpty() && actionSteps.isNotEmpty()) {
            ThinkingSection(actionSteps)
            Spacer(Modifier.height(8.dp))
        }

        if (renderBlocks.isNotEmpty()) {
            Text(
                "Preamble",
                modifier = Modifier.padding(bottom = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.6.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f),
            )
            RenderAssistantBlocks(
                messageId = msg.id,
                blocks = renderBlocks,
                dismissedSuggestions = dismissedSuggestions,
                onApproveSuggestion = onApproveSuggestion,
                onDismissSuggestion = onDismissSuggestion,
            )
            ResponseMetaRow(context = context, msg = msg)
            if (!renderBlocksIncludeSuggestions) {
                SuggestionList(
                    messageId = msg.id,
                    suggestions = suggestions,
                    dismissedSuggestions = dismissedSuggestions,
                    onApproveSuggestion = onApproveSuggestion,
                    onDismissSuggestion = onDismissSuggestion,
                )
            }
        } else {
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
                ResponseMetaRow(context = context, msg = msg)
            }

            // 4. Task suggestions (approve / skip) — shown below response
            SuggestionList(
                messageId = msg.id,
                suggestions = suggestions,
                dismissedSuggestions = dismissedSuggestions,
                onApproveSuggestion = onApproveSuggestion,
                onDismissSuggestion = onDismissSuggestion,
            )
        }
    }
}

@Composable
private fun SuggestionList(
    messageId: String,
    suggestions: List<Map<String, String>>,
    dismissedSuggestions: Set<String>,
    onApproveSuggestion: (Int, Map<String, String>) -> Unit,
    onDismissSuggestion: (Int) -> Unit,
) {
    suggestions.forEachIndexed { idx, args ->
        val key = "$messageId:$idx"
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

@Composable
private fun ResponseMetaRow(context: Context, msg: ChatMessageEntity) {
    if (msg.content.isBlank() && msg.modelUsed.isNullOrBlank() && msg.timestamp <= 0L) return
    Row(
        modifier = Modifier
            .padding(top = 2.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (msg.content.isNotBlank()) {
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
        }
        msg.modelUsed?.takeIf { it.isNotBlank() }?.let { ModelBadge(it) }
        if (msg.timestamp > 0L) {
            MetaBadge(formatConversationTime(msg.timestamp))
        }
    }
}

@Composable
private fun RenderAssistantBlocks(
    messageId: String,
    blocks: List<AiRenderBlock>,
    dismissedSuggestions: Set<String>,
    onApproveSuggestion: (Int, Map<String, String>) -> Unit,
    onDismissSuggestion: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEachIndexed { index, block ->
            when (block.type) {
                "answer", "table", "code", "checklist", "steps" -> {
                    block.markdown?.takeIf { it.isNotBlank() }?.let { markdown ->
                        SelectionContainer {
                            RichMarkdownText(
                                text = markdown,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                "task_list" -> TaskListRenderBlock(block)
                "suggested_task" -> {
                    val args = block.args.orEmpty().toMutableMap()
                    if (args["title"].isNullOrBlank()) args["title"] = block.title.orEmpty()
                    if (args["description"].isNullOrBlank()) args["description"] = block.subtitle.orEmpty()
                    val key = "$messageId:$index"
                    if (key !in dismissedSuggestions) {
                        SuggestionCard(
                            args = args,
                            onApprove = { onApproveSuggestion(index, args) },
                            onDismiss = { onDismissSuggestion(index) },
                        )
                    }
                }
                else -> StatusRenderCard(block)
            }
        }
    }
}

@Composable
private fun StatusRenderCard(block: AiRenderBlock) {
    val accent = when (block.status) {
        "error" -> MaterialTheme.colorScheme.error
        "warning" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(accent, CircleShape),
                )
                Text(
                    renderBlockTitle(block),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
            block.subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MetadataRow(block.items.orEmpty())
        }
    }
}

@Composable
private fun StructuredListBlock(block: AiRenderBlock, numbered: Boolean) {
    val items = block.items.orEmpty()
    if (items.isEmpty()) {
        block.markdown?.let {
            SelectionContainer {
                RichMarkdownText(text = it, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        return
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                block.title?.takeIf { it.isNotBlank() } ?: if (numbered) "Steps" else "Checklist",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            items.forEachIndexed { index, item ->
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        if (numbered) "${index + 1}." else "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        item.title.orEmpty(),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskListRenderBlock(block: AiRenderBlock) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                block.title?.takeIf { it.isNotBlank() } ?: "Tasks",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val groups = block.groups.orEmpty()
            if (groups.isEmpty()) {
                block.markdown?.let {
                    SelectionContainer {
                        RichMarkdownText(text = it, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            } else {
                groups.forEach { group ->
                    Text(
                        group.title.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    group.items.orEmpty().forEach { item ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val done = item.status == "done"
                            Text(
                                if (done) "✓" else "○",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (done) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                item.title.orEmpty(),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataRow(items: List<AiRenderItem>) {
    val visible = items.filter { !it.label.isNullOrBlank() && !it.value.isNullOrBlank() }
    if (visible.isEmpty()) return
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        visible.forEach { item ->
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
            ) {
                Text(
                    "${item.label}: ${item.value}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun renderBlockTitle(block: AiRenderBlock): String {
    val fallback = when (block.type) {
        "task_created" -> "Task created"
        "reminder_set" -> "Reminder set"
        "task_updated" -> "Task updated"
        "task_completed" -> "Task completed"
        "task_deleted" -> "Task deleted"
        "memory_saved" -> "Memory saved"
        "memory_updated" -> "Memory updated"
        else -> "Result"
    }
    return block.title?.takeIf { it.isNotBlank() } ?: fallback
}

private data class AiRenderEnvelope(
    val version: Int? = null,
    val blocks: List<AiRenderBlock>? = null,
)

private data class AiRenderBlock(
    val type: String = "answer",
    val title: String? = null,
    val subtitle: String? = null,
    val status: String? = null,
    val markdown: String? = null,
    val items: List<AiRenderItem>? = null,
    val groups: List<AiRenderGroup>? = null,
    val args: Map<String, String>? = null,
)

private data class AiRenderGroup(
    val title: String? = null,
    val items: List<AiRenderItem>? = null,
)

private data class AiRenderItem(
    val title: String? = null,
    val label: String? = null,
    val value: String? = null,
    val status: String? = null,
)

private fun parseRenderBlocks(json: String?): List<AiRenderBlock> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        com.google.gson.Gson().fromJson(json, AiRenderEnvelope::class.java)
            ?.blocks
            .orEmpty()
            .filter { it.type.isNotBlank() }
    }.getOrDefault(emptyList())
}

@Composable
private fun MetaBadge(label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
    MetaBadge(short)
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
    val description = args["description"]
    val tags = args["tags"]?.split("|")?.firstOrNull()
    val priority = args["priority"]?.toIntOrNull() ?: 0
    val deadlineTime = args["deadline_time"]
    val recurrence = args["recurrence"]
    val isHabit = args["is_habit"]?.lowercase() == "true"
    val isEvent = args["is_event"]?.lowercase() == "true"
    val eventIcon = args["event_icon"]
    val eventColor = args["event_color"]?.let {
        runCatching {
            val hex = it.trimStart('#')
            Color(android.graphics.Color.parseColor("#$hex"))
        }.getOrNull()
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.97f else 1f, label = "suggest_press")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .scale(scale),
    ) {
        Text(
            "✨ Suggested task",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            tonalElevation = 0.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {

                // Title row with optional event icon or priority
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (isEvent && !eventIcon.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(
                                    eventColor?.copy(alpha = 0.2f)
                                        ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(eventIcon, style = MaterialTheme.typography.bodyMedium)
                        }
                    } else if (isHabit) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("🔥", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(
                        title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (priority >= 2) {
                        val priColor = if (priority == 3) Color(0xFFE53935) else Color(0xFFFF9800)
                        Text(
                            "P$priority",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = priColor,
                        )
                    }
                }

                // Description
                if (!description.isNullOrBlank()) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                // Metadata pills row
                val hasMeta = !tags.isNullOrBlank() || !deadlineTime.isNullOrBlank() ||
                    !recurrence.isNullOrBlank() || isHabit || isEvent
                if (hasMeta) {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // Tag pill
                        tags?.takeIf { it.isNotBlank() }?.let { tag ->
                            SuggestionPill(label = "#$tag")
                        }
                        // Time pill
                        deadlineTime?.takeIf { it.isNotBlank() }?.let { t ->
                            SuggestionPill(label = "⏰ $t")
                        }
                        // Recurrence pill
                        recurrence?.takeIf { it.isNotBlank() }?.let { r ->
                            val label = when (r) {
                                "daily" -> if (isHabit) "🔥 Habit" else "🔁 Daily"
                                "weekly" -> "🔁 Weekly"
                                "monthly" -> "🔁 Monthly"
                                "rollover" -> "↩ Rollover"
                                else -> "🔁 $r"
                            }
                            SuggestionPill(label = label)
                        }
                        // Event type pill (if is_event but no recurrence)
                        if (isEvent && recurrence.isNullOrBlank()) {
                            SuggestionPill(label = "📅 Event")
                        }
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        "＋ Add task",
                        modifier = Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                        ) { onApprove() },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Dismiss",
                        modifier = Modifier.clickable { onDismiss() },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionPill(label: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
private fun WigglingMaterialShapeHeader(scaleFactor: Float) {
    val shapeColor = MaterialTheme.colorScheme.secondaryContainer

    val morph = remember {
        Morph(
            start = RoundedPolygon.star(
                numVerticesPerRadius = 6,
                innerRadius = 0.65f,
                rounding = CornerRounding(0.25f),
            ),
            end = RoundedPolygon(
                numVertices = 8,
                rounding = CornerRounding(0.35f),
            ),
        )
    }
    val transition = rememberInfiniteTransition(label = "chatSetupWiggle")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "chatSetupMorphProgress",
    )
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000),
            repeatMode = RepeatMode.Restart,
        ),
        label = "chatSetupRotation",
    )

    Box(
        modifier = Modifier
            .size(100.dp * scaleFactor)
            .clip(MorphPolygonShape(morph, progress, rotation))
            .background(shapeColor),
        contentAlignment = Alignment.Center,
    ) {
        com.theblankstate.preamble.ui.components.NotationIcon(
            type = "half_dotted",
            size = 40.dp * scaleFactor,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            spinning = true,
        )
    }
}

private data class AiSuggestionItem(val text: String, val notationType: String)

private val AI_CHAT_50_SUGGESTIONS = listOf(
    AiSuggestionItem("Focus on right now", "solid"),
    AiSuggestionItem("Gym task for tomorrow 7am", "solid"),
    AiSuggestionItem("Move hospital task to Friday", "half_dotted"),
    AiSuggestionItem("Summarize my week", "fully_dotted"),
    AiSuggestionItem("Plan my evening workflow", "solid"),
    AiSuggestionItem("Schedule morning workout at 6am", "solid"),
    AiSuggestionItem("What tasks are urgent today?", "half_dotted"),
    AiSuggestionItem("Add grocery shopping at 5pm", "solid"),
    AiSuggestionItem("How many habits did I complete?", "fully_dotted"),
    AiSuggestionItem("Reschedule missed tasks to tomorrow", "half_dotted"),
    AiSuggestionItem("Plan a productive 4-hour sprint", "solid"),
    AiSuggestionItem("Show my productivity stats", "fully_dotted"),
    AiSuggestionItem("Set reminder for dentist on Monday", "solid"),
    AiSuggestionItem("Draft my daily to-do list", "solid"),
    AiSuggestionItem("Clear my afternoon schedule", "half_dotted"),
    AiSuggestionItem("Suggest a quick break exercise", "fully_dotted"),
    AiSuggestionItem("Organize tasks by priority", "solid"),
    AiSuggestionItem("Add team sync meeting at 2pm", "solid"),
    AiSuggestionItem("Highlight my top 3 goals", "half_dotted"),
    AiSuggestionItem("Review my progress for today", "fully_dotted"),
    AiSuggestionItem("Schedule study session for 8pm", "solid"),
    AiSuggestionItem("Postpone non-urgent tasks", "half_dotted"),
    AiSuggestionItem("How much deep work time today?", "fully_dotted"),
    AiSuggestionItem("Add client call at 11am", "solid"),
    AiSuggestionItem("Plan a relaxed Sunday schedule", "solid"),
    AiSuggestionItem("Break down project into small steps", "half_dotted"),
    AiSuggestionItem("Check my completed habits count", "fully_dotted"),
    AiSuggestionItem("Add evening reading habit at 9pm", "solid"),
    AiSuggestionItem("Optimise my morning routine", "solid"),
    AiSuggestionItem("Find free slots for a 30m walk", "half_dotted"),
    AiSuggestionItem("Summarize overdue items", "fully_dotted"),
    AiSuggestionItem("Schedule meditation at 7am", "solid"),
    AiSuggestionItem("Create a quick meal prep task", "solid"),
    AiSuggestionItem("Re-align my weekly priorities", "half_dotted"),
    AiSuggestionItem("Track my daily focus score", "fully_dotted"),
    AiSuggestionItem("Add weekly planning session on Sun", "solid"),
    AiSuggestionItem("Move low priority items to weekend", "half_dotted"),
    AiSuggestionItem("What is my next upcoming task?", "solid"),
    AiSuggestionItem("Suggest time management tips", "fully_dotted"),
    AiSuggestionItem("Schedule car service on Wednesday", "solid"),
    AiSuggestionItem("Set 15-min pomodoro focus timer", "solid"),
    AiSuggestionItem("Reorganize my evening routine", "half_dotted"),
    AiSuggestionItem("Calculate total planned hours today", "fully_dotted"),
    AiSuggestionItem("Add water intake reminder", "solid"),
    AiSuggestionItem("Shift all morning tasks by 1 hour", "half_dotted"),
    AiSuggestionItem("Show list of recurring habits", "fully_dotted"),
    AiSuggestionItem("Plan a zero-distraction focus block", "solid"),
    AiSuggestionItem("Schedule stretch break at 3pm", "solid"),
    AiSuggestionItem("What tasks take less than 10 mins?", "half_dotted"),
    AiSuggestionItem("Summarize my habit streaks", "fully_dotted"),
    AiSuggestionItem("Add doctor appointment next Tuesday", "solid"),
    AiSuggestionItem("Prepare end-of-day reflection note", "solid")
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyChatPlaceholder(modifier: Modifier = Modifier, onSuggestion: (String) -> Unit) {
    val context = LocalContext.current
    val name = remember { UserProfileStore.load(context).name?.takeIf { it.isNotBlank() } }
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val scaleFactor = (screenWidth.value / 360f).coerceIn(0.85f, 1.15f)

    val motivationalList = remember {
        listOf(
            "Ready to conquer the day!" to "Ask me to plan your day, manage tasks, or answer anything.",
            "Let's make progress!" to "Turn your ideas into clear scheduled tasks in seconds.",
            "Focus & Flow Mode" to "What's on your mind? I can summarize, plan, or organize your workflow.",
            "Your AI Companion" to "Ask questions, schedule reminders, or brainstorm your next big step."
        )
    }
    val motivational = remember { motivationalList.random() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp * scaleFactor)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(50.dp * scaleFactor))

        // Wiggling Morphing Material Shape Header (same as Plan Day Init screen!)
        WigglingMaterialShapeHeader(scaleFactor = scaleFactor)

        Spacer(modifier = Modifier.height(20.dp * scaleFactor))

        // Dynamic Energetic Title (without hand emoji)
        Text(
            text = if (name != null) "Hi $name\n${motivational.first}" else motivational.first,
            fontWeight = FontWeight.Black,
            fontSize = 20.sp * scaleFactor,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp * scaleFactor))

        // Dynamic Motivational Subtitle
        Text(
            text = motivational.second,
            fontSize = 12.sp * scaleFactor,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp * scaleFactor)
        )

        Spacer(modifier = Modifier.height(24.dp * scaleFactor))

        // Sample 6 random suggestions from the 50+ variant pool
        val suggestions = remember { AI_CHAT_50_SUGGESTIONS.shuffled().take(6) }

        // FlowRow staggered horizontal chips layout matching:
        //   ----------
        //   -- ----  ---
        // ----------------
        FlowRow(
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(10.dp * scaleFactor),
            modifier = Modifier.fillMaxWidth()
        ) {
            for (s in suggestions) {
                val suggestionInteraction = remember { MutableInteractionSource() }
                Surface(
                    onClick = { onSuggestion(s.text) },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .padding(horizontal = 4.dp * scaleFactor)
                        .expressivePressScale(suggestionInteraction),
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = 14.dp * scaleFactor,
                            vertical = 10.dp * scaleFactor
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp * scaleFactor),
                    ) {
                        com.theblankstate.preamble.ui.components.NotationIcon(
                            type = s.notationType,
                            size = 14.dp * scaleFactor,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            s.text,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 12.sp * scaleFactor,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp * scaleFactor),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(150.dp * scaleFactor))
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
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trimStart()
            if (trimmed.startsWith("```")) {
                val language = trimmed.removePrefix("```").trim().takeIf { it.isNotBlank() }
                val codeLines = mutableListOf<String>()
                index++
                while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
                    codeLines += lines[index]
                    index++
                }
                if (index < lines.size) index++
                CodeFenceBlock(language = language, code = codeLines.joinToString("\n"))
            } else {
                MarkdownTextLine(line = line, codeBackground = codeBackground, color = color)
                index++
            }
        }
    }
}

@Composable
private fun MarkdownTextLine(line: String, codeBackground: Color, color: Color) {
    // Strip SUGGEST markers from display entirely
    if (line.trimStart().startsWith("[SUGGEST:")) return

    val trimmed = line.trimStart()
    val accentColor = MaterialTheme.colorScheme.primary

    when {
        // Blank line → small spacer
        line.isBlank() -> Spacer(Modifier.height(4.dp))

        // Horizontal rule: EXACTLY "---" (3+ hyphens), nothing else on the line
        trimmed.matches(Regex("^-{3,}$")) -> {
            Spacer(Modifier.height(4.dp))
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                thickness = 0.8.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(4.dp))
        }

        // H1: "# Text" — only when line starts with exactly "# " (not "##", "###")
        line.startsWith("# ") && !line.startsWith("## ") -> Text(
            parseInlineMarkdown(line.removePrefix("# "), codeBackground),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
        )

        // H2: "## Text"
        line.startsWith("## ") && !line.startsWith("### ") -> Text(
            parseInlineMarkdown(line.removePrefix("## "), codeBackground),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(top = 2.dp),
        )

        // H3: "### Text"
        line.startsWith("### ") -> Text(
            parseInlineMarkdown(line.removePrefix("### "), codeBackground),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color,
        )

        // Blockquote: "> Text" → left accent bar + indented italic text
        trimmed.startsWith("> ") -> {
            val content = trimmed.removePrefix("> ")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .heightIn(min = 20.dp)
                        .background(
                            accentColor.copy(alpha = 0.55f),
                            RoundedCornerShape(2.dp),
                        )
                        .align(Alignment.CenterVertically),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    parseInlineMarkdown(content, codeBackground),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontStyle = FontStyle.Italic,
                    ),
                    color = color.copy(alpha = 0.8f),
                )
            }
        }

        // Nested bullet: "  - " or "    - " (2+ leading spaces)
        line.startsWith("  ") && (trimmed.startsWith("- ") || trimmed.startsWith("* ")) -> {
            val content = trimmed.drop(2)
            Row(modifier = Modifier.padding(start = 24.dp)) {
                Text("◦  ", color = color.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
                Text(
                    parseInlineMarkdown(content, codeBackground),
                    style = MaterialTheme.typography.bodyMedium,
                    color = color.copy(alpha = 0.9f),
                )
            }
        }

        // Top-level bullet: "- " or "* "
        trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
            val content = trimmed.drop(2)
            Row(modifier = Modifier.padding(start = 8.dp)) {
                Text("•  ", color = color, style = MaterialTheme.typography.bodyLarge)
                Text(
                    parseInlineMarkdown(content, codeBackground),
                    style = MaterialTheme.typography.bodyLarge,
                    color = color,
                )
            }
        }

        // Numbered list: "1. text"
        trimmed.matches(Regex("^\\d+\\.\\s.*")) -> {
            val num = trimmed.substringBefore(".")
            val content = trimmed.substringAfter(". ")
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

        // Plain text (fallback) — strip any stray leading # that shouldn't be there
        else -> {
            val displayLine = if (trimmed.startsWith("#") && !trimmed.startsWith("# ")) {
                // Rogue "#something" with no space — strip the hash(es)
                trimmed.trimStart('#').trimStart()
            } else line
            Text(
                parseInlineMarkdown(displayLine, codeBackground),
                style = MaterialTheme.typography.bodyLarge,
                color = color,
            )
        }
    }
}

@Composable
private fun CodeFenceBlock(language: String?, code: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            language?.let {
                Text(
                    it.uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                code,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
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
