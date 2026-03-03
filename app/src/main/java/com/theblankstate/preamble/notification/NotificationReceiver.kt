package com.theblankstate.preamble.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.theblankstate.preamble.PreambleApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
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
                    TaskNotificationManager.updateNotification(context)
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
        val app = context.applicationContext as PreambleApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                TaskNotificationManager.updateNotification(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
