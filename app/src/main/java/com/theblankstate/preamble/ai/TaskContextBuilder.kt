package com.theblankstate.preamble.ai

import android.content.Context
import com.theblankstate.preamble.data.PreambleDatabase
import com.theblankstate.preamble.data.Task
import com.theblankstate.preamble.repository.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Builds a token-budgeted, tiered TASK CONTEXT block injected into the system prompt.
 * Distinct from AiMemoryRepository — this is volatile state (today's tasks, deadlines),
 * not durable user knowledge.
 *
 * Tiers (filled until [maxChars] hit):
 *   A: today's open tasks (always full, never truncated)
 *   B: overdue / rollover tasks (always full)
 *   C: long-pending stale work (>3 days) — top 5 by priority
 *   D: completed in last 24h — title-only summary
 *
 * Cached per process for [cacheTtlMs] to avoid rebuilding on every chat turn.
 */
object TaskContextBuilder {

    private const val DEFAULT_MAX_CHARS = 1200
    private const val CACHE_TTL_MS = 60_000L // 60s

    @Volatile private var cachedContext: String? = null
    @Volatile private var cachedAt: Long = 0L
    @Volatile private var cachedTasksHash: Int = 0

    /**
     * Cache invalidation hook — called from TaskRepository on any task mutation.
     */
    fun invalidate() {
        cachedContext = null
        cachedAt = 0L
        cachedTasksHash = 0
    }

    suspend fun build(context: Context, maxChars: Int = DEFAULT_MAX_CHARS): String? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = cachedContext
        if (cached != null && now - cachedAt < CACHE_TTL_MS) return@withContext cached

        val today = TaskRepository.todayString()
        val dao = PreambleDatabase.getInstance(context).taskDao()
        val pendingToday = dao.getPendingTasksForDate(today).filter { !it.isSyncing }
        val allTasks = dao.getAllTasks().filter { !it.isSyncing && it.parentTaskId == null }

        if (pendingToday.isEmpty() && allTasks.isEmpty()) return@withContext null

        val todayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayMs = todayFmt.parse(today)?.time ?: now

        // Bucket tasks
        // tierA = everything the DAO says belongs to today:
        //   • non-rollover tasks with createdDate == today
        //   • ALL pending rollover tasks (regardless of original date) — they ARE today's sticky work
        val tierA = pendingToday
        val tierAIds = tierA.map { it.id }.toHashSet()

        // tierB = genuinely overdue: non-rollover tasks from past dates not in today's view
        val tierB = allTasks.filter { task ->
            !task.isCompleted &&
                task.recurrenceType != "rollover" &&
                parseDate(task.createdDate, todayFmt) < todayMs &&
                task.id !in tierAIds
        }.distinctBy { it.id }
        val tierBIds = tierB.map { it.id }.toHashSet()

        val tierC = allTasks.filter { task ->
            !task.isCompleted && task.id !in tierAIds && task.id !in tierBIds &&
                dayDiff(parseDate(task.createdDate, todayFmt), todayMs) >= 3
        }.sortedByDescending { it.priority }.take(5)
        val tierDCutoff = now - 24 * 60 * 60 * 1000L
        val tierD = allTasks.filter {
            it.isCompleted && (it.completedTimestamp ?: 0L) >= tierDCutoff
        }.sortedByDescending { it.completedTimestamp ?: 0L }.take(8)

        val sb = StringBuilder()
        sb.appendLine("TASK CONTEXT (volatile, refreshed each turn — use for grounding answers, not personality):")

        // Tier A: today's open (new tasks + sticky rollover tasks)
        if (tierA.isNotEmpty()) {
            val newCount = tierA.count { it.recurrenceType != "rollover" }
            val rolloverCount = tierA.size - newCount
            val label = when {
                rolloverCount == 0 -> "TODAY ($newCount):"
                newCount == 0 -> "TODAY — $rolloverCount sticky rollover:"
                else -> "TODAY ($newCount new + $rolloverCount rollover):"
            }
            sb.appendLine("  $label")
            for (t in tierA) {
                val line = formatTaskLine(t, todayMs, todayFmt)
                if (sb.length + line.length > maxChars + 200) break
                sb.appendLine("    - $line")
            }
        }

        // Tier B: genuinely overdue (non-rollover tasks from past dates)
        if (sb.length < maxChars && tierB.isNotEmpty()) {
            sb.appendLine("  OVERDUE (${tierB.size}):")
            for (t in tierB.take(8)) {
                val line = formatOverdueLine(t, todayMs, todayFmt)
                if (sb.length + line.length > maxChars + 200) break
                sb.appendLine("    - $line")
            }
        }

        // Tier C: long-pending
        if (sb.length < maxChars && tierC.isNotEmpty()) {
            sb.appendLine("  LONG-PENDING (top ${tierC.size}):")
            for (t in tierC) {
                val line = formatStaleLine(t, todayMs, todayFmt)
                if (sb.length + line.length > maxChars + 200) break
                sb.appendLine("    - $line")
            }
        }

        // Tier D: recent completions (compact)
        if (sb.length < maxChars && tierD.isNotEmpty()) {
            val titles = tierD.joinToString(", ") { "\"${it.title.take(40)}\"" }
            val line = "  COMPLETED LAST 24H (${tierD.size}): $titles"
            if (sb.length + line.length <= maxChars + 300) sb.appendLine(line)
        }

        // Day-of-week + time-of-day signal — useful for AI tone/timing decisions
        val cal = Calendar.getInstance()
        val dow = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "Monday"; Calendar.TUESDAY -> "Tuesday"
            Calendar.WEDNESDAY -> "Wednesday"; Calendar.THURSDAY -> "Thursday"
            Calendar.FRIDAY -> "Friday"; Calendar.SATURDAY -> "Saturday"
            else -> "Sunday"
        }
        val hr = cal.get(Calendar.HOUR_OF_DAY)
        val partOfDay = when {
            hr < 6 -> "early morning"
            hr < 12 -> "morning"
            hr < 17 -> "afternoon"
            hr < 21 -> "evening"
            else -> "night"
        }
        sb.appendLine("  CONTEXT: $dow, $partOfDay (${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())})")

        val result = sb.toString().trimEnd()
        cachedContext = result
        cachedAt = now
        cachedTasksHash = (pendingToday.map { it.id } + allTasks.map { it.id }).hashCode()
        result
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun parseDate(date: String, fmt: SimpleDateFormat): Long {
        return runCatching { fmt.parse(date)?.time ?: 0L }.getOrDefault(0L)
    }

    private fun dayDiff(date: Long, now: Long): Int {
        return ((now - date) / 86_400_000L).toInt()
    }

    private fun formatTaskLine(t: Task, todayMs: Long = 0L, fmt: SimpleDateFormat? = null): String {
        val pri = if (t.priority > 0) " [P${t.priority}]" else ""
        val time = t.deadlineTime?.let { " @${it}" } ?: ""
        val tag = t.tags?.takeIf { it.isNotBlank() }?.let { " #${it.split(',').first().trim()}" } ?: ""
        val alarm = if (t.customAlarmTimeMs != null && !t.isAlarmPaused) " ⏰" else ""
        val rolloverAge = if (t.recurrenceType == "rollover" && fmt != null && todayMs > 0) {
            val days = dayDiff(parseDate(t.createdDate, fmt), todayMs)
            if (days > 0) " (rollover ${days}d)" else ""
        } else ""
        return "${t.title.take(80)}$time$pri$tag$alarm$rolloverAge"
    }

    private fun formatOverdueLine(t: Task, todayMs: Long, fmt: SimpleDateFormat): String {
        val days = dayDiff(parseDate(t.createdDate, fmt), todayMs)
        val ageStr = when {
            days <= 0 -> "today"
            days == 1 -> "1d overdue"
            else -> "${days}d overdue"
        }
        val pri = if (t.priority > 0) " [P${t.priority}]" else ""
        val rollover = if (t.recurrenceType == "rollover") " (rollover)" else ""
        return "${t.title.take(70)}$pri ($ageStr)$rollover"
    }

    private fun formatStaleLine(t: Task, todayMs: Long, fmt: SimpleDateFormat): String {
        val days = dayDiff(parseDate(t.createdDate, fmt), todayMs)
        val pri = if (t.priority > 0) " [P${t.priority}]" else ""
        return "${t.title.take(70)}$pri (${days}d stale)"
    }
}
