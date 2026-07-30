package com.theblankstate.preamble.ai.v2

/**
 * Data models for the AI V2 client layer.
 *
 * These are completely independent from V1 data classes in
 * [com.theblankstate.preamble.ai.CloudAiService].
 */

// ──────────────────────────────────────────────────────────────────────────
// Request models
// ──────────────────────────────────────────────────────────────────────────

/** Request payload for [CloudAiV2Service.chat]. */
data class V2ChatRequest(
    val message: String,
    val conversationId: String = "default",
    val mode: String = "concise",
    val model: String? = null,
)

/** Request payload for [CloudAiV2Service.chatContinue]. */
data class V2ToolResultRequest(
    val conversationId: String,
    val toolResults: List<V2ToolResult>,
    val mode: String = "concise",
    val model: String? = null,
)

/** A single tool result sent back to the server. */
data class V2ToolResult(
    val name: String,
    val result: String,
    val granted: Boolean,
)

/** Request payload for [CloudAiV2Service.parse]. */
data class V2ParseRequest(
    val text: String,
    val timezone: String,
    val currentDateTime: String? = null,
)

// ──────────────────────────────────────────────────────────────────────────
// Response models
// ──────────────────────────────────────────────────────────────────────────

/** Structured parse result from the V2 parser endpoint. */
data class V2ParseResult(
    val title: String,
    val date: String? = null,
    val time: String? = null,
    val priority: Int? = null,
    val tags: List<String> = emptyList(),
    val recurrence: V2Recurrence? = null,
    val description: String? = null,
    val confidence: Double,
    val detectedLanguage: String? = null,
    val duplicates: List<V2DuplicateIndicator> = emptyList(),
    val traceId: String? = null,
)

/** Recurrence rule extracted by the parser. */
data class V2Recurrence(
    val recurrenceType: String,
    val recurrenceInterval: Int,
    val recurrenceDays: List<Int> = emptyList(),
    val recurrenceEndDate: String? = null,
)

/** A potential duplicate flagged during parse-time deduplication. */
data class V2DuplicateIndicator(
    val taskId: String,
    val similarity: Double,
    val title: String,
)

/** Token balance and budget info. */
data class V2TokenBalance(
    val consumed: Long,
    val budget: Long,
    val remaining: Long,
    val tier: String,
    val resetTime: String,
)

/** Model info as returned by the v2Config endpoint. */
data class V2ModelInfo(
    val id: String,
    val provider: String,
    val modelId: String,
    val displayName: String,
    val enabled: Boolean,
    val isDefault: Boolean,
    val supportsReasoning: Boolean,
)

/** Daily briefing response. */
data class V2BriefingResult(
    val content: String,
    val alreadyDelivered: Boolean,
    val outsideWindow: Boolean,
    val traceId: String,
)

// ──────────────────────────────────────────────────────────────────────────
// SSE stream events
// ──────────────────────────────────────────────────────────────────────────

/** Events emitted by the chat/chatContinue SSE stream. */
sealed class V2StreamEvent {
    /** Incremental text content from the model. */
    data class Delta(val text: String) : V2StreamEvent()

    /** Model reasoning/thinking content (for models that support it). */
    data class Thinking(val text: String) : V2StreamEvent()

    /** Tool calls proposed by the model, awaiting user permission. */
    data class ToolCalls(val calls: List<V2ToolCallInfo>) : V2StreamEvent()

    /** Stream completed successfully. */
    data class Done(val info: V2ChatCompletionInfo) : V2StreamEvent()

    /** Server-side error during streaming. */
    data class Error(val message: String) : V2StreamEvent()
}

/** Information about a single proposed tool call. */
data class V2ToolCallInfo(
    val name: String,
    val category: String,
    val description: String,
    val targetData: String,
    val args: Map<String, Any>,
)

/** Metadata returned with the "done" SSE event. */
data class V2ChatCompletionInfo(
    val model: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val tokensRemaining: Long,
    val hasToolCalls: Boolean,
    val traceId: String,
    val renderBlocksJson: String? = null,
)

// ──────────────────────────────────────────────────────────────────────────
// Exceptions
// ──────────────────────────────────────────────────────────────────────────

/** Auth failure — user not signed in or token expired. */
class V2AuthException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Daily token budget has been exhausted (HTTP 429). */
class V2BudgetExceededException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Input validation failure (HTTP 400). */
class V2ValidationException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** General service/network error. */
class V2ServiceException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
