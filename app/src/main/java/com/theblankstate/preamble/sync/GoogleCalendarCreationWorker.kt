package com.theblankstate.preamble.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.theblankstate.preamble.PreambleApplication

class GoogleCalendarCreationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as PreambleApplication
        val pendingTaskId = inputData.getString("localTaskId") ?: return Result.failure()

        val task = app.repository.getTaskById(pendingTaskId) ?: return Result.success()

        if (!GoogleCalendarManager.isLinked.value) {
            app.repository.updateTask(task.copy(isSyncing = false, syncFailed = false))
            return Result.success()
        }

        try {
            val eventId = GoogleCalendarManager.createCalendarEvent(
                applicationContext,
                task.title,
                task.createdDate,
                task.deadlineTime,
                "primary",
                task.description,
                task.recurrenceType,
                task.recurrenceInterval ?: 1,
                task.recurrenceDays,
                task.recurrenceEndDate,
                tags = task.tags
            )

            if (eventId != null) {
                // Success
                val finalTask = task.copy(
                    id = "gcal_$eventId",
                    source = "google_calendar",
                    googleCalendarId = "primary",
                    isSyncing = false,
                    syncFailed = false,
                    updatedTimestamp = System.currentTimeMillis()
                )

                app.repository.insertTask(finalTask)
                app.repository.deleteTask(task)

                if (!task.tags.isNullOrBlank()) {
                    app.repository.saveTagOverride(finalTask.id, task.tags)
                }

                return Result.success()
            } else {
                app.repository.updateTask(task.copy(isSyncing = false, syncFailed = true))
                return Result.retry()
            }
        } catch (e: Exception) {
            Log.e("GoogleCalWorker", "Failed to create Google Calendar event", e)
            app.repository.updateTask(task.copy(isSyncing = false, syncFailed = true))
            return Result.retry()
        }
    }
}
