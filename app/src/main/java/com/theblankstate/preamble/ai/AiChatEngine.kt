package com.theblankstate.preamble.ai

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.theblankstate.preamble.viewmodel.TaskViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Stateless chat orchestrator.
 *
 * CLOUD MODE: When user is logged in, routes through Cloud Functions.
 *   - API keys stay on server (no keys in APK)
 *   - Prompts built server-side (updatable without release)
 *   - Credits checked server-side (tamper-proof)
 *   - Falls back to local if cloud is unavailable
 */
class AiChatEngine(
    private val appContext: Context,
    private val taskViewModel: TaskViewModel,
) {
    private val chatRepo = ChatRepository.get(appContext)
    private val memoryRepo = AiMemoryRepository.get(appContext)
    private val logger = AiProcessLogger.get(appContext)
    private val gson = Gson()

    /** True if user is logged in → use Cloud Functions */
    private fun useCloud(): Boolean = FirebaseAuth.getInstance().currentUser != null

    fun send(
        conversationId: String,
        userText: String,
        modelOverride: String? = null,
        conciseMode: Boolean = true,
    ): Flow<ChatEvent> = flow {
        if (userText.isBlank()) return@flow

        val onCloud = useCloud()

        // Persist user message — cloud path skips Firestore mirror (server is the writer)
        val userMessage = chatRepo.append(
            cid = conversationId,
            role = "user",
            content = userText,
            skipSync = onCloud,
        )

        val smartMode = appContext.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
            .getBoolean("ai_smart_mode", true)

        if (onCloud) {
            // === CLOUD PATH ===
            val requestedModel = modelOverride?.takeIf { it.isNotBlank() }
            val mode = if (conciseMode) "concise" else "normal"
            val t0 = System.currentTimeMillis()

            val fullText = StringBuilder()
            val thinkingBuffer = StringBuilder()
            var pendingToolCalls: List<CloudToolCall>? = null
            var cloudError: String? = null
            var chatResult: CloudChatResult? = null
            var assistantMessageId: String? = null
            val cloudAssistantMessageId = java.util.UUID.randomUUID().toString()

            suspend fun ensureAssistantMessage(): String {
                assistantMessageId?.let { return it }
                val saved = chatRepo.append(
                    cid = conversationId,
                    role = "assistant",
                    content = "",
                    isStreaming = true,
                    modelUsed = requestedModel,
                    skipSync = true,
                    id = cloudAssistantMessageId,
                )
                assistantMessageId = saved.id
                emit(ChatEvent.AssistantStart(saved.id))
                return saved.id
            }

            CloudAiService.chat(
                message = userText,
                conversationId = conversationId,
                userMessageId = userMessage.id,
                assistantMessageId = cloudAssistantMessageId,
                model = requestedModel,
                mode = mode,
                onDelta = { delta ->
                    fullText.append(delta)
                    val id = ensureAssistantMessage()
                    chatRepo.updateContent(id, fullText.toString(), isStreaming = true, skipSync = true)
                    emit(ChatEvent.AssistantDelta(id, delta))
                },
                onThinking = { chunk -> thinkingBuffer.append(chunk) },
                onToolCalls = { calls -> pendingToolCalls = calls },
                onDone = { result -> chatResult = result },
                onError = { err -> cloudError = err },
            )

            // Tool execution + follow-up
            val executedTools = mutableListOf<Pair<CloudToolCall, String>>()
            if (pendingToolCalls != null && pendingToolCalls!!.isNotEmpty()) {
                val todayCtx = taskViewModel.todayTasks.value + taskViewModel.pastTasks.value.values.flatten()
                val toolResults = mutableListOf<ToolResult>()

                for (call in pendingToolCalls!!) {
                    val toolCall = ToolCall(id = call.name, name = call.name, arguments = call.args)
                    emit(ChatEvent.ToolStart(call.name, call.name, call.args))
                    val result = runCatching {
                        TaskTools.execute(toolCall, taskViewModel, todayCtx)
                    }.getOrElse { "Error: ${it.message}" }
                    executedTools.add(call to result)
                    toolResults.add(ToolResult(name = call.name, result = result))
                    emit(ChatEvent.ToolResult(call.name, call.name, result))
                }

                CloudAiService.sendToolResults(
                    conversationId = conversationId,
                    toolResults = toolResults,
                    assistantMessageId = cloudAssistantMessageId,
                    model = requestedModel,
                    mode = mode,
                    onDelta = { delta ->
                        fullText.append(delta)
                        val id = ensureAssistantMessage()
                        chatRepo.updateContent(id, fullText.toString(), isStreaming = true, skipSync = true)
                        emit(ChatEvent.AssistantDelta(id, delta))
                    },
                    onDone = { r -> chatResult = r },
                    onError = { err -> cloudError = err },
                )
            }

            val resolvedModel = chatResult?.model?.takeIf { it.isNotBlank() }
                ?: requestedModel
                ?: "gemini-2.5-flash-lite"

            val dt = System.currentTimeMillis() - t0
            val text = fullText.toString()

            if (cloudError != null && text.isBlank()) {
                when (cloudError) {
                    "DAILY_LIMIT_REACHED" -> {
                        emit(ChatEvent.Error("DAILY_LIMIT_REACHED"))
                        return@flow
                    }
                    else -> {
                        Log.w(TAG, "Cloud failed ($cloudError), falling back to local")
                        sendLocalAndEmit(this, conversationId, userText, modelOverride, conciseMode, smartMode)
                        return@flow
                    }
                }
            }

            chatResult?.let { r ->
                emit(ChatEvent.Credits(r.tokensRemaining))
            }

            // Parse suggestion blocks from response text; strip them from display text
            val (cleanText, suggestionsJson) = parseSuggestionsFromText(text)

            // Build toolCalls JSON: thinking → task actions → suggestions (all in one JSON array)
            val taskToolCallsJson = if (executedTools.isNotEmpty()) {
                gson.toJson(executedTools.map { (call, _) ->
                    mapOf("id" to call.name, "name" to call.name, "args" to call.args)
                })
            } else null
            val toolResultsJson = if (executedTools.isNotEmpty()) {
                gson.toJson(executedTools.map { (call, result) ->
                    mapOf("name" to call.name, "args" to call.args, "result" to result)
                })
            } else null
            val toolCallsJson = buildMergedToolCallsJson(
                thinkingText = thinkingBuffer.takeIf { it.isNotBlank() }?.toString(),
                taskToolCallsJson = taskToolCallsJson,
                suggestionsJson = suggestionsJson,
            )

            if (cleanText.isNotBlank() || toolCallsJson != null) {
                val text = cleanText  // shadow with clean version
                val existingId = assistantMessageId
                if (existingId != null) {
                    chatRepo.updateAssistantTurn(
                        id = existingId,
                        content = text,
                        toolCalls = toolCallsJson,
                        toolResults = toolResultsJson,
                        isStreaming = false,
                        modelUsed = resolvedModel,
                        skipSync = true,
                    )
                    emit(ChatEvent.AssistantDone(existingId, text))
                } else {
                    val saved = chatRepo.append(
                        cid = conversationId,
                        role = "assistant",
                        content = text,
                        toolCalls = toolCallsJson,
                        toolResults = toolResultsJson,
                        modelUsed = resolvedModel,
                        skipSync = true,
                        id = cloudAssistantMessageId,
                    )
                    emit(ChatEvent.AssistantStart(saved.id))
                    emit(ChatEvent.AssistantDelta(saved.id, text))
                    emit(ChatEvent.AssistantDone(saved.id, text))
                }
            }

            logger.log(
                op = AiProcessLogger.OP_CHAT, provider = "cloud", model = resolvedModel,
                input = userText, output = text,
                toolCalls = toolCallsJson,
                durationMs = dt, success = text.isNotBlank() || executedTools.isNotEmpty(),
                thought = "cloud path (${executedTools.size} tools)",
            )
            chatRepo.enforceRetention(conversationId)

        } else {
            // === LOCAL PATH ===
            sendLocalAndEmit(this, conversationId, userText, modelOverride, conciseMode, smartMode)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Local path: original direct-API flow. Used when user is offline or not logged in.
     */
    private suspend fun sendLocalAndEmit(
        collector: kotlinx.coroutines.flow.FlowCollector<ChatEvent>,
        conversationId: String,
        userText: String,
        modelOverride: String?,
        conciseMode: Boolean,
        smartMode: Boolean,
    ) {
        val provider = buildProvider(modelOverride) ?: run {
            collector.emit(ChatEvent.Error("AI not configured. Please sign in to use AI chat."))
            return
        }

        val taskToolsEnabled = ChatPromptFactory.shouldUseTaskTools(userText)
        val messages = buildMessageList(conversationId, userText, conciseMode)

        if (smartMode && ChatPromptFactory.shouldAttemptMemoryExtraction(userText)) {
            MemoryExtractor.extractAsync(appContext, userText)
        }

        var rounds = 0
        var finalText: String? = null
        var workingMessages = messages
        var assistantRowId: String? = null
        val activeTools = if (taskToolsEnabled) TaskTools.tools else emptyList()

        try {
            while (rounds < MAX_TOOL_ROUNDS) {
                val t0 = System.currentTimeMillis()
                val resp = provider.chat(workingMessages, activeTools)
                val dt = System.currentTimeMillis() - t0

                logger.log(
                    op = AiProcessLogger.OP_CHAT, provider = provider.name, model = null,
                    input = userText, output = resp.text,
                    toolCalls = resp.toolCalls?.let { gson.toJson(it.map { c -> mapOf("name" to c.name, "args" to c.arguments) }) },
                    durationMs = dt, success = true,
                    thought = resp.toolCalls?.joinToString(", ") { it.name } ?: "no tool call",
                )

                if (resp.toolCalls.isNullOrEmpty()) {
                    if (!resp.text.isNullOrBlank()) {
                        val (cleanText, suggestionsJson) = parseSuggestionsFromText(resp.text)
                        val toolCallsJson = buildMergedToolCallsJson(null, null, suggestionsJson)
                        val saved = chatRepo.append(
                            cid = conversationId,
                            role = "assistant",
                            content = cleanText,
                            toolCalls = toolCallsJson,
                            modelUsed = modelOverride ?: provider.name,
                        )
                        assistantRowId = saved.id
                        collector.emit(ChatEvent.AssistantStart(saved.id))
                        collector.emit(ChatEvent.AssistantDelta(saved.id, cleanText))
                        finalText = cleanText
                    }
                    break
                }

                val todayCtx = taskViewModel.todayTasks.value + taskViewModel.pastTasks.value.values.flatten()
                val toolResultsList = mutableListOf<Pair<ToolCall, String>>()
                for (call in resp.toolCalls) {
                    collector.emit(ChatEvent.ToolStart(call.id, call.name, call.arguments))
                    val result = runCatching { TaskTools.execute(call, taskViewModel, todayCtx) }
                        .getOrElse { "Error: ${it.message ?: "tool failed"}" }
                    toolResultsList.add(call to result)
                    collector.emit(ChatEvent.ToolResult(call.id, call.name, result))
                }

                val toolJson = gson.toJson(toolResultsList.map {
                    mapOf("name" to it.first.name, "args" to it.first.arguments, "result" to it.second)
                })
                chatRepo.append(
                    cid = conversationId,
                    role = "assistant",
                    content = resp.text ?: "",
                    toolCalls = gson.toJson(resp.toolCalls.map { mapOf("id" to it.id, "name" to it.name, "args" to it.arguments) }),
                    toolResults = toolJson,
                    modelUsed = modelOverride ?: provider.name,
                )

                val nextRound = workingMessages.toMutableList()
                nextRound.add(ChatMessage(role = "assistant", content = resp.text ?: "", toolCalls = resp.toolCalls))
                for ((call, result) in toolResultsList) {
                    nextRound.add(ChatMessage("tool", result, toolCallId = call.id))
                }
                workingMessages = nextRound
                rounds++
            }

            if (finalText == null && rounds == MAX_TOOL_ROUNDS) {
                collector.emit(ChatEvent.Error("Tool-call loop exceeded max rounds"))
            } else {
                collector.emit(ChatEvent.AssistantDone(assistantRowId ?: "", finalText ?: ""))
            }
            chatRepo.enforceRetention(conversationId)
        } catch (e: Exception) {
            Log.e(TAG, "Chat turn failed", e)
            logger.log(
                op = AiProcessLogger.OP_CHAT, provider = provider.name, model = null,
                input = userText, output = null, toolCalls = null,
                durationMs = 0, success = false, error = e.message,
            )
            collector.emit(ChatEvent.Error(e.message ?: "Unknown error"))
        }
    }

    private suspend fun buildMessageList(cid: String, userText: String, conciseMode: Boolean = true): List<ChatMessage> {
        val smart = appContext.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
            .getBoolean("ai_smart_mode", true)
        val taskToolsEnabled = ChatPromptFactory.shouldUseTaskTools(userText)
        val memory = if (smart) memoryRepo.buildPromptSnapshot(maxChars = 900) else null
        val taskCtx = if (smart && taskToolsEnabled) TaskContextBuilder.build(appContext, maxChars = 900) else null
        val systemPrompt = ChatPromptFactory.buildSystemPrompt(
            memoryBlock = memory,
            taskContextBlock = taskCtx,
            conciseMode = conciseMode,
            taskToolsEnabled = taskToolsEnabled,
        )

        val historyRows = chatRepo.snapshot(cid)
        val summaryRow = historyRows.firstOrNull { it.role == "summary" }
        val recent = historyRows
            .filter { row ->
                when (row.role) {
                    "user" -> true
                    "assistant" -> row.content.isNotBlank()
                    else -> false
                }
            }
            .takeLast(HISTORY_WINDOW)

        val messages = mutableListOf<ChatMessage>()
        messages.add(ChatMessage("system", systemPrompt))
        if (summaryRow != null) {
            messages.add(ChatMessage("system", "Earlier conversation summary: ${summaryRow.content}"))
        }
        val historyToInclude = if (recent.isNotEmpty() && recent.last().role == "user" && recent.last().content == userText)
            recent.dropLast(1) else recent
        for (row in historyToInclude) {
            messages.add(ChatMessage(row.role, row.content))
        }
        messages.add(ChatMessage("user", userText))
        return messages
    }

    /**
     * Extracts [SUGGEST:{...}] blocks from AI response text.
     * Returns (cleanText, suggestionsJsonArray?) where clean text has markers stripped.
     */
    private fun parseSuggestionsFromText(text: String): Pair<String, String?> {
        val regex = Regex("""\[SUGGEST:\s*(\{[^}]*\})\]""")
        val entries = regex.findAll(text).mapNotNull { match ->
            try {
                @Suppress("UNCHECKED_CAST")
                val raw = gson.fromJson(match.groupValues[1], Map::class.java) as Map<String, Any?>
                val args = raw.mapValues { (_, v) -> v?.toString() ?: "" }
                mapOf("name" to "suggest_task", "args" to args)
            } catch (_: Exception) { null }
        }.toList()
        val clean = regex.replace(text, "").trimEnd()
        return clean to if (entries.isNotEmpty()) gson.toJson(entries) else null
    }

    /**
     * Merges thinking, task tool calls, and suggestion entries into a single toolCalls JSON array.
     * Order: thinking → task actions → suggestions (so UI renders top-to-bottom correctly).
     */
    private fun buildMergedToolCallsJson(
        thinkingText: String?,
        taskToolCallsJson: String?,
        suggestionsJson: String?,
    ): String? {
        val all = mutableListOf<Any>()
        if (!thinkingText.isNullOrBlank()) {
            all.add(mapOf("name" to "thinking", "args" to mapOf("text" to thinkingText)))
        }
        if (!taskToolCallsJson.isNullOrBlank()) {
            @Suppress("UNCHECKED_CAST")
            (gson.fromJson(taskToolCallsJson, List::class.java) as? List<Any>)?.let { all.addAll(it) }
        }
        if (!suggestionsJson.isNullOrBlank()) {
            @Suppress("UNCHECKED_CAST")
            (gson.fromJson(suggestionsJson, List::class.java) as? List<Any>)?.let { all.addAll(it) }
        }
        return if (all.isNotEmpty()) gson.toJson(all) else null
    }

    private fun buildProvider(modelOverride: String? = null): AiProvider? =
        if (!modelOverride.isNullOrBlank()) AiProviderFactory.mainWithModelOverride(modelOverride)
        else AiProviderFactory.main()

    companion object {
        private const val TAG = "AiChatEngine"
        private const val MAX_TOOL_ROUNDS = 3
        private const val HISTORY_WINDOW = 12
    }
}

sealed class ChatEvent {
    data class AssistantStart(val messageId: String) : ChatEvent()
    data class AssistantDelta(val messageId: String, val delta: String) : ChatEvent()
    data class AssistantDone(val messageId: String, val finalText: String) : ChatEvent()
    data class ToolStart(val callId: String, val name: String, val args: Map<String, String>) : ChatEvent()
    data class ToolResult(val callId: String, val name: String, val output: String) : ChatEvent()
    data class Error(val reason: String) : ChatEvent()
    data class Credits(val tokensRemaining: Int) : ChatEvent()
}
