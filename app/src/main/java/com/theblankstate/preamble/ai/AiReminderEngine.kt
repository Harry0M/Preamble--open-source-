package com.theblankstate.preamble.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.theblankstate.preamble.BuildConfig
import com.theblankstate.preamble.MainActivity
import com.theblankstate.preamble.R
import com.theblankstate.preamble.data.PreambleDatabase
import com.theblankstate.preamble.data.Task
import com.theblankstate.preamble.data.UserProfileStore
import com.theblankstate.preamble.repository.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Core logic for the personal AI reminder engine.
 * Called from AiReminderReceiver (AlarmManager-based, daily).
 *
 * Tone modes (stored in preamble_prefs as "ai_reminder_tone"):
 *   empathetic | humorous | coach | blunt | random
 */
object AiReminderEngine {

    private const val TAG = "AiReminderEngine"
    const val CHANNEL_ID = "preamble_ai_reminder"
    private const val NOTIFICATION_ID = 8001
    const val PREF_TONE = "ai_reminder_tone"
    const val PREF_ENABLED = "ai_reminder_enabled"
    const val PREF_HOUR = "ai_reminder_hour"
    const val PREF_MINUTE = "ai_reminder_minute"

    enum class Tone(val key: String, val label: String, val emoji: String) {
        EMPATHETIC("empathetic", "Caring", "🤗"),
        HUMOROUS("humorous", "Funny", "😄"),
        COACH("coach", "Coach", "💪"),
        BLUNT("blunt", "Blunt", "⚡"),
        RANDOM("random", "Surprise me", "🎲"),
    }

    fun getTone(context: Context): Tone {
        val key = context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
            .getString(PREF_TONE, Tone.RANDOM.key) ?: Tone.RANDOM.key
        return Tone.values().firstOrNull { it.key == key } ?: Tone.RANDOM
    }

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
            .getBoolean(PREF_ENABLED, true)
    }

    suspend fun run(context: Context) = withContext(Dispatchers.IO) {
        if (!isEnabled(context)) {
            Log.d(TAG, "AI reminders disabled — skip")
            return@withContext
        }

        if (BuildConfig.AI_API_KEY.isBlank()) {
            Log.d(TAG, "No AI key — skip")
            return@withContext
        }

        val tasks = getScoredTasks(context)
        if (tasks.isEmpty()) {
            Log.d(TAG, "No incomplete tasks — skip")
            return@withContext
        }

        val tone = resolveTone(getTone(context))
        val profile = runCatching { UserProfileStore.load(context) }.getOrNull()
        val memoryRepo = AiMemoryRepository.get(context)
        val memBlock = runCatching { memoryRepo.buildPromptSnapshot() }.getOrNull()
        val logger = AiProcessLogger.get(context)

        val topTasks = tasks.take(5)
        val systemPrompt = buildReminderSystemPrompt(tone, profile?.name, memBlock)
        val userPrompt = buildTasksPrompt(topTasks)

        val provider = buildProvider()
        val t0 = System.currentTimeMillis()
        val result = runCatching {
            provider?.chat(
                listOf(ChatMessage("system", systemPrompt), ChatMessage("user", userPrompt)),
                emptyList()
            )
        }
        val dt = System.currentTimeMillis() - t0

        val text = result.getOrNull()?.text?.trim()
        if (text.isNullOrBlank()) {
            val errMsg = result.exceptionOrNull()?.message ?: "no text in response"
            Log.w(TAG, "Reminder gen failed: $errMsg")
            logger.log(
                op = "reminder",
                provider = provider?.name ?: "none",
                model = null,
                input = userPrompt,
                output = null,
                toolCalls = null,
                durationMs = dt,
                success = false,
                error = errMsg,
            )
            // Fallback to static reminder
            postNotification(context, buildFallbackReminder(topTasks.first(), profile?.name, tone))
            return@withContext
        }

        logger.log(
            op = "reminder",
            provider = provider?.name ?: "none",
            model = null,
            input = userPrompt,
            output = text,
            toolCalls = null,
            durationMs = dt,
            success = true,
            thought = "Tone=$tone, tasks=${topTasks.size}",
        )

        // Split first line as title, rest as body
        val lines = text.lines().filter { it.isNotBlank() }
        val title = lines.firstOrNull()?.take(80) ?: "Preamble AI"
        val body = if (lines.size > 1) lines.drop(1).joinToString(" ").take(300) else text.take(300)

        postNotification(context, ReminderContent(title, body))
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    data class ReminderContent(val title: String, val body: String)

    private fun buildProvider(): AiProvider? = AiProviderFactory.memory()

    private suspend fun getScoredTasks(context: Context): List<Task> {
        val today = TaskRepository.todayString()
        val dao = PreambleDatabase.getInstance(context).taskDao()
        val allIncomplete = dao.getPendingTasksForDate(today)
        if (allIncomplete.isEmpty()) return emptyList()

        val todayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayMs = todayFmt.parse(today)?.time ?: System.currentTimeMillis()

        return allIncomplete
            .filter { !it.isSyncing }
            .map { task ->
                val taskDate = runCatching { todayFmt.parse(task.createdDate)?.time ?: todayMs }.getOrDefault(todayMs)
                val stalenessDays = ((todayMs - taskDate) / 86_400_000L).coerceIn(0, 14).toInt()
                val priorityScore = task.priority * 3
                val stalenessScore = stalenessDays * 2
                val rolloverBonus = if (task.recurrenceType == "rollover") 4 else 0
                val overdueBonus = if (taskDate < todayMs) 6 else 0
                Pair(task, priorityScore + stalenessScore + rolloverBonus + overdueBonus)
            }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun resolveTone(tone: Tone): Tone {
        return if (tone == Tone.RANDOM) Tone.values()
            .filter { it != Tone.RANDOM }
            .random()
        else tone
    }

    private fun buildReminderSystemPrompt(tone: Tone, userName: String?, memBlock: String?): String {
        val sb = StringBuilder()
        sb.appendLine("You write mobile push notification reminders for a productivity app called Preamble.")
        sb.appendLine("You must remind the user about their incomplete tasks.")
        sb.appendLine()
        if (!memBlock.isNullOrBlank()) {
            sb.appendLine(memBlock.trimEnd())
            sb.appendLine()
        }
        sb.appendLine("TONE: ${tone.key.uppercase()}")
        when (tone) {
            Tone.EMPATHETIC -> {
                sb.appendLine("Be warm, caring, and supportive. Acknowledge the user might be busy or tired.")
                sb.appendLine("Use gentle encouragement. Never pressure. Make them feel understood.")
                sb.appendLine("Example style: 'Hey ${userName ?: "friend"}, whenever you get a moment...'")
            }
            Tone.HUMOROUS -> {
                sb.appendLine("Be witty, fun, and a little cheeky. Use a joke, pun, or playful jab related to the task.")
                sb.appendLine("Make the user laugh but still feel the nudge. Keep humor friendly, not sarcastic.")
                sb.appendLine("Example style: 'Your tasks are giving you the side-eye... 👀'")
            }
            Tone.COACH -> {
                sb.appendLine("Be energetic, motivating, direct. Talk like a personal trainer pushing them forward.")
                sb.appendLine("Use action language. No excuses. Celebrate small wins. Drive urgency.")
                sb.appendLine("Example style: 'Come on! These tasks won't complete themselves!'")
            }
            Tone.BLUNT -> {
                sb.appendLine("Be short, direct, no fluff. State what's undone. No emoji overload. No softening.")
                sb.appendLine("Example style: '3 tasks unfinished. Get to it.'")
            }
            else -> {}
        }
        sb.appendLine()
        sb.appendLine("FORMAT:")
        sb.appendLine("Line 1: Short title (max 60 chars) — catchy, unique, NOT generic like 'Task Reminder'.")
        sb.appendLine("Line 2 onward: Body text (max 200 chars) — reference the actual task names naturally.")
        sb.appendLine("MUST reference at least one real task name from the list the user gives you.")
        sb.appendLine("NEVER say 'click here', 'tap here', 'open app'. Never generic 'You have X tasks'.")
        sb.appendLine("Keep total under 300 chars across both lines. Be creative. Vary the message every time.")
        sb.appendLine("Respond in the SAME language the task titles are in (Hindi → Hindi, English → English, Hinglish → Hinglish).")
        return sb.toString().trimEnd()
    }

    private fun buildTasksPrompt(tasks: List<Task>): String {
        val today = TaskRepository.todayString()
        val todayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayMs = todayFmt.parse(today)?.time ?: System.currentTimeMillis()
        val sb = StringBuilder("Incomplete tasks:\n")
        tasks.forEachIndexed { i, t ->
            val taskDate = runCatching { todayFmt.parse(t.createdDate)?.time ?: todayMs }.getOrDefault(todayMs)
            val days = ((todayMs - taskDate) / 86_400_000L).toInt()
            val age = when {
                days == 0 -> "today"
                days == 1 -> "since yesterday"
                else -> "$days days old"
            }
            val pri = when (t.priority) { 3 -> " [HIGH]"; 2 -> " [MED]"; else -> "" }
            sb.appendLine("${i + 1}. \"${t.title}\" ($age)$pri")
        }
        sb.appendLine("\nWrite the reminder notification now.")
        return sb.toString().trimEnd()
    }

    private fun buildFallbackReminder(task: Task, userName: String?, tone: Tone): ReminderContent {
        val name = userName?.let { ", $it" } ?: ""
        return when (tone) {
            Tone.HUMOROUS -> ReminderContent(
                "👀 Your tasks are still waiting$name",
                "\"${task.title}\" has been giving you the side-eye all day."
            )
            Tone.COACH -> ReminderContent(
                "Let's GO$name! 💪",
                "\"${task.title}\" is still unfinished. You've got this — one task at a time!"
            )
            Tone.BLUNT -> ReminderContent(
                "Still pending$name.",
                "\"${task.title}\" not done. Open app."
            )
            else -> ReminderContent(
                "Hey$name 👋, a gentle nudge",
                "\"${task.title}\" is still waiting for you whenever you're ready."
            )
        }
    }

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(
            CHANNEL_ID,
            "AI Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Personalized AI-generated task reminders"
            enableVibration(true)
            setShowBadge(true)
        }
        nm.createNotificationChannel(ch)
    }

    private fun postNotification(context: Context, content: ReminderContent) {
        ensureChannel(context)
        val openIntent = PendingIntent.getActivity(
            context, NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(content.title)
            .setContentText(content.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.body))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "Reminder posted: ${content.title}")
    }
}
