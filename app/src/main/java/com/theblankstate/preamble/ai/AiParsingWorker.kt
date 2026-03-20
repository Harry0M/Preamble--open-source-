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
 * On success, silently updates the task with AI-refined data.
 * On failure, returns Result.retry() for exponential backoff.
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
            val systemMsg = ChatMessage("system",
                AiPromptFactory.buildSystemPrompt()
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
                            val validRecurrence = recurrence?.takeIf { it in listOf("daily", "weekly", "monthly", "yearly") }

                            val updated = task.copy(
                                title = refinedTitle,
                                createdDate = date ?: today,
                                deadlineTime = time,
                                tags = tags,
                                priority = priority,
                                recurrenceType = validRecurrence ?: task.recurrenceType,
                                isSyncing = false,
                                updatedTimestamp = System.currentTimeMillis()
                            )
                            app.repository.updateTask(updated)
                            if (tags != null) {
                                app.repository.saveTagOverride(taskId, tags)
                            }
                        }
                        else -> {
                            // For modify/delete/etc, just clear syncing on the placeholder
                            app.repository.updateTask(task.copy(isSyncing = false))
                        }
                    }
                }
            } else {
                // No tool calls — keep raw task as-is
                app.repository.updateTask(task.copy(isSyncing = false))
            }

            Log.d(TAG, "AI parsing succeeded for task $taskId")
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "AI parsing failed for task $taskId, will retry", e)
            // Keep isSyncing=true so UI still shows the shimmer/spinner
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "AiParsingWorker"
    }
}
