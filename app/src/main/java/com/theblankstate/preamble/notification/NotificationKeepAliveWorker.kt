package com.theblankstate.preamble.notification

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Periodic WorkManager worker that ensures TaskNotificationService stays alive.
 *
 * If the OS kills the service process while the app is in background,
 * START_STICKY alone is unreliable on many OEMs.  This worker runs every
 * 15 minutes (minimum WorkManager interval) and restarts the service
 * if it's not running.
 */
class NotificationKeepAliveWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!TaskNotificationService.isEnabled(applicationContext)) {
            Log.d(TAG, "Notification disabled by user — skipping restart")
            return Result.success()
        }

        val nm = applicationContext.getSystemService(
            android.app.NotificationManager::class.java
        )
        val isVisible = nm.activeNotifications.any {
            it.id == TaskNotificationService.NOTIFICATION_ID
        }

        if (!isVisible) {
            Log.d(TAG, "Service not running — restarting")
            TaskNotificationService.start(applicationContext)
        } else {
            Log.d(TAG, "Service is alive — nothing to do")
        }

        return Result.success()
    }

    companion object {
        private const val TAG = "NotifKeepAlive"
        const val WORK_NAME = "notification_keep_alive"
    }
}
