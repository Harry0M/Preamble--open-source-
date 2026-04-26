package com.theblankstate.preamble.ai

/**
 * Universal AI provider interface. Any LLM provider implements this.
 */
interface AiProvider {
    val name: String
    suspend fun chat(messages: List<ChatMessage>, tools: List<AiTool>): AiResponse
}

data class ChatMessage(
    val role: String,  // "system", "user", "assistant", "tool"
    val content: String,
    val toolCallId: String? = null,
    val toolCalls: List<ToolCall>? = null,  // only for assistant turns that emitted tool calls
)

data class AiTool(
    val name: String,
    val description: String,
    val parameters: List<ToolParam>
)

data class ToolParam(
    val name: String,
    val type: String,       // "string", "integer", etc.
    val description: String,
    val required: Boolean = true
)

data class AiResponse(
    val text: String?,
    val toolCalls: List<ToolCall>?
)

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: Map<String, String>
)

enum class AiProviderType(val displayName: String) {
    MISTRAL("Mistral"),
    OPENAI("OpenAI"),
    GEMINI("Gemini"),
    CLAUDE("Claude")
}
