package com.theblankstate.preamble.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.theblankstate.preamble.PreambleApplication
import com.theblankstate.preamble.data.Task

class GoogleCalendarDeletionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as PreambleApplication
        val eventId = inputData.getString("eventId") ?: return Result.failure()
        val calendarId = inputData.getString("calendarId") ?: return Result.failure()
        val taskJson = inputData.getString("taskJson")
        
        if (!GoogleCalendarManager.isLinked.value) {
            return Result.success()
        }

        return try {
            val success = GoogleCalendarManager.deleteCalendarEvent(applicationContext, eventId, calendarId)
            if (success) {
                GoogleSyncWorker.enqueueImmediate(
                    applicationContext,
                    forceFull = false,
                    reason = "google_calendar_delete"
                )
                Result.success()
            } else {
                Log.e("GoogleCalDeletion", "Failed to delete event remotely. Retrying...")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("GoogleCalDeletion", "Permanent failure. Rolling back deletion.", e)
            rollbackDeletion(app, taskJson)
            Result.failure()
        }
    }

    private suspend fun rollbackDeletion(app: PreambleApplication, taskJson: String?) {
        if (taskJson == null) return
        try {
            val task = Gson().fromJson(taskJson, Task::class.java)
            val restoredTask = task.copy(isSyncing = false, syncFailed = true)
            app.repository.insertTask(restoredTask)
            Log.d("GoogleCalDeletion", "Locally rolled back task: ${task.id}")
        } catch (e: Exception) {
            Log.e("GoogleCalDeletion", "Error deserializing rollback task", e)
        }
    }
}
