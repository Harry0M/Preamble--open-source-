package com.theblankstate.preamble.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val isCompleted: Boolean = false,
    val createdDate: String,
    val createdTimestamp: Long = System.currentTimeMillis(),
    val completedTimestamp: Long? = null,
    val deadlineTime: String? = null
)
