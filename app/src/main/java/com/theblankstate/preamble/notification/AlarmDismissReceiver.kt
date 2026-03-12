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
        val taskTitle = intent.getStringExtra("task_title")

        // Stop any playing ringtone
        activeRingtone?.stop()
        activeRingtone = null

        // Cancel the notification
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(notificationId)

        // If action is COMPLETE, mark the task as completed
        if (intent.action == "COMPLETE" && taskTitle != null) {
            val app = context.applicationContext as PreambleApplication
            app.appScope.launch {
                val task = app.database.taskDao().getPendingTaskByTitle(taskTitle)
                if (task != null) {
                    app.repository.toggleTask(task)
                }
            }
        }
    }

    companion object {
        var activeRingtone: Ringtone? = null
    }
}
