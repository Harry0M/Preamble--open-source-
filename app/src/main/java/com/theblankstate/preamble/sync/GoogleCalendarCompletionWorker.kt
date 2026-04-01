package com.theblankstate.preamble.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.theblankstate.preamble.PreambleApplication

/**
 * Background worker to sync task completion status to Google Calendar.
 * The local toggle happens instantly (optimistic UI), this worker
 * handles the network call in the background.
 */
class GoogleCalendarCompletionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as PreambleApplication
        val taskId = inputData.getString("taskId") ?: return Result.failure()
        val eventId = inputData.getString("eventId") ?: return Result.failure()
        val calendarId = inputData.getString("calendarId") ?: "primary"
        val completed = inputData.getBoolean("completed", false)

        if (!GoogleCalendarManager.isLinked.value) {
            return Result.success()
        }

        return try {
            val success = GoogleCalendarManager.updateCalendarEventCompletion(
                applicationContext, eventId, calendarId, completed
            )
            if (success) {
                val task = app.repository.getTaskById(taskId)
                if (task != null) {
                    app.repository.updateTask(task.copy(isSyncing = false, syncFailed = false))
                }
                GoogleSyncWorker.enqueueImmediate(
                    applicationContext,
                    forceFull = false,
                    reason = "google_calendar_completion"
                )
                Result.success()
            } else {
                val task = app.repository.getTaskById(taskId)
                if (task != null) {
                    app.repository.updateTask(task.copy(isSyncing = false, syncFailed = true))
                }
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("CalCompletionWorker", "Failed to sync completion", e)
            val task = app.repository.getTaskById(taskId)
            if (task != null) {
                app.repository.updateTask(task.copy(isSyncing = false, syncFailed = true))
            }
            Result.retry()
        }
    }
}
