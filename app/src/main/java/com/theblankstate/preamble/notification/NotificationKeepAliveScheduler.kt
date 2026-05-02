package com.theblankstate.preamble.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.theblankstate.preamble.MainActivity
import com.theblankstate.preamble.util.OemHelper

object NotificationKeepAliveScheduler {
    private const val TAG = "NotifKeepAlive"
    private const val REQUEST_CODE = 1002
    private const val REQUEST_CODE_SHOW = 1003   // showIntent for setAlarmClock

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        if (!TaskNotificationService.isEnabled(appContext)) {
            cancel(appContext)
            return
        }

        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        val intervalMs = OemHelper.recommendedKeepAliveIntervalMs
        Log.d(TAG, "Scheduling keep-alive in ${intervalMs / 1000}s (oem=${Build.MANUFACTURER})")

        val intent = Intent(appContext, NotificationKeepAliveReceiver::class.java).apply {
            action = NotificationKeepAliveReceiver.ACTION_KEEP_ALIVE
        }
        val firePendingIntent = PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (OemHelper.isVivo) {
            // setAlarmClock bypasses ALL OEM Doze/bgclean restrictions — the same tier used
            // by clock apps. Vivo aggressively defers setExactAndAllowWhileIdle, so this is
            // the only method that reliably fires. Minor side-effect: alarm icon in status bar.
            val showIntent = PendingIntent.getActivity(
                appContext,
                REQUEST_CODE_SHOW,
                Intent(appContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerAtRtc = System.currentTimeMillis() + intervalMs
            val info = AlarmManager.AlarmClockInfo(triggerAtRtc, showIntent)
            alarmManager.setAlarmClock(info, firePendingIntent)
            Log.d(TAG, "Vivo: keep-alive via setAlarmClock (guaranteed fire)")
        } else {
            val triggerAt = SystemClock.elapsedRealtime() + intervalMs
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    firePendingIntent
                )
                Log.w(TAG, "Exact alarms not allowed, using inexact keep-alive")
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    firePendingIntent
                )
            }
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
