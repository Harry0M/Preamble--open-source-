package com.theblankstate.preamble.ai

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.theblankstate.preamble.BuildConfig
import com.theblankstate.preamble.data.ChatMessageEntity
import com.theblankstate.preamble.viewmodel.TaskViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Stateless chat orchestrator. Each turn:
 *
 *   1. Pulls Room conversation history (last N user/assistant rows)
 *   2. Pre-pends a SUMMARY system-row if present (token saver)
 *   3. Builds full system prompt: rules + USER CONTEXT (memory) + TASK CONTEXT (volatile)
 *   4. Calls main provider with tools enabled
 *   5. If model returns tool_calls → execute against TaskTools, append "tool" results,
 *      loop up to [MAX_TOOL_ROUNDS] more turns
 *   6. Persists every message (user, assistant, tool) into ChatRepository (Firestore-mirrored)
 *   7. Fires async memory extraction on the user's text
 *   8. Triggers retention sweep (auto-summarize + prune)
 *
 * Streaming is implemented as flow emissions for incremental text.
 * Providers that don't support SSE just emit the final text once.
 */
class AiChatEngine(
    private val appContext: Context,
    private val taskViewModel: TaskViewModel,
) {
    private val chatRepo = ChatRepository.get(appContext)
    private val memoryRepo = AiMemoryRepository.get(appContext)
    private val logger = AiProcessLogger.get(appContext)
    private val gson = Gson()

    /**
     * Sends [userText] in [conversationId] and emits engine events:
     *   AssistantStart(messageId)
     *   AssistantDelta(messageId, deltaText)   [for streaming providers; otherwise one big chunk]
     *   ToolStart(messageId, toolName, args)
     *   ToolResult(messageId, toolName, output)
     *   AssistantDone(messageId, finalText)
     *   Error(reason)
     */
    fun send(
        conversationId: String,
        userText: String,
        modelOverride: String? = null,
        conciseMode: Boolean = true,
    ): Flow<ChatEvent> = flow {
        if (userText.isBlank()) return@flow

        // 1. Persist user message immediately
        chatRepo.append(conversationId, role = "user", content = userText)

        val provider = buildProvider(modelOverride) ?: run {
            emit(ChatEvent.Error("AI not configured. Add AI_API_KEY in local.properties."))
            return@flow
        }

        // 2. Detect if this is a simple conversational query (no tools needed)
        val simple = isSimpleQuery(userText)

        // 3. Build messages list — system + summary + history + user already saved (no duplicate)
        val messages = buildMessageList(conversationId, userText, conciseMode)

        // Token-aware extraction trigger: only for substantial messages (skip "ok", "yes", "hmm")
        val smartMode = appContext.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
            .getBoolean("ai_smart_mode", true)
        if (smartMode && userText.length >= 20) MemoryExtractor.extractAsync(appContext, userText)

        // 3. Tool-call loop
        var rounds = 0
        var finalText: String? = null
        var workingMessages = messages
        var assistantRowId: String? = null

        // Use empty tools for simple queries to save tokens and speed up response
        val activeTools = if (simple) emptyList() else TaskTools.tools

        try {
            while (rounds < MAX_TOOL_ROUNDS) {
                val t0 = System.currentTimeMillis()
                val resp = provider.chat(workingMessages, activeTools)
                val dt = System.currentTimeMillis() - t0

                logger.log(
                    op = AiProcessLogger.OP_CHAT,
                    provider = provider.name,
                    model = null,
                    input = userText,
                    output = resp.text,
                    toolCalls = resp.toolCalls?.let { gson.toJson(it.map { c -> mapOf("name" to c.name, "args" to c.arguments) }) },
                    durationMs = dt,
                    success = true,
                    thought = resp.toolCalls?.joinToString(", ") { it.name } ?: "no tool call",
                )

                if (resp.toolCalls.isNullOrEmpty()) {
                    // Final answer turn — persist assistant text once, exit loop.
                    if (!resp.text.isNullOrBlank()) {
                        val saved = chatRepo.append(
                            conversationId,
                            role = "assistant",
                            content = resp.text,
                        )
                        assistantRowId = saved.id
                        emit(ChatEvent.AssistantStart(saved.id))
                        emit(ChatEvent.AssistantDelta(saved.id, resp.text))
                    }
                    finalText = resp.text
                    break
                }

                // Execute tool calls, append results
                val todayCtx = taskViewModel.todayTasks.value + taskViewModel.pastTasks.value.values.flatten()
                val toolResultsList = mutableListOf<Pair<ToolCall, String>>()
                for (call in resp.toolCalls) {
                    emit(ChatEvent.ToolStart(call.id, call.name, call.arguments))
                    val result = runCatching { TaskTools.execute(call, taskViewModel, todayCtx) }
                        .getOrElse { "Error: ${it.message ?: "tool failed"}" }
                    toolResultsList.add(call to result)
                    emit(ChatEvent.ToolResult(call.id, call.name, result))
                }

                // Persist a single assistant row capturing tool calls + results so chat replay
                // can show the trace. Empty content is fine — buildMessageList filters these out
                // of conversation history to keep prompts clean (the trace stays for the UI).
                val toolJson = gson.toJson(toolResultsList.map {
                    mapOf("name" to it.first.name, "args" to it.first.arguments, "result" to it.second)
                })
                chatRepo.append(
                    conversationId,
                    role = "assistant",
                    content = resp.text ?: "",
                    toolCalls = gson.toJson(resp.toolCalls.map { mapOf("id" to it.id, "name" to it.name, "args" to it.arguments) }),
                    toolResults = toolJson,
                )

                // Build next-round messages. The assistant turn that emitted the tool calls
                // MUST be included with tool_calls attached — Mistral/OpenAI reject tool messages
                // that come straight after a user turn. Content can be empty.
                val nextRound = workingMessages.toMutableList()
                nextRound.add(
                    ChatMessage(
                        role = "assistant",
                        content = resp.text ?: "",
                        toolCalls = resp.toolCalls,
                    )
                )
                for ((call, result) in toolResultsList) {
                    nextRound.add(ChatMessage("tool", result, toolCallId = call.id))
                }
                workingMessages = nextRound
                rounds++
            }

            if (finalText == null && rounds == MAX_TOOL_ROUNDS) {
                emit(ChatEvent.Error("Tool-call loop exceeded max rounds"))
            } else {
                emit(ChatEvent.AssistantDone(assistantRowId ?: "", finalText ?: ""))
            }

            // Retention sweep (summarize+prune oldest)
            chatRepo.enforceRetention(conversationId)
        } catch (e: Exception) {
            Log.e(TAG, "Chat turn failed", e)
            logger.log(
                op = AiProcessLogger.OP_CHAT,
                provider = provider.name,
                model = null,
                input = userText,
                output = null,
                toolCalls = null,
                durationMs = 0,
                success = false,
                error = e.message,
            )
            emit(ChatEvent.Error(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun buildMessageList(cid: String, userText: String, conciseMode: Boolean = true): List<ChatMessage> {
        val smart = appContext.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
            .getBoolean("ai_smart_mode", true)
        val memory = if (smart) memoryRepo.buildPromptSnapshot() else null
        val taskCtx = if (smart) TaskContextBuilder.build(appContext) else null
        val systemPrompt = AiPromptFactory.buildSystemPrompt(
            existingTasks = null,
            subtaskIntensity = appContext.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
                .getInt("ai_subtask_intensity", 0),
            memoryBlock = memory,
            taskContextBlock = taskCtx,
            conciseMode = conciseMode,
        )

        val historyRows = chatRepo.snapshot(cid)
        val summaryRow = historyRows.firstOrNull { it.role == "summary" }
        // Drop assistant rows that exist only as a tool-call trace (no user-visible text).
        // They're kept in the DB for replay/debug but pollute prompts on follow-up turns.
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
        // History excludes the row we just appended (which equals userText). Drop the last user row to avoid duplication.
        val historyToInclude = if (recent.isNotEmpty() && recent.last().role == "user" && recent.last().content == userText)
            recent.dropLast(1) else recent
        for (row in historyToInclude) {
            messages.add(ChatMessage(row.role, row.content))
        }
        messages.add(ChatMessage("user", userText))
        return messages
    }

    private fun buildProvider(modelOverride: String? = null): AiProvider? =
        if (!modelOverride.isNullOrBlank()) AiProviderFactory.mainWithModelOverride(modelOverride)
        else AiProviderFactory.main()

    /**
     * Heuristic: skip tool definitions for messages that are clearly conversational
     * (questions about the user, general knowledge, greetings, short queries).
     * This saves ~15K chars from the request body → faster response.
     */
    private fun isSimpleQuery(text: String): Boolean {
        val lower = text.lowercase().trim()
        // Short messages are almost never task operations
        if (lower.length < 25) {
            // But check for task-intent keywords first
            val taskKeywords = listOf(
                "add", "create", "task", "remind", "delete", "remove", "cancel",
                "complete", "done", "modify", "change", "shift", "move", "schedule",
                "karna", "banana", "hatao", "nikaal", "karo", "kar do", "set",
                "hata do", "gym", "meeting", "hospital"
            )
            if (taskKeywords.any { it in lower }) return false
            return true
        }
        // Longer messages with clear task intent
        val taskSignals = listOf(
            "add task", "add_task", "create task", "new task",
            "remind me", "set reminder", "delete task", "remove task",
            "mark done", "mark complete", "cancel task",
            "kaam", "task banana", "task karo", "karna hai",
            "shift karo", "change karo", "badal do",
        )
        if (taskSignals.any { it in lower }) return false
        // Questions / conversational patterns → skip tools
        val convPatterns = listOf(
            "what is", "what's", "who is", "how many", "tell me",
            "explain", "describe", "define", "meaning of",
            "kya hai", "kaun hai", "kitne", "batao",
            "my name", "mera naam", "about me",
        )
        if (convPatterns.any { it in lower }) return true
        return false
    }

    companion object {
        private const val TAG = "AiChatEngine"
        private const val MAX_TOOL_ROUNDS = 3
        private const val HISTORY_WINDOW = 16
    }
}

sealed class ChatEvent {
    data class AssistantStart(val messageId: String) : ChatEvent()
    data class AssistantDelta(val messageId: String, val delta: String) : ChatEvent()
    data class AssistantDone(val messageId: String, val finalText: String) : ChatEvent()
    data class ToolStart(val callId: String, val name: String, val args: Map<String, String>) : ChatEvent()
    data class ToolResult(val callId: String, val name: String, val output: String) : ChatEvent()
    data class Error(val reason: String) : ChatEvent()
}
