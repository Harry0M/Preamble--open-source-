package com.theblankstate.preamble.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.theblankstate.preamble.PreambleApplication
import com.theblankstate.preamble.data.Task

class GoogleTaskDeletionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as PreambleApplication
        val googleTaskId = inputData.getString("googleTaskId") ?: return Result.failure()
        val taskJson = inputData.getString("taskJson")
        
        if (!GoogleTasksManager.isLinked.value) {
            // Unlinked while syncing... we probably just ignore the remote deletion.
            return Result.success()
        }

        return try {
            val success = GoogleTasksManager.deleteGoogleTask(applicationContext, googleTaskId)
            if (success) {
                GoogleSyncWorker.enqueueImmediate(
                    applicationContext,
                    forceFull = false,
                    reason = "google_task_delete"
                )
                Result.success()
            } else {
                Log.e("GoogleTaskDeletion", "Failed to delete task remotely. Retrying...")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("GoogleTaskDeletion", "Permanent failure. Rolling back deletion.", e)
            rollbackDeletion(app, taskJson)
            Result.failure() // Returning failure will stop retries.
        }
    }

    private suspend fun rollbackDeletion(app: PreambleApplication, taskJson: String?) {
        if (taskJson == null) return
        try {
            val task = Gson().fromJson(taskJson, Task::class.java)
            val restoredTask = task.copy(isSyncing = false, syncFailed = true)
            app.repository.insertTask(restoredTask)
            Log.d("GoogleTaskDeletion", "Locally rolled back task: ${task.id}")
        } catch (e: Exception) {
            Log.e("GoogleTaskDeletion", "Error deserializing rollback task", e)
        }
    }
}
