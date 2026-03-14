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
    val priority: Int = 0,  // 0=None, 1=Low, 2=Medium, 3=High
    val description: String? = null,
    val recurrenceType: String? = null,       // "daily","weekly","monthly","yearly","custom"
    val recurrenceInterval: Int? = null,       // every N units
    val recurrenceDays: String? = null,        // comma-separated Calendar.DAY_OF_WEEK values e.g. "2,4,6"
    val recurrenceEndDate: String? = null,     // "yyyy-MM-dd"
    val recurrenceParentId: String? = null     // ID of template task that generated this instance
) {
    val isCalendarEvent: Boolean get() = source == "google_calendar"
    val isGoogleTask: Boolean get() = source == "google_tasks"
    val isRecurrenceTemplate: Boolean get() = recurrenceType != null && recurrenceParentId == null
    val isRecurrenceInstance: Boolean get() = recurrenceParentId != null
}
