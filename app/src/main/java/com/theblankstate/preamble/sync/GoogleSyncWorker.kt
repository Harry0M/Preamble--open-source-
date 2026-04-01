package com.theblankstate.preamble.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf

class GoogleSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val forceFull = inputData.getBoolean(KEY_FORCE_FULL, false)
        val isBackground = inputData.getBoolean(KEY_IS_BACKGROUND, true)
        val reason = inputData.getString(KEY_REASON) ?: "periodic"

        try {
            val summary = GoogleSyncCoordinator.syncLinkedData(
                context = applicationContext,
                forceFull = forceFull,
                isManual = false,
                isBackground = isBackground,
                reason = reason
            )
            Log.d(
                TAG,
                "Background Google sync completed: calendar=${summary.calendarEvents}, tasks=${summary.googleTasks}"
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Background Google sync failed", e)
            return Result.retry()
        }

        return Result.success()
    }

    companion object {
        private const val TAG = "GoogleSyncWorker"

        /** Periodic incremental sync (every 30 min) */
        const val WORK_NAME = "google_periodic_sync"

        /** One-time full sync (after link or 410 token expiry) */
        const val FULL_SYNC_WORK_NAME = "google_full_sync_bg"

        private const val IMMEDIATE_WORK_NAME = "google_immediate_sync"
        private const val KEY_FORCE_FULL = "force_full"
        private const val KEY_IS_BACKGROUND = "is_background"
        private const val KEY_REASON = "reason"

        private val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /**
         * Enqueue a one-time immediate sync.
         * @param forceFull If true, clears syncTokens and does a full re-fetch.
         * @param isBackground If true, uses isBgSyncing flag (no spinner), else uses manual.
         */
        fun enqueueImmediate(
            context: Context,
            forceFull: Boolean = false,
            isBackground: Boolean = false,
            reason: String = "local_change"
        ) {
            val request = OneTimeWorkRequestBuilder<GoogleSyncWorker>()
                .setInputData(
                    workDataOf(
                        KEY_FORCE_FULL to forceFull,
                        KEY_IS_BACKGROUND to isBackground,
                        KEY_REASON to reason
                    )
                )
                .setConstraints(networkConstraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        /**
         * Enqueue a background full sync (e.g. after 410 token expiry or first account link).
         * Uses KEEP policy so concurrent 410s don't queue multiple full syncs.
         */
        fun enqueueFullSync(
            context: Context,
            reason: String = "token_expired_or_link"
        ) {
            val request = OneTimeWorkRequestBuilder<GoogleSyncWorker>()
                .setInputData(
                    workDataOf(
                        KEY_FORCE_FULL to true,
                        KEY_IS_BACKGROUND to true,
                        KEY_REASON to reason
                    )
                )
                .setConstraints(networkConstraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                FULL_SYNC_WORK_NAME,
                ExistingWorkPolicy.KEEP, // Don't re-queue if one is already pending
                request
            )
        }
    }
}
