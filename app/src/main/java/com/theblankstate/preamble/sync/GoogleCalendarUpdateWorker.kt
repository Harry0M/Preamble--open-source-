package com.theblankstate.preamble.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.theblankstate.preamble.PreambleApplication

/**
 * Background worker to sync task edits to Google Calendar.
 * The local update happens instantly (optimistic UI), this worker
 * handles the network call in the background.
 */
class GoogleCalendarUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as PreambleApplication
        val taskId = inputData.getString("taskId") ?: return Result.failure()
        val eventId = inputData.getString("eventId") ?: return Result.failure()
        val calendarId = inputData.getString("calendarId") ?: "primary"
        val title = inputData.getString("title") ?: return Result.failure()
        val date = inputData.getString("date") ?: return Result.failure()
        val deadlineTime = inputData.getString("deadlineTime")
        val description = inputData.getString("description")
        val recurrenceType = inputData.getString("recurrenceType")
        val recurrenceInterval = inputData.getInt("recurrenceInterval", 1)
        val recurrenceDays = inputData.getString("recurrenceDays")
        val recurrenceEndDate = inputData.getString("recurrenceEndDate")
        val tags = inputData.getString("tags")
        val endDate = inputData.getString("endDate")
        val endTime = inputData.getString("endTime")

        if (!GoogleCalendarManager.isLinked.value) {
            val task = app.repository.getTaskById(taskId)
            if (task != null) {
                app.repository.updateTask(task.copy(isSyncing = false, syncFailed = false))
            }
            return Result.success()
        }

        return try {
            val success = GoogleCalendarManager.updateCalendarEvent(
                applicationContext, eventId, calendarId, title, date,
                deadlineTime, description,
                recurrenceType,
                if (recurrenceType != null) recurrenceInterval else null,
                if (recurrenceType != null) recurrenceDays else null,
                if (recurrenceType != null) recurrenceEndDate else null,
                tags = tags,
                endDate = endDate,
                endTime = endTime
            )
            if (success) {
                val task = app.repository.getTaskById(taskId)
                if (task != null) {
                    app.repository.updateTask(task.copy(isSyncing = false, syncFailed = false))
                }
                GoogleSyncWorker.enqueueImmediate(
                    applicationContext,
                    forceFull = false,
                    reason = "google_calendar_update"
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
            Log.e("CalUpdateWorker", "Failed to sync update", e)
            val task = app.repository.getTaskById(taskId)
            if (task != null) {
                app.repository.updateTask(task.copy(isSyncing = false, syncFailed = true))
            }
            Result.retry()
        }
    }
}
