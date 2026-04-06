package com.theblankstate.preamble.recurrence

import com.theblankstate.preamble.data.Task
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

object RecurrenceGenerator {

    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /**
     * Given a recurrence template task, generate all dates from [fromDate] up to [toDate]
     * that should have an instance created.
     */
    fun generateDates(template: Task, fromDate: String, toDate: String): List<String> {
        if (template.recurrenceType == null) return emptyList()

        val from = sdf.parse(fromDate) ?: return emptyList()
        val to = sdf.parse(toDate) ?: return emptyList()
        val endDate = template.recurrenceEndDate?.let { sdf.parse(it) }
        val effectiveTo = if (endDate != null && endDate.before(to)) endDate else to
        val interval = template.recurrenceInterval ?: 1
        val templateDate = sdf.parse(template.createdDate) ?: return emptyList()

        val cal = Calendar.getInstance().apply { time = templateDate }
        val startCal = Calendar.getInstance().apply { time = from }

        val dates = mutableListOf<String>()

        when (template.recurrenceType) {
            "daily", "custom" -> {
                val cursor = startCal.clone() as Calendar
                while (!cursor.time.after(effectiveTo)) {
                    val daysDiff = daysBetween(cal, cursor)
                    if (daysDiff > 0 && daysDiff % interval == 0L) {
                        dates.add(sdf.format(cursor.time))
                    }
                    cursor.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            "weekly" -> {
                val allowedDays = template.recurrenceDays
                    ?.split(",")
                    ?.mapNotNull { it.trim().toIntOrNull() }
                    ?.toSet()

                val cursor = startCal.clone() as Calendar
                while (!cursor.time.after(effectiveTo)) {
                    val dow = cursor.get(Calendar.DAY_OF_WEEK)
                    if (allowedDays != null && allowedDays.isNotEmpty()) {
                        if (dow in allowedDays && cursor.time.after(templateDate)) {
                            // Respect the interval: calculate which week this is relative to template
                            val weeksDiff = daysBetween(cal, cursor) / 7
                            if (interval == 1 || (weeksDiff >= 0 && weeksDiff % interval == 0L)) {
                                dates.add(sdf.format(cursor.time))
                            }
                        }
                    } else {
                        val templateDow = cal.get(Calendar.DAY_OF_WEEK)
                        if (dow == templateDow) {
                            val weeksDiff = daysBetween(cal, cursor) / 7
                            if (weeksDiff > 0 && weeksDiff % interval == 0L) {
                                dates.add(sdf.format(cursor.time))
                            }
                        }
                    }
                    cursor.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            "monthly" -> {
                val templateDay = cal.get(Calendar.DAY_OF_MONTH)
                val cursor = startCal.clone() as Calendar
                while (!cursor.time.after(effectiveTo)) {
                    if (cursor.get(Calendar.DAY_OF_MONTH) == templateDay) {
                        val monthsDiff = monthsBetween(cal, cursor)
                        if (monthsDiff > 0 && monthsDiff % interval == 0) {
                            dates.add(sdf.format(cursor.time))
                        }
                    }
                    cursor.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            "yearly" -> {
                val templateMonth = cal.get(Calendar.MONTH)
                val templateDay = cal.get(Calendar.DAY_OF_MONTH)
                val cursor = startCal.clone() as Calendar
                while (!cursor.time.after(effectiveTo)) {
                    if (cursor.get(Calendar.MONTH) == templateMonth &&
                        cursor.get(Calendar.DAY_OF_MONTH) == templateDay) {
                        val yearsDiff = cursor.get(Calendar.YEAR) - cal.get(Calendar.YEAR)
                        if (yearsDiff > 0 && yearsDiff % interval == 0) {
                            dates.add(sdf.format(cursor.time))
                        }
                    }
                    cursor.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
        }
        return dates
    }

    // ── Virtual Expansion (Google-style) ──

    /**
     * Generate in-memory Task instances for a recurring template within a date range.
     * These are NOT persisted — they're for calendar display only.
     * Each virtual instance gets a deterministic ID based on template ID + date,
     * so we can deduplicate against physically-stored instances.
     *
     * @param template The recurrence template task
     * @param fromDate Start of the visible window (inclusive) "yyyy-MM-dd"
     * @param toDate End of the visible window (inclusive) "yyyy-MM-dd"
     * @param existingInstanceDates Set of dates that already have physical rows in the DB
     * @return List of virtual Task instances (not persisted)
     */
    fun generateVirtualInstances(
        template: Task,
        fromDate: String,
        toDate: String,
        existingInstanceDates: Set<String> = emptySet()
    ): List<Task> {
        if (template.recurrenceType == null) return emptyList()
        // Never expand rollover — it's a single sticky task, not a recurring series
        if (template.recurrenceType == "rollover") return emptyList()
        // Never expand Google Calendar events — Google handles its own expansion
        if (template.source == "google_calendar") return emptyList()

        val dates = generateDates(template, fromDate, toDate)

        return dates
            .filter { date -> date !in existingInstanceDates }
            .map { date ->
                template.copy(
                    id = virtualInstanceId(template.id, date),
                    createdDate = date,
                    isCompleted = false,
                    completedTimestamp = null,
                    completedDate = null,
                    createdTimestamp = System.currentTimeMillis(),
                    updatedTimestamp = System.currentTimeMillis(),
                    recurrenceType = null,        // Instance, not template
                    recurrenceInterval = null,
                    recurrenceDays = null,
                    recurrenceEndDate = null,
                    recurrenceParentId = template.id
                )
            }
    }

    /**
     * Check if a recurring template has an occurrence on a specific date.
     * Lightweight — doesn't create Task objects, just checks the date math.
     */
    fun isDueOnDate(template: Task, date: String): Boolean {
        if (template.recurrenceType == null) return false
        if (template.recurrenceType == "rollover") return false
        if (template.source == "google_calendar") return false
        return generateDates(template, date, date).isNotEmpty()
    }

    /**
     * Count virtual recurring instances per day for a month — used by calendar heat map.
     * Returns Map<dayOfMonth, virtualCount>.
     *
     * @param templates All active recurrence templates (non-rollover, non-google)
     * @param year Calendar year
     * @param month Calendar month (0-indexed, like Calendar.MONTH)
     * @param existingInstanceDates Set of "yyyy-MM-dd" dates that already have physical rows
     */
    fun generateVirtualHeatMap(
        templates: List<Task>,
        year: Int,
        month: Int,
        existingInstanceDates: Set<String> = emptySet()
    ): Map<Int, Int> {
        val calendar = Calendar.getInstance()
        calendar.set(year, month, 1)
        val maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

        val fromDate = sdf.format(calendar.time)
        calendar.set(year, month, maxDay)
        val toDate = sdf.format(calendar.time)

        val counts = mutableMapOf<Int, Int>()

        for (template in templates) {
            val dates = generateDates(template, fromDate, toDate)
            for (date in dates) {
                if (date in existingInstanceDates) continue
                val parsed = sdf.parse(date) ?: continue
                val dayCal = Calendar.getInstance().apply { time = parsed }
                val day = dayCal.get(Calendar.DAY_OF_MONTH)
                counts[day] = (counts[day] ?: 0) + 1
            }
        }

        return counts
    }

    /**
     * Deterministic ID for a virtual instance: "vri_{templateId}_{date}"
     * This allows us to:
     * 1. Deduplicate virtual instances against physical rows
     * 2. Materialize them into the DB when the user interacts (e.g. complete)
     */
    fun virtualInstanceId(templateId: String, date: String): String {
        return "vri_${templateId}_$date"
    }

    /**
     * Check if a task ID represents a virtual recurring instance.
     */
    fun isVirtualInstance(taskId: String): Boolean {
        return taskId.startsWith("vri_")
    }

    /**
     * Extract the template ID and date from a virtual instance ID.
     * Returns Pair(templateId, date) or null if not a virtual instance.
     */
    fun parseVirtualInstanceId(taskId: String): Pair<String, String>? {
        if (!isVirtualInstance(taskId)) return null
        val parts = taskId.removePrefix("vri_").split("_")
        // Template ID might contain underscores, date is always last in yyyy-MM-dd format (10 chars)
        if (parts.size < 2) return null
        val date = taskId.takeLast(10) // "yyyy-MM-dd" = 10 chars
        val templateId = taskId.removePrefix("vri_").removeSuffix("_$date")
        return templateId to date
    }

    // ── Private helpers ──

    private fun daysBetween(from: Calendar, to: Calendar): Long {
        val diffMs = to.timeInMillis - from.timeInMillis
        return diffMs / (24 * 60 * 60 * 1000)
    }

    private fun monthsBetween(from: Calendar, to: Calendar): Int {
        return (to.get(Calendar.YEAR) - from.get(Calendar.YEAR)) * 12 +
               (to.get(Calendar.MONTH) - from.get(Calendar.MONTH))
    }
}
