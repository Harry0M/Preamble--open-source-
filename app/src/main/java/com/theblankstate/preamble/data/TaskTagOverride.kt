package com.theblankstate.preamble.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores user-assigned tags for Google Calendar/Tasks events.
 * Keyed by stable Google ID (stripped of "gcal_"/"gtask_" prefix).
 * Synced to Firestore at users/{uid}/tagOverrides/{googleId}
 * so tags persist across syncs and devices.
 */
@Entity(tableName = "task_tag_overrides")
data class TaskTagOverride(
    @PrimaryKey
    val googleId: String,
    val tags: String,           // comma-separated user-assigned tags (excluding source tag)
    val updatedTimestamp: Long = System.currentTimeMillis()
)
