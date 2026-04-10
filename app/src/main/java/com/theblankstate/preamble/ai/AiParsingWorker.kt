package com.theblankstate.preamble.ai

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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

        val apiKey = BuildConfig.AI_API_KEY
        if (apiKey.isBlank()) {
            // No AI configured — just clear syncing and keep raw task
            app.repository.updateTask(task.copy(isSyncing = false))
            return Result.success()
        }

        val provider: AiProvider = try {
            val providerName = BuildConfig.AI_PROVIDER
            when (AiProviderType.valueOf(providerName)) {
                AiProviderType.MISTRAL -> MistralProvider(apiKey)
                AiProviderType.OPENAI -> OpenAiProvider(apiKey)
                AiProviderType.GEMINI -> GeminiProvider(apiKey)
                AiProviderType.CLAUDE -> ClaudeProvider(apiKey)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AI provider", e)
            app.repository.updateTask(task.copy(isSyncing = false))
            return Result.success()
        }

        return try {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val smartBreakdown = applicationContext.getSharedPreferences("preamble_prefs", android.content.Context.MODE_PRIVATE)
                .getBoolean("ai_smart_breakdown", false)
            val systemMsg = ChatMessage("system",
                AiPromptFactory.buildSystemPrompt(smartBreakdown = smartBreakdown)
            )
            val userMsg = ChatMessage("user", rawText)

            val response = provider.chat(listOf(systemMsg, userMsg), TaskTools.tools)

            if (!response.toolCalls.isNullOrEmpty()) {
                for (call in response.toolCalls) {
                    when (call.name) {
                        "add_task", "set_reminder" -> {
                            val refinedTitle = call.arguments["title"] ?: rawText
                            val date = call.arguments["date"]
                            val time = call.arguments["deadline_time"] ?: call.arguments["time"]
                            val tags = call.arguments["tags"]
                            val priority = call.arguments["priority"]?.toIntOrNull() ?: 0
                            val recurrence = call.arguments["recurrence"]
                            val description = call.arguments["description"]
                            val subtasksList = TaskTools.parseSubtasks(call.arguments["subtasks"])
                            val validRecurrence = recurrence?.takeIf { it in listOf("daily", "weekly", "monthly", "yearly") }

                            val updated = task.copy(
                                title = refinedTitle,
                                createdDate = date ?: today,
                                deadlineTime = time,
                                tags = tags,
                                priority = priority,
                                description = description ?: task.description,
                                recurrenceType = validRecurrence ?: task.recurrenceType,
                                isSyncing = false,
                                updatedTimestamp = System.currentTimeMillis()
                            )
                            app.repository.updateTask(updated)
                            if (tags != null) {
                                app.repository.saveTagOverride(taskId, tags)
                            }
                            // Add subtasks if AI generated them
                            if (subtasksList.isNotEmpty()) {
                                app.repository.addSubtasks(taskId, subtasksList)
                                Log.d(TAG, "Added ${subtasksList.size} subtasks to task $taskId")
                            }
                        }
                        else -> {
                            // For modify/delete/etc, just clear syncing on the placeholder
                            app.repository.updateTask(task.copy(isSyncing = false))
                        }
                    }
                }
            } else {
                // No tool calls — AI couldn't parse input, save raw task as-is
                Log.w(TAG, "AI returned no tool calls for task $taskId, saving as-is")
                app.repository.updateTask(task.copy(isSyncing = false))
            }

            Log.d(TAG, "AI parsing succeeded for task $taskId")
            Result.success()

        } catch (e: java.net.UnknownHostException) {
            // Network error — retry with exponential backoff
            Log.e(TAG, "Network error for task $taskId, will retry", e)
            Result.retry()
        } catch (e: java.net.SocketTimeoutException) {
            // Timeout — retry with exponential backoff
            Log.e(TAG, "Timeout for task $taskId, will retry", e)
            Result.retry()
        } catch (e: java.io.IOException) {
            // I/O error (connection reset, etc.) — retry
            Log.e(TAG, "I/O error for task $taskId, will retry", e)
            Result.retry()
        } catch (e: Exception) {
            // Non-network error (JSON parse failure, API returned garbage, etc.)
            // Don't retry endlessly — save the task as-is and move on
            Log.e(TAG, "AI parsing failed (non-retryable) for task $taskId, saving raw", e)
            app.repository.updateTask(task.copy(isSyncing = false))
            Result.success()
        }
    }

    companion object {
        private const val TAG = "AiParsingWorker"
    }
}
