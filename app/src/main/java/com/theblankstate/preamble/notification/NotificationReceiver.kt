package com.theblankstate.preamble.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import com.theblankstate.preamble.PreambleApplication
import com.theblankstate.preamble.notification.NotificationKeepAliveScheduler
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
        val receivedAt = System.currentTimeMillis()
        val pendingResult = goAsync()
        val results = RemoteInput.getResultsFromIntent(intent)
        val taskText = results?.getCharSequence(TaskNotificationManager.KEY_TASK_TEXT)?.toString()

        // Mark RemoteInput interaction so subsequent rebuilds are suppressed during the
        // grace window. Without this, the optimistic insert below triggers the DB flow
        // observer which rebuilds the notification and closes the input mid-typing
        // when the user is queueing a second task.
        TaskNotificationService.lastRemoteInputTimeMs = System.currentTimeMillis()

        // OPTIMISTIC UI: build the raw Task synchronously (object alloc only — no IO)
        // and finish the BroadcastReceiver immediately so Android collapses the
        // RemoteInput "Sending…" spinner without waiting for DB / AI work. User can
        // tap Quick Add again right away and queue another task with no perceived lag.
        val rawTask = if (!taskText.isNullOrBlank()) {
            val now = System.currentTimeMillis()
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            com.theblankstate.preamble.data.Task(
                title = taskText,
                createdDate = today,
                createdTimestamp = now,
                updatedTimestamp = now,
                isSyncing = true  // shimmer in UI while AI refines
            )
        } else null

        // Release the receiver NOW — UI collapses input within ~10 ms instead of
        // waiting for Room insert + foreground service start (~200–700 ms).
        pendingResult.finish()
        Log.d(
            "NotifReceiver",
            "QuickAdd onReceive->finish: ${System.currentTimeMillis() - receivedAt}ms " +
                "(hasText=${rawTask != null}, len=${taskText?.length ?: 0})"
        )
        TaskNotificationService.acknowledgeQuickAdd(context, rawTask)

        if (rawTask != null) {
            val app = context.applicationContext as PreambleApplication
            // appScope (SupervisorJob, process-lifetime) keeps work alive after
            // receiver returns. Foreground notification service holds the process up.
            app.appScope.launch(Dispatchers.IO) {
                try {
                    app.repository.insertTask(rawTask)
                    val aiIntent = Intent(context, VoiceTaskService::class.java).apply {
                        putExtra(VoiceTaskService.EXTRA_TEXT_COMMAND, taskText)
                        putExtra(VoiceTaskService.EXTRA_TASK_ID, rawTask.id)
                        putExtra(VoiceTaskService.EXTRA_IS_NOTIFICATION, true)
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(aiIntent)
                    } else {
                        context.startService(aiIntent)
                    }
                } catch (e: Exception) {
                    Log.e("NotifReceiver", "Background quick-add failed", e)
                }
            }
        }
    }

    private fun handleBoot(context: Context) {
        val pendingResult = goAsync()
        NotificationKeepAliveScheduler.schedule(context)
        // Only restart if user has notification enabled
        if (TaskNotificationService.isEnabled(context)) {
            TaskNotificationService.start(context)
        }
        pendingResult.finish()
    }
}
