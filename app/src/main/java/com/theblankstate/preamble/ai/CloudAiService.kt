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
        tasks: List<com.theblankstate.preamble.data.Task> = emptyList(),
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

            // Send local Room DB tasks so Cloud Function needs zero Firestore reads
            if (tasks.isNotEmpty()) {
                val tasksArr = JSONArray()
                tasks.take(24).forEach { t ->
                    tasksArr.put(JSONObject().apply {
                        put("title", t.title)
                        put("createdDate", t.createdDate ?: "")
                        put("deadlineTime", t.deadlineTime ?: "")
                        put("priority", t.priority ?: 0)
                        put("isCompleted", t.isCompleted)
                        put("isSyncing", t.isSyncing)
                    })
                }
                put("tasks", tasksArr)
            }
        }

        val request = Request.Builder()
            .url("$BASE_URL/aiChat")
            .addHeader("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        Log.i("Preamble_AiChat", "[REQUEST] conversationId=$conversationId model=${model ?: "default"} text=\"$message\"")

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
        context: android.content.Context,
        rawText: String,
        subtaskIntensity: Int = 1,
        isNotificationEdit: Boolean = false,
        tasks: List<com.theblankstate.preamble.data.Task> = emptyList(),
    ): ParseTaskResult? = withContext(Dispatchers.IO) {
        val token = runCatching { getAuthToken() }.getOrNull() ?: return@withContext null

        val prefs = context.getSharedPreferences("preamble_prefs", android.content.Context.MODE_PRIVATE)
        val langString = prefs.getString("profile_preferred_languages", null)

        val body = JSONObject().apply {
            put("text", rawText)
            put("subtaskIntensity", subtaskIntensity)
            put("isNotificationEdit", isNotificationEdit)
            put("appVersionCode", com.theblankstate.preamble.BuildConfig.VERSION_CODE)

            if (!langString.isNullOrBlank()) {
                put("preferredLanguages", org.json.JSONArray(langString.split(",")))
            }

            // Send local Room DB tasks so Cloud Function needs zero Firestore reads
            if (tasks.isNotEmpty()) {
                val tasksArr = org.json.JSONArray()
                val limit = if (isNotificationEdit) 40 else 20
                tasks.take(limit).forEach { t ->
                    tasksArr.put(JSONObject().apply {
                        put("title", t.title)
                        put("createdDate", t.createdDate ?: "")
                        put("deadlineTime", t.deadlineTime ?: "")
                        put("priority", t.priority ?: 0)
                        put("isCompleted", t.isCompleted)
                        put("isSyncing", t.isSyncing)
                    })
                }
                put("tasks", tasksArr)
            }
        }

        Log.i("Preamble_TaskParser", "[REQUEST] text=\"$rawText\" subtaskIntensity=$subtaskIntensity isNotificationEdit=$isNotificationEdit appVersionCode=${com.theblankstate.preamble.BuildConfig.VERSION_CODE} preferredLanguages=${langString ?: "auto"} taskCount=${tasks.size}")

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
            
            Log.i("Preamble_TaskParser", "[RESPONSE] model=${json.optString("model", "unknown")} toolCalls=${toolCalls.size} tools=[${toolCalls.joinToString { "${it.name}(${it.args})" }}]")

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

    /**
     * Request an AI day plan through the Cloud Function — used by Plan-My-Day (Track A).
     * Mirrors [parseTask]'s one-shot request/parse shape but is pointed at `aiPlanDay`
     * and runs under the credit-charged path.
     *
     * The returned [PlanDayResult.Success.assignments] are **untrusted** raw `(id, time)`
     * pairs straight from the model; the caller converts them to `RawAssignment`s and feeds
     * them to the pure client-side `ScheduleNormalizer`, which is the authority on correctness.
     *
     * @return [PlanDayResult.Success] with assignments + model on success,
     *         [PlanDayResult.InsufficientCredits] for HTTP 402 / `success:false` with that error,
     *         or `null` on the daily-limit (429), network, or parse error.
     */
    suspend fun planDay(
        schedulable: List<PlanTaskDto>,
        fixed: List<PlanFixedDto>,
        date: String,
        dayStart: String,
        dayEnd: String,
        dayOfWeek: String = "",
        nowTime: String = "",
        priorAssignments: List<PlanAssignmentDto>? = null,
        adjustment: String? = null,
        allowRescheduleFixed: Boolean = false,
    ): PlanDayResult? = withContext(Dispatchers.IO) {
        val token = runCatching { getAuthToken() }.getOrNull() ?: return@withContext null

        val schedulableJson = JSONArray()
        for (t in schedulable) {
            schedulableJson.put(JSONObject().apply {
                put("id", t.id)
                // Title/description/tags are passed UNMODIFIED so any language/script is preserved (Req 17.2).
                put("title", t.title)
                put("priority", t.priority)
                t.estimatedMinutes?.let { put("estimatedMinutes", it) }
                if (!t.description.isNullOrBlank()) put("description", t.description)
                if (!t.tags.isNullOrBlank()) put("tags", t.tags)
            })
        }
        val fixedJson = JSONArray()
        for (f in fixed) {
            fixedJson.put(JSONObject().apply {
                put("start", f.start)
                if (!f.end.isNullOrBlank()) put("end", f.end)
            })
        }

        // Planning context so the model is time-aware (Req 13.5).
        val contextJson = JSONObject().apply {
            if (dayOfWeek.isNotBlank()) put("dayOfWeek", dayOfWeek)
            if (nowTime.isNotBlank()) put("nowTime", nowTime)
        }

        val body = JSONObject().apply {
            put("schedulable", schedulableJson)
            put("fixed", fixedJson)
            put("date", date)
            // dayStart is the Effective_Window_Start the caller computed (Req 13.5).
            put("dayStart", dayStart)
            put("dayEnd", dayEnd)
            put("context", contextJson)
            // Conversational replanning: echo the prior proposal + adjustment text (Req 15.2).
            if (!priorAssignments.isNullOrEmpty()) {
                val priorJson = JSONArray()
                for (a in priorAssignments) {
                    priorJson.put(JSONObject().apply {
                        put("id", a.id)
                        put("time", a.time)
                    })
                }
                put("priorAssignments", priorJson)
            }
            if (!adjustment.isNullOrBlank()) put("adjustment", adjustment)
            if (allowRescheduleFixed) put("allowRescheduleFixed", true)
            // Weather is RESERVED for a future iteration; MVP sends null and never calls a weather API (Req 14.2).
            put("weather", JSONObject.NULL)
            put("appVersionCode", com.theblankstate.preamble.BuildConfig.VERSION_CODE)
        }

        val request = Request.Builder()
            .url("$BASE_URL/aiPlanDay")
            .addHeader("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        try {
            val response = client.newCall(request).execute()

            // Insufficient credits — distinct, recoverable signal (Req 5.5).
            if (response.code == 402) {
                Log.w(TAG, "planDay insufficient credits (402)")
                return@withContext PlanDayResult.InsufficientCredits
            }

            // Daily limit — map to existing daily-limit message, treat as failure here.
            if (response.code == 429) {
                Log.w(TAG, "planDay daily limit reached (429)")
                return@withContext null
            }

            if (!response.isSuccessful) {
                Log.w(TAG, "planDay failed: ${response.code}")
                return@withContext null
            }

            val json = JSONObject(response.body!!.string())
            if (!json.optBoolean("success", false)) {
                val error = json.optString("error")
                if (error == "INSUFFICIENT_CREDITS") {
                    return@withContext PlanDayResult.InsufficientCredits
                }
                Log.w(TAG, "planDay error: $error")
                return@withContext null
            }

            val assignmentsArr = json.optJSONArray("assignments") ?: JSONArray()
            val assignments = (0 until assignmentsArr.length()).map { i ->
                val a = assignmentsArr.getJSONObject(i)
                PlanAssignmentDto(
                    id = a.optString("id"),
                    time = a.optString("time"),
                    reason = a.optString("reason").takeIf { it.isNotBlank() }
                )
            }

            PlanDayResult.Success(
                assignments = assignments,
                briefing = json.optString("briefing").takeIf { it.isNotBlank() },
                recommendation = json.optString("recommendation").takeIf { it.isNotBlank() },
                model = json.optString("model", ""),
            )
        } catch (e: Exception) {
            Log.e(TAG, "planDay request failed", e)
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
                    Log.i("Preamble_AiChat", "  ToolCall: ${toolCalls.joinToString { "${it.name}(${it.args})" }}")
                    onToolCalls(toolCalls)
                }
                "done" -> {
                    val renderBlocks = json.optJSONArray("renderBlocks")
                    val modelName = json.optString("model", "")
                    val remaining = json.optInt("tokensRemaining", -1)
                    Log.i("Preamble_AiChat", "[RESPONSE DONE] model=$modelName tokensRemaining=$remaining")
                    onDone(CloudChatResult(
                        tokensRemaining = remaining,
                        model = modelName,
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

/** Request DTO: a task eligible for auto-scheduling. */
data class PlanTaskDto(
    val id: String,
    val title: String,
    val priority: Int,
    val estimatedMinutes: Int? = null,
    /** Optional detail passed through unmodified so the model can infer a realistic estimate (Req 16.1, 17.2). */
    val description: String? = null,
    /** Optional comma-separated tags, passed through unmodified to preserve any language/script (Req 16.1, 17.2). */
    val tags: String? = null,
)

/** Request DTO: an immovable current-day item. A point commitment has [end] == null. */
data class PlanFixedDto(val start: String, val end: String? = null)

/** Response DTO: one untrusted `(id, time)` pair exactly as the model proposed it. */
data class PlanAssignmentDto(val id: String, val time: String, val reason: String? = null)

/** Outcome of a [CloudAiService.planDay] call. `null` is reserved for network/parse/limit errors. */
sealed interface PlanDayResult {
    /** Server returned a (still untrusted) proposal to be validated by the client normalizer. */
    data class Success(
        val assignments: List<PlanAssignmentDto>,
        val briefing: String? = null,
        val recommendation: String? = null,
        val model: String
    ) : PlanDayResult

    /** Server rejected the request because the user has insufficient AI credits (Req 5.5). */
    data object InsufficientCredits : PlanDayResult
}
