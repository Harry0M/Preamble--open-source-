package com.theblankstate.preamble.ai

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.theblankstate.preamble.viewmodel.TaskViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Backs the AiChatScreen — manages the conversation flow + inflight state for streaming.
 * One instance per Activity; tied to the default conversation.
 */
class AiChatScreenViewModel(
    app: Application,
    taskViewModel: TaskViewModel,
) : AndroidViewModel(app) {

    private val chatRepo = ChatRepository.get(app)
    private val engine = AiChatEngine(app, taskViewModel)
    private val cid = chatRepo.defaultConversationId()
    private val prefs = app.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)

    val messages = chatRepo.observe(cid)

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _chatModelOverride = MutableStateFlow(prefs.getString("chat_model_override", "") ?: "")
    val chatModelOverride: StateFlow<String> = _chatModelOverride.asStateFlow()

    private val _conciseMode = MutableStateFlow(prefs.getBoolean("chat_concise_mode", true))
    val conciseMode: StateFlow<Boolean> = _conciseMode.asStateFlow()

    init {
        // Pull remote conversation on first open (best-effort, non-blocking)
        viewModelScope.launch {
            runCatching { chatRepo.pullRemote(cid) }
            runCatching { chatRepo.flushPending() }
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
                        else -> {} // history updates flow through Room observe
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
