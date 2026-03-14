package com.theblankstate.preamble

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.theblankstate.preamble.data.PreambleDatabase
import com.theblankstate.preamble.notification.NotificationKeepAliveScheduler
import com.theblankstate.preamble.notification.NotificationKeepAliveWorker
import com.theblankstate.preamble.notification.TaskNotificationManager
import com.theblankstate.preamble.notification.TaskNotificationService
import com.theblankstate.preamble.repository.TaskRepository
import com.theblankstate.preamble.sync.FirebaseTaskSyncManager
import com.theblankstate.preamble.sync.GoogleCalendarManager
import com.theblankstate.preamble.sync.GoogleTasksManager
import com.google.android.gms.ads.MobileAds
import com.theblankstate.preamble.ads.FeatureGateManager
import com.theblankstate.preamble.ads.RewardedAdManager
import com.theblankstate.preamble.ads.AppOpenAdManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.TimeUnit

class PreambleApplication : Application() {

    /** Application-scoped coroutine scope — survives configuration changes and screen navigations. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val database by lazy { PreambleDatabase.getInstance(this) }
    val syncManager by lazy { FirebaseTaskSyncManager(this, database.taskDao()) }
    val repository by lazy { TaskRepository(database.taskDao(), syncManager) }

    override fun onCreate() {
        super.onCreate()
        FirebaseTaskSyncManager.enableOfflinePersistence()
        syncManager.start()
        TaskNotificationManager.createChannel(this)
        GoogleCalendarManager.init(this)
        GoogleTasksManager.init(this)
        MobileAds.initialize(this) {
            // SDK is ready — preload the app open ad so it's available when onResume fires
            AppOpenAdManager.preload()
        }
        FeatureGateManager.init(this)
        RewardedAdManager.init(this)
        AppOpenAdManager.init(this)
        // Start the persistent notification service only if user hasn't disabled it
        if (TaskNotificationService.isEnabled(this)) {
            TaskNotificationService.start(this)
        }
        // Schedule a periodic keep-alive worker so the OS can't permanently
        // kill the notification service in background
        scheduleNotificationKeepAlive()
        NotificationKeepAliveScheduler.schedule(this)
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
}
