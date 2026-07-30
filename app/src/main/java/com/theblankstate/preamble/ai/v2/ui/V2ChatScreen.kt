package com.theblankstate.preamble.ai.v2.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theblankstate.preamble.ai.v2.BudgetExceededState
import com.theblankstate.preamble.ai.v2.V2ChatMessage
import com.theblankstate.preamble.ai.v2.V2ChatRole
import com.theblankstate.preamble.ai.v2.V2ChatViewModel
import com.theblankstate.preamble.ai.v2.V2ModelInfo
import com.theblankstate.preamble.ai.v2.V2TokenBalance
import com.theblankstate.preamble.ai.v2.V2ToolCallInfo
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * AI V2 Chat Screen — the primary UI entry point for the V2 chat experience.
 *
 * Wires [V2ChatViewModel] into a Jetpack Compose UI with:
 * - Token balance display (Req 17.5)
 * - Model info from v2Config (Req 15.3)
 * - Daily briefing on first open per day in 04:00–12:00 window (Req 23.1, 23.3)
 * - Budget exceeded (429) countdown UI (Req 17.2)
 * - Auth errors (401) login redirect signal
 * - Rich response rendering via [RichResponseRenderer]
 * - Tool permission flow via [ToolPermissionHandler]
 *
 * Requirements: 23.1, 23.3, 17.2, 17.5, 15.3
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun V2ChatScreen(
    viewModel: V2ChatViewModel,
    onAuthError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val messages by viewModel.messages.collectAsState()
    val streamingText by viewModel.streamingText.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val pendingToolCalls by viewModel.pendingToolCalls.collectAsState()
    val tokenBalance by viewModel.tokenBalance.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val budgetExceeded by viewModel.budgetExceeded.collectAsState()
    val authError by viewModel.authError.collectAsState()
    val error by viewModel.error.collectAsState()
    val briefing by viewModel.briefing.collectAsState()

    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var showModelPicker by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf<String?>(null) }

    // Trigger screen open logic (loads model config, token balance, daily briefing)
    LaunchedEffect(Unit) {
        viewModel.onScreenOpen()
    }

    // Handle auth error → redirect to login
    LaunchedEffect(authError) {
        if (authError) {
            onAuthError()
            viewModel.clearAuthError()
        }
    }

    // Auto-scroll to bottom on new messages
    LaunchedEffect(messages.size, isStreaming) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val showScrollToBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && lastVisible < info.totalItemsCount - 1
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Header: Model info + Token balance ──
            V2ChatHeader(
                tokenBalance = tokenBalance,
                availableModels = availableModels,
                selectedModel = selectedModel,
                onModelPickerToggle = { showModelPicker = !showModelPicker },
            )

            // ── Message list ──
            if (messages.isEmpty() && !isStreaming) {
                V2EmptyChatPlaceholder(
                    modifier = Modifier.weight(1f),
                    onSuggestion = { input = it },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
                ) {
                    items(messages, key = { "${it.role}_${it.content.hashCode()}" }) { msg ->
                        V2MessageBubble(message = msg)
                    }
                    // Show streaming text as a partial assistant message
                    if (isStreaming && streamingText.isNotBlank()) {
                        item("streaming") {
                            V2MessageBubble(
                                message = V2ChatMessage(
                                    role = V2ChatRole.ASSISTANT,
                                    content = streamingText,
                                ),
                                isStreaming = true,
                            )
                        }
                    }
                    if (isStreaming && streamingText.isBlank()) {
                        item("typing") { V2TypingIndicator() }
                    }
                }
            }
        }

        // ── Budget exceeded overlay ──
        budgetExceeded?.let { state ->
            BudgetExceededBanner(
                state = state,
                onDismiss = { viewModel.clearBudgetExceeded() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 80.dp),
            )
        }

        // ── General error banner ──
        error?.let { msg ->
            V2ErrorBanner(
                message = msg,
                onDismiss = { viewModel.clearError() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 80.dp),
            )
        }

        // ── Scroll-to-bottom FAB ──
        AnimatedVisibility(
            visible = showScrollToBottom,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 84.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable {
                        scope.launch {
                            val last = listState.layoutInfo.totalItemsCount - 1
                            if (last >= 0) listState.animateScrollToItem(last)
                        }
                    },
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.ExpandMore,
                        contentDescription = "Scroll to bottom",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        // ── Input composer ──
        val isImeVisible = WindowInsets.isImeVisible
        V2ChatComposer(
            input = input,
            onInputChange = { input = it },
            isSending = isStreaming,
            onSend = {
                if (input.isNotBlank() && !isStreaming) {
                    val text = input.trim()
                    viewModel.send(
                        message = text,
                        model = selectedModel,
                    )
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
                .padding(bottom = if (isImeVisible) 8.dp else 24.dp),
        )
    }

    // ── Tool permission dialog queue ──
    ToolPermissionHandler(
        pendingToolCalls = pendingToolCalls,
        onAllResolved = { results ->
            viewModel.submitToolResults(results)
        },
    )

    // ── Model picker bottom sheet ──
    if (showModelPicker) {
        V2ModelPickerSheet(
            models = availableModels,
            selectedModel = selectedModel,
            tokenBalance = tokenBalance,
            onSelectModel = { model ->
                selectedModel = model
                showModelPicker = false
            },
            onDismiss = { showModelPicker = false },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header with token balance + model info
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Header row showing the active model name and remaining token balance.
 * Tapping the model pill opens the model picker.
 *
 * Requirements: 15.3, 17.5
 */
@Composable
private fun V2ChatHeader(
    tokenBalance: V2TokenBalance?,
    availableModels: List<V2ModelInfo>,
    selectedModel: String?,
    onModelPickerToggle: () -> Unit,
) {
    val activeModel = remember(availableModels, selectedModel) {
        if (selectedModel != null) {
            availableModels.find { it.id == selectedModel }
        } else {
            availableModels.find { it.isDefault }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Model pill
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.clickable { onModelPickerToggle() },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = activeModel?.displayName ?: "AI V2",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        // Token balance indicator
        tokenBalance?.let { balance ->
            val fraction = if (balance.budget > 0) {
                (balance.remaining.toFloat() / balance.budget.toFloat()).coerceIn(0f, 1f)
            } else 0f
            val balanceColor = when {
                fraction > 0.5f -> MaterialTheme.colorScheme.primary
                fraction > 0.2f -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.error
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .width(48.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = balanceColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(
                    text = "${balance.remaining}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = balanceColor,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Message bubble
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun V2MessageBubble(
    message: V2ChatMessage,
    isStreaming: Boolean = false,
) {
    val isUser = message.role == V2ChatRole.USER
    val bgColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val textColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val shape = if (isUser) {
        RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        if (message.isBriefing) {
            // Briefing badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                Text(
                    "☀️ Daily Briefing",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }

        Surface(
            shape = shape,
            color = bgColor,
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!isUser) {
                    // Render assistant messages through the rich renderer
                    val blocks = remember(message.content) {
                        parseContentToRenderBlocks(message.content)
                    }
                    RichResponseRenderer(blocks = blocks)
                } else {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor,
                    )
                }
                if (isStreaming) {
                    Spacer(Modifier.height(4.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/**
 * Simple heuristic to split plain-text assistant content into RenderBlocks.
 * In a full implementation, the server sends structured renderBlocks JSON.
 * This fallback handles plain text gracefully.
 */
private fun parseContentToRenderBlocks(content: String): List<RenderBlock> {
    if (content.isBlank()) return emptyList()
    // Wrap the whole content as a Markdown block for rich rendering
    return listOf(RenderBlock.Markdown(content))
}

// ─────────────────────────────────────────────────────────────────────────────
// Typing indicator
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun V2TypingIndicator() {
    Surface(
        shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Budget Exceeded Banner with countdown
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Shows a banner when the user's daily token budget is exhausted (HTTP 429).
 * Displays a countdown to the next reset time.
 *
 * Requirement: 17.2
 */
@Composable
private fun BudgetExceededBanner(
    state: BudgetExceededState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val countdownText = remember(state.resetTime) {
        formatCountdown(state.resetTime)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(
            modifier = Modifier
                .clickable { onDismiss() }
                .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Filled.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    "Daily limit reached",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (countdownText.isNotBlank()) {
                    "Resets in $countdownText"
                } else {
                    state.message
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
            )
        }
    }
}

/**
 * Parses the ISO 8601 reset time and formats a human-readable countdown.
 */
private fun formatCountdown(resetTime: String): String {
    if (resetTime.isBlank()) return ""
    return try {
        val reset = ZonedDateTime.parse(resetTime, DateTimeFormatter.ISO_DATE_TIME)
        val now = ZonedDateTime.now()
        val minutes = ChronoUnit.MINUTES.between(now, reset)
        if (minutes <= 0) return "soon"
        val hours = minutes / 60
        val mins = minutes % 60
        when {
            hours > 0 -> "${hours}h ${mins}m"
            else -> "${mins}m"
        }
    } catch (_: Exception) {
        ""
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// General error banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun V2ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier
                .clickable { onDismiss() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "dismiss",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state placeholder
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun V2EmptyChatPlaceholder(
    modifier: Modifier = Modifier,
    onSuggestion: (String) -> Unit,
) {
    val suggestions = remember {
        listOf(
            "What do I have planned today?",
            "Help me plan my study session",
            "What tasks are due this week?",
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Preamble AI",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Ask me anything about your tasks, schedule, or goals.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        suggestions.forEach { suggestion ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onSuggestion(suggestion) },
            ) {
                Text(
                    text = suggestion,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Chat input composer
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun V2ChatComposer(
    input: String,
    onInputChange: (String) -> Unit,
    isSending: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canSend = input.isNotBlank() && !isSending

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(
                    onSend = { if (canSend) onSend() },
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
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
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(38.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        modifier = Modifier.size(18.dp),
                        tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Model picker bottom sheet
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Bottom sheet displaying available models from v2Config.
 * Shows model name, provider, and reasoning support badge.
 *
 * Requirement: 15.3
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun V2ModelPickerSheet(
    models: List<V2ModelInfo>,
    selectedModel: String?,
    tokenBalance: V2TokenBalance?,
    onSelectModel: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Choose Model",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Token balance summary
            tokenBalance?.let { balance ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("⚡", fontSize = 14.sp)
                    Text(
                        "${balance.remaining} / ${balance.budget} tokens remaining",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // "Auto" option — server picks default
            val autoSelected = selectedModel == null
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (autoSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectModel(null) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Auto (server default)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (autoSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (autoSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Model list
            models.filter { it.enabled }.forEach { model ->
                val isSelected = selectedModel == model.id
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectModel(model.id) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                model.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.SemiBold
                                else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                model.provider.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (model.isDefault) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                ) {
                                    Text(
                                        "DEFAULT",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            if (model.supportsReasoning) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
                                ) {
                                    Text(
                                        "THINKS",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}
