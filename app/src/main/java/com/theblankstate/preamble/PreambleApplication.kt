package com.theblankstate.preamble

import android.app.Application
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.theblankstate.preamble.data.PreambleDatabase
import com.theblankstate.preamble.notification.NotificationKeepAliveJobService
import com.theblankstate.preamble.notification.NotificationKeepAliveScheduler
import com.theblankstate.preamble.notification.NotificationKeepAliveWorker
import com.theblankstate.preamble.notification.TaskNotificationManager
import com.theblankstate.preamble.notification.TaskNotificationService
import com.theblankstate.preamble.repository.TaskRepository
import com.theblankstate.preamble.sync.FirebaseTaskSyncManager
import com.theblankstate.preamble.sync.GoogleCalendarManager
import com.theblankstate.preamble.sync.GoogleTasksManager
import com.theblankstate.preamble.ads.FeatureGateManager
import com.theblankstate.preamble.analytics.AnalyticsManager
import com.theblankstate.preamble.recurrence.RecurrenceWorker
import com.theblankstate.preamble.sync.GoogleSyncWorker
import com.theblankstate.preamble.widget.WidgetUpdater
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class PreambleApplication : Application() {

    /** Application-scoped coroutine scope — survives configuration changes and screen navigations. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val database by lazy { PreambleDatabase.getInstance(this) }
    val syncManager: FirebaseTaskSyncManager? by lazy {
        if (isMainProcess(this)) FirebaseTaskSyncManager(this, database.taskDao()) else null
    }
    val repository by lazy { TaskRepository(database.taskDao(), syncManager, database.focusSessionDao()) }

    override fun onCreate() {
        super.onCreate()

        // 1. Process Verification: Only initialize heavy SDKs and Firestore in the Main process.
        // This prevents SQLiteDatabaseLockedException when OEM battery savers start background processes.
        if (!isMainProcess(this)) {
            android.util.Log.i("PreambleApp", "Secondary process started, skipping heavy initialization.")
            // Do the bare minimum for notifications
            TaskNotificationManager.createChannel(this)
            com.theblankstate.preamble.notification.PreambleFcmService.createChannels(this)
            return
        }

        // 2. Thread-Race Prevention: Enable offline persistence synchronously on the Main Thread FIRST
        // before any Dispatchers.IO coroutines can attempt to initialize Firestore concurrently.
        FirebaseTaskSyncManager.enableOfflinePersistence()

        // ── PostHog SDK initialization — full config ──
        // Yeh block PostHog ko configure karta hai with session replay,
        // lifecycle events, screen views, aur feature flag preloading.
        val posthogConfig = PostHogAndroidConfig(
            apiKey = BuildConfig.POSTHOG_API_KEY,
            host = BuildConfig.POSTHOG_HOST
        ).apply {
            // App lifecycle events automatically capture karo (app_opened, app_backgrounded etc.)
            captureApplicationLifecycleEvents = true
            // Screen views auto-capture (Activity-based; Compose screens manually track hoti hain)
            captureScreenViews = true
            // Session replay enable karo — user sessions record hongi PostHog mein
            sessionReplay = true
            // Session replay config — sensitive content mask karo by default
            sessionReplayConfig.maskAllTextInputs = true   // Saare text inputs mask honge
            sessionReplayConfig.maskAllImages = false       // Images dikhao (non-sensitive)
            // Feature flags preload karo app start pe — A/B test flags turant available hongi
            preloadFeatureFlags = true
            // Debug mode sirf debug builds mein (verbose logging)
            debug = BuildConfig.DEBUG
        }
        PostHogAndroid.setup(this, posthogConfig)

        // Super properties register karo — har event ke saath attach hongi
        AnalyticsManager.registerSuperProperties(
            appVersion = BuildConfig.VERSION_NAME,
            buildType = if (BuildConfig.DEBUG) "debug" else "release"
        )

        // Crash tracking setup — app crash hone pe PostHog ko report hoga
        AnalyticsManager.setupCrashTracking()

        // Agar user already signed in hai, toh identify karo PostHog mein
        identifyCurrentUser()

        syncManager?.start()
        TaskNotificationManager.createChannel(this)
        com.theblankstate.preamble.notification.PreambleFcmService.createChannels(this)
        persistFcmToken()
        GoogleCalendarManager.init(this)
        GoogleTasksManager.init(this)
        FeatureGateManager.init(this)

        // PM Messages: Firestore se override messages fetch karo (background mein)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            if (com.theblankstate.preamble.repository.PmMessageRepository.isStale(this@PreambleApplication)) {
                com.theblankstate.preamble.repository.PmMessageRepository.fetchAndCache(this@PreambleApplication)
            }
        }
        // Start the persistent notification service only if user hasn't disabled it
        if (TaskNotificationService.isEnabled(this)) {
            TaskNotificationService.start(this)
        }
        // Schedule a periodic keep-alive worker so the OS can't permanently
        // kill the notification service in background
        scheduleNotificationKeepAlive()
        NotificationKeepAliveScheduler.schedule(this)
        // Independent recovery path on platform JobScheduler. Several OEMs freeze
        // WorkManager-managed jobs while leaving raw JobScheduler alone, so we run both.
        NotificationKeepAliveJobService.schedule(this)
        // Schedule recurring task instance generation
        scheduleRecurrenceWorker()
        generateRecurrenceInstancesNow()
        // Schedule daily 6 PM evening task summary reminder
        com.theblankstate.preamble.notification.EveningReminderScheduler.schedule(this)
        // Schedule daily AI-generated personalized reminder
        com.theblankstate.preamble.ai.AiReminderEngine.ensureChannel(this)
        com.theblankstate.preamble.notification.AiReminderScheduler.schedule(this)
        // Initialize Remote Config first so the rest of the AI pipeline reads live values.
        // Then pull cloud memory + chat history, flush any pending writes, run maintenance.
        // All best-effort, non-blocking.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                com.theblankstate.preamble.ai.AiConfigService.initialize()
            } catch (e: Exception) {
                android.util.Log.w("PreambleApp", "AiConfigService init failed", e)
            }
            try {
                val mem = com.theblankstate.preamble.ai.AiMemoryRepository.get(this@PreambleApplication)
                mem.pullRemote()
                mem.cleanupDuplicateKeys()
                mem.flushPending()
            } catch (e: Exception) {
                android.util.Log.w("PreambleApp", "Memory bootstrap failed", e)
            }
            try {
                val chat = com.theblankstate.preamble.ai.ChatRepository.get(this@PreambleApplication)
                chat.pullRemote(chat.defaultConversationId())
                chat.flushPending()
            } catch (e: Exception) {
                android.util.Log.w("PreambleApp", "Chat bootstrap failed", e)
            }
            try {
                com.theblankstate.preamble.ai.MemoryMaintenance.run(this@PreambleApplication)
            } catch (e: Exception) {
                android.util.Log.w("PreambleApp", "Memory maintenance failed", e)
            }
        }
        // Schedule periodic Google sync (every 15 min)
        scheduleGoogleSyncWorker()
        // Observe task changes to refresh home screen widget
        observeTaskChangesForWidget()
    }

    /**
     * Firebase se currently signed-in user ko PostHog mein identify karta hai.
     * Isse PostHog events Firebase UID se link ho jaate hain — cross-referencing possible hoti hai.
     */
    private fun identifyCurrentUser() {
        try {
            val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (firebaseUser != null) {
                AnalyticsManager.identifyUser(
                    firebaseUid = firebaseUser.uid,
                    email = firebaseUser.email,
                    displayName = firebaseUser.displayName
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("PreambleApp", "PostHog identify failed", e)
        }
    }

    private fun scheduleNotificationKeepAlive() {
        val request = PeriodicWorkRequestBuilder<NotificationKeepAliveWorker>(
            15, TimeUnit.MINUTES  // minimum WorkManager interval
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            NotificationKeepAliveWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun scheduleRecurrenceWorker() {
        val request = PeriodicWorkRequestBuilder<RecurrenceWorker>(
            24, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            RecurrenceWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun scheduleGoogleSyncWorker() {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()

        // Periodic incremental sync every 10 min (uses syncToken — only fetches changes, very cheap)
        val request = androidx.work.PeriodicWorkRequestBuilder<GoogleSyncWorker>(
            10, java.util.concurrent.TimeUnit.MINUTES
        ).setConstraints(constraints)
            .setInputData(
                androidx.work.workDataOf(
                    "is_background" to true,
                    "reason" to "periodic"
                )
            ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            GoogleSyncWorker.WORK_NAME,
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun generateRecurrenceInstancesNow() {
        val workRequest = androidx.work.OneTimeWorkRequestBuilder<RecurrenceWorker>().build()
        WorkManager.getInstance(this).enqueue(workRequest)
    }

    private fun persistFcmToken() {
        appScope.launch(Dispatchers.IO) {
            try {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                    .addOnSuccessListener { token ->
                        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return@addOnSuccessListener
                        com.google.firebase.firestore.FirebaseFirestore.getInstance("preamble")
                            .collection("users").document(uid)
                            .update(mapOf("fcmToken" to token, "fcmTokenUpdatedAt" to System.currentTimeMillis()))
                    }
            } catch (e: Exception) {
                android.util.Log.w("PreambleApp", "FCM token persist failed", e)
            }
        }
    }

    private fun observeTaskChangesForWidget() {
        appScope.launch(Dispatchers.IO) {
            val today = com.theblankstate.preamble.repository.TaskRepository.todayString()
            repository.getTasksForDate(today).collect {
                WidgetUpdater.refresh(this@PreambleApplication)
            }
        }
    }

    private fun isMainProcess(context: Context): Boolean {
        try {
            val pid = android.os.Process.myPid()
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            for (processInfo in manager.runningAppProcesses ?: emptyList()) {
                if (processInfo.pid == pid) {
                    return processInfo.processName == context.packageName
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return false
    }
}
