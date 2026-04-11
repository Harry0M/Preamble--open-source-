package com.theblankstate.preamble.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.theblankstate.preamble.PreambleApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Thin bridge from AlarmManager → AlarmRingtoneService.
 *
 * Verifies the task still exists and is not completed/deleted before ringing.
 * This prevents stale alarms (from deleted or Google-sync-removed tasks) from firing.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra("task_id") ?: return
        val taskTitle = intent.getStringExtra("task_title") ?: "Task Reminder"

        Log.d("PreambleAlarm", "AlarmReceiver fired for '$taskTitle' (id=$taskId)")

        val pendingResult = goAsync()
        val app = context.applicationContext as PreambleApplication

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val task = app.database.taskDao().getTaskById(taskId)
                when {
                    task == null -> {
                        // Task was deleted — silently skip, cancel any remaining alarm slots
                        Log.w("PreambleAlarm", "⚠ Task '$taskId' no longer exists — skipping alarm")
                        TaskAlarmManager.cancelAllReminders(context, taskId)
                    }
                    task.isCompleted -> {
                        // Task already completed — cancel alarm
                        Log.d("PreambleAlarm", "Task '$taskTitle' already completed — cancelling alarm")
                        TaskAlarmManager.cancelAllReminders(context, taskId)
                    }
                    task.isAlarmPaused -> {
                        Log.d("PreambleAlarm", "Alarm paused for '$taskTitle' — skipping")
                    }
                    else -> {
                        // Task is valid and active — ring the alarm
                        AlarmRingtoneService.startAlarm(context, taskId, task.title)
                    }
                }
            } catch (e: Exception) {
                Log.e("PreambleAlarm", "Error verifying task before alarm", e)
                // On error, ring anyway (better to ring than silently fail)
                AlarmRingtoneService.startAlarm(context, taskId, taskTitle)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
