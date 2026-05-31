package com.theblankstate.preamble.repository

import com.theblankstate.preamble.data.HabitEntry
import com.theblankstate.preamble.data.HabitEntryDao
import com.theblankstate.preamble.data.Task
import com.theblankstate.preamble.data.TaskDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

// ── Data classes for habit stats ──

data class HabitWithStatus(
    val habit: Task,
    val todayEntry: HabitEntry?,
    val currentStreak: Int = 0,
    val completionRate: Float = 0f
)

data class HabitStats(
    val currentStreak: Int,
    val longestStreak: Int,
    val totalCompletions: Int,
    val completionRate: Float,  // 0.0–1.0
    val bestDay: String?,       // Day of week with highest completion rate (e.g., "Monday")
    val totalDays: Int,
    val totalScheduledDays: Int
)

data class WeeklyData(
    val weekStart: String,
    val completedDays: Int,
    val totalDays: Int
)

data class MonthlyData(
    val month: String,
    val completionRate: Float
)

class HabitRepository(
    private val habitEntryDao: HabitEntryDao,
    private val taskDao: TaskDao
) {
    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // ── CRUD ──

    suspend fun createHabit(
        title: String,
        frequency: String,          // "daily", "weekly", "custom"
        targetDays: String? = null,  // Comma-separated days for weekly (e.g., "2,4,6")
        type: String = "continuous", // "continuous" or "deadline"
        deadlineDate: String? = null,
        reminderTime: String? = null,
        description: String? = null,
        tags: String? = null,
        interval: Int = 1,
        timesPerWeek: Int? = null
    ): Task {
        val now = System.currentTimeMillis()
        val today = sdf.format(Date())
        val habit = Task(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            createdDate = today,
            createdTimestamp = now,
            updatedTimestamp = now,
            deadlineTime = reminderTime,
            description = description,
            tags = tags,
            recurrenceType = frequency,  // Reuse for habit frequency pattern
            recurrenceInterval = interval,
            recurrenceDays = targetDays,
            recurrenceEndDate = if (type == "deadline") deadlineDate else null,
            taskCategory = "habit",
            habitFrequency = frequency,
            habitTargetDays = targetDays,
            habitType = type,
            habitTimesPerWeek = timesPerWeek
        )
        taskDao.insertTask(habit)
        return habit
    }

    suspend fun updateHabit(habit: Task) {
        taskDao.updateTask(habit.copy(updatedTimestamp = System.currentTimeMillis()))
    }

    suspend fun deleteHabit(habitId: String) {
        val habit = taskDao.getHabitById(habitId) ?: return
        habitEntryDao.deleteAllForHabit(habitId)
        taskDao.deleteTask(habit)
    }

    // ── Daily Tracking ──

    suspend fun toggleHabitForDate(habitId: String, date: String): HabitEntry {
        val existing = habitEntryDao.getEntry(habitId, date)
        val entry = if (existing != null) {
            existing.copy(
                isCompleted = !existing.isCompleted,
                completedTimestamp = if (!existing.isCompleted) System.currentTimeMillis() else null
            )
        } else {
            HabitEntry(
                habitId = habitId,
                date = date,
                isCompleted = true,
                completedTimestamp = System.currentTimeMillis()
            )
        }
        habitEntryDao.upsertEntry(entry)
        return entry
    }

    suspend fun setHabitValue(habitId: String, date: String, value: Float, note: String? = null) {
        val existing = habitEntryDao.getEntry(habitId, date)
        val entry = existing?.copy(
            value = value,
            note = note,
            isCompleted = true,
            completedTimestamp = existing.completedTimestamp ?: System.currentTimeMillis()
        ) ?: HabitEntry(
            habitId = habitId,
            date = date,
            isCompleted = true,
            completedTimestamp = System.currentTimeMillis(),
            value = value,
            note = note
        )
        habitEntryDao.upsertEntry(entry)
    }

    // ── Queries ──

    fun getAllHabits(): Flow<List<Task>> = taskDao.getAllHabits()

    fun getTodayHabitEntries(): Flow<List<HabitEntry>> {
        val today = sdf.format(Date())
        return habitEntryDao.getEntriesForDate(today)
    }

    suspend fun getHabitEntry(habitId: String, date: String): HabitEntry? {
        return habitEntryDao.getEntry(habitId, date)
    }

    fun getTodayHabitsWithStatus(): Flow<List<HabitWithStatus>> {
        val today = sdf.format(Date())
        return combine(
            taskDao.getAllHabits(),
            habitEntryDao.getEntriesForDate(today)
        ) { habits, entries ->
            val entryMap = entries.associateBy { it.habitId }
            habits
                .filter { isHabitDueOnDate(it, today) }
                .map { habit ->
                    HabitWithStatus(
                        habit = habit,
                        todayEntry = entryMap[habit.id]
                    )
                }
        }
    }

    /**
     * Check if a habit is due on a given date based on its frequency.
     */
    fun isHabitDueOnDate(habit: Task, dateStr: String): Boolean {
        if (!habit.isHabit) return false

        // Check if habit has ended (deadline type)
        if (habit.habitType == "deadline" && habit.recurrenceEndDate != null) {
            if (dateStr > habit.recurrenceEndDate!!) return false
        }

        // Check if habit hasn't started yet
        if (dateStr < habit.createdDate) return false

        return when (habit.habitFrequency) {
            "daily" -> true
            "weekly" -> {
                // Check if today's day of week is in the target days
                val cal = Calendar.getInstance()
                try {
                    cal.time = sdf.parse(dateStr) ?: return false
                } catch (_: Exception) { return false }
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                habit.habitTargetDaysList.contains(dayOfWeek)
            }
            "custom" -> {
                // Every N days from start
                val interval = habit.recurrenceInterval ?: 1
                try {
                    val start = sdf.parse(habit.createdDate) ?: return false
                    val target = sdf.parse(dateStr) ?: return false
                    val daysDiff = ((target.time - start.time) / (24 * 60 * 60 * 1000)).toInt()
                    daysDiff >= 0 && daysDiff % interval == 0
                } catch (_: Exception) { false }
            }
            else -> true  // Default to daily for unknown frequencies
        }
    }

    // ── Stats ──

    suspend fun getCurrentStreak(habitId: String): Int {
        val habit = taskDao.getHabitById(habitId) ?: return 0
        val completedEntries = habitEntryDao.getCompletedEntries(habitId)
        if (completedEntries.isEmpty()) return 0

        val completedDates = completedEntries.map { it.date }.toSet()
        val today = sdf.format(Date())

        var streak = 0
        val cal = Calendar.getInstance()

        // Start from today and walk backward
        var current = today
        while (true) {
            if (isHabitDueOnDate(habit, current)) {
                if (completedDates.contains(current)) {
                    streak++
                } else if (current != today) {
                    // Allow today to be incomplete (streak isn't broken until end of day)
                    break
                } else {
                    break
                }
            }
            // Go back one day
            try {
                cal.time = sdf.parse(current) ?: break
            } catch (_: Exception) { break }
            cal.add(Calendar.DAY_OF_YEAR, -1)
            current = sdf.format(cal.time)

            // Don't go before habit creation date
            if (current < habit.createdDate) break
        }
        return streak
    }

    suspend fun getLongestStreak(habitId: String): Int {
        val habit = taskDao.getHabitById(habitId) ?: return 0
        val completedEntries = habitEntryDao.getCompletedEntries(habitId)
        if (completedEntries.isEmpty()) return 0

        val completedDates = completedEntries.map { it.date }.toSet()

        var longestStreak = 0
        var currentStreak = 0
        val cal = Calendar.getInstance()

        // Walk forward from habit creation date to today
        try {
            cal.time = sdf.parse(habit.createdDate) ?: return 0
        } catch (_: Exception) { return 0 }

        val today = sdf.format(Date())
        while (sdf.format(cal.time) <= today) {
            val dateStr = sdf.format(cal.time)
            if (isHabitDueOnDate(habit, dateStr)) {
                if (completedDates.contains(dateStr)) {
                    currentStreak++
                    longestStreak = maxOf(longestStreak, currentStreak)
                } else {
                    currentStreak = 0
                }
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return longestStreak
    }

    suspend fun getCompletionRate(habitId: String, days: Int = 30): Float {
        val habit = taskDao.getHabitById(habitId) ?: return 0f
        val cal = Calendar.getInstance()
        val today = sdf.format(Date())
        cal.add(Calendar.DAY_OF_YEAR, -days)
        val from = sdf.format(cal.time)

        val entries = habitEntryDao.getEntriesInRange(habitId, from, today)
        val completedSet = entries.filter { it.isCompleted }.map { it.date }.toSet()

        // Count scheduled days in range
        var scheduledDays = 0
        var completedDays = 0
        cal.time = sdf.parse(from) ?: return 0f
        while (sdf.format(cal.time) <= today) {
            val dateStr = sdf.format(cal.time)
            if (dateStr >= habit.createdDate && isHabitDueOnDate(habit, dateStr)) {
                scheduledDays++
                if (completedSet.contains(dateStr)) completedDays++
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        return if (scheduledDays > 0) completedDays.toFloat() / scheduledDays else 0f
    }

    suspend fun getHabitStats(habitId: String): HabitStats {
        val currentStreak = getCurrentStreak(habitId)
        val longestStreak = getLongestStreak(habitId)
        val totalCompletions = habitEntryDao.getTotalCompletedCount(habitId)
        val completionRate = getCompletionRate(habitId, 30)

        val habit = taskDao.getHabitById(habitId)
        val totalDays = if (habit != null) {
            try {
                val start = sdf.parse(habit.createdDate) ?: Date()
                val now = Date()
                ((now.time - start.time) / (24 * 60 * 60 * 1000)).toInt() + 1
            } catch (_: Exception) { 0 }
        } else 0

        // Calculate best day of week
        val bestDay = calculateBestDay(habitId)

        // Count total scheduled days
        var scheduledDays = 0
        if (habit != null) {
            val cal = Calendar.getInstance()
            try {
                cal.time = sdf.parse(habit.createdDate) ?: Date()
            } catch (_: Exception) { /* use current date */ }
            val today = sdf.format(Date())
            while (sdf.format(cal.time) <= today) {
                if (isHabitDueOnDate(habit, sdf.format(cal.time))) scheduledDays++
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        return HabitStats(
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            totalCompletions = totalCompletions,
            completionRate = completionRate,
            bestDay = bestDay,
            totalDays = totalDays,
            totalScheduledDays = scheduledDays
        )
    }

    private suspend fun calculateBestDay(habitId: String): String? {
        val entries = habitEntryDao.getCompletedEntries(habitId)
        if (entries.isEmpty()) return null

        val dayCounts = IntArray(7) // Sun=0 through Sat=6
        val cal = Calendar.getInstance()
        for (entry in entries) {
            try {
                cal.time = sdf.parse(entry.date) ?: continue
                val dayIndex = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0-indexed
                dayCounts[dayIndex]++
            } catch (_: Exception) { continue }
        }

        val bestIndex = dayCounts.indices.maxByOrNull { dayCounts[it] } ?: return null
        if (dayCounts[bestIndex] == 0) return null

        val dayNames = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        return dayNames[bestIndex]
    }

    // ── Heatmap ──

    suspend fun getHeatmapData(habitId: String, months: Int = 6): Map<String, Boolean> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -months)
        val from = sdf.format(cal.time)
        val today = sdf.format(Date())

        val entries = habitEntryDao.getHeatmapDataInRange(habitId, from, today)
        return entries.associate { it.date to it.isCompleted }
    }

    // ── Graphs ──

    suspend fun getWeeklyGraph(habitId: String, weeks: Int = 12): List<WeeklyData> {
        val habit = taskDao.getHabitById(habitId) ?: return emptyList()
        val result = mutableListOf<WeeklyData>()
        val cal = Calendar.getInstance()
        val today = sdf.format(Date())

        // Go back N weeks
        cal.add(Calendar.WEEK_OF_YEAR, -weeks)
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)

        for (w in 0 until weeks) {
            val weekStart = sdf.format(cal.time)
            var completed = 0
            var total = 0

            for (d in 0 until 7) {
                val dateStr = sdf.format(cal.time)
                if (dateStr <= today && dateStr >= habit.createdDate && isHabitDueOnDate(habit, dateStr)) {
                    total++
                    val entry = habitEntryDao.getEntry(habitId, dateStr)
                    if (entry?.isCompleted == true) completed++
                }
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }

            if (total > 0) {
                result.add(WeeklyData(weekStart, completed, total))
            }
        }
        return result
    }

    suspend fun getMonthlyGraph(habitId: String, months: Int = 12): List<MonthlyData> {
        val habit = taskDao.getHabitById(habitId) ?: return emptyList()
        val result = mutableListOf<MonthlyData>()
        val cal = Calendar.getInstance()
        val today = sdf.format(Date())
        val monthFormat = SimpleDateFormat("MMM yyyy", Locale.US)

        cal.add(Calendar.MONTH, -months + 1)
        cal.set(Calendar.DAY_OF_MONTH, 1)

        for (m in 0 until months) {
            val monthLabel = monthFormat.format(cal.time)
            val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            var completed = 0
            var total = 0

            for (d in 0 until daysInMonth) {
                val dateStr = sdf.format(cal.time)
                if (dateStr <= today && dateStr >= habit.createdDate && isHabitDueOnDate(habit, dateStr)) {
                    total++
                    val entry = habitEntryDao.getEntry(habitId, dateStr)
                    if (entry?.isCompleted == true) completed++
                }
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }

            if (total > 0) {
                result.add(MonthlyData(monthLabel, completed.toFloat() / total))
            }

            // Move to first day of next month (cal is already there after the loop)
        }
        return result
    }

    // ── Times per week tracking ──

    /**
     * For "X times per week" habits, get the current week's completion count.
     */
    suspend fun getWeekCompletionCount(habitId: String): Int {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val weekStart = sdf.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, 6)
        val weekEnd = sdf.format(cal.time)
        return habitEntryDao.getCompletedCount(habitId, weekStart, weekEnd)
    }

    /**
     * Check if a "times per week" habit has met its weekly goal.
     */
    suspend fun isWeeklyGoalMet(habit: Task): Boolean {
        val target = habit.habitTimesPerWeek ?: return false
        return getWeekCompletionCount(habit.id) >= target
    }
}
