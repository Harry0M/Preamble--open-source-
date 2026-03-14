package com.theblankstate.preamble.ai

import com.theblankstate.preamble.data.Task
import com.theblankstate.preamble.viewmodel.TaskViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Defines tools (functions) that any AI can call to control the app's tasks.
 */
object TaskTools {

    private val DATE_PATTERN = Regex("""^\d{4}-\d{2}-\d{2}$""")
    private val TIME_PATTERN = Regex("""^\d{2}:\d{2}$""")

    private fun isValidDate(date: String): Boolean {
        if (!DATE_PATTERN.matches(date)) return false
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            sdf.isLenient = false
            sdf.parse(date) != null
        } catch (_: Exception) { false }
    }

    private fun isValidTime(time: String): Boolean {
        if (!TIME_PATTERN.matches(time)) return false
        val parts = time.split(":")
        val h = parts[0].toIntOrNull() ?: return false
        val m = parts[1].toIntOrNull() ?: return false
        return h in 0..23 && m in 0..59
    }

    val tools = listOf(
        AiTool(
            name = "add_task",
            description = "Add a new task. If the user specifies a date, use date parameter (format: YYYY-MM-DD). If they specify a time/deadline, use deadline_time (format: HH:mm in 24h).",
            parameters = listOf(
                ToolParam("title", "string", "The task title/description", required = true),
                ToolParam("date", "string", "Date for the task in YYYY-MM-DD format. Omit for today.", required = false),
                ToolParam("deadline_time", "string", "Deadline time in HH:mm 24-hour format", required = false)
            )
        ),
        AiTool(
            name = "delete_task",
            description = "Delete a task by its title. Finds the closest matching task.",
            parameters = listOf(
                ToolParam("title", "string", "The task title to delete", required = true)
            )
        ),
        AiTool(
            name = "complete_task",
            description = "Mark a task as completed by its title.",
            parameters = listOf(
                ToolParam("title", "string", "The task title to mark complete", required = true)
            )
        ),
        AiTool(
            name = "list_tasks",
            description = "List all tasks for a given date. Defaults to today if no date given.",
            parameters = listOf(
                ToolParam("date", "string", "Date in YYYY-MM-DD format. Omit for today.", required = false)
            )
        ),
        AiTool(
            name = "set_reminder",
            description = "Add a task with an alarm reminder at a specific time.",
            parameters = listOf(
                ToolParam("title", "string", "The task/reminder title", required = true),
                ToolParam("time", "string", "Time for reminder in HH:mm 24-hour format", required = true),
                ToolParam("date", "string", "Date in YYYY-MM-DD format. Omit for today.", required = false)
            )
        )
    )

    suspend fun execute(
        call: ToolCall,
        viewModel: TaskViewModel,
        todayTasks: List<Task>
    ): String {
        return when (call.name) {
            "add_task" -> {
                val title = call.arguments["title"] ?: return "Error: title is required"
                val date = call.arguments["date"]
                val deadlineTime = call.arguments["deadline_time"]
                if (date != null && !isValidDate(date)) return "Error: invalid date format '$date', expected YYYY-MM-DD"
                if (deadlineTime != null && !isValidTime(deadlineTime)) return "Error: invalid time format '$deadlineTime', expected HH:mm"
                viewModel.addTask(title, date, deadlineTime)
                "Task \"$title\" added successfully" +
                        (if (date != null) " for $date" else "") +
                        (if (deadlineTime != null) " with deadline at $deadlineTime" else "")
            }

            "delete_task" -> {
                val title = call.arguments["title"] ?: return "Error: title is required"
                val task = findMatchingTask(title, todayTasks)
                if (task != null) {
                    viewModel.deleteTask(task)
                    "Task \"${task.title}\" deleted successfully"
                } else {
                    "No task found matching \"$title\""
                }
            }

            "complete_task" -> {
                val title = call.arguments["title"] ?: return "Error: title is required"
                val task = findMatchingTask(title, todayTasks)
                if (task != null) {
                    if (!task.isCompleted) viewModel.toggleTask(task)
                    "Task \"${task.title}\" marked as completed ✓"
                } else {
                    "No task found matching \"$title\""
                }
            }

            "list_tasks" -> {
                if (todayTasks.isEmpty()) {
                    "No tasks for today"
                } else {
                    val list = todayTasks.mapIndexed { i, t ->
                        val status = if (t.isCompleted) "✓" else "○"
                        val time = if (t.deadlineTime != null) " (${t.deadlineTime})" else ""
                        "${i + 1}. $status ${t.title}$time"
                    }.joinToString("\n")
                    "Tasks for today:\n$list"
                }
            }

            "set_reminder" -> {
                val title = call.arguments["title"] ?: return "Error: title is required"
                val time = call.arguments["time"] ?: return "Error: time is required"
                val date = call.arguments["date"]
                if (!isValidTime(time)) return "Error: invalid time format '$time', expected HH:mm"
                if (date != null && !isValidDate(date)) return "Error: invalid date format '$date', expected YYYY-MM-DD"
                viewModel.addTask(title, date, time)
                "Reminder set: \"$title\" at $time" + (if (date != null) " on $date" else " today")
            }

            else -> "Unknown tool: ${call.name}"
        }
    }

    private fun findMatchingTask(query: String, tasks: List<Task>): Task? {
        val lower = query.lowercase()
        // Exact match first
        tasks.find { it.title.lowercase() == lower }?.let { return it }
        // Contains match
        tasks.find { it.title.lowercase().contains(lower) }?.let { return it }
        // Query contained in task
        tasks.find { lower.contains(it.title.lowercase()) }?.let { return it }
        return null
    }
}
