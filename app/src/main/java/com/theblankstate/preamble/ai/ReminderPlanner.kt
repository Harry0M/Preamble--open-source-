package com.theblankstate.preamble.ai

import android.content.Context
import android.util.Log
import com.google.gson.JsonParser
import com.theblankstate.preamble.BuildConfig
import com.theblankstate.preamble.data.AiProcessLogEntity
import com.theblankstate.preamble.data.PreambleDatabase
import com.theblankstate.preamble.data.UserProfileStore
import com.theblankstate.preamble.repository.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * AI-driven decision engine: looks at user memory + completion patterns + time-of-week
 * and decides {nextHour, nextMinute, tone} for the next AI reminder.
 *
 * Uses a cheap model (Gemini Flash by default) so this is ~$0.0001 per decision.
 *
 * Output is bounded — model can pick:
 *   hour: 6..22
 *   minute: {0, 15, 30, 45}
 *   tone: empathetic | humorous | coach | blunt
 *   urgency: low | medium | high
 *
 * Falls back to deterministic defaults if model fails or AI mode is disabled.
 *
 * Default-on: `ai_reminder_planner_enabled` defaults to true. Manual time/tone in
 * settings still apply when planner is disabled.
 */
object ReminderPlanner {
    private const val TAG = "ReminderPlanner"
    const val PREF_PLANNER_ENABLED = "ai_reminder_planner_enabled"

    data class Decision(
        val hour: Int,
        val minute: Int,
        val tone: AiReminderEngine.Tone,
        val urgency: String,
        val rationale: String?,
    )

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
            .getBoolean(PREF_PLANNER_ENABLED, true)
    }

    suspend fun plan(context: Context): Decision = withContext(Dispatchers.IO) {
        if (!isEnabled(context)) return@withContext fallback(context)

        val provider = buildCheapProvider() ?: return@withContext fallback(context)
        val signals = buildSignals(context)
        val systemPrompt = buildSystemPrompt()
        val userPrompt = buildUserPrompt(signals)

        val logger = AiProcessLogger.get(context)
        val t0 = System.currentTimeMillis()
        val response = runCatching {
            provider.chat(
                listOf(ChatMessage("system", systemPrompt), ChatMessage("user", userPrompt)),
                emptyList()
            )
        }
        val dt = System.currentTimeMillis() - t0

        val raw = response.getOrNull()?.text?.trim().orEmpty()
        val decision = parseDecision(raw) ?: run {
            logger.log(
                op = "reminder_plan",
                provider = provider.name,
                model = null,
                input = userPrompt,
                output = raw,
                toolCalls = null,
                durationMs = dt,
                success = false,
                error = "parse failed",
            )
            return@withContext fallback(context)
        }

        logger.log(
            op = "reminder_plan",
            provider = provider.name,
            model = null,
            input = userPrompt,
            output = raw,
            toolCalls = null,
            durationMs = dt,
            success = true,
            thought = "Picked ${decision.hour}:${"%02d".format(decision.minute)} · ${decision.tone.key} · ${decision.urgency}. ${decision.rationale ?: ""}".take(400),
        )
        decision
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun fallback(context: Context): Decision {
        val sp = context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
        val h = sp.getInt(AiReminderEngine.PREF_HOUR, 10)
        val m = sp.getInt(AiReminderEngine.PREF_MINUTE, 0)
        val toneFromSetting = AiReminderEngine.getTone(context)
        val resolvedTone = if (toneFromSetting == AiReminderEngine.Tone.RANDOM) {
            AiReminderEngine.Tone.values().filter { it != AiReminderEngine.Tone.RANDOM }.random()
        } else toneFromSetting
        return Decision(h, m, resolvedTone, "medium", "Fallback: planner disabled or model failed")
    }

    private fun buildCheapProvider(): AiProvider? = AiProviderFactory.memory()

    private suspend fun buildSignals(context: Context): String {
        val today = TaskRepository.todayString()
        val dao = PreambleDatabase.getInstance(context).taskDao()
        val pending = dao.getPendingTasksForDate(today).filter { !it.isSyncing }
        val now = System.currentTimeMillis()
        val day24 = now - 86_400_000L
        val day3 = now - 3 * 86_400_000L

        val allTasks = dao.getAllTasks().filter { !it.isSyncing && it.parentTaskId == null }
        val completedLast24 = allTasks.count { it.isCompleted && (it.completedTimestamp ?: 0L) >= day24 }
        val completedLast72 = allTasks.count { it.isCompleted && (it.completedTimestamp ?: 0L) >= day3 }

        // Past-week reminder log — what worked, what user dismissed quickly
        val logDao = PreambleDatabase.getInstance(context).aiProcessLogDao()
        val recentReminders = runCatching {
            getRecentReminderLogs(logDao)
        }.getOrDefault(emptyList())
        val pastReminderSummary = if (recentReminders.isEmpty()) "(no past reminders)"
        else recentReminders.takeLast(5).joinToString(" | ") {
            val ts = SimpleDateFormat("MMM d HH:mm", Locale.getDefault()).format(Date(it.timestamp))
            "$ts/${it.thought ?: ""}"
        }

        val cal = Calendar.getInstance()
        val dow = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "Monday"; Calendar.TUESDAY -> "Tuesday"
            Calendar.WEDNESDAY -> "Wednesday"; Calendar.THURSDAY -> "Thursday"
            Calendar.FRIDAY -> "Friday"; Calendar.SATURDAY -> "Saturday"
            else -> "Sunday"
        }
        val nowHM = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        val profile = runCatching { UserProfileStore.load(context) }.getOrNull()
        val mem = runCatching { AiMemoryRepository.get(context).buildPromptSnapshot(maxChars = 500) }.getOrNull()
        val name = profile?.name ?: "user"

        val sb = StringBuilder()
        sb.appendLine("Day: $dow, time now: $nowHM")
        sb.appendLine("User: $name (load=${profile?.tasksPerDay?.label ?: "unknown"})")
        sb.appendLine("Pending today: ${pending.size}")
        sb.appendLine("Completed last 24h: $completedLast24")
        sb.appendLine("Completed last 72h: $completedLast72")
        sb.appendLine("Recent reminders (rationale only): $pastReminderSummary")
        if (!mem.isNullOrBlank()) {
            sb.appendLine("Memory snapshot: $mem")
        }
        return sb.toString().trimEnd()
    }

    private suspend fun getRecentReminderLogs(dao: com.theblankstate.preamble.data.AiProcessLogDao): List<AiProcessLogEntity> {
        return runCatching {
            dao.observeByOp("reminder", limit = 10).first()
        }.getOrDefault(emptyList())
    }

    private fun buildSystemPrompt(): String {
        return """
You decide WHEN and HOW the AI should remind a user about pending tasks tomorrow.
You receive: day, time, pending count, recent completion rate, past reminder log, user memory.

Output ONLY a JSON object — no prose, no markdown:
{
  "hour": <integer 6..22>,
  "minute": <integer one of: 0, 15, 30, 45>,
  "tone": <one of: "empathetic", "humorous", "coach", "blunt">,
  "urgency": <one of: "low", "medium", "high">,
  "rationale": <one short sentence — why this choice>
}

Rules:
- If user is highly productive last 24-72h → softer tone, later time.
- If user has piled up overdue tasks → coach or blunt, earlier time, high urgency.
- Vary tone vs past reminders — don't repeat the same tone 3 days running.
- If pending count == 0, still pick (next-day reminder may still trigger if tasks added).
- Match tone to time-of-day: early morning → coach, late night → empathetic.
- Hour is in 24h. Avoid 23:00-05:59 (sleep window).

Examples:

Signals: "Day: Monday, time: 22:00, Pending: 8, Completed24h: 0, Completed72h: 1"
Output: {"hour":7,"minute":30,"tone":"coach","urgency":"high","rationale":"Pile-up + zero recent completion — push hard early."}

Signals: "Day: Friday, time: 19:00, Pending: 2, Completed24h: 6, Completed72h: 14"
Output: {"hour":11,"minute":0,"tone":"humorous","urgency":"low","rationale":"Crushing it — a fun ping mid-morning is enough."}
        """.trimIndent()
    }

    private fun buildUserPrompt(signals: String): String {
        return "Signals:\n$signals\n\nDecide tomorrow's reminder. Respond with the JSON object only."
    }

    private fun parseDecision(raw: String): Decision? {
        if (raw.isBlank()) return null
        val cleaned = raw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val slice = cleaned.substring(start, end + 1)
        return runCatching {
            val obj = JsonParser.parseString(slice).asJsonObject
            val h = obj.get("hour").asInt.coerceIn(6, 22)
            val rawMin = obj.get("minute").asInt
            val m = listOf(0, 15, 30, 45).minByOrNull { kotlin.math.abs(it - rawMin) } ?: 0
            val toneStr = obj.get("tone").asString.lowercase()
            val tone = AiReminderEngine.Tone.values().firstOrNull { it.key == toneStr }
                ?: AiReminderEngine.Tone.EMPATHETIC
            val urgency = obj.get("urgency")?.asString?.lowercase() ?: "medium"
            val rationale = obj.get("rationale")?.asString
            Decision(h, m, tone, urgency, rationale)
        }.onFailure { Log.w(TAG, "Decision parse failed: $slice", it) }.getOrNull()
    }
}
