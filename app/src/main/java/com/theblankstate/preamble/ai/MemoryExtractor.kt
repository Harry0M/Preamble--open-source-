package com.theblankstate.preamble.ai

import android.content.Context
import android.util.Log
import com.google.gson.JsonParser
import com.theblankstate.preamble.BuildConfig
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

    private fun buildProvider(): AiProvider? = AiProviderFactory.memory()

    private fun buildPrompt(): String {
        return """
You are a memory triage agent. Given a user message, decide what (if anything) to save to LONG-TERM MEMORY.

## Two tiers — pick the right one:
MEMORY = permanent, cross-chat user facts. Survives forever. Shared across ALL chats.
  → identity (name, age, gender, location, job, timezone)
  → stable preferences (language, theme, work style, likes/dislikes)
  → long-term goals (career, health, learning targets)
  → enduring interests (hobbies, passions, favorite topics)
  → relationships (family names, key people)

CONTEXT = ephemeral, only relevant to THIS conversation. Do NOT save these.
  → what user just asked about, current task discussion
  → today's plan, today's mood, one-off decisions
  → generic knowledge questions ("what is Asia", "scientific name of X")
  → greetings, confirmations, task additions, reminders

## Output
JSON array. Each element: {"key":<snake_case>,"value":<short phrase ≤60 chars>,"category":<identity|preference|goal|interest|relationship>,"confidence":<0.7-1.0>}
Return [] if nothing qualifies as MEMORY.
No markdown, no prose, no explanation. ONLY the JSON array.

## Rules
- ONLY emit MEMORY-tier facts about the USER themselves.
- Confidence ≥ 0.7 or skip.
- Keys: lowercase snake_case english. Values: user's language or english.
- NEVER save: task content, reminders, generic questions, greetings, ephemeral plans, knowledge queries.
- When unsure → []. Being conservative is correct.

## Examples
"mera naam Harry hai aur mujhe filmmaking pasand hai"
[{"key":"name","value":"Harry","category":"identity","confidence":0.95},{"key":"interest_filmmaking","value":"filmmaking","category":"interest","confidence":0.9}]

"remind me to pick up milk at 7pm" → []
"what is Asia" → []
"add gym task for tomorrow" → []
"how many days in a week" → []

"I'm a CS student at IIT Delhi, I work mostly at night"
[{"key":"education","value":"CS student at IIT Delhi","category":"identity","confidence":0.9},{"key":"work_time","value":"night owl","category":"preference","confidence":0.8}]

"my sister's name is Priya and she lives in Mumbai"
[{"key":"sister_name","value":"Priya","category":"relationship","confidence":0.9},{"key":"sister_location","value":"Mumbai","category":"relationship","confidence":0.85}]

"I prefer dark mode and minimal UI" → [{"key":"ui_preference","value":"dark mode, minimal","category":"preference","confidence":0.9}]
"call done" → []
"what should I focus on today" → []
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

        val sys = ChatMessage("system", buildPrompt())
        val usr = ChatMessage("user", userMessage)

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
            val key = fact.key.ifBlank { continue }
            val value = fact.value.ifBlank { continue }
            if (fact.confidence < 0.7f) continue
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
        val key: String,
        val value: String,
        val category: String,
        val confidence: Float,
    )

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
                val key = obj.get("key")?.asString?.trim() ?: return@mapNotNull null
                val value = obj.get("value")?.asString?.trim() ?: return@mapNotNull null
                val cat = obj.get("category")?.asString?.trim()?.lowercase() ?: "context"
                val conf = obj.get("confidence")?.asFloat ?: 0.7f
                val allowed = setOf("identity", "preference", "goal", "interest", "context", "relationship")
                val cleanCat = if (cat in allowed) cat else "context"
                Fact(key.lowercase(), value, cleanCat, conf)
            }
        }.getOrElse {
            Log.w(TAG, "parse failed: $slice", it)
            emptyList()
        }
    }
}
