package com.theblankstate.preamble.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import com.theblankstate.preamble.PreambleApplication
import kotlinx.coroutines.launch

class AlarmDismissReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra("notification_id", 0)
        val taskId = intent.getStringExtra("task_id")
        val taskTitle = intent.getStringExtra("task_title")

        // Stop any playing ringtone
        activeRingtone?.stop()
        activeRingtone = null

        // Cancel the notification
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(notificationId)

        // If action is COMPLETE, mark the task as completed using the UUID
        if (intent.action == "COMPLETE" && taskId != null) {
            val app = context.applicationContext as PreambleApplication
            app.appScope.launch {
                val task = app.database.taskDao().getTaskById(taskId)
                if (task != null && !task.isCompleted) {
                    app.repository.toggleTask(task)
                }
            }
        }

        // If action is SNOOZE, physically update the database with the customAlarmTimeMs and reschedule
        if (intent.action == "SNOOZE" && taskId != null && taskTitle != null) {
            val snoozeTimeMs = System.currentTimeMillis() + (10 * 60 * 1000L)
            val app = context.applicationContext as PreambleApplication
            app.appScope.launch {
                val task = app.database.taskDao().getTaskById(taskId)
                if (task != null) {
                    val updated = task.copy(
                        customAlarmTimeMs = snoozeTimeMs,
                        isAlarmPaused = false,
                        updatedTimestamp = System.currentTimeMillis()
                    )
                    app.repository.updateTask(updated)
                    TaskAlarmManager.scheduleAlarm(context, taskId, taskTitle, snoozeTimeMs)
                }
            }
        }
    }

    companion object {
        var activeRingtone: Ringtone? = null
    }
}
