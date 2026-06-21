package com.theblankstate.preamble.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class ParsedDateTime(
    val date: String?,
    val time: String?,
    val cleanedTitle: String
)

object NaturalDateParser {

    private val dayNames = mapOf(
        "monday" to Calendar.MONDAY, "mon" to Calendar.MONDAY,
        "tuesday" to Calendar.TUESDAY, "tue" to Calendar.TUESDAY, "tues" to Calendar.TUESDAY,
        "wednesday" to Calendar.WEDNESDAY, "wed" to Calendar.WEDNESDAY,
        "thursday" to Calendar.THURSDAY, "thu" to Calendar.THURSDAY, "thur" to Calendar.THURSDAY, "thurs" to Calendar.THURSDAY,
        "friday" to Calendar.FRIDAY, "fri" to Calendar.FRIDAY,
        "saturday" to Calendar.SATURDAY, "sat" to Calendar.SATURDAY,
        "sunday" to Calendar.SUNDAY, "sun" to Calendar.SUNDAY
    )

    private val dayNamesPattern = dayNames.keys.joinToString("|")

    // Ordered: longer patterns first to avoid partial matches
    private val datePatterns = listOf(
        // "day after tomorrow" / "parso" / "parson"
        Regex("""(?i)\b(day\s+after\s+tomorrow|parso[n]?|perso)\b""") to { _: MatchResult -> daysFromNow(2) },
        // "tomorrow" / "tmrw" / "kal" / "cal"
        Regex("""(?i)\b(tomorrow|tmrw|tmw|kal|cal)\b""") to { _: MatchResult -> daysFromNow(1) },
        // "today" / "aaj"
        Regex("""(?i)\b(today|aaj)\b""") to { _: MatchResult -> daysFromNow(0) },
        // "next monday" etc
        Regex("""(?i)\b(next\s+($dayNamesPattern))\b""") to { m: MatchResult ->
            val dayName = m.groupValues[2].lowercase()
            nextWeekday(dayNames[dayName] ?: Calendar.MONDAY)
        },
        // "this monday" etc
        Regex("""(?i)\b(this\s+($dayNamesPattern))\b""") to { m: MatchResult ->
            val dayName = m.groupValues[2].lowercase()
            thisWeekday(dayNames[dayName] ?: Calendar.MONDAY)
        },
        // "on monday" etc
        Regex("""(?i)\b(on\s+($dayNamesPattern))\b""") to { m: MatchResult ->
            val dayName = m.groupValues[2].lowercase()
            nextWeekday(dayNames[dayName] ?: Calendar.MONDAY)
        },
        // "in N days"
        Regex("""(?i)\b(in\s+(\d+)\s+days?)\b""") to { m: MatchResult ->
            daysFromNow(m.groupValues[2].toIntOrNull() ?: 0)
        },
        // "in N weeks"
        Regex("""(?i)\b(in\s+(\d+)\s+weeks?)\b""") to { m: MatchResult ->
            daysFromNow((m.groupValues[2].toIntOrNull() ?: 0) * 7)
        },
    )

    private val timePatterns = listOf(
        // "at 3:30 pm" / "at 3:30pm" / "by 3:30 pm"
        Regex("""(?i)\b((?:at|by)\s+(\d{1,2}):(\d{2})\s*(am|pm))\b""") to { m: MatchResult ->
            convertTo24Hour(m.groupValues[2].toInt(), m.groupValues[3].toInt(), m.groupValues[4])
        },
        // "at 3 pm" / "at 3pm" / "by 3 pm"
        Regex("""(?i)\b((?:at|by)\s+(\d{1,2})\s*(am|pm))\b""") to { m: MatchResult ->
            convertTo24Hour(m.groupValues[2].toInt(), 0, m.groupValues[3])
        },
        // "at 14:00" / "at 14:30"
        Regex("""(?i)\b((?:at|by)\s+(\d{1,2}):(\d{2}))\b""") to { m: MatchResult ->
            val h = m.groupValues[2].toInt()
            val min = m.groupValues[3].toInt()
            if (h in 0..23 && min in 0..59) String.format("%02d:%02d", h, min) else null
        },
        // standalone "3:30pm" / "3pm" without at/by
        Regex("""(?i)\b((\d{1,2}):(\d{2})\s*(am|pm))\b""") to { m: MatchResult ->
            convertTo24Hour(m.groupValues[2].toInt(), m.groupValues[3].toInt(), m.groupValues[4])
        },
        Regex("""(?i)\b((\d{1,2})\s*(am|pm))\b""") to { m: MatchResult ->
            convertTo24Hour(m.groupValues[2].toInt(), 0, m.groupValues[3])
        },
    )

    fun parse(input: String): ParsedDateTime? {
        if (input.isBlank()) return null

        var detectedDate: String? = null
        var detectedTime: String? = null
        var cleaned = input

        // Try date patterns
        for ((regex, resolver) in datePatterns) {
            val match = regex.find(cleaned)
            if (match != null) {
                detectedDate = resolver(match)
                if (detectedDate != null) {
                    cleaned = cleaned.removeRange(match.range).trim()
                    break
                }
            }
        }

        // Try time patterns
        for ((regex, resolver) in timePatterns) {
            val match = regex.find(cleaned)
            if (match != null) {
                detectedTime = resolver(match)
                if (detectedTime != null) {
                    cleaned = cleaned.removeRange(match.range).trim()
                    break
                }
            }
        }

        if (detectedDate == null && detectedTime == null) return null

        // Clean up extra whitespace
        cleaned = cleaned.replace(Regex("""\s+"""), " ").trim()

        return ParsedDateTime(
            date = detectedDate,
            time = detectedTime,
            cleanedTitle = cleaned
        )
    }

    private fun daysFromNow(days: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, days)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return dateFormat.format(cal.time)
    }

    private fun nextWeekday(targetDay: Int): String {
        val cal = Calendar.getInstance()
        val currentDay = cal.get(Calendar.DAY_OF_WEEK)
        var diff = targetDay - currentDay
        if (diff <= 0) diff += 7
        cal.add(Calendar.DAY_OF_YEAR, diff)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return dateFormat.format(cal.time)
    }

    private fun thisWeekday(targetDay: Int): String {
        val cal = Calendar.getInstance()
        val currentDay = cal.get(Calendar.DAY_OF_WEEK)
        var diff = targetDay - currentDay
        if (diff < 0) diff += 7
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        if (diff == 0) return dateFormat.format(cal.time) // today
        cal.add(Calendar.DAY_OF_YEAR, diff)
        return dateFormat.format(cal.time)
    }

    private fun convertTo24Hour(hour: Int, minute: Int, amPm: String): String? {
        if (hour !in 1..12 || minute !in 0..59) return null
        val h24 = when {
            amPm.equals("am", ignoreCase = true) && hour == 12 -> 0
            amPm.equals("pm", ignoreCase = true) && hour == 12 -> 12
            amPm.equals("pm", ignoreCase = true) -> hour + 12
            else -> hour
        }
        return String.format("%02d:%02d", h24, minute)
    }
}
