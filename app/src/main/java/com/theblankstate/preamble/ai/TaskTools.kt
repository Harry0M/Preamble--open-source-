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

    /**
     * Parse a comma-separated subtasks string into a clean list.
     * Handles whitespace, empty entries, and deduplication.
     */
    fun parseSubtasks(subtasksStr: String?): List<String> {
        if (subtasksStr.isNullOrBlank()) return emptyList()
        return subtasksStr.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    val tools = listOf(
        AiTool(
            name = "add_task",
            description = "Add a new task. If the user specifies a date, use date parameter (format: YYYY-MM-DD). If they specify a time/deadline, use deadline_time (format: HH:mm in 24h). ALWAYS provide at least 1 tag. If user mentions a list of items, create subtasks.",
            parameters = listOf(
                ToolParam("title", "string", "Short but self-explanatory task title. Remove temporal/urgency words but keep core action and key context. If list of items, show first 2-3 items + 'etc'.", required = true),
                ToolParam("date", "string", "Date for the task in YYYY-MM-DD format. Omit for today.", required = false),
                ToolParam("deadline_time", "string", "Deadline time in HH:mm 24-hour format", required = false),
                ToolParam("priority", "integer", "Priority: 0=None, 1=Low, 2=Medium, 3=High. Default to 1 for general tasks.", required = false),
                ToolParam("tags", "string", "REQUIRED. 1-4 comma-separated tags from: ${com.theblankstate.preamble.data.PredefinedTags.aiTagNames}. Example: 'Health,Fitness' or 'Work,Meeting'.", required = true),
                ToolParam("recurrence", "string", "Recurrence pattern: daily, weekly, monthly, yearly. Only set if user explicitly requests repetition.", required = false),
                ToolParam("subtasks", "string", "Comma-separated list of subtask items. Only when user mentions 3+ related items/steps. Example: 'Blue color,Yellow color,Pink color' or 'Lamp,Stove,Tent,Powerbank'. Do NOT create subtasks for simple single tasks.", required = false),
                ToolParam("description", "string", "Brief 1-2 sentence description providing context about the task. Always generate this. Example: 'Bazaar se art supplies kharidne hain' or 'Regular health checkup appointment'. Use same language as user.", required = false)
            )
        ),
        AiTool(
            name = "modify_task",
            description = "Modify an existing task's properties. Finds the closest matching task.",
            parameters = listOf(
                ToolParam("target_title", "string", "The current title of the task to modify", required = true),
                ToolParam("new_title", "string", "New title (if changing)", required = false),
                ToolParam("new_date", "string", "New date in YYYY-MM-DD format", required = false),
                ToolParam("new_time", "string", "New deadline time in HH:mm format", required = false),
                ToolParam("new_priority", "integer", "New priority: 0=None, 1=Low, 2=Medium, 3=High", required = false),
                ToolParam("new_tags", "string", "New tag matching app predefined list", required = false)
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
                ToolParam("date", "string", "Date in YYYY-MM-DD format. Omit for today.", required = false),
                ToolParam("subtasks", "string", "Comma-separated subtask items if applicable.", required = false),
                ToolParam("description", "string", "Brief description providing context.", required = false)
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
                val priority = call.arguments["priority"]?.toIntOrNull() ?: 0
                val tags = call.arguments["tags"]
                val description = call.arguments["description"]
                val subtasksList = parseSubtasks(call.arguments["subtasks"])
                if (date != null && !isValidDate(date)) return "Error: invalid date format '$date', expected YYYY-MM-DD"
                if (deadlineTime != null && !isValidTime(deadlineTime)) return "Error: invalid time format '$deadlineTime', expected HH:mm"
                viewModel.addTask(title, date = date, deadlineTime = deadlineTime, priority = priority, tags = tags, description = description, subtasks = subtasksList)
                "Task \"$title\" added successfully" +
                        (if (date != null) " for $date" else "") +
                        (if (deadlineTime != null) " with deadline at $deadlineTime" else "") +
                        (if (priority > 0) " [Priority $priority]" else "") +
                        (if (tags != null) " [Tag: $tags]" else "") +
                        (if (subtasksList.isNotEmpty()) " with ${subtasksList.size} subtasks" else "")
            }

            "modify_task" -> {
                val targetTitle = call.arguments["target_title"] ?: return "Error: target_title is required"
                val task = findMatchingTask(targetTitle, todayTasks)
                if (task != null) {
                    val newTitle = call.arguments["new_title"] ?: task.title
                    val newDate = call.arguments["new_date"] ?: task.createdDate
                    val newTime = call.arguments["new_time"] ?: task.deadlineTime
                    val newPriority = call.arguments["new_priority"]?.toIntOrNull() ?: task.priority
                    val newTags = call.arguments["new_tags"] ?: task.tags
                    if (newDate != task.createdDate && !isValidDate(newDate)) return "Error: invalid date format '$newDate', expected YYYY-MM-DD"
                    if (newTime != null && newTime != task.deadlineTime && !isValidTime(newTime)) return "Error: invalid time format '$newTime', expected HH:mm"
                    
                    viewModel.updateTask(task, newTitle = newTitle, newDate = newDate, newDeadlineTime = newTime, newPriority = newPriority, newDescription = task.description, newTags = newTags)
                    "Task \"${task.title}\" modified successfully"
                } else {
                    "No task found matching \"$targetTitle\""
                }
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
                val description = call.arguments["description"]
                val subtasksList = parseSubtasks(call.arguments["subtasks"])
                if (!isValidTime(time)) return "Error: invalid time format '$time', expected HH:mm"
                if (date != null && !isValidDate(date)) return "Error: invalid date format '$date', expected YYYY-MM-DD"
                viewModel.addTask(title, date, time, description = description, subtasks = subtasksList)
                "Reminder set: \"$title\" at $time" + (if (date != null) " on $date" else " today")
            }

            else -> "Unknown tool: ${call.name}"
        }
    }

    fun findMatchingTask(query: String, tasks: List<Task>): Task? {
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
