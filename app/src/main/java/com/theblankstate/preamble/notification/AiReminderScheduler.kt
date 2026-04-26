package com.theblankstate.preamble.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.theblankstate.preamble.ai.AiReminderEngine
import java.util.Calendar

/**
 * Schedules the daily AI reminder alarm using AlarmManager.
 * Default time: 10:00 AM. User-configurable via preamble_prefs.
 */
object AiReminderScheduler {

    private const val TAG = "AiReminderScheduler"
    private const val REQUEST_CODE = 8100

    fun schedule(context: Context) {
        if (!AiReminderEngine.isEnabled(context)) {
            cancel(context)
            return
        }

        val sp = context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
        val hour = sp.getInt(AiReminderEngine.PREF_HOUR, 10)
        val minute = sp.getInt(AiReminderEngine.PREF_MINUTE, 0)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = buildPendingIntent(context)
        val triggerMs = nextTriggerMs(hour, minute)

        val readable = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(triggerMs))

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
                Log.w(TAG, "Scheduled AI reminder (inexact) at $readable")
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
                Log.d(TAG, "Scheduled AI reminder (exact) at $readable")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule AI reminder", e)
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
        Log.d(TAG, "AI reminder cancelled")
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean(AiReminderEngine.PREF_ENABLED, enabled).apply()
        if (enabled) schedule(context) else cancel(context)
    }

    fun setTime(context: Context, hour: Int, minute: Int) {
        context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
            .edit()
            .putInt(AiReminderEngine.PREF_HOUR, hour)
            .putInt(AiReminderEngine.PREF_MINUTE, minute)
            .apply()
        schedule(context)
    }

    private fun buildIntent(context: Context) =
        Intent(context, AiReminderReceiver::class.java).apply {
            action = AiReminderReceiver.ACTION
        }

    private fun buildPendingIntent(context: Context) =
        PendingIntent.getBroadcast(
            context, REQUEST_CODE, buildIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun nextTriggerMs(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }
}
