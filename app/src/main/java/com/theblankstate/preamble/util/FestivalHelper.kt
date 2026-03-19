package com.theblankstate.preamble.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Provides Indian festival / national holiday names for any given date.
 * Uses a static map of well-known fixed-date festivals + computed dates
 * for variable festivals (like Diwali, Holi, Eid etc. — approximated for upcoming years).
 *
 * Format: "MM-dd" → festival name for fixed-date festivals
 * For variable festivals, we store year-specific dates.
 */
object FestivalHelper {

    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val monthDaySdf = SimpleDateFormat("MM-dd", Locale.US)

    /**
     * Fixed-date festivals & national holidays (every year).
     */
    private val fixedFestivals = mapOf(
        "01-01" to "New Year",
        "01-13" to "Lohri",
        "01-14" to "Makar Sankranti",
        "01-15" to "Pongal",
        "01-26" to "Republic Day 🇮🇳",
        "02-14" to "Valentine's Day",
        "03-08" to "Women's Day",
        "04-14" to "Ambedkar Jayanti",
        "05-01" to "May Day",
        "06-21" to "Yoga Day",
        "07-04" to "Independence Day 🇺🇸",
        "08-15" to "Independence Day 🇮🇳",
        "08-29" to "National Sports Day",
        "09-05" to "Teachers' Day",
        "10-02" to "Gandhi Jayanti",
        "10-31" to "Halloween",
        "11-14" to "Children's Day",
        "11-26" to "Constitution Day",
        "12-25" to "Christmas 🎄",
        "12-31" to "New Year's Eve"
    )

    /**
     * Get the festival name for a date string "yyyy-MM-dd", or null if none.
     */
    fun getFestival(dateStr: String): String? {

        // Then check fixed-date festivals (MM-dd)
        return try {
            val date = sdf.parse(dateStr) ?: return null
            val mmdd = monthDaySdf.format(date)
            fixedFestivals[mmdd]
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Get festival for a Date object.
     */
    fun getFestival(date: Date): String? {
        return getFestival(sdf.format(date))
    }

    /**
     * Format a date string "yyyy-MM-dd" into a rich display:
     * "Monday, 10 March 2026"
     */
    fun formatDateRich(dateStr: String, externalFestival: String? = null): RichDate? {
        return try {
            val date = sdf.parse(dateStr) ?: return null
            val cal = Calendar.getInstance().apply { time = date }
            val dayName = SimpleDateFormat("EEEE", Locale.getDefault()).format(date)
            val day = cal.get(Calendar.DAY_OF_MONTH).toString()
            val month = SimpleDateFormat("MMMM", Locale.getDefault()).format(date)
            val year = cal.get(Calendar.YEAR).toString()
            val festival = externalFestival ?: getFestival(dateStr)
            RichDate(dayName, day, month, year, festival)
        } catch (_: Exception) {
            null
        }
    }
}

data class RichDate(
    val dayName: String,    // "Monday"
    val day: String,        // "10"
    val month: String,      // "March"
    val year: String,       // "2026"
    val festival: String?   // "Holi 🎨" or null
)
