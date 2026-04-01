package com.theblankstate.preamble.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.theblankstate.preamble.PreambleApplication

/**
 * Background worker to sync task edits (title, date) to Google Tasks.
 * The local update happens instantly (optimistic UI), this worker
 * handles the network call in the background.
 */
class GoogleTaskUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as PreambleApplication
        val taskId = inputData.getString("taskId") ?: return Result.failure()
        val googleTaskId = inputData.getString("googleTaskId") ?: return Result.failure()
        val title = inputData.getString("title") ?: return Result.failure()
        val date = inputData.getString("date")

        if (!GoogleTasksManager.isLinked.value) {
            val task = app.repository.getTaskById(taskId)
            if (task != null) {
                app.repository.updateTask(task.copy(isSyncing = false, syncFailed = false))
            }
            return Result.success()
        }

        return try {
            val success = GoogleTasksManager.updateGoogleTask(
                applicationContext, googleTaskId, title, date
            )
            if (success) {
                val task = app.repository.getTaskById(taskId)
                if (task != null) {
                    app.repository.updateTask(task.copy(isSyncing = false, syncFailed = false))
                }
                GoogleSyncWorker.enqueueImmediate(
                    applicationContext,
                    forceFull = false,
                    reason = "google_task_update"
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
            Log.e("TaskUpdateWorker", "Failed to sync update", e)
            val task = app.repository.getTaskById(taskId)
            if (task != null) {
                app.repository.updateTask(task.copy(isSyncing = false, syncFailed = true))
            }
            Result.retry()
        }
    }
}
