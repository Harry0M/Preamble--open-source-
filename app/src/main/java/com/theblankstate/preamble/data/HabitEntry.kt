package com.theblankstate.preamble.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "habit_entries",
    indices = [
        Index(value = ["habitId"]),
        Index(value = ["date"]),
        Index(value = ["habitId", "date"], unique = true)
    ]
)
data class HabitEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val habitId: String,           // FK → Task.id where taskCategory = "habit"
    val date: String,              // "yyyy-MM-dd"
    val isCompleted: Boolean = false,
    val completedTimestamp: Long? = null,
    val value: Float? = null,      // For measurable habits (glasses of water, pages read)
    val note: String? = null       // Optional daily note
)

data class HabitHeatmapEntry(
    val date: String,
    val isCompleted: Boolean
)
