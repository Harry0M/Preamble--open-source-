package com.theblankstate.preamble.ai.v2

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * AI V2 Cloud Service — completely independent from V1 [com.theblankstate.preamble.ai.CloudAiService].
 *
 * Talks to V2-specific Cloud Function endpoints:
 *   - v2Chat (SSE streaming)
 *   - v2ChatContinue (tool results → SSE streaming)
 *   - v2Parse (single-shot JSON response)
 *   - v2TokenBalance (GET)
 *   - v2Config (GET)
 *   - v2DailyBriefing (POST)
 *
 * All V2 data lives in separate Firestore collections (v2_memory, v2_conversations,
 * v2_token_usage, v2_config) — zero overlap with V1.
 */
object CloudAiV2Service : AiV2ServiceContract {

    private const val TAG = "CloudAiV2Service"

    private const val BASE_URL = "https://us-central1-preambl-fbea6.cloudfunctions.net"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    // ──────────────────────────────────────────────────────────────────────
    // Auth
    // ──────────────────────────────────────────────────────────────────────

    private suspend fun getAuthToken(): String = withContext(Dispatchers.IO) {
        val user = FirebaseAuth.getInstance().currentUser
            ?: throw V2AuthException("No signed-in user")
        suspendCancellableCoroutine { cont ->
            user.getIdToken(false)
                .addOnSuccessListener { result ->
                    val token = result.token
                    if (token.isNullOrBlank()) {
                        cont.resumeWithException(V2AuthException("Empty auth token"))
                    } else {
                        cont.resume(token)
                    }
                }
                .addOnFailureListener { e ->
                    cont.resumeWithException(V2AuthException("Token fetch failed", e))
                }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // chat() — POST to v2Chat, return Flow<V2StreamEvent> for SSE
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Streams a chat response from the V2 Chat endpoint.
     *
     * The returned [Flow] emits [V2StreamEvent]s as they arrive over SSE.
     * Collect the flow to receive delta text, thinking blocks, tool calls, and
     * the final done signal. The flow completes normally when the stream ends.
     *
     * @throws V2AuthException if the user is not signed in
     * @throws V2BudgetExceededException if daily token budget is exhausted (HTTP 429)
     * @throws V2ServiceException for server errors
     */
    override fun chat(request: V2ChatRequest): Flow<V2StreamEvent> = callbackFlow {
        val token = getAuthToken()

        val body = JSONObject().apply {
            put("message", request.message)
            put("conversationId", request.conversationId)
            put("mode", request.mode)
            request.model?.let { put("model", it) }
            put("appVersionCode", com.theblankstate.preamble.BuildConfig.VERSION_CODE)
        }

        val httpRequest = Request.Builder()
            .url("$BASE_URL/v2Chat")
            .addHeader("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        val call = client.newCall(httpRequest)

        try {
            val response = withContext(Dispatchers.IO) { call.execute() }

            when {
                response.code == 401 -> {
                    response.close()
                    throw V2AuthException("Unauthorized (401)")
                }
                response.code == 429 -> {
                    response.close()
                    throw V2BudgetExceededException("Daily token budget exceeded")
                }
                !response.isSuccessful -> {
                    val code = response.code
                    response.close()
                    throw V2ServiceException("Server error: $code")
                }
            }

            val reader = BufferedReader(InputStreamReader(response.body!!.byteStream()))
            var currentEvent = ""
            var dataBuffer = StringBuilder()

            while (true) {
                val line = withContext(Dispatchers.IO) { reader.readLine() } ?: break
                when {
                    line.startsWith("event: ") -> {
                        currentEvent = line.removePrefix("event: ").trim()
                    }
                    line.startsWith("data: ") -> {
                        dataBuffer.append(line.removePrefix("data: "))
                    }
                    line.isBlank() && dataBuffer.isNotEmpty() -> {
                        val event = parseSSEEvent(currentEvent, dataBuffer.toString())
                        if (event != null) trySend(event)
                        dataBuffer = StringBuilder()
                        currentEvent = ""
                    }
                }
            }

            withContext(Dispatchers.IO) {
                reader.close()
                response.close()
            }
            channel.close()
        } catch (e: Exception) {
            if (e is V2AuthException || e is V2BudgetExceededException || e is V2ServiceException) {
                channel.close(e)
            } else {
                Log.e(TAG, "chat() stream failed", e)
                channel.close(V2ServiceException("Network error: ${e.message}", e))
            }
        }

        awaitClose { call.cancel() }
    }

    // ──────────────────────────────────────────────────────────────────────
    // chatContinue() — POST tool results to v2ChatContinue
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Sends tool call results back to the server for final response generation.
     *
     * After the user grants or denies tool permissions, call this with the results.
     * The returned flow emits the continuation response as SSE events.
     */
    override fun chatContinue(request: V2ToolResultRequest): Flow<V2StreamEvent> = callbackFlow {
        val token = getAuthToken()

        val toolResultsJson = JSONArray().apply {
            for (result in request.toolResults) {
                put(JSONObject().apply {
                    put("name", result.name)
                    put("result", result.result)
                    put("granted", result.granted)
                })
            }
        }

        val body = JSONObject().apply {
            put("conversationId", request.conversationId)
            put("toolResults", toolResultsJson)
            put("mode", request.mode)
            request.model?.let { put("model", it) }
            put("appVersionCode", com.theblankstate.preamble.BuildConfig.VERSION_CODE)
        }

        val httpRequest = Request.Builder()
            .url("$BASE_URL/v2ChatContinue")
            .addHeader("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        val call = client.newCall(httpRequest)

        try {
            val response = withContext(Dispatchers.IO) { call.execute() }

            when {
                response.code == 401 -> {
                    response.close()
                    throw V2AuthException("Unauthorized (401)")
                }
                response.code == 429 -> {
                    response.close()
                    throw V2BudgetExceededException("Daily token budget exceeded")
                }
                !response.isSuccessful -> {
                    val code = response.code
                    response.close()
                    throw V2ServiceException("Server error: $code")
                }
            }

            val reader = BufferedReader(InputStreamReader(response.body!!.byteStream()))
            var currentEvent = ""
            var dataBuffer = StringBuilder()

            while (true) {
                val line = withContext(Dispatchers.IO) { reader.readLine() } ?: break
                when {
                    line.startsWith("event: ") -> {
                        currentEvent = line.removePrefix("event: ").trim()
                    }
                    line.startsWith("data: ") -> {
                        dataBuffer.append(line.removePrefix("data: "))
                    }
                    line.isBlank() && dataBuffer.isNotEmpty() -> {
                        val event = parseSSEEvent(currentEvent, dataBuffer.toString())
                        if (event != null) trySend(event)
                        dataBuffer = StringBuilder()
                        currentEvent = ""
                    }
                }
            }

            withContext(Dispatchers.IO) {
                reader.close()
                response.close()
            }
            channel.close()
        } catch (e: Exception) {
            if (e is V2AuthException || e is V2BudgetExceededException || e is V2ServiceException) {
                channel.close(e)
            } else {
                Log.e(TAG, "chatContinue() stream failed", e)
                channel.close(V2ServiceException("Network error: ${e.message}", e))
            }
        }

        awaitClose { call.cancel() }
    }

    // ──────────────────────────────────────────────────────────────────────
    // parse() — POST to v2Parse, return V2ParseResult
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Parses natural language input into a structured task via the V2 endpoint.
     *
     * Unlike chat, this is a one-shot request/response (no streaming).
     *
     * @throws V2AuthException if the user is not signed in
     * @throws V2BudgetExceededException if daily token budget is exhausted
     * @throws V2ServiceException for server errors or parse failures
     */
    override suspend fun parse(request: V2ParseRequest): V2ParseResult = withContext(Dispatchers.IO) {
        val token = getAuthToken()

        val body = JSONObject().apply {
            put("text", request.text)
            put("timezone", request.timezone)
            request.currentDateTime?.let { put("currentDateTime", it) }
            put("appVersionCode", com.theblankstate.preamble.BuildConfig.VERSION_CODE)
        }

        val httpRequest = Request.Builder()
            .url("$BASE_URL/v2Parse")
            .addHeader("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        val response = client.newCall(httpRequest).execute()

        when {
            response.code == 401 -> {
                response.close()
                throw V2AuthException("Unauthorized (401)")
            }
            response.code == 400 -> {
                val errorBody = response.body?.string() ?: ""
                response.close()
                val errorJson = runCatching { JSONObject(errorBody) }.getOrNull()
                throw V2ValidationException(
                    errorJson?.optString("error") ?: "Invalid input"
                )
            }
            response.code == 429 -> {
                response.close()
                throw V2BudgetExceededException("Daily token budget exceeded")
            }
            !response.isSuccessful -> {
                val code = response.code
                response.close()
                throw V2ServiceException("Server error: $code")
            }
        }

        val json = JSONObject(response.body!!.string())
        response.close()

        parseV2ParseResult(json)
    }

    // ──────────────────────────────────────────────────────────────────────
    // getTokenBalance() — GET v2TokenBalance
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Retrieves the user's current token consumption and remaining daily budget.
     */
    override suspend fun getTokenBalance(): V2TokenBalance = withContext(Dispatchers.IO) {
        val token = getAuthToken()

        val httpRequest = Request.Builder()
            .url("$BASE_URL/v2TokenBalance")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        val response = client.newCall(httpRequest).execute()

        when {
            response.code == 401 -> {
                response.close()
                throw V2AuthException("Unauthorized (401)")
            }
            !response.isSuccessful -> {
                val code = response.code
                response.close()
                throw V2ServiceException("Server error: $code")
            }
        }

        val json = JSONObject(response.body!!.string())
        response.close()

        V2TokenBalance(
            consumed = json.optLong("consumed", 0),
            budget = json.optLong("budget", 0),
            remaining = json.optLong("remaining", 0),
            tier = json.optString("tier", "pro_standard"),
            resetTime = json.optString("resetTime", ""),
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    // getModelConfig() — GET v2Config
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Fetches the available model registry so the client can display model names.
     */
    override suspend fun getModelConfig(): List<V2ModelInfo> = withContext(Dispatchers.IO) {
        val token = getAuthToken()

        val httpRequest = Request.Builder()
            .url("$BASE_URL/v2Config")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        val response = client.newCall(httpRequest).execute()

        when {
            response.code == 401 -> {
                response.close()
                throw V2AuthException("Unauthorized (401)")
            }
            !response.isSuccessful -> {
                val code = response.code
                response.close()
                throw V2ServiceException("Server error: $code")
            }
        }

        val json = JSONObject(response.body!!.string())
        response.close()

        val modelsArray = json.optJSONArray("models") ?: JSONArray()
        (0 until modelsArray.length()).map { i ->
            val m = modelsArray.getJSONObject(i)
            V2ModelInfo(
                id = m.optString("id"),
                provider = m.optString("provider"),
                modelId = m.optString("modelId"),
                displayName = m.optString("displayName"),
                enabled = m.optBoolean("enabled", true),
                isDefault = m.optBoolean("isDefault", false),
                supportsReasoning = m.optBoolean("supportsReasoning", false),
            )
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // getDailyBriefing() — POST to v2DailyBriefing
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Requests an on-demand daily briefing from the V2 endpoint.
     *
     * The server checks whether a briefing has already been delivered today
     * and whether the current time is within the 04:00–12:00 window.
     */
    override suspend fun getDailyBriefing(): V2BriefingResult = withContext(Dispatchers.IO) {
        val token = getAuthToken()

        val body = JSONObject().apply {
            put("appVersionCode", com.theblankstate.preamble.BuildConfig.VERSION_CODE)
        }

        val httpRequest = Request.Builder()
            .url("$BASE_URL/v2DailyBriefing")
            .addHeader("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        val response = client.newCall(httpRequest).execute()

        when {
            response.code == 401 -> {
                response.close()
                throw V2AuthException("Unauthorized (401)")
            }
            response.code == 429 -> {
                response.close()
                throw V2BudgetExceededException("Daily token budget exceeded")
            }
            !response.isSuccessful -> {
                val code = response.code
                response.close()
                throw V2ServiceException("Server error: $code")
            }
        }

        val json = JSONObject(response.body!!.string())
        response.close()

        V2BriefingResult(
            content = json.optString("content", ""),
            alreadyDelivered = json.optBoolean("alreadyDelivered", false),
            outsideWindow = json.optBoolean("outsideWindow", false),
            traceId = json.optString("traceId", ""),
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    // SSE event parsing
    // ──────────────────────────────────────────────────────────────────────

    private fun parseSSEEvent(event: String, data: String): V2StreamEvent? {
        return try {
            val json = JSONObject(data)
            when (event) {
                "delta" -> {
                    val text = json.optString("text", "")
                    if (text.isNotEmpty()) V2StreamEvent.Delta(text) else null
                }
                "thinking" -> {
                    val text = json.optString("text", "")
                    if (text.isNotEmpty()) V2StreamEvent.Thinking(text) else null
                }
                "tool_calls" -> {
                    val callsArray = json.optJSONArray("calls") ?: return null
                    val calls = (0 until callsArray.length()).map { i ->
                        val c = callsArray.getJSONObject(i)
                        val args = mutableMapOf<String, Any>()
                        val argsJson = c.optJSONObject("args")
                        argsJson?.keys()?.forEach { key ->
                            args[key] = argsJson.opt(key) ?: ""
                        }
                        V2ToolCallInfo(
                            name = c.optString("name"),
                            category = c.optString("category", "read"),
                            description = c.optString("description", ""),
                            targetData = c.optString("targetData", ""),
                            args = args,
                        )
                    }
                    V2StreamEvent.ToolCalls(calls)
                }
                "done" -> {
                    val renderBlocksJson = json.optJSONArray("renderBlocks")
                    V2StreamEvent.Done(
                        V2ChatCompletionInfo(
                            model = json.optString("model", ""),
                            inputTokens = json.optInt("inputTokens", 0),
                            outputTokens = json.optInt("outputTokens", 0),
                            tokensRemaining = json.optLong("tokensRemaining", -1),
                            hasToolCalls = json.optBoolean("hasToolCalls", false),
                            traceId = json.optString("traceId", ""),
                            renderBlocksJson = renderBlocksJson?.toString(),
                        )
                    )
                }
                "error" -> {
                    V2StreamEvent.Error(json.optString("message", "Unknown error"))
                }
                else -> {
                    Log.w(TAG, "Unknown SSE event type: $event")
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "SSE event parse error: $event", e)
            null
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Parse result deserialization
    // ──────────────────────────────────────────────────────────────────────

    private fun parseV2ParseResult(json: JSONObject): V2ParseResult {
        val duplicatesArray = json.optJSONArray("duplicates")
        val duplicates = if (duplicatesArray != null) {
            (0 until duplicatesArray.length()).map { i ->
                val d = duplicatesArray.getJSONObject(i)
                V2DuplicateIndicator(
                    taskId = d.optString("taskId"),
                    similarity = d.optDouble("similarity", 0.0),
                    title = d.optString("title", ""),
                )
            }
        } else emptyList()

        val tagsArray = json.optJSONArray("tags")
        val tags = if (tagsArray != null) {
            (0 until tagsArray.length()).map { tagsArray.getString(it) }
        } else emptyList()

        val recurrenceJson = json.optJSONObject("recurrence")
        val recurrence = if (recurrenceJson != null) {
            val daysArray = recurrenceJson.optJSONArray("recurrenceDays")
            val days = if (daysArray != null) {
                (0 until daysArray.length()).map { daysArray.getInt(it) }
            } else emptyList()
            V2Recurrence(
                recurrenceType = recurrenceJson.optString("recurrenceType"),
                recurrenceInterval = recurrenceJson.optInt("recurrenceInterval", 1),
                recurrenceDays = days,
                recurrenceEndDate = recurrenceJson.optString("recurrenceEndDate").takeIf { it.isNotBlank() },
            )
        } else null

        return V2ParseResult(
            title = json.optString("title", ""),
            date = json.optString("date").takeIf { it.isNotBlank() },
            time = json.optString("time").takeIf { it.isNotBlank() },
            priority = json.optInt("priority", -1).takeIf { it in 1..4 },
            tags = tags,
            recurrence = recurrence,
            description = json.optString("description").takeIf { it.isNotBlank() },
            confidence = json.optDouble("confidence", 0.0),
            detectedLanguage = json.optString("detectedLanguage").takeIf { it.isNotBlank() },
            duplicates = duplicates,
            traceId = json.optString("traceId").takeIf { it.isNotBlank() },
        )
    }
}
