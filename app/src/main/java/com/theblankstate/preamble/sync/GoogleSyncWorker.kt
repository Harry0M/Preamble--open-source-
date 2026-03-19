package com.theblankstate.preamble.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.theblankstate.preamble.PreambleApplication

class GoogleSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as PreambleApplication

        try {
            // Full sync Google Tasks
            if (GoogleTasksManager.isLinked.value) {
                val gTasks = GoogleTasksManager.fetchGoogleTasks(applicationContext)
                app.repository.syncGoogleTasks(gTasks, GoogleTasksManager.autoDeleteGoogleTasks.value)
            }

            // Sync Google Calendar — route based on incremental vs full
            if (GoogleCalendarManager.isLinked.value) {
                val calResult = GoogleCalendarManager.fetchCalendarEvents(applicationContext)
                if (calResult.isIncremental) {
                    app.repository.quickSyncCalendarEvents(calResult.events)
                } else {
                    app.repository.syncCalendarEvents(calResult.events)
                }
            }

            Log.d(TAG, "Background Google sync completed")
        } catch (e: Throwable) {
            Log.e(TAG, "Background Google sync failed", e)
            return Result.retry()
        }

        return Result.success()
    }

    companion object {
        private const val TAG = "GoogleSyncWorker"
        const val WORK_NAME = "google_periodic_sync"
    }
}
