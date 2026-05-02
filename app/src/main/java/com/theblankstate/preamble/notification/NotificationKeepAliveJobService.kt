package com.theblankstate.preamble.notification

import android.app.NotificationManager
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Independent kill-recovery path running on the platform JobScheduler instead of
 * WorkManager. Several OEMs (Vivo, MIUI, ColorOS) freeze WorkManager-scheduled
 * jobs while leaving raw JobScheduler ones alone, so we run both.
 *
 * Periodic minimum on Android 7+ is 15 minutes; we accept that floor.
 */
class NotificationKeepAliveJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        if (TaskNotificationService.isEnabled(applicationContext)) {
            val nm = applicationContext.getSystemService(NotificationManager::class.java)
            val isVisible = nm.activeNotifications.any {
                it.id == TaskNotificationService.NOTIFICATION_ID
            }
            if (!isVisible) {
                Log.d(TAG, "JobService tick — notification missing, restarting")
                TaskNotificationService.start(applicationContext)
            }
        }
        // No async work — return false so the system frees the wakelock immediately.
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false

    companion object {
        private const val TAG = "NotifKeepAliveJob"
        private const val JOB_ID = 4711

        fun schedule(context: Context) {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val component = ComponentName(context, NotificationKeepAliveJobService::class.java)
            val builder = JobInfo.Builder(JOB_ID, component)
                .setPersisted(true) // survive reboot
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // 15 min minimum periodic on N+. With flex = full interval the system
                // can fire any time inside the window, which is fine for a recovery ping.
                builder.setPeriodic(15 * 60 * 1000L, 5 * 60 * 1000L)
            } else {
                builder.setPeriodic(15 * 60 * 1000L)
            }
            try {
                val result = scheduler.schedule(builder.build())
                Log.d(TAG, "schedule() result=$result")
            } catch (e: Exception) {
                Log.w(TAG, "schedule() failed", e)
            }
        }

        fun cancel(context: Context) {
            try {
                val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
                scheduler.cancel(JOB_ID)
            } catch (e: Exception) {
                Log.w(TAG, "cancel() failed", e)
            }
        }
    }
}
