package com.theblankstate.preamble.ai

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.theblankstate.preamble.BuildConfig
import com.theblankstate.preamble.PreambleApplication
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Background worker that retries AI parsing for tasks that failed due to network
 * or other transient errors. The raw task is already in the DB with isSyncing=true.
 * On success, silently updates the task with AI-refined data (title, date, time, tags,
 * priority, recurrence, description, subtasks).
 * On network failure, returns Result.retry() for exponential backoff.
 * On non-network failure (bad input, API error), saves task as-is and returns Result.success().
 *
 * CLOUD MODE: When user is logged in, routes through Cloud Function (aiParseTask).
 *   - No API keys needed in APK
 *   - Same prompt system, same quality
 *   - Falls back to local if cloud fails
 */
class AiParsingWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as PreambleApplication
        val taskId = inputData.getString("taskId") ?: return Result.failure()
        val rawText = inputData.getString("rawText") ?: return Result.failure()

        val task = app.repository.getTaskById(taskId)
        if (task == null) {
            // Task was deleted by user in the meantime, nothing to do
            return Result.success()
        }

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val subtaskIntensity = applicationContext.getSharedPreferences("preamble_prefs", android.content.Context.MODE_PRIVATE)
            .getInt("ai_subtask_intensity", 0)

        // Try cloud first if logged in
        if (FirebaseAuth.getInstance().currentUser != null) {
            try {
                val result = CloudAiService.parseTask(
                    rawText = rawText,
                    subtaskIntensity = subtaskIntensity,
                    isNotificationEdit = false,
                )

                if (result != null && result.toolCalls.isNotEmpty()) {
                    applyToolCalls(app, task, taskId, result.toolCalls, today)
                    Log.d(TAG, "Cloud AI parsing succeeded for task $taskId")
                    return Result.success()
                }
                // Cloud returned no tool calls — fall through to local
            } catch (e: java.net.UnknownHostException) {
                Log.e(TAG, "Cloud: Network error for task $taskId, will retry", e)
                return Result.retry()
            } catch (e: java.net.SocketTimeoutException) {
                Log.e(TAG, "Cloud: Timeout for task $taskId, will retry", e)
                return Result.retry()
            } catch (e: java.io.IOException) {
                Log.e(TAG, "Cloud: I/O error for task $taskId, will retry", e)
                return Result.retry()
            } catch (e: Exception) {
                Log.w(TAG, "Cloud parsing failed, trying local fallback", e)
                // Fall through to local
            }
        }

        // LOCAL FALLBACK — direct API call (for offline or not logged in)
        val apiKey = BuildConfig.AI_API_KEY
        if (apiKey.isBlank()) {
            // No AI configured — just clear syncing and keep raw task
            app.repository.updateTask(task.copy(isSyncing = false))
            return Result.success()
        }

        val provider: AiProvider = AiProviderFactory.main() ?: run {
            Log.e(TAG, "AI provider unavailable")
            app.repository.updateTask(task.copy(isSyncing = false))
            return Result.success()
        }

        return try {
            val systemMsg = ChatMessage("system",
                AiPromptFactory.buildSystemPrompt(subtaskIntensity = subtaskIntensity)
            )
            val userMsg = ChatMessage("user", rawText)

            val response = provider.chat(listOf(systemMsg, userMsg), TaskTools.tools)

            if (!response.toolCalls.isNullOrEmpty()) {
                applyLocalToolCalls(app, task, taskId, response.toolCalls, today)
            } else {
                // No tool calls — AI couldn't parse input, save raw task as-is
                Log.w(TAG, "AI returned no tool calls for task $taskId, saving as-is")
                app.repository.updateTask(task.copy(isSyncing = false))
            }

            Log.d(TAG, "Local AI parsing succeeded for task $taskId")
            Result.success()

        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "Network error for task $taskId, will retry", e)
            Result.retry()
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Timeout for task $taskId, will retry", e)
            Result.retry()
        } catch (e: java.io.IOException) {
            Log.e(TAG, "I/O error for task $taskId, will retry", e)
            Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "AI parsing failed (non-retryable) for task $taskId, saving raw", e)
            app.repository.updateTask(task.copy(isSyncing = false))
            Result.success()
        }
    }

    /**
     * Apply tool calls from CLOUD response (CloudToolCall format).
     */
    private suspend fun applyToolCalls(
        app: PreambleApplication,
        task: com.theblankstate.preamble.data.Task,
        taskId: String,
        toolCalls: List<CloudToolCall>,
        today: String,
    ) {
        for (call in toolCalls) {
            when (call.name) {
                "add_task", "set_reminder" -> {
                    applyParsedTask(app, task, taskId, call.args, today)
                }
                else -> {
                    app.repository.updateTask(task.copy(isSyncing = false))
                }
            }
        }
    }

    /**
     * Apply tool calls from LOCAL response (ToolCall format).
     */
    private suspend fun applyLocalToolCalls(
        app: PreambleApplication,
        task: com.theblankstate.preamble.data.Task,
        taskId: String,
        toolCalls: List<ToolCall>,
        today: String,
    ) {
        for (call in toolCalls) {
            when (call.name) {
                "add_task", "set_reminder" -> {
                    applyParsedTask(app, task, taskId, call.arguments, today)
                }
                else -> {
                    app.repository.updateTask(task.copy(isSyncing = false))
                }
            }
        }
    }

    /**
     * Shared logic: apply parsed task arguments to the existing task entity.
     */
    private suspend fun applyParsedTask(
        app: PreambleApplication,
        task: com.theblankstate.preamble.data.Task,
        taskId: String,
        args: Map<String, String>,
        today: String,
    ) {
        val rawText = task.title
        val refinedTitle = args["title"] ?: rawText
        val date = args["date"]
        val time = args["deadline_time"] ?: args["time"]
        val tags = args["tags"]
        val priority = args["priority"]?.toIntOrNull() ?: 0
        val recurrence = args["recurrence"]
        val description = args["description"]
        val subtasksList = TaskTools.parseSubtasks(args["subtasks"])
        val validRecurrence = recurrence?.takeIf { it in listOf("daily", "weekly", "monthly", "yearly") }
        val rolloverDecision = TaskTools.decideRollover(
            rolloverArg = args["rollover"],
            title = refinedTitle,
            date = date,
            deadlineTime = time,
            recurrence = validRecurrence,
            today = today
        )
        val effectiveRecurrence = validRecurrence ?: if (rolloverDecision) "rollover" else task.recurrenceType

        var updated = task.copy(
            title = refinedTitle,
            createdDate = date ?: today,
            deadlineTime = time,
            tags = tags,
            priority = priority,
            description = description ?: task.description,
            recurrenceType = effectiveRecurrence,
            isSyncing = false,
            updatedTimestamp = System.currentTimeMillis()
        )
        // Auto-set default reminder if none exist
        if (updated.remindersJson == null) {
            val defaultReminder = if (time != null) {
                // Tasks with deadline: 10-min-before reminder
                com.theblankstate.preamble.data.Reminder.DEFAULT
            } else {
                // All-day tasks (no deadline): 9 AM morning reminder
                com.theblankstate.preamble.data.Reminder.defaultAllDay(updated.createdDate)
            }
            if (defaultReminder != null) {
                updated = updated.copy(remindersJson = com.theblankstate.preamble.data.Reminder.toJson(listOf(defaultReminder)))
            }
        }
        app.repository.updateTask(updated)
        if (tags != null) {
            app.repository.saveTagOverride(taskId, tags)
        }
        // Schedule alarm reminders
        if (updated.remindersJson != null) {
            com.theblankstate.preamble.notification.TaskAlarmManager.scheduleReminders(applicationContext, updated)
            Log.d(TAG, "Scheduled reminders for task $taskId")
        }
        // Add subtasks if AI generated them
        if (subtasksList.isNotEmpty()) {
            app.repository.addSubtasks(taskId, subtasksList)
            Log.d(TAG, "Added ${subtasksList.size} subtasks to task $taskId")
        }
    }

    companion object {
        private const val TAG = "AiParsingWorker"
    }
}
