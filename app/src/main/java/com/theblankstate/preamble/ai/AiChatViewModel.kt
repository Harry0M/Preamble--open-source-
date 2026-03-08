package com.theblankstate.preamble.ai

import android.app.Application
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
            val systemMsg = ChatMessage("system",
                "You are Preamble AI. Today is $today. The user gave a voice command. " +
                "Interpret it and use the appropriate tool. Be very concise. " +
                "IMPORTANT HINGLISH/HINDI RULES: " +
                "1. 'aaj' = today ($today) " +
                "2. 'kal' or 'cal' = tomorrow " +
                "3. 'parso', 'parson', or 'perso' = day after tomorrow."
            )
            val userMsg = ChatMessage("user", text)

            try {
                val response = provider.chat(listOf(systemMsg, userMsg), TaskTools.tools)

                if (!response.toolCalls.isNullOrEmpty()) {
                    val todayTasks = taskViewModel.todayTasks.value
                    val results = response.toolCalls.map { call ->
                        TaskTools.execute(call, taskViewModel, todayTasks)
                    }
                    onResult(results.joinToString(". "))
                } else {
                    // AI didn't call any tool, fallback to direct save
                    taskViewModel.addTask(text, null, null)
                    onResult("Task saved: $text")
                }
            } catch (e: Exception) {
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
            val systemMsg = ChatMessage("system",
                "You are Preamble AI. Today is $today. " +
                "The user typed a task. Use add_task tool to add it. " +
                "Parse any date/time from the text intelligently. " +
                "If it mentions a time like '5pm', '17:00', set deadline_time. " +
                "IMPORTANT HINGLISH/HINDI RULES: " +
                "1. 'aaj' = today ($today) " +
                "2. 'kal' or 'cal' = tomorrow " +
                "3. 'parso', 'parson', or 'perso' = day after tomorrow."
            )
            val userMsg = ChatMessage("user", text)

            try {
                val response = provider.chat(listOf(systemMsg, userMsg), TaskTools.tools)

                if (!response.toolCalls.isNullOrEmpty()) {
                    val todayTasks = taskViewModel.todayTasks.value
                    for (call in response.toolCalls) {
                        TaskTools.execute(call, taskViewModel, todayTasks)
                    }
                    onResult(true)
                } else {
                    onResult(false)
                }
            } catch (e: Exception) {
                onResult(false)
            } finally {
                _isLoading.value = false
            }
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
