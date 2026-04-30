package com.theblankstate.preamble.ai

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.theblankstate.preamble.data.ChatMessageEntity
import com.theblankstate.preamble.viewmodel.TaskViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.theblankstate.preamble.ai.TaskTools
import com.theblankstate.preamble.ai.ToolCall

/**
 * Backs the AiChatScreen — manages the conversation flow + inflight state for streaming.
 * One instance per Activity; tied to the default conversation.
 */
class AiChatScreenViewModel(
    app: Application,
    private val taskViewModel: TaskViewModel,
) : AndroidViewModel(app) {

    private val chatRepo = ChatRepository.get(app)
    private val engine = AiChatEngine(app, taskViewModel)
    private val cid = chatRepo.defaultConversationId()
    private val prefs = app.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)

    private val _messages = MutableStateFlow<List<ChatMessageEntity>?>(null)
    val messages: StateFlow<List<ChatMessageEntity>?> = _messages.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _isHistoryReady = MutableStateFlow(false)
    val isHistoryReady: StateFlow<Boolean> = _isHistoryReady.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _tokensRemaining = MutableStateFlow(-1)
    val tokensRemaining: StateFlow<Int> = _tokensRemaining.asStateFlow()

    private val _chatModelOverride = MutableStateFlow(prefs.getString("chat_model_override", "") ?: "")
    val chatModelOverride: StateFlow<String> = _chatModelOverride.asStateFlow()

    private val _conciseMode = MutableStateFlow(prefs.getBoolean("chat_concise_mode", true))
    val conciseMode: StateFlow<Boolean> = _conciseMode.asStateFlow()

    init {
        viewModelScope.launch {
            chatRepo.observe(cid).collect { rows ->
                _messages.value = rows
            }
        }
        viewModelScope.launch {
            try {
                // Only pull remote if Room is empty — prevents duplicate messages
                // (cloud path writes both to Room and Firestore; re-pulling creates dupes)
                val existing = runCatching { chatRepo.snapshot(cid).size }.getOrDefault(0)
                if (existing == 0) {
                    runCatching { chatRepo.pullRemote(cid) }
                }
                runCatching { chatRepo.flushPending() }
            } finally {
                _isHistoryReady.value = true
            }
        }
    }

    fun send(text: String) {
        if (text.isBlank() || _isSending.value) return
        _error.value = null
        val modelOverride = _chatModelOverride.value.takeIf { it.isNotBlank() }
        val concise = _conciseMode.value
        viewModelScope.launch {
            _isSending.value = true
            try {
                engine.send(cid, text, modelOverride, concise).collect { ev ->
                    when (ev) {
                        is ChatEvent.Error -> _error.value = ev.reason
                        is ChatEvent.Credits -> _tokensRemaining.value = ev.tokensRemaining
                        else -> {}
                    }
                }
            } finally {
                _isSending.value = false
            }
        }
    }

    fun setChatModel(modelId: String) {
        _chatModelOverride.value = modelId
        prefs.edit().putString("chat_model_override", modelId).apply()
    }

    fun setConciseMode(enabled: Boolean) {
        _conciseMode.value = enabled
        prefs.edit().putBoolean("chat_concise_mode", enabled).apply()
    }

    fun clearError() { _error.value = null }

    // In-memory set of dismissed suggestion keys: "$messageId:$suggestionIndex"
    private val _dismissedSuggestions = MutableStateFlow<Set<String>>(emptySet())
    val dismissedSuggestions: StateFlow<Set<String>> = _dismissedSuggestions.asStateFlow()

    fun approveTaskSuggestion(messageId: String, index: Int, args: Map<String, String>) {
        dismissSuggestion(messageId, index)
        viewModelScope.launch {
            val toolCall = ToolCall(id = "suggest_task", name = "add_task", arguments = args)
            val ctx = taskViewModel.todayTasks.value + taskViewModel.pastTasks.value.values.flatten()
            runCatching { TaskTools.execute(toolCall, taskViewModel, ctx) }
        }
    }

    fun dismissSuggestion(messageId: String, index: Int) {
        _dismissedSuggestions.value = _dismissedSuggestions.value + "$messageId:$index"
    }

    fun clearConversation() {
        viewModelScope.launch {
            runCatching { chatRepo.clear(cid) }
        }
    }

    fun deleteMessage(id: String) {
        viewModelScope.launch { runCatching { chatRepo.delete(id) } }
    }

    class Factory(
        private val app: Application,
        private val taskViewModel: TaskViewModel,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return AiChatScreenViewModel(app, taskViewModel) as T
        }
    }
}
