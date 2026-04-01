package com.theblankstate.preamble.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.theblankstate.preamble.PreambleApplication

/**
 * Background worker to sync task completion status to Google Tasks.
 * The local toggle happens instantly (optimistic UI), this worker
 * handles the network call in the background.
 */
class GoogleTaskCompletionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as PreambleApplication
        val taskId = inputData.getString("taskId") ?: return Result.failure()
        val googleTaskId = inputData.getString("googleTaskId") ?: return Result.failure()
        val completed = inputData.getBoolean("completed", false)

        if (!GoogleTasksManager.isLinked.value) {
            return Result.success()
        }

        return try {
            val success = GoogleTasksManager.updateTaskCompletion(
                applicationContext, googleTaskId, completed
            )
            if (success) {
                // Clear syncing state
                val task = app.repository.getTaskById(taskId)
                if (task != null) {
                    app.repository.updateTask(task.copy(isSyncing = false, syncFailed = false))
                }
                GoogleSyncWorker.enqueueImmediate(
                    applicationContext,
                    forceFull = false,
                    reason = "google_task_completion"
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
            Log.e("TaskCompletionWorker", "Failed to sync completion", e)
            val task = app.repository.getTaskById(taskId)
            if (task != null) {
                app.repository.updateTask(task.copy(isSyncing = false, syncFailed = true))
            }
            Result.retry()
        }
    }
}
