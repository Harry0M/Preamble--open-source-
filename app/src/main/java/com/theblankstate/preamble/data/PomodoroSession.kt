package com.theblankstate.preamble.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "pomodoro_sessions",
    indices = [
        Index(value = ["taskId"]),
        Index(value = ["date"]),
        Index(value = ["startTimestamp"])
    ]
)
data class PomodoroSession(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val taskId: String? = null,
    val taskTitle: String? = null,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val durationSeconds: Int,
    val date: String // yyyy-MM-dd
)
