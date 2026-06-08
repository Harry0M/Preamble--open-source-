package com.theblankstate.preamble.ai

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Client for Preamble AI Cloud Functions.
 * Replaces direct API provider calls with server-side proxy.
 *
 * Benefits:
 *   - No API keys in APK
 *   - Prompts live server-side (update without release)
 *   - Credits enforced server-side (tamper-proof)
 *   - Rate limiting and abuse protection
 */
object CloudAiService {
    private const val TAG = "CloudAiService"

    // TODO: Update with your deployed Cloud Function URL after running `firebase deploy`
    private const val BASE_URL = "https://us-central1-preambl-fbea6.cloudfunctions.net"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)     // Long timeout for streaming
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    /** Get current user's Firebase Auth token */
    private suspend fun getAuthToken(): String? = withContext(Dispatchers.IO) {
        val user = FirebaseAuth.getInstance().currentUser ?: return@withContext null
        suspendCancellableCoroutine { cont ->
            user.getIdToken(false).addOnSuccessListener { result ->
                cont.resume(result.token)
            }.addOnFailureListener { e ->
                cont.resumeWithException(e)
            }
        }
    }

    /**
     * Send a chat message through the Cloud Function.
     * Returns SSE events via the callback.
     */
    suspend fun chat(
        message: String,
        conversationId: String = "default",
        userMessageId: String? = null,
        assistantMessageId: String? = null,
        model: String? = null,
        mode: String = "concise",
        smartMode: Boolean = true,
        onDelta: suspend (String) -> Unit = {},
        onThinking: suspend (String) -> Unit = {},
        onToolCalls: suspend (List<CloudToolCall>) -> Unit = {},
        onDone: suspend (CloudChatResult) -> Unit = {},
        onError: suspend (String) -> Unit = {},
    ) {
        val token = runCatching { getAuthToken() }
            .onFailure { Log.w(TAG, "Auth token unavailable", it) }
            .getOrNull()
        if (token == null) {
            onError("AUTH_TOKEN_UNAVAILABLE")
            return
        }

        val body = JSONObject().apply {
            put("message", message)
            put("conversationId", conversationId)
            put("mode", mode)
            put("smartMode", smartMode)
            if (!userMessageId.isNullOrBlank()) put("userMessageId", userMessageId)
            if (!assistantMessageId.isNullOrBlank()) put("assistantMessageId", assistantMessageId)
            if (!model.isNullOrBlank()) put("model", model)
            put("appVersionCode", com.theblankstate.preamble.BuildConfig.VERSION_CODE)
        }

        val request = Request.Builder()
            .url("$BASE_URL/aiChat")
            .addHeader("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        try {
            val response = client.newCall(request).execute()

            if (response.code == 429) {
                onError("DAILY_LIMIT_REACHED")
                return
            }

            if (!response.isSuccessful) {
                onError("Server error: ${response.code}")
                return
            }

            // Parse SSE stream
            val reader = BufferedReader(InputStreamReader(response.body!!.byteStream()))
            var currentEvent = ""
            var dataBuffer = StringBuilder()

            while (true) {
                val line = reader.readLine() ?: break
                when {
                    line.startsWith("event: ") -> {
                        currentEvent = line.removePrefix("event: ").trim()
                    }
                    line.startsWith("data: ") -> {
                        dataBuffer.append(line.removePrefix("data: "))
                    }
                    line.isBlank() && dataBuffer.isNotEmpty() -> {
                        // End of SSE event — process it
                        val data = dataBuffer.toString()
                        dataBuffer = StringBuilder()
                        processSSEEvent(currentEvent, data, onDelta, onThinking, onToolCalls, onDone, onError)
                        currentEvent = ""
                    }
                }
            }

            reader.close()
            response.close()
        } catch (e: Exception) {
            Log.e(TAG, "Chat request failed", e)
            onError(e.message ?: "Network error")
        }
    }

    /**
     * Send tool execution results back to Cloud Function for final response.
     */
    suspend fun sendToolResults(
        conversationId: String,
        toolResults: List<ToolResult>,
        assistantMessageId: String? = null,
        model: String? = null,
        mode: String = "concise",
        onDelta: suspend (String) -> Unit = {},
        onDone: suspend (CloudChatResult) -> Unit = {},
        onError: suspend (String) -> Unit = {},
    ) {
        val token = runCatching { getAuthToken() }
            .onFailure { Log.w(TAG, "Auth token unavailable for tool results", it) }
            .getOrNull() ?: run {
            onError("AUTH_TOKEN_UNAVAILABLE")
            return
        }

        val resultsJson = JSONArray()
        for (result in toolResults) {
            resultsJson.put(JSONObject().apply {
                put("name", result.name)
                put("result", result.result)
            })
        }

        val body = JSONObject().apply {
            put("conversationId", conversationId)
            put("toolResults", resultsJson)
            put("mode", mode)
            if (!assistantMessageId.isNullOrBlank()) put("assistantMessageId", assistantMessageId)
            if (!model.isNullOrBlank()) put("model", model)
            put("appVersionCode", com.theblankstate.preamble.BuildConfig.VERSION_CODE)
        }

        val request = Request.Builder()
            .url("$BASE_URL/aiChatContinue")
            .addHeader("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                onError("Server error: ${response.code}")
                return
            }

            val reader = BufferedReader(InputStreamReader(response.body!!.byteStream()))
            var currentEvent = ""
            var dataBuffer = StringBuilder()

            while (true) {
                val line = reader.readLine() ?: break
                when {
                    line.startsWith("event: ") -> currentEvent = line.removePrefix("event: ").trim()
                    line.startsWith("data: ") -> dataBuffer.append(line.removePrefix("data: "))
                    line.isBlank() && dataBuffer.isNotEmpty() -> {
                        processSSEEvent(currentEvent, dataBuffer.toString(), onDelta, { _ -> }, { _ -> }, onDone, onError)
                        dataBuffer = StringBuilder()
                        currentEvent = ""
                    }
                }
            }

            reader.close()
            response.close()
        } catch (e: Exception) {
            Log.e(TAG, "Tool results request failed", e)
            onError(e.message ?: "Network error")
        }
    }

    /** Get credit balance from server */
    suspend fun getBalance(): CreditBalance? = withContext(Dispatchers.IO) {
        val token = runCatching { getAuthToken() }.getOrNull() ?: return@withContext null
        val request = Request.Builder()
            .url("$BASE_URL/aiCreditsBalance")
            .addHeader("Authorization", "Bearer $token")
            .get().build()
        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            val json = JSONObject(response.body!!.string())
            CreditBalance(
                balance = json.optInt("balance", 0),
                totalEarned = json.optInt("totalEarned", 0),
                totalSpent = json.optInt("totalSpent", 0),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Balance check failed", e)
            null
        }
    }

    /** Reward credits after ad watch */
    suspend fun rewardCredits(): RewardResult? = withContext(Dispatchers.IO) {
        val token = runCatching { getAuthToken() }.getOrNull() ?: return@withContext null
        val request = Request.Builder()
            .url("$BASE_URL/aiCreditsReward")
            .addHeader("Authorization", "Bearer $token")
            .post("{}".toRequestBody(JSON_MEDIA))
            .build()
        try {
            val response = client.newCall(request).execute()
            val json = JSONObject(response.body!!.string())
            if (!response.isSuccessful) return@withContext null
            RewardResult(
                creditsAdded = json.optInt("creditsAdded", 10),
                firstTimeBonus = json.optInt("firstTimeBonus", 0),
                newBalance = json.optInt("balance", 0),
                adsRemainingToday = json.optInt("adsRemainingToday", -1).takeIf { it >= 0 },
            )
        } catch (e: Exception) {
            Log.w(TAG, "Reward failed", e)
            null
        }
    }

    /**
     * Parse raw task text via Cloud Function — used by voice input, notification edit, task sheet.
     * Same prompt system as offline AiPromptFactory. Always FREE (no credits).
     *
     * @return ParseTaskResult with tool calls, or null on failure
     */
    suspend fun parseTask(
        rawText: String,
        subtaskIntensity: Int = 0,
        isNotificationEdit: Boolean = false,
    ): ParseTaskResult? = withContext(Dispatchers.IO) {
        val token = runCatching { getAuthToken() }.getOrNull() ?: return@withContext null

        val body = JSONObject().apply {
            put("text", rawText)
            put("subtaskIntensity", subtaskIntensity)
            put("isNotificationEdit", isNotificationEdit)
            put("appVersionCode", com.theblankstate.preamble.BuildConfig.VERSION_CODE)
        }

        val request = Request.Builder()
            .url("$BASE_URL/aiParseTask")
            .addHeader("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "parseTask failed: ${response.code}")
                return@withContext null
            }

            val json = JSONObject(response.body!!.string())
            if (!json.optBoolean("success", false)) {
                Log.w(TAG, "parseTask error: ${json.optString("error")}")
                return@withContext null
            }

            val toolCallsArr = json.optJSONArray("toolCalls") ?: JSONArray()
            val toolCalls = (0 until toolCallsArr.length()).map { i ->
                val tc = toolCallsArr.getJSONObject(i)
                val args = mutableMapOf<String, String>()
                val argsJson = tc.optJSONObject("args")
                argsJson?.keys()?.forEach { key ->
                    args[key] = argsJson.optString(key, "")
                }
                CloudToolCall(name = tc.optString("name"), args = args)
            }

            ParseTaskResult(
                toolCalls = toolCalls,
                text = json.optString("text", ""),
                model = json.optString("model", ""),
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseTask request failed", e)
            null
        }
    }

    private suspend fun processSSEEvent(
        event: String,
        data: String,
        onDelta: suspend (String) -> Unit,
        onThinking: suspend (String) -> Unit,
        onToolCalls: suspend (List<CloudToolCall>) -> Unit,
        onDone: suspend (CloudChatResult) -> Unit,
        onError: suspend (String) -> Unit,
    ) {
        try {
            val json = JSONObject(data)
            when (event) {
                "thinking" -> {
                    val text = json.optString("text", "")
                    if (text.isNotEmpty()) onThinking(text)
                }
                "delta" -> {
                    val text = json.optString("text", "")
                    if (text.isNotEmpty()) onDelta(text)
                }
                "tool_calls" -> {
                    val calls = json.optJSONArray("calls") ?: return
                    val toolCalls = (0 until calls.length()).map { i ->
                        val c = calls.getJSONObject(i)
                        val args = mutableMapOf<String, String>()
                        val argsJson = c.optJSONObject("args")
                        argsJson?.keys()?.forEach { key ->
                            args[key] = argsJson.optString(key, "")
                        }
                        CloudToolCall(name = c.optString("name"), args = args)
                    }
                    onToolCalls(toolCalls)
                }
                "done" -> {
                    val renderBlocks = json.optJSONArray("renderBlocks")
                    onDone(CloudChatResult(
                        tokensRemaining = json.optInt("tokensRemaining", -1),
                        model = json.optString("model", ""),
                        hasToolCalls = json.optBoolean("hasToolCalls", false),
                        inputTokens = json.optInt("inputTokens", 0),
                        outputTokens = json.optInt("outputTokens", 0),
                        renderBlocksJson = renderBlocks?.let {
                            JSONObject()
                                .put("version", json.optInt("renderBlocksVersion", 1))
                                .put("blocks", it)
                                .toString()
                        },
                    ))
                }
                "error" -> {
                    onError(json.optString("message", "Unknown error"))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "SSE parse error: $event", e)
        }
    }
}

data class CloudToolCall(val name: String, val args: Map<String, String>)
data class CloudChatResult(
    val tokensRemaining: Int = -1,
    val model: String = "",
    val hasToolCalls: Boolean = false,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val renderBlocksJson: String? = null,
)
data class CreditBalance(val balance: Int, val totalEarned: Int, val totalSpent: Int)
data class RewardResult(val creditsAdded: Int, val firstTimeBonus: Int, val newBalance: Int, val adsRemainingToday: Int? = null)
data class ToolResult(val name: String, val result: String)
data class ParseTaskResult(val toolCalls: List<CloudToolCall>, val text: String, val model: String)
