package com.theblankstate.preamble.ai

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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

    private fun getProvider(): AiProvider? {
        val apiKey = BuildConfig.AI_API_KEY
        if (apiKey.isBlank()) return null
        val providerName = BuildConfig.AI_PROVIDER
        return when (AiProviderType.valueOf(providerName)) {
            AiProviderType.MISTRAL -> MistralProvider(apiKey)
            AiProviderType.OPENAI -> OpenAiProvider(apiKey)
            AiProviderType.GEMINI -> GeminiProvider(apiKey)
            AiProviderType.CLAUDE -> ClaudeProvider(apiKey)
        }
    }

    fun isConfigured(): Boolean = BuildConfig.AI_API_KEY.isNotBlank()

    /**
     * For voice input: send text through AI, show result via callback.
     * Falls back to direct task save if no AI configured or on error.
     */
    fun processVoiceCommand(text: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val provider = getProvider()
            if (provider == null) {
                // Fallback: save as task directly
                taskViewModel.addTask(text, null, null)
                onResult("Task saved: $text")
                return@launch
            }

            _isLoading.value = true
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val contextTasks = taskViewModel.todayTasks.value + taskViewModel.pastTasks.value.values.flatten()
            val subtaskIntensity = application.getSharedPreferences("preamble_prefs", android.content.Context.MODE_PRIVATE)
                .getInt("ai_subtask_intensity", 0)
            val systemMsg = ChatMessage("system",
                AiPromptFactory.buildSystemPrompt(contextTasks, subtaskIntensity = subtaskIntensity)
            )
            val userMsg = ChatMessage("user", text)

            try {
                val response = provider.chat(listOf(systemMsg, userMsg), TaskTools.tools)

                if (!response.toolCalls.isNullOrEmpty()) {
                    val results = response.toolCalls.map { call ->
                        TaskTools.execute(call, taskViewModel, contextTasks)
                    }
                    onResult(results.joinToString(". "))
                } else {
                    // AI didn't call any tool, fallback to direct save
                    taskViewModel.addTask(text, null, null)
                    onResult("Task saved: $text")
                }
            } catch (e: Exception) {
                Log.e("AiChatViewModel", "Error processing voice command", e)
                // Fallback: save as task
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
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val contextTasks = taskViewModel.todayTasks.value + taskViewModel.pastTasks.value.values.flatten()
            val subtaskIntensity = application.getSharedPreferences("preamble_prefs", android.content.Context.MODE_PRIVATE)
                .getInt("ai_subtask_intensity", 0)
            val systemMsg = ChatMessage("system",
                AiPromptFactory.buildSystemPrompt(contextTasks, subtaskIntensity = subtaskIntensity)
            )
            val userMsg = ChatMessage("user", text)

            try {
                val response = provider.chat(listOf(systemMsg, userMsg), TaskTools.tools)

                if (!response.toolCalls.isNullOrEmpty()) {
                    for (call in response.toolCalls) {
                        TaskTools.execute(call, taskViewModel, contextTasks)
                    }
                    onResult(true)
                } else {
                    onResult(false)
                }
            } catch (e: Exception) {
                Log.e("AiChatViewModel", "Error processing task input", e)
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
            val subtaskIntensity = application.getSharedPreferences("preamble_prefs", android.content.Context.MODE_PRIVATE)
                .getInt("ai_subtask_intensity", 0)
            val systemMsg = ChatMessage("system",
                AiPromptFactory.buildSystemPrompt(contextTasks, subtaskIntensity = subtaskIntensity)
            )
            val userMsg = ChatMessage("user", text)
            val response = provider.chat(listOf(systemMsg, userMsg), TaskTools.tools)
            response.toolCalls?.firstOrNull { it.name == "add_task" }?.arguments
        } catch (e: Exception) {
            Log.e("AiChatViewModel", "parseTaskPreview failed", e)
            null
        } finally {
            _isLoading.value = false
        }
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
