package com.theblankstate.preamble.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.app.ServiceCompat
import com.theblankstate.preamble.MainActivity
import com.theblankstate.preamble.PreambleApplication
import com.theblankstate.preamble.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service with an aggressively persistent notification.
 *
 * Android 16 (API 36) deliberately lets users swipe away foreground-service
 * notifications for most service types (including specialUse).  We cannot
 * prevent the dismiss, but we can detect it instantly and re-post within
 * ~1 second using three layers:
 *
 *  1. deleteIntent  → BroadcastReceiver fires → sends ACTION_RESHOW to this service
 *  2. Active monitor → coroutine checks every 3 s whether the notification is
 *     still in the status-bar; re-calls startForeground() if missing
 *  3. Flow observer  → any DB change triggers startForeground() again
 */
class TaskNotificationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isRunning = false

    // Cache the latest task data so we can rebuild the notification instantly
    private var lastPendingCount = 0
    private var lastPendingTasks: List<com.theblankstate.preamble.data.Task> = emptyList()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand action=$action isRunning=$isRunning")

        // If user explicitly disabled notification in settings, stop ourselves
        if (!isNotificationPreferenceEnabled()) {
            Log.d(TAG, "Notification disabled by user — stopping service")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()

        // Always (re-)post the notification — this is the core Android 16 fix.
        promoteToForeground(buildNotification(lastPendingCount, lastPendingTasks))

        if (!isRunning) {
            isRunning = true
            startTaskObserver()
            startNotificationGuard()
            Log.d(TAG, "Observers started")
        }
        return START_STICKY
    }

    /** Returns false only when user explicitly toggled off in Settings */
    private fun isNotificationPreferenceEnabled(): Boolean {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_NOTIFICATION_ENABLED, true)  // default = enabled
    }

    // ── Notification Channel ──────────────────────────────────────────────

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val existing = nm.getNotificationChannel(CHANNEL_ID)

        if (existing != null && existing.importance < NotificationManager.IMPORTANCE_DEFAULT) {
            try {
                nm.deleteNotificationChannel(CHANNEL_ID)
                Log.d(TAG, "Deleted old low-importance channel")
            } catch (e: SecurityException) {
                Log.w(TAG, "Could not delete channel since it's used by a foreground service", e)
                return // Use existing channel
            }
        } else if (existing != null) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Preamble Tasks",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Permanent task notification"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setAllowBubbles(false)
            }
        }
        nm.createNotificationChannel(channel)
        Log.d(TAG, "Channel created: $CHANNEL_ID")
    }

    // ── Foreground promotion ──────────────────────────────────────────────

    private fun promoteToForeground(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.d(TAG, "Foreground posted")
        } catch (e: Exception) {
            Log.e(TAG, "promoteToForeground failed", e)
        }
    }

    // ── Active notification guard ─────────────────────────────────────────
    //
    // Every 3 seconds, verify our notification is still in the status bar.
    // If the user swiped it away and the deleteIntent somehow didn't fire,
    // this guarantees re-show within 3 s.

    private fun startNotificationGuard() {
        serviceScope.launch {
            while (isActive) {
                delay(3_000)
                if (!isActive) break
                // Only re-post if user hasn't disabled notification
                if (!isNotificationPreferenceEnabled()) continue
                if (!isNotificationVisible()) {
                    Log.d(TAG, "Guard: notification missing — re-posting")
                    promoteToForeground(
                        buildNotification(lastPendingCount, lastPendingTasks)
                    )
                }
            }
        }
    }

    private fun isNotificationVisible(): Boolean {
        val nm = getSystemService(NotificationManager::class.java)
        return nm.activeNotifications.any { it.id == NOTIFICATION_ID }
    }

    // ── Live task observer ────────────────────────────────────────────────

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun startTaskObserver() {
        val app = applicationContext as PreambleApplication

        // Observe only today's pending tasks, debounced to avoid rapid re-builds
        serviceScope.launch {
            val today = com.theblankstate.preamble.repository.TaskRepository.todayString()
            app.repository.getTasksForDate(today)
                .map { tasks -> tasks.filter { !it.isCompleted } }
                .debounce(1_000) // Wait 1s after last change before rebuilding notification
                .distinctUntilChanged { old, new -> old.size == new.size && old.map { it.id }.toSet() == new.map { it.id }.toSet() }
                .collect { pending ->
                    if (!isActive) return@collect
                    lastPendingCount = pending.size
                    lastPendingTasks = pending
                    promoteToForeground(buildNotification(pending.size, pending))
                }
        }

        // Fallback periodic refresh every 30s
        serviceScope.launch {
            while (isActive) {
                delay(30_000)
                if (!isActive) break
                val today = com.theblankstate.preamble.repository.TaskRepository.todayString()
                val pending = app.repository.getPendingTasksForDate(today)
                lastPendingCount = pending.size
                lastPendingTasks = pending
                promoteToForeground(buildNotification(pending.size, pending))
            }
        }
    }

    // ── Build the notification ────────────────────────────────────────────

    private fun buildNotification(
        pendingCount: Int,
        pendingTasks: List<com.theblankstate.preamble.data.Task>
    ): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val deleteIntent = PendingIntent.getBroadcast(
            this, 1,
            Intent(this, NotificationReceiver::class.java).apply {
                action = TaskNotificationManager.ACTION_NOTIFICATION_DISMISSED
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Quick add
        val remoteInput = RemoteInput.Builder(TaskNotificationManager.KEY_TASK_TEXT)
            .setLabel("Type task…")
            .build()
        val addIntent = PendingIntent.getBroadcast(
            this, 0,
            Intent(this, NotificationReceiver::class.java).apply {
                action = TaskNotificationManager.ACTION_QUICK_ADD
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val addAction = NotificationCompat.Action.Builder(
            R.drawable.ic_launcher_foreground, "Quick Add", addIntent
        ).addRemoteInput(remoteInput).build()

        // Voice
        val voiceIntent = PendingIntent.getService(
            this, 2,
            Intent(this, VoiceTaskService::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val voiceAction = NotificationCompat.Action.Builder(
            R.drawable.ic_launcher_foreground, "\uD83C\uDF99 Voice", voiceIntent
        ).build()

        val contentText = if (pendingCount > 0)
            "$pendingCount task${if (pendingCount > 1) "s" else ""} pending"
        else "All done for today!"

        // ── Collapsed custom view ──
        val collapsedView = RemoteViews(packageName, R.layout.notification_collapsed)
        collapsedView.setTextViewText(R.id.notif_title, "Preamble")
        collapsedView.setTextViewText(R.id.notif_subtitle, contentText)

        // ── Expanded custom view with 2-column task grid ──
        val expandedView = RemoteViews(packageName, R.layout.notification_expanded)
        expandedView.setTextViewText(R.id.notif_exp_title, "Pending Tasks")
        expandedView.setTextViewText(R.id.notif_exp_count, if (pendingCount > 0) "$pendingCount" else "")

        // Left column IDs and right column IDs
        val leftIds = intArrayOf(
            R.id.task_left_1, R.id.task_left_2, R.id.task_left_3,
            R.id.task_left_4, R.id.task_left_5
        )
        val rightIds = intArrayOf(
            R.id.task_right_1, R.id.task_right_2, R.id.task_right_3,
            R.id.task_right_4, R.id.task_right_5
        )

        // Hide all task slots first
        leftIds.forEach { expandedView.setViewVisibility(it, android.view.View.GONE) }
        rightIds.forEach { expandedView.setViewVisibility(it, android.view.View.GONE) }
        expandedView.setViewVisibility(R.id.notif_more, android.view.View.GONE)
        expandedView.setViewVisibility(R.id.notif_empty, android.view.View.GONE)

        if (pendingCount == 0) {
            expandedView.setViewVisibility(R.id.notif_empty, android.view.View.VISIBLE)
        } else {
            // Fill left column (indices 0-4), right column (indices 5-9)
            val visibleTasks = pendingTasks.take(10)
            for (i in visibleTasks.indices) {
                val taskText = "• ${visibleTasks[i].title}"
                if (i < 5) {
                    expandedView.setTextViewText(leftIds[i], taskText)
                    expandedView.setViewVisibility(leftIds[i], android.view.View.VISIBLE)
                } else {
                    expandedView.setTextViewText(rightIds[i - 5], taskText)
                    expandedView.setViewVisibility(rightIds[i - 5], android.view.View.VISIBLE)
                }
            }
            if (pendingCount > 10) {
                expandedView.setTextViewText(R.id.notif_more, "+${pendingCount - 10} more")
                expandedView.setViewVisibility(R.id.notif_more, android.view.View.VISIBLE)
            }
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Preamble")
            .setContentText(contentText)
            .setContentIntent(openIntent)
            .setDeleteIntent(deleteIntent)
            .addAction(addAction)
            .addAction(voiceAction)
            .setCustomContentView(collapsedView)
            .setCustomBigContentView(expandedView)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setShowWhen(false)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        val notification = builder.build()
        notification.flags = notification.flags or
                Notification.FLAG_ONGOING_EVENT or
                Notification.FLAG_NO_CLEAR or
                Notification.FLAG_FOREGROUND_SERVICE
        return notification
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (isNotificationPreferenceEnabled()) {
            Log.d(TAG, "onTaskRemoved — restarting")
            start(applicationContext)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        isRunning = false
        serviceScope.cancel()
        // Only auto-restart if user hasn't disabled notification
        if (isNotificationPreferenceEnabled()) {
            start(applicationContext)
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "TaskNotifService"
        private const val CHANNEL_ID = "preamble_tasks_persistent"
        private const val PREFS_NAME = "preamble_prefs"
        private const val PREF_NOTIFICATION_ENABLED = "notification_enabled"
        const val NOTIFICATION_ID = 1001
        const val ACTION_RESHOW = "com.theblankstate.preamble.ACTION_RESHOW_NOTIFICATION"

        /** Check if user has notification enabled (for use by receivers) */
        fun isEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_NOTIFICATION_ENABLED, true)
        }

        fun start(context: Context) {
            val intent = Intent(context, TaskNotificationService::class.java)
            startServiceSafely(context, intent, "start()")
        }

        /** Called by NotificationReceiver when user dismisses notification */
        fun reshow(context: Context) {
            val intent = Intent(context, TaskNotificationService::class.java).apply {
                action = ACTION_RESHOW
            }
            startServiceSafely(context, intent, "reshow()")
        }

        private fun startServiceSafely(context: Context, intent: Intent, label: String) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.d(TAG, "$label sent")
            } catch (e: RuntimeException) {
                if (isForegroundStartNotAllowed(e)) {
                    Log.w(TAG, "$label blocked by system", e)
                } else {
                    Log.e(TAG, "$label failed", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "$label failed", e)
            }
        }

        private fun isForegroundStartNotAllowed(e: RuntimeException): Boolean {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e.javaClass.name == "android.app.ForegroundServiceStartNotAllowedException"
        }

        fun stop(context: Context) {
            Log.d(TAG, "stop() — fully stopping service")
            try {
                val intent = Intent(context, TaskNotificationService::class.java)
                context.stopService(intent)
                // Also cancel the notification directly to be sure
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(NOTIFICATION_ID)
            } catch (e: Exception) {
                Log.e(TAG, "stop() failed", e)
            }
        }
    }
}
