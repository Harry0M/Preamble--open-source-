package com.theblankstate.preamble.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import com.theblankstate.preamble.PreambleApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TaskNotificationManager.ACTION_NOTIFICATION_DISMISSED -> {
                // Only re-show if user hasn't disabled notification in settings
                if (TaskNotificationService.isEnabled(context)) {
                    Log.d("NotifReceiver", "Notification dismissed — re-showing")
                    TaskNotificationService.reshow(context)
                } else {
                    Log.d("NotifReceiver", "Notification disabled by user — not re-showing")
                }
            }
            TaskNotificationManager.ACTION_QUICK_ADD -> handleQuickAdd(context, intent)
            Intent.ACTION_BOOT_COMPLETED -> handleBoot(context)
        }
    }

    private fun handleQuickAdd(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val results = RemoteInput.getResultsFromIntent(intent)
        val taskText = results?.getCharSequence(TaskNotificationManager.KEY_TASK_TEXT)?.toString()

        if (!taskText.isNullOrBlank()) {
            val app = context.applicationContext as PreambleApplication
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    app.repository.addTask(taskText)
                    // Notification auto-updates via TaskNotificationService
                } finally {
                    pendingResult.finish()
                }
            }
        } else {
            pendingResult.finish()
        }
    }

    private fun handleBoot(context: Context) {
        val pendingResult = goAsync()
        // Only restart if user has notification enabled
        if (TaskNotificationService.isEnabled(context)) {
            TaskNotificationService.start(context)
        }
        pendingResult.finish()
    }
}
