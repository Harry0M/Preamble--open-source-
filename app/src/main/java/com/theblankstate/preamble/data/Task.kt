package com.theblankstate.preamble.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val isCompleted: Boolean = false,
    val createdDate: String,
    val createdTimestamp: Long = System.currentTimeMillis(),
    val completedTimestamp: Long? = null,
    val deadlineTime: String? = null,
    val updatedTimestamp: Long = System.currentTimeMillis(),
    val source: String = "local",  // "local", "google_calendar", "google_tasks"
    val deletedFromGoogle: Boolean = false,
    val isSyncing: Boolean = false,
    val syncFailed: Boolean = false,
    val priority: Int = 0,  // 0=None, 1=Low, 2=Medium, 3=High
    val description: String? = null,
    val recurrenceType: String? = null,       // "daily","weekly","monthly","yearly","custom"
    val recurrenceInterval: Int? = null,       // every N units
    val recurrenceDays: String? = null,        // comma-separated Calendar.DAY_OF_WEEK values e.g. "2,4,6"
    val recurrenceEndDate: String? = null,     // "yyyy-MM-dd"
    val recurrenceParentId: String? = null,    // ID of template task that generated this instance
    val parentTaskId: String? = null,          // ID of parent task (for subtasks)
    val tags: String? = null,                   // Comma-separated tag names e.g. "work,personal"
    val googleCalendarId: String? = null,        // Calendar ID for write-back (e.g. "primary")
    val googleRecurrenceInfo: String? = null,     // Human-readable recurrence text from Google Cal (display only)
    // ── New Google Calendar fields ──
    val eventType: String? = null,               // "default","birthday","holiday","focusTime","outOfOffice","fromGmail"
    val calendarName: String? = null,            // Source calendar name e.g. "Holidays in India", "Work"
    val location: String? = null,                // Event location/venue
    val endTime: String? = null,                 // Event end time "HH:mm" (null for all-day)
    val meetingLink: String? = null,               // Google Meet / conference URL
    // ── Independent Alarm overrides ──
    val customAlarmTimeMs: Long? = null,           // Explicit trigger override decoupled from `deadlineTime`
    val isAlarmPaused: Boolean = false             // User completely halted the alarm from ringing
) {
    val isCalendarEvent: Boolean get() = source == "google_calendar"
    val isGoogleTask: Boolean get() = source == "google_tasks"
    val isRecurrenceTemplate: Boolean get() = recurrenceType != null && recurrenceParentId == null
    val isRecurrenceInstance: Boolean get() = recurrenceParentId != null
    val isSubtask: Boolean get() = parentTaskId != null
    val tagList: List<String> get() = tags?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    // Calendar event type helpers
    val isHoliday: Boolean get() = eventType == "holiday"
    val isBirthday: Boolean get() = eventType == "birthday"
    val isInfoOnly: Boolean get() = eventType in listOf("holiday", "birthday", "focusTime", "outOfOffice")
}

