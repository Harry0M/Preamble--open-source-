package com.theblankstate.preamble.ai

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.theblankstate.preamble.BuildConfig
import com.theblankstate.preamble.viewmodel.TaskViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AiChatViewModel(
    private val application: Application,
    private val taskViewModel: TaskViewModel
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val memoryRepo = AiMemoryRepository.get(application)
    private val logger = AiProcessLogger.get(application)
    private val gson = Gson()

    private fun prefs() = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun smartModeEnabled(): Boolean = prefs().getBoolean(PREF_SMART_MODE, true)
    private fun subtaskIntensity(): Int = prefs().getInt("ai_subtask_intensity", 0)

    private fun getProvider(): AiProvider? = AiProviderFactory.main()

    fun isConfigured(): Boolean = BuildConfig.AI_API_KEY.isNotBlank()

    private suspend fun buildSystemMessage(contextTasks: List<com.theblankstate.preamble.data.Task>): ChatMessage {
        val memory = if (smartModeEnabled()) memoryRepo.buildPromptSnapshot() else null
        val taskCtx = if (smartModeEnabled()) TaskContextBuilder.build(application) else null
        return ChatMessage(
            "system",
            AiPromptFactory.buildSystemPrompt(
                existingTasks = contextTasks,
                subtaskIntensity = subtaskIntensity(),
                memoryBlock = memory,
                taskContextBlock = taskCtx,
            )
        )
    }

    private fun serializeToolCalls(calls: List<ToolCall>?): String? =
        calls?.takeIf { it.isNotEmpty() }?.let { list ->
            runCatching { gson.toJson(list.map { mapOf("name" to it.name, "args" to it.arguments) }) }
                .getOrNull()
        }

    /**
     * For voice input: send text through AI, show result via callback.
     * Falls back to direct task save if no AI configured or on error.
     */
    fun processVoiceCommand(text: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val provider = getProvider()
            if (provider == null) {
                taskViewModel.addTask(text, null, null)
                onResult("Task saved: $text")
                return@launch
            }

            _isLoading.value = true
            val contextTasks = taskViewModel.todayTasks.value + taskViewModel.pastTasks.value.values.flatten()
            val systemMsg = buildSystemMessage(contextTasks)
            val userMsg = ChatMessage("user", text)

            val t0 = System.currentTimeMillis()
            try {
                val response = provider.chat(listOf(systemMsg, userMsg), TaskTools.tools)
                val dt = System.currentTimeMillis() - t0

                logger.log(
                    op = AiProcessLogger.OP_VOICE,
                    provider = provider.name,
                    model = null,
                    input = text,
                    output = response.text,
                    toolCalls = serializeToolCalls(response.toolCalls),
                    durationMs = dt,
                    success = true,
                    thought = response.toolCalls?.joinToString(", ") { it.name }
                        ?: "no tool call — fallback save",
                )

                if (!response.toolCalls.isNullOrEmpty()) {
                    val results = response.toolCalls.map { call ->
                        TaskTools.execute(call, taskViewModel, contextTasks)
                    }
                    onResult(results.joinToString(". "))
                } else {
                    taskViewModel.addTask(text, null, null)
                    onResult("Task saved: $text")
                }

                if (smartModeEnabled()) MemoryExtractor.extractAsync(application, text)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing voice command", e)
                logger.log(
                    op = AiProcessLogger.OP_VOICE,
                    provider = provider.name,
                    model = null,
                    input = text,
                    output = null,
                    toolCalls = null,
                    durationMs = System.currentTimeMillis() - t0,
                    success = false,
                    error = e.message,
                )
                taskViewModel.addTask(text, null, null)
                onResult("Task saved: $text")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Process text from AddTaskSheet through AI.
     * If AI is configured, it interprets the text.
     * If not, returns null (caller should save directly).
     */
    fun processTaskInput(text: String, onResult: (Boolean) -> Unit) {
        if (!isConfigured()) {
            onResult(false)
            return
        }

        viewModelScope.launch {
            val provider = getProvider() ?: run {
                onResult(false)
                return@launch
            }

            _isLoading.value = true
            val contextTasks = taskViewModel.todayTasks.value + taskViewModel.pastTasks.value.values.flatten()
            val systemMsg = buildSystemMessage(contextTasks)
            val userMsg = ChatMessage("user", text)

            val t0 = System.currentTimeMillis()
            try {
                val response = provider.chat(listOf(systemMsg, userMsg), TaskTools.tools)
                val dt = System.currentTimeMillis() - t0

                logger.log(
                    op = AiProcessLogger.OP_TASK_INPUT,
                    provider = provider.name,
                    model = null,
                    input = text,
                    output = response.text,
                    toolCalls = serializeToolCalls(response.toolCalls),
                    durationMs = dt,
                    success = true,
                    thought = response.toolCalls?.joinToString(", ") { it.name }
                        ?: "no tool call produced",
                )

                if (!response.toolCalls.isNullOrEmpty()) {
                    for (call in response.toolCalls) {
                        TaskTools.execute(call, taskViewModel, contextTasks)
                    }
                    onResult(true)
                } else {
                    onResult(false)
                }

                if (smartModeEnabled()) MemoryExtractor.extractAsync(application, text)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing task input", e)
                logger.log(
                    op = AiProcessLogger.OP_TASK_INPUT,
                    provider = provider.name,
                    model = null,
                    input = text,
                    output = null,
                    toolCalls = null,
                    durationMs = System.currentTimeMillis() - t0,
                    success = false,
                    error = e.message,
                )
                onResult(false)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Dry-run parse: send text to AI and return the first add_task tool-call arguments
     * WITHOUT saving anything. Used by AddTaskSheet live preview.
     * Returns null if AI not configured, request fails, or no add_task tool-call produced.
     */
    suspend fun parseTaskPreview(text: String): Map<String, String>? {
        if (!isConfigured()) return null
        val provider = getProvider() ?: return null
        return try {
            _isLoading.value = true
            val contextTasks = taskViewModel.todayTasks.value + taskViewModel.pastTasks.value.values.flatten()
            val systemMsg = buildSystemMessage(contextTasks)
            val userMsg = ChatMessage("user", text)

            val t0 = System.currentTimeMillis()
            val response = provider.chat(listOf(systemMsg, userMsg), TaskTools.tools)
            val dt = System.currentTimeMillis() - t0
            val args = response.toolCalls?.firstOrNull { it.name == "add_task" }?.arguments

            logger.log(
                op = AiProcessLogger.OP_PREVIEW,
                provider = provider.name,
                model = null,
                input = text,
                output = response.text,
                toolCalls = serializeToolCalls(response.toolCalls),
                durationMs = dt,
                success = args != null,
                thought = if (args != null) "Preview built." else "No add_task in response.",
            )
            args
        } catch (e: Exception) {
            Log.e(TAG, "parseTaskPreview failed", e)
            null
        } finally {
            _isLoading.value = false
        }
    }

    companion object {
        private const val TAG = "AiChatViewModel"
        const val PREFS = "preamble_prefs"
        const val PREF_SMART_MODE = "ai_smart_mode"
    }

    class Factory(
        private val application: Application,
        private val taskViewModel: TaskViewModel
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AiChatViewModel(application, taskViewModel) as T
        }
    }
}
