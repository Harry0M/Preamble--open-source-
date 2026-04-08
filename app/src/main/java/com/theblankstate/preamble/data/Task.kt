package com.theblankstate.preamble.data

import androidx.compose.runtime.Stable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

// Nested data classes for advanced fields
@Stable
data class Media(
    val url: String,
    val type: String, // MIME type
    val size: Long? = null
)

@Stable
data class Link(
    val url: String,
    val title: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)

@Stable
data class Subtask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val isCompleted: Boolean = false
)

@Stable
@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["createdDate"]),
        Index(value = ["recurrenceType", "recurrenceParentId"]),
        Index(value = ["recurrenceParentId", "createdDate"]),
        Index(value = ["isCompleted", "createdDate"]),
        Index(value = ["source"])
    ]
)
data class Task(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val isCompleted: Boolean = false,
    val createdDate: String,
    val createdTimestamp: Long = System.currentTimeMillis(),
    val completedTimestamp: Long? = null,
    val completedDate: String? = null,
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
    // ── Extended Google Calendar metadata ──
    val attendeesJson: String? = null,            // JSON: [{email, displayName, responseStatus}]
    val remindersJson: String? = null,            // JSON: [{method, minutes}]
    val htmlLink: String? = null,                 // Direct link to Google Calendar event
    val organizerJson: String? = null,            // JSON: {email, displayName}
    val visibility: String? = null,               // default/public/private/confidential
    val attachmentsJson: String? = null,          // JSON: [{title, fileUrl, mimeType}]
    val conferencePhone: String? = null,          // Phone dial-in with PIN
    // ── Extended Google Tasks metadata ──
    val webViewLink: String? = null,              // Browser link to task
    val taskLinksJson: String? = null,            // JSON: [{type, description, link}]
    // ── Independent Alarm overrides ──
    val customAlarmTimeMs: Long? = null,           // Explicit trigger override decoupled from `deadlineTime`
    val isAlarmPaused: Boolean = false,             // User completely halted the alarm from ringing
    // ── Advanced Fields ──
    val mediaJson: String? = null,                 // JSON: Media object
    val linksJson: String? = null,                 // JSON: List<Link>
    val subtasksJson: String? = null,              // JSON: List<Subtask>
    val syncMetadataJson: String? = null,          // JSON: Map<String, Any> for future sync
    val snoozedUntil: Long? = null                 // Epoch millis: hide rollover task until this time
) {
    val isCalendarEvent: Boolean get() = source == "google_calendar"
    val isGoogleTask: Boolean get() = source == "google_tasks"
    val isRecurrenceTemplate: Boolean get() = recurrenceType != null && recurrenceParentId == null
    val isRecurrenceInstance: Boolean get() = recurrenceParentId != null
    val isSubtask: Boolean get() = parentTaskId != null
    // Calendar event type helpers
    val isHoliday: Boolean get() = eventType == "holiday"
    val isBirthday: Boolean get() = eventType == "birthday"
    val isInfoOnly: Boolean get() = eventType in listOf("holiday", "birthday", "focusTime", "outOfOffice")
    // Extended metadata helpers
    val hasAttendees: Boolean get() = !attendeesJson.isNullOrBlank()
    val hasReminders: Boolean get() = !remindersJson.isNullOrBlank()
    val hasAttachments: Boolean get() = !attachmentsJson.isNullOrBlank()
    // Lazily parsed: computed once per instance, not on every access
    val tagList: List<String> by lazy { tags?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList() }
    val media: Media? by lazy { mediaJson?.let { Gson().fromJson(it, Media::class.java) } }
    val links: List<Link> by lazy { linksJson?.let { Gson().fromJson(it, object : TypeToken<List<Link>>() {}.type) } ?: emptyList() }
    val subtasks: List<Subtask> by lazy { subtasksJson?.let { Gson().fromJson(it, object : TypeToken<List<Subtask>>() {}.type) } ?: emptyList() }
    val syncMetadata: Map<String, Any>? by lazy { syncMetadataJson?.let { Gson().fromJson(it, object : TypeToken<Map<String, Any>>() {}.type) } }
}

