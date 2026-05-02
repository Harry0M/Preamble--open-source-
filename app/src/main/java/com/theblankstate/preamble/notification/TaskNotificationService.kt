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
import com.theblankstate.preamble.data.Task
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
 * ~1 second using two optimized layers:
 *
 *  1. deleteIntent  → BroadcastReceiver fires → sends ACTION_RESHOW to this service
 *  2. Flow observer  → any DB change triggers update (debounced 1s)
 *  3. Periodic refresh → every N seconds (configurable: 10s, 30s, 120s), only updates if tasks changed
 *
 * Optimization: Only rebuilds notification if task content actually changed, preventing jank.
 */
class TaskNotificationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isRunning = false

    // Cache the latest task data so we can rebuild the notification instantly
    private var lastPendingCount = 0
    private var lastPendingTasks: List<Task> = emptyList()

    // Cache the last built notification to avoid rebuilding when nothing changed
    private var lastBuiltNotification: Notification? = null
    private var lastTaskIds: Set<String> = emptySet()

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

        if (action == ACTION_QUICK_ADD_ACK) {
            mergeOptimisticQuickAdd(intent.toOptimisticQuickAddTask())
            Log.d(TAG, "QuickAdd ack: reposting notification to clear RemoteInput")
        }

        // Initial start and explicit re-show (after dismiss) bypass the RemoteInput grace.
        // Subsequent calls (e.g. KeepAlive ping while service alive) respect grace so they
        // don't kill an open Quick Add input.
        val bypassGrace = !isRunning || action == ACTION_RESHOW || action == ACTION_QUICK_ADD_ACK
        val forceStartForeground = !isRunning || action == ACTION_RESHOW
        promoteToForeground(
            buildNotification(lastPendingCount, lastPendingTasks),
            bypassGrace = bypassGrace,
            forceStartForeground = forceStartForeground
        )

        if (!isRunning) {
            isRunning = true
            startTaskObserver()
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

    private fun promoteToForeground(
        notification: Notification,
        bypassGrace: Boolean = false,
        forceStartForeground: Boolean = false
    ) {
        // Single chokepoint: any non-bypass post during the RemoteInput grace window
        // is suppressed and a deferred rebuild is queued. Prevents the open Quick Add
        // input field from being closed mid-typing by spurious notification rebuilds.
        if (!bypassGrace) {
            val timeSinceInput = System.currentTimeMillis() - lastRemoteInputTimeMs
            if (timeSinceInput < REMOTE_INPUT_GRACE_PERIOD_MS) {
                val remaining = REMOTE_INPUT_GRACE_PERIOD_MS - timeSinceInput
                Log.d(TAG, "Suppressed rebuild — RemoteInput grace active (${remaining}ms left, no deferred repost)")
                return
            }
        }
        try {
            val shouldStartForeground = forceStartForeground || !isRunning
            if (shouldStartForeground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else if (shouldStartForeground) {
                startForeground(NOTIFICATION_ID, notification)
            } else {
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
            }
            Log.d(TAG, if (shouldStartForeground) "Foreground posted" else "Notification updated")
        } catch (e: Exception) {
            Log.e(TAG, "promoteToForeground failed", e)
        }
    }

    private fun mergeOptimisticQuickAdd(task: Task?) {
        if (task == null) return
        val withoutDuplicate = lastPendingTasks.filterNot { it.id == task.id }
        lastPendingTasks = (listOf(task) + withoutDuplicate)
            .sortedByDescending { it.createdTimestamp }
        lastPendingCount = lastPendingTasks.size
        lastTaskIds = lastPendingTasks.map { it.id }.toSet()
        lastBuiltNotification = buildNotification(lastPendingCount, lastPendingTasks)
        Log.d(TAG, "QuickAdd ack: optimistic task merged count=$lastPendingCount")
    }

    private fun Intent?.toOptimisticQuickAddTask(): Task? {
        if (this == null) return null
        val id = getStringExtra(EXTRA_ACK_TASK_ID) ?: return null
        val title = getStringExtra(EXTRA_ACK_TASK_TITLE)?.takeIf { it.isNotBlank() } ?: return null
        val date = getStringExtra(EXTRA_ACK_TASK_DATE) ?: return null
        val createdAt = getLongExtra(EXTRA_ACK_TASK_CREATED_AT, System.currentTimeMillis())
        val updatedAt = getLongExtra(EXTRA_ACK_TASK_UPDATED_AT, createdAt)
        return Task(
            id = id,
            title = title,
            createdDate = date,
            createdTimestamp = createdAt,
            updatedTimestamp = updatedAt,
            isSyncing = true
        )
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
                .debounce(500) // Wait 500ms after last change before rebuilding notification
                .distinctUntilChanged { old, new -> 
                    old.size == new.size && 
                    old.map { it.id }.toSet() == new.map { it.id }.toSet() &&
                    old.map { it.title }.toSet() == new.map { it.title }.toSet() &&
                    old.none { it.isSyncing } == new.none { it.isSyncing }
                }
                .collect { pending ->
                    if (!isActive) return@collect

                    lastPendingCount = pending.size
                    lastPendingTasks = pending
                    lastTaskIds = pending.map { it.id }.toSet()

                    val notification = buildNotification(pending.size, pending)
                    lastBuiltNotification = notification
                    promoteToForeground(notification)
                }
        }

        // Periodic refresh with smart change detection and configurable intervals
        serviceScope.launch {
            while (isActive) {
                val mode = getNotificationUpdateMode()
                val interval = NotificationUpdatePreference.getIntervalForMode(mode, lastPendingCount > 0)
                delay(interval)

                if (!isActive) break

                // Only re-post if user hasn't disabled notification
                if (!isNotificationPreferenceEnabled()) continue

                val today = com.theblankstate.preamble.repository.TaskRepository.todayString()
                val pending = app.repository.getPendingTasksForDate(today)

                // Check if task content actually changed
                if (isPendingTasksChanged(pending)) {
                    Log.d(TAG, "Periodic refresh: tasks changed (${pending.size} tasks), updating notification")
                    lastPendingCount = pending.size
                    lastPendingTasks = pending
                    lastTaskIds = pending.map { it.id }.toSet()

                    val notification = buildNotification(pending.size, pending)
                    lastBuiltNotification = notification
                    promoteToForeground(notification)
                } else {
                    Log.d(TAG, "Periodic refresh: no changes, skipping notification update")
                }
            }
        }
    }

    /**
     * Compares current pending tasks with cached tasks.
     * Returns true if count changed or task IDs changed.
     */
    private fun isPendingTasksChanged(newPending: List<Task>): Boolean {
        if (newPending.size != lastPendingCount) return true
        val newIds = newPending.map { it.id }.toSet()
        return newIds != lastTaskIds
    }

    /**
     * Gets the current notification update mode from SharedPreferences.
     * Defaults to BALANCED if not set.
     */
    private fun getNotificationUpdateMode(): NotificationUpdatePreference {
        val prefs = getSharedPreferences(NotificationUpdatePreference.PREF_NAME, Context.MODE_PRIVATE)
        val modeString = prefs.getString(NotificationUpdatePreference.PREF_UPDATE_MODE, null)
        return NotificationUpdatePreference.fromStringOrDefault(modeString)
    }

    // ── Build the notification ────────────────────────────────────────────

    private fun buildNotification(
        pendingCount: Int,
        pendingTasks: List<Task>
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

        // Day name badge with per-day color
        val calendar = java.util.Calendar.getInstance()
        val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
        val dayName = java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault()).format(calendar.time)
        collapsedView.setTextViewText(R.id.notif_day_badge, dayName)

        val dayBadgeBg = when (dayOfWeek) {
            java.util.Calendar.MONDAY -> R.drawable.badge_monday
            java.util.Calendar.TUESDAY -> R.drawable.badge_tuesday
            java.util.Calendar.WEDNESDAY -> R.drawable.badge_wednesday
            java.util.Calendar.THURSDAY -> R.drawable.badge_thursday
            java.util.Calendar.FRIDAY -> R.drawable.badge_friday
            java.util.Calendar.SATURDAY -> R.drawable.badge_saturday
            java.util.Calendar.SUNDAY -> R.drawable.badge_sunday
            else -> R.drawable.badge_monday
        }
        collapsedView.setInt(R.id.notif_day_badge, "setBackgroundResource", dayBadgeBg)

        // Special day / festival badge
        val specialDay = getSpecialDayInfo(calendar)
        if (specialDay != null) {
            collapsedView.setTextViewText(R.id.notif_festival_badge, specialDay)
            collapsedView.setInt(R.id.notif_festival_badge, "setBackgroundResource", R.drawable.badge_festival)
            collapsedView.setViewVisibility(R.id.notif_festival_badge, android.view.View.VISIBLE)
        } else {
            collapsedView.setViewVisibility(R.id.notif_festival_badge, android.view.View.GONE)
        }

        // Date text
        val dateText = java.text.SimpleDateFormat("d MMMM", java.util.Locale.getDefault()).format(calendar.time)
        collapsedView.setTextViewText(R.id.notif_date, dateText)

        // Task count
        val countText = if (pendingCount > 0)
            "$pendingCount task${if (pendingCount > 1) "s" else ""}"
        else "All done!"
        collapsedView.setTextViewText(R.id.notif_task_count, countText)

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

    private fun getSpecialDayInfo(calendar: java.util.Calendar): String? {
        val month = calendar.get(java.util.Calendar.MONTH) // 0-based
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        val specialDays = mapOf(
            (0 to 1) to "New Year",
            (0 to 26) to "Republic Day",
            (1 to 14) to "Valentine's Day",
            (2 to 8) to "Women's Day",
            (3 to 1) to "April Fools",
            (3 to 22) to "Earth Day",
            (4 to 1) to "May Day",
            (5 to 5) to "Environment Day",
            (5 to 21) to "Yoga Day",
            (7 to 15) to "Independence Day",
            (9 to 2) to "Gandhi Jayanti",
            (9 to 31) to "Halloween",
            (10 to 14) to "Children's Day",
            (11 to 25) to "Christmas",
            (11 to 31) to "New Year's Eve",
        )
        return specialDays[month to day]
    }

    companion object {
        private const val TAG = "TaskNotifService"
        private const val CHANNEL_ID = "preamble_tasks_persistent"
        private const val PREFS_NAME = "preamble_prefs"
        private const val PREF_NOTIFICATION_ENABLED = "notification_enabled"
        private const val ACTION_QUICK_ADD_ACK = "com.theblankstate.preamble.ACTION_QUICK_ADD_ACK"
        private const val EXTRA_ACK_TASK_ID = "extra_ack_task_id"
        private const val EXTRA_ACK_TASK_TITLE = "extra_ack_task_title"
        private const val EXTRA_ACK_TASK_DATE = "extra_ack_task_date"
        private const val EXTRA_ACK_TASK_CREATED_AT = "extra_ack_task_created_at"
        private const val EXTRA_ACK_TASK_UPDATED_AT = "extra_ack_task_updated_at"
        // Grace window after user interacts with RemoteInput (Quick Add).
        // During this window any non-essential notification rebuild is suppressed
        // to keep the open input field from being torn down mid-typing.
        private const val REMOTE_INPUT_GRACE_PERIOD_MS = 10_000L
        const val NOTIFICATION_ID = 1001
        const val ACTION_RESHOW = "com.theblankstate.preamble.ACTION_RESHOW_NOTIFICATION"

        // Track when RemoteInput was last used to suppress notification updates during grace period
        @Volatile
        internal var lastRemoteInputTimeMs = 0L

        /** Check if user has notification enabled (for use by receivers) */
        fun isEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_NOTIFICATION_ENABLED, true)
        }

        fun start(context: Context) {
            val intent = Intent(context, TaskNotificationService::class.java)
            startServiceSafely(context, intent, "start()")
        }

        fun acknowledgeQuickAdd(context: Context, optimisticTask: Task?) {
            val intent = Intent(context, TaskNotificationService::class.java).apply {
                action = ACTION_QUICK_ADD_ACK
                optimisticTask?.let { task ->
                    putExtra(EXTRA_ACK_TASK_ID, task.id)
                    putExtra(EXTRA_ACK_TASK_TITLE, task.title)
                    putExtra(EXTRA_ACK_TASK_DATE, task.createdDate)
                    putExtra(EXTRA_ACK_TASK_CREATED_AT, task.createdTimestamp)
                    putExtra(EXTRA_ACK_TASK_UPDATED_AT, task.updatedTimestamp)
                }
            }
            startServiceSafely(context, intent, "quickAddAck()")
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
