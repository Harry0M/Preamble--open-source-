package com.theblankstate.preamble.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

object TaskAlarmManager {

    private const val TAG = "TaskAlarmManager"

    /**
     * Cancel a scheduled alarm for a task.
     * Uses the exact taskId to regenerate the correct requestCode.
     */
    fun cancelAlarm(context: Context, taskId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, AlarmReceiver::class.java)
        val requestCode = taskId.hashCode() and 0x7FFFFFFF
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Alarm cancelled for taskId '$taskId' (requestCode=$requestCode)")
        }
    }

    fun scheduleAlarm(context: Context, taskId: String, taskTitle: String, triggerTimeMs: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (triggerTimeMs <= System.currentTimeMillis()) {
            Log.w(TAG, "Alarm time is in the past ($triggerTimeMs), skipping.")
            return
        }

        Log.d(TAG, "Scheduling alarm for taskId '$taskId' ('$taskTitle') at ${triggerTimeMs}ms from epoch")

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("task_id", taskId)
            putExtra("task_title", taskTitle)
            action = "com.theblankstate.preamble.TASK_ALARM_${taskId}"
        }

        val requestCode = taskId.hashCode() and 0x7FFFFFFF
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            // setAlarmClock is the MOST reliable method - treated as alarm clock by the system
            // It bypasses Doze, battery optimization, and is guaranteed to fire
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTimeMs, pendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            Log.d(TAG, "Alarm scheduled via setAlarmClock successfully (requestCode=$requestCode)")
        } catch (e: Exception) {
            Log.e(TAG, "setAlarmClock failed, trying fallback", e)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                }
                Log.d(TAG, "Alarm scheduled via fallback method")
            } catch (e2: Exception) {
                Log.e(TAG, "All alarm methods failed", e2)
            }
        }
    }
}
