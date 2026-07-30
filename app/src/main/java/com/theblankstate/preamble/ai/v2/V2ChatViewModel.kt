package com.theblankstate.preamble.ai.v2

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

/**
 * ViewModel for the AI V2 Chat screen.
 *
 * Thin integration layer connecting [CloudAiV2Service] (via [V2ServiceProvider])
 * to the Jetpack Compose UI. Manages:
 * - Chat message list + streaming text state
 * - Daily briefing check on first open (04:00–12:00 window)
 * - Token balance display
 * - Model info from v2Config
 * - Budget exceeded (429) countdown state
 * - Auth error (401) login redirect signal
 * - Tool call permission flow
 *
 * Requirements: 23.1, 23.3, 17.2, 17.5, 15.3
 */
class V2ChatViewModel : ViewModel() {

    companion object {
        private const val TAG = "V2ChatViewModel"
        private const val BRIEFING_WINDOW_START_HOUR = 4
        private const val BRIEFING_WINDOW_END_HOUR = 12
    }

    private val service: AiV2ServiceContract = V2ServiceProvider.service

    // ──────────────────────────────────────────────────────────────────────
    // Chat state
    // ──────────────────────────────────────────────────────────────────────

    private val _messages = MutableStateFlow<List<V2ChatMessage>>(emptyList())
    val messages: StateFlow<List<V2ChatMessage>> = _messages.asStateFlow()

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    // ──────────────────────────────────────────────────────────────────────
    // Tool calls state
    // ──────────────────────────────────────────────────────────────────────

    private val _pendingToolCalls = MutableStateFlow<List<V2ToolCallInfo>>(emptyList())
    val pendingToolCalls: StateFlow<List<V2ToolCallInfo>> = _pendingToolCalls.asStateFlow()

    /** Conversation ID for tool result submission. */
    private var activeConversationId: String = "default"
    private var activeMode: String = "concise"
    private var activeModel: String? = null

    // ──────────────────────────────────────────────────────────────────────
    // Token balance state
    // ──────────────────────────────────────────────────────────────────────

    private val _tokenBalance = MutableStateFlow<V2TokenBalance?>(null)
    val tokenBalance: StateFlow<V2TokenBalance?> = _tokenBalance.asStateFlow()

    // ──────────────────────────────────────────────────────────────────────
    // Model info state
    // ──────────────────────────────────────────────────────────────────────

    private val _availableModels = MutableStateFlow<List<V2ModelInfo>>(emptyList())
    val availableModels: StateFlow<List<V2ModelInfo>> = _availableModels.asStateFlow()

    // ──────────────────────────────────────────────────────────────────────
    // Error states
    // ──────────────────────────────────────────────────────────────────────

    private val _budgetExceeded = MutableStateFlow<BudgetExceededState?>(null)
    val budgetExceeded: StateFlow<BudgetExceededState?> = _budgetExceeded.asStateFlow()

    private val _authError = MutableStateFlow(false)
    val authError: StateFlow<Boolean> = _authError.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // ──────────────────────────────────────────────────────────────────────
    // Daily briefing tracking
    // ──────────────────────────────────────────────────────────────────────

    private val _briefing = MutableStateFlow<V2BriefingResult?>(null)
    val briefing: StateFlow<V2BriefingResult?> = _briefing.asStateFlow()

    private var briefingCheckedToday = false

    // ──────────────────────────────────────────────────────────────────────
    // Initialization — called when chat screen opens
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Called on first composition / screen open.
     * Loads model config, token balance, and checks daily briefing eligibility.
     */
    fun onScreenOpen() {
        loadModelConfig()
        refreshTokenBalance()
        checkDailyBriefing()
    }

    // ──────────────────────────────────────────────────────────────────────
    // Send message
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Sends a user message to the V2 chat endpoint and collects the SSE stream.
     *
     * @param message User's text input
     * @param conversationId Optional conversation thread ID (defaults to "default")
     * @param mode "concise" or "detailed"
     * @param model Explicit model selection, or null for server default
     */
    fun send(
        message: String,
        conversationId: String = "default",
        mode: String = "concise",
        model: String? = null,
    ) {
        if (message.isBlank()) return

        activeConversationId = conversationId
        activeMode = mode
        activeModel = model

        // Add user message to the list
        _messages.value = _messages.value + V2ChatMessage(
            role = V2ChatRole.USER,
            content = message,
        )

        // Clear any previous error states
        _error.value = null
        _budgetExceeded.value = null

        viewModelScope.launch {
            _isStreaming.value = true
            _streamingText.value = ""

            try {
                val request = V2ChatRequest(
                    message = message,
                    conversationId = conversationId,
                    mode = mode,
                    model = model,
                )

                service.chat(request).collect { event ->
                    handleStreamEvent(event)
                }
            } catch (e: V2BudgetExceededException) {
                Log.w(TAG, "Budget exceeded", e)
                _budgetExceeded.value = BudgetExceededState(
                    message = e.message ?: "Daily limit reached",
                    resetTime = _tokenBalance.value?.resetTime ?: "",
                )
                // Refresh balance to get accurate reset time
                refreshTokenBalance()
            } catch (e: V2AuthException) {
                Log.w(TAG, "Auth error", e)
                _authError.value = true
            } catch (e: Exception) {
                Log.e(TAG, "Chat error", e)
                _error.value = e.message ?: "Something went wrong"
            } finally {
                finalizeStream()
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tool results submission
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Submits resolved tool call results back to the server.
     * Called after the user grants/denies all pending tool calls
     * via [ToolPermissionHandler].
     */
    fun submitToolResults(results: List<V2ToolResult>) {
        _pendingToolCalls.value = emptyList()

        viewModelScope.launch {
            _isStreaming.value = true
            _streamingText.value = ""

            try {
                val request = V2ToolResultRequest(
                    conversationId = activeConversationId,
                    toolResults = results,
                    mode = activeMode,
                    model = activeModel,
                )

                service.chatContinue(request).collect { event ->
                    handleStreamEvent(event)
                }
            } catch (e: V2BudgetExceededException) {
                Log.w(TAG, "Budget exceeded during tool continue", e)
                _budgetExceeded.value = BudgetExceededState(
                    message = e.message ?: "Daily limit reached",
                    resetTime = _tokenBalance.value?.resetTime ?: "",
                )
                refreshTokenBalance()
            } catch (e: V2AuthException) {
                Log.w(TAG, "Auth error during tool continue", e)
                _authError.value = true
            } catch (e: Exception) {
                Log.e(TAG, "chatContinue error", e)
                _error.value = e.message ?: "Something went wrong"
            } finally {
                finalizeStream()
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Token balance
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Refreshes the user's current token balance from the server.
     */
    fun refreshTokenBalance() {
        viewModelScope.launch {
            try {
                _tokenBalance.value = service.getTokenBalance()
            } catch (e: V2AuthException) {
                Log.w(TAG, "Auth error fetching balance", e)
                _authError.value = true
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch token balance", e)
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Model config
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Loads available models from the v2Config endpoint.
     */
    private fun loadModelConfig() {
        viewModelScope.launch {
            try {
                _availableModels.value = service.getModelConfig()
            } catch (e: V2AuthException) {
                Log.w(TAG, "Auth error fetching model config", e)
                _authError.value = true
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch model config", e)
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Daily briefing
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Checks if a daily briefing should be fetched.
     * Requirement 23.1: first chat open per day, 04:00–12:00 window.
     * Requirement 23.3: at most once per calendar day.
     */
    private fun checkDailyBriefing() {
        if (briefingCheckedToday) return

        val now = Calendar.getInstance(TimeZone.getDefault())
        val hour = now.get(Calendar.HOUR_OF_DAY)

        if (hour < BRIEFING_WINDOW_START_HOUR || hour >= BRIEFING_WINDOW_END_HOUR) {
            return
        }

        briefingCheckedToday = true

        viewModelScope.launch {
            try {
                val result = service.getDailyBriefing()
                // Server handles "already delivered" logic — only show if content is fresh
                if (!result.alreadyDelivered && !result.outsideWindow && result.content.isNotBlank()) {
                    _briefing.value = result
                    // Add briefing as an assistant message
                    _messages.value = _messages.value + V2ChatMessage(
                        role = V2ChatRole.ASSISTANT,
                        content = result.content,
                        isBriefing = true,
                    )
                }
            } catch (e: V2AuthException) {
                Log.w(TAG, "Auth error fetching briefing", e)
                _authError.value = true
            } catch (e: V2BudgetExceededException) {
                // Briefing counts against budget — silently skip
                Log.d(TAG, "Briefing skipped: budget exceeded")
            } catch (e: Exception) {
                // Briefing is non-critical; don't surface errors
                Log.w(TAG, "Failed to fetch daily briefing", e)
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Error state management
    // ──────────────────────────────────────────────────────────────────────

    /** Clears auth error after login redirect is handled. */
    fun clearAuthError() {
        _authError.value = false
    }

    /** Clears budget exceeded state (e.g., after user dismisses the countdown). */
    fun clearBudgetExceeded() {
        _budgetExceeded.value = null
    }

    /** Clears general error state. */
    fun clearError() {
        _error.value = null
    }

    // ──────────────────────────────────────────────────────────────────────
    // Internal stream handling
    // ──────────────────────────────────────────────────────────────────────

    private fun handleStreamEvent(event: V2StreamEvent) {
        when (event) {
            is V2StreamEvent.Delta -> {
                _streamingText.value += event.text
            }

            is V2StreamEvent.Thinking -> {
                // Thinking text could be accumulated separately if needed
                // For now, included in streaming text with a marker
                _streamingText.value += event.text
            }

            is V2StreamEvent.ToolCalls -> {
                // Pause streaming — present tool permission UI
                _pendingToolCalls.value = event.calls
            }

            is V2StreamEvent.Done -> {
                // Update token balance from done event if available
                val info = event.info
                if (info.tokensRemaining >= 0) {
                    _tokenBalance.value = _tokenBalance.value?.copy(
                        remaining = info.tokensRemaining,
                        consumed = (_tokenBalance.value?.budget ?: 0) - info.tokensRemaining,
                    )
                }
            }

            is V2StreamEvent.Error -> {
                _error.value = event.message
            }
        }
    }

    private fun finalizeStream() {
        val text = _streamingText.value
        if (text.isNotBlank()) {
            _messages.value = _messages.value + V2ChatMessage(
                role = V2ChatRole.ASSISTANT,
                content = text,
            )
        }
        _streamingText.value = ""
        _isStreaming.value = false
    }
}

// ──────────────────────────────────────────────────────────────────────────
// UI state models
// ──────────────────────────────────────────────────────────────────────────

/** A single chat message for UI display. */
data class V2ChatMessage(
    val role: V2ChatRole,
    val content: String,
    val isBriefing: Boolean = false,
)

enum class V2ChatRole {
    USER,
    ASSISTANT,
}

/** State indicating the user's daily budget has been exceeded. */
data class BudgetExceededState(
    val message: String,
    val resetTime: String,
)
