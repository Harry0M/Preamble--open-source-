package com.theblankstate.preamble.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Handles alarm dismissal from notification actions.
 * Delegates to AlarmRingtoneService for stop/complete/snooze.
 */
class AlarmDismissReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra("notification_id", 0)
        val taskId = intent.getStringExtra("task_id")
        val taskTitle = intent.getStringExtra("task_title") ?: "Task Reminder"

        Log.d("PreambleAlarm", "AlarmDismissReceiver: action=${intent.action}, taskId=$taskId")

        // Cancel the notification
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(notificationId)

        // Delegate to the service for actual stop/complete/snooze logic
        val serviceIntent = Intent(context, AlarmRingtoneService::class.java).apply {
            action = when (intent.action) {
                "COMPLETE" -> AlarmRingtoneService.ACTION_COMPLETE
                "SNOOZE" -> AlarmRingtoneService.ACTION_SNOOZE
                else -> AlarmRingtoneService.ACTION_STOP
            }
            putExtra("task_id", taskId)
            putExtra("task_title", taskTitle)
        }

        try {
            context.startService(serviceIntent)
        } catch (e: Exception) {
            Log.e("PreambleAlarm", "Failed to send action to AlarmRingtoneService", e)
        }
    }
}
