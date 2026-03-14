package com.theblankstate.preamble.recurrence

import com.theblankstate.preamble.data.Task
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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
                // Both "daily" and "custom" mean every N days
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
                            dates.add(sdf.format(cursor.time))
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

    private fun daysBetween(from: Calendar, to: Calendar): Long {
        val diffMs = to.timeInMillis - from.timeInMillis
        return diffMs / (24 * 60 * 60 * 1000)
    }

    private fun monthsBetween(from: Calendar, to: Calendar): Int {
        return (to.get(Calendar.YEAR) - from.get(Calendar.YEAR)) * 12 +
               (to.get(Calendar.MONTH) - from.get(Calendar.MONTH))
    }
}
