package com.theblankstate.preamble.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Shared OkHttpClient for all AI providers.
 * Avoids creating 4 separate connection pools + thread pools (~2-3MB each).
 */
private val sharedAiClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .readTimeout(60, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .build()
}

class MistralProvider(private val apiKey: String) : AiProvider {
    override val name = "Mistral"
    private val client = sharedAiClient
    private val gson = Gson()

    override suspend fun chat(messages: List<ChatMessage>, tools: List<AiTool>): AiResponse =
        withContext(Dispatchers.IO) {
            val body = JsonObject().apply {
                addProperty("model", "mistral-small-latest")
                add("messages", gson.toJsonTree(messages.map { msg ->
                    mapOf("role" to msg.role, "content" to msg.content).let {
                        if (msg.toolCallId != null) it + ("tool_call_id" to msg.toolCallId) else it
                    }
                }))
                if (tools.isNotEmpty()) {
                    add("tools", gson.toJsonTree(tools.map { tool ->
                        mapOf(
                            "type" to "function",
                            "function" to mapOf(
                                "name" to tool.name,
                                "description" to tool.description,
                                "parameters" to mapOf(
                                    "type" to "object",
                                    "properties" to tool.parameters.associate { p ->
                                        p.name to mapOf("type" to p.type, "description" to p.description)
                                    },
                                    "required" to tool.parameters.filter { it.required }.map { it.name }
                                )
                            )
                        )
                    }))
                }
            }

            val request = Request.Builder()
                .url("https://api.mistral.ai/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val json = JsonParser.parseString(response.body?.string()).asJsonObject
            parseOpenAiStyleResponse(json)
        }
}

class OpenAiProvider(private val apiKey: String) : AiProvider {
    override val name = "OpenAI"
    private val client = sharedAiClient
    private val gson = Gson()

    override suspend fun chat(messages: List<ChatMessage>, tools: List<AiTool>): AiResponse =
        withContext(Dispatchers.IO) {
            val body = JsonObject().apply {
                addProperty("model", "gpt-4o-mini")
                add("messages", gson.toJsonTree(messages.map { msg ->
                    mapOf("role" to msg.role, "content" to msg.content).let {
                        if (msg.toolCallId != null) it + ("tool_call_id" to msg.toolCallId) else it
                    }
                }))
                if (tools.isNotEmpty()) {
                    add("tools", gson.toJsonTree(tools.map { tool ->
                        mapOf(
                            "type" to "function",
                            "function" to mapOf(
                                "name" to tool.name,
                                "description" to tool.description,
                                "parameters" to mapOf(
                                    "type" to "object",
                                    "properties" to tool.parameters.associate { p ->
                                        p.name to mapOf("type" to p.type, "description" to p.description)
                                    },
                                    "required" to tool.parameters.filter { it.required }.map { it.name }
                                )
                            )
                        )
                    }))
                }
            }

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val json = JsonParser.parseString(response.body?.string()).asJsonObject
            parseOpenAiStyleResponse(json)
        }
}

class GeminiProvider(private val apiKey: String) : AiProvider {
    override val name = "Gemini"
    private val client = sharedAiClient
    private val gson = Gson()

    override suspend fun chat(messages: List<ChatMessage>, tools: List<AiTool>): AiResponse =
        withContext(Dispatchers.IO) {
            val contents = messages.filter { it.role != "system" }.map { msg ->
                mapOf(
                    "role" to if (msg.role == "assistant") "model" else "user",
                    "parts" to listOf(mapOf("text" to msg.content))
                )
            }
            val systemInstruction = messages.find { it.role == "system" }?.content

            val body = JsonObject().apply {
                add("contents", gson.toJsonTree(contents))
                if (systemInstruction != null) {
                    add("system_instruction", gson.toJsonTree(
                        mapOf("parts" to listOf(mapOf("text" to systemInstruction)))
                    ))
                }
                if (tools.isNotEmpty()) {
                    add("tools", gson.toJsonTree(listOf(mapOf(
                        "function_declarations" to tools.map { tool ->
                            mapOf(
                                "name" to tool.name,
                                "description" to tool.description,
                                "parameters" to mapOf(
                                    "type" to "OBJECT",
                                    "properties" to tool.parameters.associate { p ->
                                        p.name to mapOf("type" to "STRING", "description" to p.description)
                                    },
                                    "required" to tool.parameters.filter { it.required }.map { it.name }
                                )
                            )
                        }
                    ))))
                }
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val json = JsonParser.parseString(response.body?.string()).asJsonObject

            val candidates = json.getAsJsonArray("candidates")
            if (candidates == null || candidates.size() == 0) {
                return@withContext AiResponse("Sorry, I couldn't process that.", null)
            }

            val parts = candidates[0].asJsonObject
                .getAsJsonObject("content")
                .getAsJsonArray("parts")

            var text: String? = null
            val toolCalls = mutableListOf<ToolCall>()

            for (part in parts) {
                val p = part.asJsonObject
                if (p.has("text")) {
                    text = p.get("text").asString
                }
                if (p.has("functionCall")) {
                    val fc = p.getAsJsonObject("functionCall")
                    val args = mutableMapOf<String, String>()
                    fc.getAsJsonObject("args")?.entrySet()?.forEach { (k, v) ->
                        args[k] = v.asString
                    }
                    toolCalls.add(ToolCall(
                        id = java.util.UUID.randomUUID().toString(),
                        name = fc.get("name").asString,
                        arguments = args
                    ))
                }
            }

            AiResponse(text, toolCalls.ifEmpty { null })
        }
}

class ClaudeProvider(private val apiKey: String) : AiProvider {
    override val name = "Claude"
    private val client = sharedAiClient
    private val gson = Gson()

    override suspend fun chat(messages: List<ChatMessage>, tools: List<AiTool>): AiResponse =
        withContext(Dispatchers.IO) {
            val systemMsg = messages.find { it.role == "system" }?.content
            val chatMsgs = messages.filter { it.role != "system" }

            val body = JsonObject().apply {
                addProperty("model", "claude-sonnet-4-20250514")
                addProperty("max_tokens", 1024)
                if (systemMsg != null) addProperty("system", systemMsg)
                add("messages", gson.toJsonTree(chatMsgs.map { msg ->
                    mapOf("role" to msg.role, "content" to msg.content)
                }))
                if (tools.isNotEmpty()) {
                    add("tools", gson.toJsonTree(tools.map { tool ->
                        mapOf(
                            "name" to tool.name,
                            "description" to tool.description,
                            "input_schema" to mapOf(
                                "type" to "object",
                                "properties" to tool.parameters.associate { p ->
                                    p.name to mapOf("type" to p.type, "description" to p.description)
                                },
                                "required" to tool.parameters.filter { it.required }.map { it.name }
                            )
                        )
                    }))
                }
            }

            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val json = JsonParser.parseString(response.body?.string()).asJsonObject

            val content = json.getAsJsonArray("content") ?: return@withContext AiResponse("Error processing response", null)

            var text: String? = null
            val toolCalls = mutableListOf<ToolCall>()

            for (block in content) {
                val b = block.asJsonObject
                when (b.get("type").asString) {
                    "text" -> text = b.get("text").asString
                    "tool_use" -> {
                        val args = mutableMapOf<String, String>()
                        b.getAsJsonObject("input")?.entrySet()?.forEach { (k, v) ->
                            args[k] = v.asString
                        }
                        toolCalls.add(ToolCall(
                            id = b.get("id")?.asString ?: "claude_${System.currentTimeMillis()}",
                            name = b.get("name").asString,
                            arguments = args
                        ))
                    }
                }
            }

            AiResponse(text, toolCalls.ifEmpty { null })
        }
}

// Shared parser for OpenAI-style responses (Mistral + OpenAI use the same format)
internal fun parseOpenAiStyleResponse(json: JsonObject): AiResponse {
    val choices = json.getAsJsonArray("choices")
    if (choices == null || choices.size() == 0) {
        val errorMsg = json.getAsJsonObject("error")?.get("message")?.asString
        return AiResponse(errorMsg ?: "Error processing response", null)
    }

    val message = choices[0].asJsonObject.getAsJsonObject("message")
    val text = message.get("content")?.let { if (it.isJsonNull) null else it.asString }

    val toolCallsArray = message.getAsJsonArray("tool_calls")
    val toolCalls = toolCallsArray?.map { tc ->
        val obj = tc.asJsonObject
        val fn = obj.getAsJsonObject("function")
        val argsJson = JsonParser.parseString(fn.get("arguments").asString).asJsonObject
        val args = mutableMapOf<String, String>()
        argsJson.entrySet().forEach { (k, v) -> args[k] = v.asString }
        ToolCall(
            id = obj.get("id")?.asString ?: "call_${System.currentTimeMillis()}",
            name = fn.get("name").asString,
            arguments = args
        )
    }

    return AiResponse(text, toolCalls?.ifEmpty { null })
}
