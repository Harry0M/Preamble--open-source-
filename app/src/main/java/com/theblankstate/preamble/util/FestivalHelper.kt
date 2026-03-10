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
     * Variable-date festivals keyed by "yyyy-MM-dd".
     * These change every year based on lunar calendar.
     * Coverage: 2025-2027 for all major Indian festivals.
     */
    private val variableFestivals = mapOf(
        // ── 2025 ──
        "2025-01-14" to "Makar Sankranti",
        "2025-02-26" to "Maha Shivaratri",
        "2025-03-14" to "Holi 🎨",
        "2025-03-30" to "Eid ul-Fitr",
        "2025-03-31" to "Ram Navami",
        "2025-04-06" to "Mahavir Jayanti",
        "2025-04-10" to "Good Friday",
        "2025-04-13" to "Baisakhi",
        "2025-04-14" to "Vishu",
        "2025-05-12" to "Buddha Purnima",
        "2025-06-07" to "Eid ul-Adha",
        "2025-06-27" to "Muharram",
        "2025-07-06" to "Rath Yatra",
        "2025-08-09" to "Raksha Bandhan",
        "2025-08-16" to "Janmashtami",
        "2025-08-27" to "Milad-un-Nabi",
        "2025-09-05" to "Onam",
        "2025-09-22" to "Navratri Start",
        "2025-10-01" to "Dussehra",
        "2025-10-02" to "Dussehra",
        "2025-10-20" to "Diwali 🪔",
        "2025-10-21" to "Govardhan Puja",
        "2025-10-23" to "Bhai Dooj",
        "2025-11-01" to "Chhath Puja",
        "2025-11-05" to "Guru Nanak Jayanti",
        // ── 2026 ──
        "2026-02-16" to "Maha Shivaratri",
        "2026-03-04" to "Holi 🎨",
        "2026-03-20" to "Eid ul-Fitr",
        "2026-03-19" to "Ram Navami",
        "2026-03-26" to "Mahavir Jayanti",
        "2026-04-03" to "Good Friday",
        "2026-04-13" to "Baisakhi",
        "2026-05-01" to "Buddha Purnima",
        "2026-05-27" to "Eid ul-Adha",
        "2026-06-16" to "Muharram",
        "2026-07-25" to "Rath Yatra",
        "2026-08-28" to "Raksha Bandhan",
        "2026-09-04" to "Janmashtami",
        "2026-08-17" to "Milad-un-Nabi",
        "2026-09-12" to "Navratri Start",
        "2026-09-20" to "Dussehra",
        "2026-10-09" to "Diwali 🪔",
        "2026-10-10" to "Govardhan Puja",
        "2026-10-12" to "Bhai Dooj",
        "2026-10-21" to "Chhath Puja",
        "2026-10-25" to "Guru Nanak Jayanti",
        // ── 2027 ──
        "2027-02-06" to "Maha Shivaratri",
        "2027-02-22" to "Holi 🎨",
        "2027-03-10" to "Eid ul-Fitr",
        "2027-04-08" to "Ram Navami",
        "2027-04-15" to "Mahavir Jayanti",
        "2027-03-26" to "Good Friday",
        "2027-04-13" to "Baisakhi",
        "2027-05-20" to "Buddha Purnima",
        "2027-05-17" to "Eid ul-Adha",
        "2027-06-06" to "Muharram",
        "2027-07-14" to "Rath Yatra",
        "2027-08-17" to "Raksha Bandhan",
        "2027-08-25" to "Janmashtami",
        "2027-08-06" to "Milad-un-Nabi",
        "2027-10-01" to "Navratri Start",
        "2027-10-10" to "Dussehra",
        "2027-10-29" to "Diwali 🪔",
        "2027-10-30" to "Govardhan Puja",
        "2027-11-01" to "Bhai Dooj",
        "2027-11-10" to "Chhath Puja",
        "2027-11-14" to "Guru Nanak Jayanti"
    )

    /**
     * Get the festival name for a date string "yyyy-MM-dd", or null if none.
     */
    fun getFestival(dateStr: String): String? {
        // Check variable festivals first (more specific — year-based)
        variableFestivals[dateStr]?.let { return it }

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
    fun formatDateRich(dateStr: String): RichDate? {
        return try {
            val date = sdf.parse(dateStr) ?: return null
            val cal = Calendar.getInstance().apply { time = date }
            val dayName = SimpleDateFormat("EEEE", Locale.getDefault()).format(date)
            val day = cal.get(Calendar.DAY_OF_MONTH).toString()
            val month = SimpleDateFormat("MMMM", Locale.getDefault()).format(date)
            val year = cal.get(Calendar.YEAR).toString()
            val festival = getFestival(dateStr)
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
