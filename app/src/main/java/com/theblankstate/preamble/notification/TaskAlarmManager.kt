package com.theblankstate.preamble.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.theblankstate.preamble.MainActivity

object TaskAlarmManager {

    private const val TAG = "PreambleAlarm"

    /**
     * Cancel a scheduled alarm for a task.
     * Uses the exact taskId to regenerate the correct requestCode.
     */
    fun cancelAlarm(context: Context, taskId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.theblankstate.preamble.TASK_ALARM_${taskId}"
        }
        val requestCode = taskId.hashCode() and 0x7FFFFFFF
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "✓ Alarm cancelled for taskId '$taskId' (rc=$requestCode)")
        } else {
            Log.d(TAG, "No alarm found to cancel for taskId '$taskId'")
        }
    }

    fun scheduleAlarm(context: Context, taskId: String, taskTitle: String, triggerTimeMs: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (triggerTimeMs <= System.currentTimeMillis()) {
            Log.w(TAG, "⚠ Alarm time is in the past for '$taskTitle', skipping.")
            return
        }

        // Check exact alarm permission on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.e(TAG, "✘ Cannot schedule exact alarms — USE_EXACT_ALARM not granted!")
            Log.e(TAG, "  Falling back to inexact alarm for '$taskTitle'")
            // Fall back to inexact but still fire
            scheduleInexactAlarm(alarmManager, context, taskId, taskTitle, triggerTimeMs)
            return
        }

        val triggerDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(triggerTimeMs))
        Log.d(TAG, "Scheduling alarm: '$taskTitle' at $triggerDate (${triggerTimeMs}ms)")

        // Fire intent — goes to AlarmReceiver → AlarmRingtoneService
        val fireIntent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("task_id", taskId)
            putExtra("task_title", taskTitle)
            action = "com.theblankstate.preamble.TASK_ALARM_${taskId}"
        }
        val requestCode = taskId.hashCode() and 0x7FFFFFFF
        val firePendingIntent = PendingIntent.getBroadcast(
            context, requestCode, fireIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Show intent — opens the app when user taps alarm icon in status bar
        val showIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val showPendingIntent = PendingIntent.getActivity(
            context, requestCode + 1000, showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            // setAlarmClock is the MOST reliable method — treated as alarm clock by the system
            // It bypasses Doze, battery optimization, and is guaranteed to fire
            // Also shows alarm icon in status bar
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTimeMs, showPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, firePendingIntent)
            Log.d(TAG, "✓ Alarm scheduled via setAlarmClock (rc=$requestCode)")
        } catch (e: SecurityException) {
            Log.e(TAG, "setAlarmClock SecurityException — permission revoked?", e)
            scheduleInexactAlarm(alarmManager, context, taskId, taskTitle, triggerTimeMs)
        } catch (e: Exception) {
            Log.e(TAG, "setAlarmClock failed, trying fallback", e)
            scheduleInexactAlarm(alarmManager, context, taskId, taskTitle, triggerTimeMs)
        }
    }

    /**
     * Fallback for when exact alarms are not available.
     * Uses setAndAllowWhileIdle which may delay up to ~10 minutes on some devices.
     */
    private fun scheduleInexactAlarm(
        alarmManager: AlarmManager,
        context: Context,
        taskId: String,
        taskTitle: String,
        triggerTimeMs: Long
    ) {
        val fireIntent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("task_id", taskId)
            putExtra("task_title", taskTitle)
            action = "com.theblankstate.preamble.TASK_ALARM_${taskId}"
        }
        val requestCode = taskId.hashCode() and 0x7FFFFFFF
        val firePendingIntent = PendingIntent.getBroadcast(
            context, requestCode, fireIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, firePendingIntent)
            Log.w(TAG, "⚠ Alarm scheduled via inexact fallback for '$taskTitle'")
        } catch (e: Exception) {
            Log.e(TAG, "✘ All alarm methods failed for '$taskTitle'", e)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // MULTI-REMINDER SUPPORT
    // ═══════════════════════════════════════════════════════════════

    /**
     * Schedule all reminders for a task. Each reminder gets a unique alarm.
     * Request codes: baseHash + reminderIndex (0-4).
     */
    fun scheduleReminders(context: Context, task: com.theblankstate.preamble.data.Task) {
        // First cancel any existing reminders
        cancelAllReminders(context, task.id)

        if (task.isAlarmPaused || task.isCompleted) return

        val triggers = task.computeReminderTriggerMs()
        val now = System.currentTimeMillis()

        for ((index, triggerMs) in triggers) {
            if (triggerMs > now) {
                scheduleReminderAlarm(context, task.id, task.title, triggerMs, index)
            }
        }

        if (triggers.isEmpty()) {
            Log.d(TAG, "No reminders to schedule for '${task.title}'")
        }
    }

    /**
     * Cancel ALL reminder alarms (slots 0-4) for a task.
     */
    fun cancelAllReminders(context: Context, taskId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val baseHash = taskId.hashCode() and 0x7FFFFFFF

        for (i in 0 until com.theblankstate.preamble.data.Reminder.MAX_REMINDERS) {
            val requestCode = baseHash + i
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = "com.theblankstate.preamble.TASK_REMINDER_${taskId}_$i"
            }
            val pi = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                alarmManager.cancel(pi)
                pi.cancel()
            }
        }
        // Also cancel legacy single-alarm format
        cancelAlarm(context, taskId)
    }

    /**
     * Schedule a single reminder alarm at a specific index.
     */
    private fun scheduleReminderAlarm(
        context: Context,
        taskId: String,
        taskTitle: String,
        triggerTimeMs: Long,
        reminderIndex: Int
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val baseHash = taskId.hashCode() and 0x7FFFFFFF
        val requestCode = baseHash + reminderIndex

        val triggerDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(triggerTimeMs))
        Log.d(TAG, "Scheduling reminder #$reminderIndex for '$taskTitle' at $triggerDate")

        val fireIntent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("task_id", taskId)
            putExtra("task_title", taskTitle)
            putExtra("reminder_index", reminderIndex)
            action = "com.theblankstate.preamble.TASK_REMINDER_${taskId}_$reminderIndex"
        }
        val firePi = PendingIntent.getBroadcast(
            context, requestCode, fireIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, com.theblankstate.preamble.MainActivity::class.java)
        val showPi = PendingIntent.getActivity(
            context, requestCode + 5000, showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, firePi)
                Log.w(TAG, "⚠ Reminder #$reminderIndex scheduled via inexact fallback")
            } else {
                val info = AlarmManager.AlarmClockInfo(triggerTimeMs, showPi)
                alarmManager.setAlarmClock(info, firePi)
                Log.d(TAG, "✓ Reminder #$reminderIndex scheduled via setAlarmClock (rc=$requestCode)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule reminder #$reminderIndex", e)
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, firePi)
            } catch (_: Exception) { }
        }
    }
}

