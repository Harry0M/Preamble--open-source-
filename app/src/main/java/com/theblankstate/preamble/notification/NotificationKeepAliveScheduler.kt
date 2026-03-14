package com.theblankstate.preamble.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log

object NotificationKeepAliveScheduler {
    private const val TAG = "NotifKeepAlive"
    private const val REQUEST_CODE = 1002
    private const val INTERVAL_MS = 30 * 60 * 1000L

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        if (!TaskNotificationService.isEnabled(appContext)) {
            cancel(appContext)
            return
        }

        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        val triggerAt = SystemClock.elapsedRealtime() + INTERVAL_MS
        val intent = Intent(appContext, NotificationKeepAliveReceiver::class.java).apply {
            action = NotificationKeepAliveReceiver.ACTION_KEEP_ALIVE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                pendingIntent
            )
            Log.w(TAG, "Exact alarms not allowed, using inexact keep-alive")
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                pendingIntent
            )
        }
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        val intent = Intent(appContext, NotificationKeepAliveReceiver::class.java).apply {
            action = NotificationKeepAliveReceiver.ACTION_KEEP_ALIVE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
