package com.theblankstate.preamble.ai

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.theblankstate.preamble.data.ChatConversationPreview
import com.theblankstate.preamble.data.ChatMessageEntity
import com.theblankstate.preamble.viewmodel.TaskViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
    private val prefs = app.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
    private var messagesJob: Job? = null

    private val _currentConversationId = MutableStateFlow(
        prefs.getString("ai_current_conversation_id", null)?.takeIf { it.isNotBlank() }
            ?: chatRepo.defaultConversationId()
    )
    val currentConversationId: StateFlow<String> = _currentConversationId.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessageEntity>?>(null)
    val messages: StateFlow<List<ChatMessageEntity>?> = _messages.asStateFlow()

    private val _conversations = MutableStateFlow<List<ChatConversationPreview>>(emptyList())
    val conversations: StateFlow<List<ChatConversationPreview>> = _conversations.asStateFlow()

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
            chatRepo.observeConversations().collect { rows ->
                _conversations.value = rows
            }
        }
        viewModelScope.launch {
            _currentConversationId.collectLatest { cid ->
                observeConversation(cid)
                bootstrapConversation(cid)
            }
        }
    }

    private fun observeConversation(cid: String) {
        messagesJob?.cancel()
        _messages.value = null
        _isHistoryReady.value = false
        messagesJob = viewModelScope.launch {
            chatRepo.observe(cid).collect { rows ->
                _messages.value = rows
            }
        }
    }

    private suspend fun bootstrapConversation(cid: String) {
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

    fun send(text: String) {
        if (text.isBlank() || _isSending.value) return
        _error.value = null
        val modelOverride = _chatModelOverride.value.takeIf { it.isNotBlank() }
        val concise = _conciseMode.value
        viewModelScope.launch {
            _isSending.value = true
            try {
                engine.send(_currentConversationId.value, text, modelOverride, concise).collect { ev ->
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

    fun editAndResend(messageId: String, text: String) {
        if (messageId.isBlank() || text.isBlank() || _isSending.value) return
        _error.value = null
        val cid = _currentConversationId.value
        val modelOverride = _chatModelOverride.value.takeIf { it.isNotBlank() }
        val concise = _conciseMode.value
        viewModelScope.launch {
            _isSending.value = true
            try {
                val deleted = runCatching { chatRepo.deleteFromMessage(cid, messageId) }
                if (deleted.isFailure) {
                    _error.value = "Could not edit that message. Please try again."
                    return@launch
                }
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

    fun selectConversation(cid: String) {
        if (cid.isBlank() || cid == _currentConversationId.value || _isSending.value) return
        _error.value = null
        _currentConversationId.value = cid
        prefs.edit().putString("ai_current_conversation_id", cid).apply()
    }

    fun newConversation() {
        if (_isSending.value) return
        val cid = chatRepo.newConversationId()
        _error.value = null
        _currentConversationId.value = cid
        prefs.edit().putString("ai_current_conversation_id", cid).apply()
    }

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
            runCatching { chatRepo.clear(_currentConversationId.value) }
        }
    }

    fun deleteConversation(cid: String) {
        viewModelScope.launch {
            runCatching { chatRepo.clear(cid) }
            if (cid == _currentConversationId.value) {
                val fallback = _conversations.value.firstOrNull { it.conversationId != cid }?.conversationId
                    ?: chatRepo.defaultConversationId()
                _currentConversationId.value = fallback
                prefs.edit().putString("ai_current_conversation_id", fallback).apply()
            }
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
