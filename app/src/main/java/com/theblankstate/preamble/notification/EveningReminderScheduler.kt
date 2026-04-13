package com.theblankstate.preamble.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

object EveningReminderScheduler {

    private const val TAG = "EveningReminder"
    private const val REQUEST_CODE = 7001
    private const val PREF_NAME = "preamble_prefs"
    private const val PREF_EVENING_ENABLED = "evening_reminder_enabled"
    const val HOUR = 6    // 6 AM
    const val MINUTE = 0

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_EVENING_ENABLED, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_EVENING_ENABLED, enabled).apply()
        if (enabled) schedule(context) else cancel(context)
    }

    /**
     * Schedule (or reschedule) the daily 6 PM summary alarm.
     * Uses RTC_WAKEUP so it fires even if device is asleep.
     * If 6 PM today has already passed, schedules for tomorrow 6 PM.
     */
    fun schedule(context: Context) {
        if (!isEnabled(context)) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = buildIntent(context)
        val pi = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerMs = nextTriggerMs()
        val readable = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(triggerMs))

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
                Log.w(TAG, "Scheduled evening reminder (inexact) at $readable")
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
                Log.d(TAG, "Scheduled evening reminder (exact) at $readable")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule evening reminder", e)
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context, REQUEST_CODE, buildIntent(context),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(pi)
        pi.cancel()
        Log.d(TAG, "Evening reminder cancelled")
    }

    private fun buildIntent(context: Context): Intent {
        return Intent(context, EveningReminderReceiver::class.java).apply {
            action = EveningReminderReceiver.ACTION
        }
    }

    fun nextTriggerMs(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, HOUR)
            set(Calendar.MINUTE, MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }
}
