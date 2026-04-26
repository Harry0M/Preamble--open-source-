package com.theblankstate.preamble.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.theblankstate.preamble.data.ChatMessageEntity
import com.theblankstate.preamble.data.UserProfileStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    viewModel: AiChatScreenViewModel,
    modifier: Modifier = Modifier,
) {
    val messages by viewModel.messages.collectAsState(initial = emptyList())
    val isSending by viewModel.isSending.collectAsState()
    val error by viewModel.error.collectAsState()
    val chatModelOverride by viewModel.chatModelOverride.collectAsState()
    val conciseMode by viewModel.conciseMode.collectAsState()

    var input by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current

    // Auto-scroll to bottom on new message
    LaunchedEffect(messages.size, isSending) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .imePadding()
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Preamble AI",
                        modifier = Modifier.padding(start = 8.dp),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            actions = {
                if (messages.isNotEmpty()) {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = "Clear")
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )

        if (messages.isEmpty()) {
            EmptyChatPlaceholder(
                modifier = Modifier.weight(1f),
                onSuggestion = { suggestion ->
                    input = suggestion
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageRow(msg)
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
                    Text(
                        error ?: "",
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
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
                    val models = listOf(
                        "" to "Auto",
                        "gemini-2.5-flash-lite" to "Gemini Flash Lite",
                        "gemini-2.5-flash" to "Gemini Flash",
                        "mistral-small-latest" to "Mistral Small",
                        "mistral-medium-latest" to "Mistral Medium",
                    )
                    for ((id, label) in models) {
                        val selected = chatModelOverride == id
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.clickable { viewModel.setChatModel(id) },
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(
                onClick = { showOptions = !showOptions },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.Filled.Tune,
                    contentDescription = "Options",
                    modifier = Modifier.size(20.dp),
                    tint = if (showOptions) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                )
            }
            androidx.compose.material3.TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "Ask anything, or describe a task…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send,
                ),
                maxLines = 5,
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
            )
            IconButton(
                onClick = {
                    if (input.isNotBlank() && !isSending) {
                        viewModel.send(input.trim())
                        input = ""
                        keyboard?.hide()
                    }
                },
                enabled = input.isNotBlank() && !isSending,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (input.isBlank() || isSending)
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear conversation?") },
            text = { Text("This deletes all chat messages on this device and on the cloud. Your tasks and memory stay intact.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearConversation()
                    showClearDialog = false
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun MessageRow(msg: ChatMessageEntity) {
    when (msg.role) {
        "user" -> UserBubble(msg)
        "assistant" -> AssistantBubble(msg)
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
private fun AssistantBubble(msg: ChatMessageEntity) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(end = 24.dp)) {
        // Thinking section (tool calls)
        if (!msg.toolCalls.isNullOrBlank()) {
            ThinkingSection(msg.toolCalls, msg.toolResults)
            Spacer(Modifier.height(8.dp))
        }
        if (msg.content.isNotBlank()) {
            // AI label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Preamble AI",
                    modifier = Modifier.padding(start = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            // Rich text content — no bubble, flat like Claude
            SelectionContainer {
                RichMarkdownText(
                    text = msg.content,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            // Copy button
            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clip.setPrimaryClip(ClipData.newPlainText("AI Response", msg.content))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "Copy response",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
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

@Composable
private fun ThinkingSection(toolCallsJson: String, toolResultsJson: String?) {
    val steps = remember(toolCallsJson, toolResultsJson) {
        parseToolSteps(toolCallsJson, toolResultsJson)
    }
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
