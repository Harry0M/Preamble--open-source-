package com.theblankstate.preamble.ai

import android.util.Log
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
 * Retryable AI error — transient server/rate-limit failure (429, 500, 502, 503, 504).
 * Extends IOException so AiParsingWorker's existing IOException catch triggers Result.retry().
 */
class RetryableAiException(message: String) : java.io.IOException(message)

private val RETRYABLE_HTTP_CODES = setOf(429, 500, 502, 503, 504)

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
            val model = "mistral-small-latest"
            Log.d(TAG, "┌─── Mistral API Request ───")
            Log.d(TAG, "│ Model: $model")
            Log.d(TAG, "│ Messages: ${messages.size} (system + user)")
            Log.d(TAG, "│ Tools: ${tools.map { it.name }}")
            messages.forEachIndexed { i, msg ->
                val preview = msg.content.take(200).replace("\n", " ")
                Log.d(TAG, "│ Msg[$i] role=${msg.role}: $preview${if (msg.content.length > 200) "..." else ""}")
            }

            val body = JsonObject().apply {
                addProperty("model", model)
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

            Log.d(TAG, "│ Request body size: ${body.toString().length} chars")

            val request = Request.Builder()
                .url("https://api.mistral.ai/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            
            Log.d(TAG, "│ HTTP Status: ${response.code}")
            Log.d(TAG, "│ Response size: ${responseBody.length} chars")
            
            if (!response.isSuccessful) {
                Log.e(TAG, "└─── Mistral API ERROR ───")
                Log.e(TAG, "  HTTP ${response.code}: ${responseBody.take(500)}")
                if (response.code in RETRYABLE_HTTP_CODES) {
                    throw RetryableAiException("Mistral API transient error ${response.code}: ${responseBody.take(300)}")
                }
                throw RuntimeException("Mistral API error ${response.code}: ${responseBody.take(300)}")
            }
            
            val json = JsonParser.parseString(responseBody).asJsonObject
            Log.d(TAG, "└─── Parsing response ───")
            parseOpenAiStyleResponse(json)
        }

    companion object {
        private const val TAG = "PreambleAI"
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
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                if (response.code in RETRYABLE_HTTP_CODES) {
                    throw RetryableAiException("OpenAI API transient error ${response.code}: ${responseBody.take(300)}")
                }
                throw RuntimeException("OpenAI API error ${response.code}: ${responseBody.take(300)}")
            }
            val json = JsonParser.parseString(responseBody).asJsonObject
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
                                        p.name to mapOf(
                                            "type" to when(p.type) {
                                                "integer" -> "INTEGER"
                                                "boolean" -> "BOOLEAN"
                                                "number" -> "NUMBER"
                                                else -> "STRING"
                                            },
                                            "description" to p.description
                                        )
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
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                if (response.code in RETRYABLE_HTTP_CODES) {
                    throw RetryableAiException("Gemini API transient error ${response.code}: ${responseBody.take(300)}")
                }
                throw RuntimeException("Gemini API error ${response.code}: ${responseBody.take(300)}")
            }
            val json = JsonParser.parseString(responseBody).asJsonObject

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
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                if (response.code in RETRYABLE_HTTP_CODES) {
                    throw RetryableAiException("Claude API transient error ${response.code}: ${responseBody.take(300)}")
                }
                throw RuntimeException("Claude API error ${response.code}: ${responseBody.take(300)}")
            }
            val json = JsonParser.parseString(responseBody).asJsonObject

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
    val tag = "PreambleAI"
    val choices = json.getAsJsonArray("choices")
    if (choices == null || choices.size() == 0) {
        val errorMsg = json.getAsJsonObject("error")?.get("message")?.asString
        Log.e(tag, "  ✘ No choices in response. Error: ${errorMsg ?: "unknown"}")
        Log.e(tag, "  Full response: ${json.toString().take(500)}")
        return AiResponse(errorMsg ?: "Error processing response", null)
    }

    val message = choices[0].asJsonObject.getAsJsonObject("message")
    val text = message.get("content")?.let { if (it.isJsonNull) null else it.asString }

    val toolCallsArray = message.getAsJsonArray("tool_calls")
    Log.d(tag, "  Content: ${text?.take(100) ?: "null"}")
    Log.d(tag, "  Tool calls: ${toolCallsArray?.size() ?: 0}")
    
    val toolCalls = toolCallsArray?.map { tc ->
        val obj = tc.asJsonObject
        val fn = obj.getAsJsonObject("function")
        val fnName = fn.get("name").asString
        val argsRaw = fn.get("arguments").asString
        Log.d(tag, "  Tool: $fnName")
        Log.d(tag, "  Args raw: $argsRaw")
        
        val argsJson = JsonParser.parseString(argsRaw).asJsonObject
        val args = mutableMapOf<String, String>()
        argsJson.entrySet().forEach { (k, v) ->
            val value = if (v.isJsonPrimitive) v.asString else v.toString()
            args[k] = value
            Log.d(tag, "    $k = $value")
        }
        ToolCall(
            id = obj.get("id")?.asString ?: "call_${System.currentTimeMillis()}",
            name = fnName,
            arguments = args
        )
    }

    Log.d(tag, "  ✓ Parsed ${toolCalls?.size ?: 0} tool calls")
    return AiResponse(text, toolCalls?.ifEmpty { null })
}
