package com.theblankstate.preamble.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.theblankstate.preamble.PreambleApplication

class GoogleTaskCreationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as PreambleApplication
        val pendingTaskId = inputData.getString("localTaskId") ?: return Result.failure()

        val task = app.repository.getTaskById(pendingTaskId) ?: return Result.success()

        if (!GoogleTasksManager.isLinked.value) {
            // Unlinked while syncing
            app.repository.updateTask(task.copy(isSyncing = false, syncFailed = false))
            return Result.success()
        }

        try {
            val googleId = GoogleTasksManager.createGoogleTask(
                applicationContext,
                task.title,
                task.createdDate
            )

            if (googleId != null) {
                // Success: create the final Google-linked task
                val finalTask = task.copy(
                    id = "gtask_$googleId",
                    source = "google_tasks",
                    isSyncing = false,
                    syncFailed = false,
                    updatedTimestamp = System.currentTimeMillis()
                )

                app.repository.insertTask(finalTask)
                app.repository.deleteTask(task)

                // Persist tag override
                if (!task.tags.isNullOrBlank()) {
                    app.repository.saveTagOverride(finalTask.id, task.tags)
                }

                return Result.success()
            } else {
                // Failed due to API error
                app.repository.updateTask(task.copy(isSyncing = false, syncFailed = true))
                return Result.retry()
            }
        } catch (e: Exception) {
            Log.e("GoogleTaskWorker", "Failed to create Google task", e)
            app.repository.updateTask(task.copy(isSyncing = false, syncFailed = true))
            return Result.retry()
        }
    }
}
