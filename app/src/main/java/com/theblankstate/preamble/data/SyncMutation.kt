package com.theblankstate.preamble.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_mutations")
data class SyncMutation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: String,
    val provider: String, // "GOOGLE_CALENDAR", "GOOGLE_TASKS", "FIRESTORE"
    val actionType: String, // "CREATE", "UPDATE", "DELETE", "TOGGLE_COMPLETE"
    val payloadJson: String, // Serialized payload (e.g. task details or changes)
    val timestamp: Long = System.currentTimeMillis(),
    val attemptCount: Int = 0
)
