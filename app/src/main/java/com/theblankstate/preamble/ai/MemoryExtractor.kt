package com.theblankstate.preamble.ai

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.theblankstate.preamble.BuildConfig
import com.theblankstate.preamble.data.AiMemoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Extracts durable user facts from free-form chat/voice input.
 * Runs asynchronously in the background so it never blocks the user flow.
 *
 * Uses a cheap model (Gemini Flash by default) configured via BuildConfig.AI_MEMORY_*
 * so extraction cost is decoupled from the primary chat provider.
 *
 * Categories:
 *  - identity   : name, age, gender, location, role
 *  - preference : "loves X", "prefers Y", dislikes
 *  - goal       : aspirations, targets, deadlines
 *  - interest   : hobbies, topics they care about
 *  - context    : work/study/project context
 */
object MemoryExtractor {
    private const val TAG = "MemoryExtractor"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()

    private fun buildProvider(): AiProvider? = AiProviderFactory.memory()

    private fun buildPrompt(): String {
        return """
You are Preamble's long-term memory controller. Decide whether the latest USER message should change permanent memory.

Memory is durable, cross-chat user knowledge. It should help future replies without the user repeating themselves.

Save only facts that are:
- stable beyond this chat or likely useful for many future chats
- about the user or the user's close relationships
- explicitly stated by the user, not inferred from the assistant
- safe to store

Allowed categories:
- identity: name, age range, role, job/study, city/region, timezone, language
- preference: communication style, UI/style preferences, likes/dislikes, work style
- goal: long-term career, health, study, habit, or learning goals
- interest: enduring hobbies, favorite topics, recurring passions
- relationship: close people and their stable details
- context: stable background such as "night-shift worker", "preparing for UPSC", "runs a small shop"

Never save:
- passwords, OTPs, API keys, auth tokens, card/bank/government IDs, private exact addresses
- medical/legal/financial secrets unless user explicitly asks to remember a non-sensitive preference
- one-off tasks, reminders, shopping items, today's plan, current mood, greetings, confirmations
- generic knowledge questions or facts not about the user
- guesses, stereotypes, diagnoses, or anything the user did not clearly say

Use existing memory to avoid duplicates and handle corrections:
- If the message corrects an old fact, upsert the same key with the new value.
- If the user asks to forget/remove something, emit a delete op for the matching key.
- If a new fact overlaps an existing key, prefer updating that key over creating a near-duplicate.

Output ONLY a JSON array. No markdown, no prose.
Each item:
{"op":"upsert","key":"snake_case","value":"short phrase <=80 chars","category":"identity|preference|goal|interest|relationship|context","confidence":0.7-1.0}
or:
{"op":"delete","key":"snake_case","value":"","category":"context","confidence":0.8-1.0}

Return [] when nothing should change. Be conservative.
        """.trimIndent()
    }

    /**
     * Enqueue an extraction job. Non-blocking.
     */
    fun extractAsync(context: Context, userMessage: String) {
        if (userMessage.isBlank() || userMessage.length < 12) return
        scope.launch {
            runCatching { extract(context, userMessage) }
                .onFailure { Log.w(TAG, "extract failed", it) }
        }
    }

    /**
     * Blocking extraction. Exposed for tests / manual triggers.
     */
    suspend fun extract(context: Context, userMessage: String): Int = withContext(Dispatchers.IO) {
        val provider = buildProvider() ?: return@withContext 0
        val repo = AiMemoryRepository.get(context)
        val logger = AiProcessLogger.get(context)

        val existing = repo.snapshot(limit = 40)
        val sys = ChatMessage("system", buildPrompt())
        val usr = ChatMessage("user", buildUserPayload(existing, userMessage))

        val t0 = System.currentTimeMillis()
        val response = try {
            provider.chat(listOf(sys, usr), emptyList())
        } catch (e: Exception) {
            logger.log(
                op = AiProcessLogger.OP_EXTRACT_MEMORY,
                provider = provider.name,
                model = null,
                input = userMessage,
                output = null,
                toolCalls = null,
                durationMs = System.currentTimeMillis() - t0,
                success = false,
                error = e.message,
            )
            return@withContext 0
        }
        val dt = System.currentTimeMillis() - t0

        val raw = response.text?.trim().orEmpty()
        val facts = parseJsonArray(raw)

        var saved = 0
        for (fact in facts) {
            val key = cleanKey(fact.key).ifBlank { continue }
            val value = cleanValue(fact.value)
            if (fact.confidence < 0.7f) continue
            if (fact.op == "delete") {
                if (repo.deleteByKey(key)) saved++
                continue
            }
            if (value.isBlank() || isLikelySensitiveMemory(key, value)) continue
            val saved0 = repo.save(key, value, fact.category, fact.confidence, source = "chat")
            if (saved0 != null) saved++
        }

        val thought = if (facts.isEmpty()) "No durable facts extracted."
        else "Saved $saved: " + facts.joinToString(", ") { "${it.key}=${it.value}" }

        logger.log(
            op = AiProcessLogger.OP_EXTRACT_MEMORY,
            provider = provider.name,
            model = null,
            input = userMessage,
            output = raw.take(800),
            toolCalls = null,
            durationMs = dt,
            success = true,
            thought = thought,
        )
        saved
    }

    private data class Fact(
        val op: String,
        val key: String,
        val value: String,
        val category: String,
        val confidence: Float,
    )

    private fun buildUserPayload(existing: List<AiMemoryEntity>, userMessage: String): String {
        val rows = existing.take(40).map {
            mapOf(
                "key" to it.key.take(64),
                "value" to it.value.take(140),
                "category" to it.category.take(32),
            )
        }
        return gson.toJson(
            mapOf(
                "existing_memory" to rows,
                "latest_user_message" to userMessage,
            )
        )
    }

    private fun parseJsonArray(raw: String): List<Fact> {
        if (raw.isBlank()) return emptyList()
        // Strip common Markdown fences if the model wraps JSON in them
        val cleaned = raw
            .removePrefix("```json").removePrefix("```").removeSuffix("```")
            .trim()
        val start = cleaned.indexOf('[')
        val end = cleaned.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        val slice = cleaned.substring(start, end + 1)
        return runCatching {
            val arr = JsonParser.parseString(slice).asJsonArray
            arr.mapNotNull { el ->
                val obj = el.asJsonObject
                val opEl = obj.get("op")
                val opRaw = if (opEl == null || opEl.isJsonNull) "upsert" else opEl.asString.trim().lowercase()
                val op = if (opRaw == "delete") "delete" else "upsert"
                val keyEl = obj.get("key")
                val key = if (keyEl == null || keyEl.isJsonNull) return@mapNotNull null else keyEl.asString.trim()
                val valueEl = obj.get("value")
                val value = if (valueEl == null || valueEl.isJsonNull) "" else valueEl.asString.trim()
                val catEl = obj.get("category")
                val cat = if (catEl == null || catEl.isJsonNull) "context" else catEl.asString.trim().lowercase()
                val confEl = obj.get("confidence")
                val conf = if (confEl == null || confEl.isJsonNull) 0.7f else confEl.asFloat
                val allowed = setOf("identity", "preference", "goal", "interest", "context", "relationship")
                val cleanCat = if (cat in allowed) cat else "context"
                Fact(op, key.lowercase(), value, cleanCat, conf)
            }
        }.getOrElse {
            Log.w(TAG, "parse failed: $slice", it)
            emptyList()
        }
    }

    private fun cleanKey(value: String): String =
        value.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
            .take(64)

    private fun cleanValue(value: String): String =
        value.replace(Regex("\\s+"), " ")
            .trim()
            .take(120)

    private fun isLikelySensitiveMemory(key: String, value: String): Boolean {
        val combined = "$key $value".lowercase()
        if (Regex("\\b(password|passcode|otp|one[_\\s-]?time|pin|api[_\\s-]?key|secret|token|bearer|private[_\\s-]?key)\\b").containsMatchIn(combined)) return true
        if (Regex("\\b(card|credit|debit|cvv|cvc|bank|account|ifsc|routing|ssn|aadhaar|aadhar|pan card|passport)\\b").containsMatchIn(combined)) return true
        if (Regex("\\b\\d{12,19}\\b").containsMatchIn(value.replace(Regex("[\\s-]"), ""))) return true
        if (Regex("AIza[0-9A-Za-z_-]{20,}").containsMatchIn(value)) return true
        if (Regex("sk-[0-9A-Za-z_-]{20,}").containsMatchIn(value)) return true
        return false
    }
}
