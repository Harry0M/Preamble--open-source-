package com.theblankstate.preamble.data

data class SourceBreakdown(
    val source: String,
    val count: Int,
    val completed: Int
)

data class TagCompletionRow(
    val tags: String?,
    val isCompleted: Boolean
)

data class RolloverHealthData(
    val activeCount: Int,
    val averageDaysPending: Float,
    val oldestTaskTitle: String?,
    val oldestDaysPending: Int,
    val completionRate: Float
)

data class TagStats(
    val tag: String,
    val totalCount: Int,
    val completedCount: Int,
    val completionRate: Float
)

data class TaskTypeBreakdown(
    val label: String,
    val count: Int,
    val percentage: Float
)
