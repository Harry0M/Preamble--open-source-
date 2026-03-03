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

    fun scheduleAlarm(context: Context, taskTitle: String, taskDate: String, deadlineTime: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val timeParts = deadlineTime.split(":")
        if (timeParts.size != 2) {
            Log.e(TAG, "Invalid time format: $deadlineTime")
            return
        }
        val hour = timeParts[0].toIntOrNull()
        val minute = timeParts[1].toIntOrNull()
        if (hour == null || minute == null) {
            Log.e(TAG, "Cannot parse time: $deadlineTime")
            return
        }

        val dateParts = taskDate.split("-")
        if (dateParts.size != 3) {
            Log.e(TAG, "Invalid date format: $taskDate")
            return
        }
        val year = dateParts[0].toIntOrNull()
        val month = dateParts[1].toIntOrNull()
        val day = dateParts[2].toIntOrNull()
        if (year == null || month == null || day == null) {
            Log.e(TAG, "Cannot parse date: $taskDate")
            return
        }

        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val triggerTime = cal.timeInMillis
        if (triggerTime <= System.currentTimeMillis()) {
            Log.w(TAG, "Alarm time is in the past ($taskDate $deadlineTime), skipping.")
            return
        }

        Log.d(TAG, "Scheduling alarm for '$taskTitle' at $taskDate $deadlineTime => ${triggerTime}ms from epoch")

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("task_title", taskTitle)
            action = "com.theblankstate.preamble.TASK_ALARM_${System.currentTimeMillis()}"
        }

        val requestCode = (taskTitle.hashCode() xor triggerTime.toInt()) and 0x7FFFFFFF
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            // setAlarmClock is the MOST reliable method - treated as alarm clock by the system
            // It bypasses Doze, battery optimization, and is guaranteed to fire
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, pendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            Log.d(TAG, "Alarm scheduled via setAlarmClock successfully (requestCode=$requestCode)")
        } catch (e: Exception) {
            Log.e(TAG, "setAlarmClock failed, trying fallback", e)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
                Log.d(TAG, "Alarm scheduled via fallback method")
            } catch (e2: Exception) {
                Log.e(TAG, "All alarm methods failed", e2)
            }
        }
    }
}
