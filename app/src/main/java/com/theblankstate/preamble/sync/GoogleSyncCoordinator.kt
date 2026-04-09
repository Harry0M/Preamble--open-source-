package com.theblankstate.preamble.sync

import android.content.Context
import android.util.Log
import com.theblankstate.preamble.PreambleApplication

data class GoogleSyncSummary(
    val calendarEvents: Int = 0,
    val googleTasks: Int = 0,
    val usedIncrementalCalendar: Boolean = false,
    val usedIncrementalTasks: Boolean = false
) {
    val performedAnySync: Boolean
        get() = calendarEvents > 0 || googleTasks > 0
}

object GoogleSyncCoordinator {
    private const val TAG = "GoogleSyncCoordinator"

    suspend fun syncLinkedData(
        context: Context,
        forceFull: Boolean = false,
        isManual: Boolean = false,
        isBackground: Boolean = false,
        reason: String = "unspecified"
    ): GoogleSyncSummary {
        val app = context.applicationContext as PreambleApplication
        var calendarEvents = 0
        var googleTasks = 0
        var usedIncrementalCalendar = false
        var usedIncrementalTasks = false

        Log.i(TAG, "══════════════════════════════════════════════════════")
        Log.i(TAG, "▶ SYNC_START reason='$reason' forceFull=$forceFull isManual=$isManual isBackground=$isBackground")
        Log.i(TAG, "  calendarLinked=${GoogleCalendarManager.isLinked.value} tasksLinked=${GoogleTasksManager.isLinked.value}")
        Log.i(TAG, "  hasAnySyncToken=${GoogleCalendarManager.hasAnySyncToken(context)}")
        Log.i(TAG, "  isBgSyncing=${GoogleCalendarManager.isBgSyncing.value} isManualSyncing=${GoogleCalendarManager.isManualSyncing.value}")

        // ── Google Tasks ────────────────────────────────────────────────────────
        if (GoogleTasksManager.isLinked.value) {
            try {
                val autoDelete = GoogleTasksManager.autoDeleteGoogleTasks.value
                val updatedAfter = if (forceFull) null else GoogleTasksManager.incrementalUpdatedAfterRfc()
                Log.i(TAG, "  TASKS_START: mode=${if (updatedAfter != null) "INCREMENTAL updatedAfter=$updatedAfter" else "FULL"}")

                // Atomic: fetch ALL tasks in memory, then write once
                val tasks = GoogleTasksManager.fetchGoogleTasks(
                    context,
                    updatedAfter = updatedAfter,
                    onProgressUpdate = {}  // no-op: atomic write only after full fetch
                )
                googleTasks = tasks.size
                usedIncrementalTasks = updatedAfter != null
                Log.i(TAG, "  TASKS_FETCHED: count=${tasks.size} mode=${if (usedIncrementalTasks) "INCREMENTAL" else "FULL"}")

                if (usedIncrementalTasks) {
                    app.repository.quickSyncGoogleTasks(tasks, autoDelete)
                } else {
                    // SAFETY: Full sync with 0 tasks would delete everything.
                    // Only proceed if we actually fetched tasks, otherwise skip.
                    val existingCount = app.repository.getGoogleTaskCount()
                    if (tasks.isEmpty() && existingCount > 0) {
                        Log.w(TAG, "  ⚠️ TASKS_SAFETY: Full sync returned 0 tasks but $existingCount exist locally — SKIPPING delete-sync to prevent data loss")
                    } else {
                        app.repository.syncGoogleTasks(tasks, autoDelete)
                    }
                }
                Log.i(TAG, "  ✅ TASKS_WRITTEN_TO_ROOM: ${tasks.size} tasks")
            } catch (e: Throwable) {
                Log.e(TAG, "  ❌ TASKS_SYNC_FAILED (non-fatal, Calendar will still sync): ${e.message}", e)
            }
        } else {
            Log.d(TAG, "  TASKS: not linked, skipping")
        }

        // ── Google Calendar ─────────────────────────────────────────────────────
        if (GoogleCalendarManager.isLinked.value) {
            try {
                Log.i(TAG, "  CALENDAR_START: forceFull=$forceFull isManual=$isManual isBackground=$isBackground")

                // Atomic: fetch ALL events in memory, then write once
                val calendarResult = GoogleCalendarManager.fetchCalendarEvents(
                    context,
                    forceFullSync = forceFull,
                    isManual = isManual,
                    isBackground = isBackground,
                    onProgressUpdate = {}  // no-op: atomic write only after full fetch
                )
                calendarEvents = calendarResult.events.size
                usedIncrementalCalendar = !forceFull && calendarResult.isIncremental

                Log.i(TAG, "  CALENDAR_FETCHED: events=${calendarResult.events.size} " +
                    "isIncremental=${calendarResult.isIncremental} " +
                    "usedIncrementalCalendar=$usedIncrementalCalendar")

                if (usedIncrementalCalendar) {
                    Log.i(TAG, "  CALENDAR: writing via quickSync (incremental merge)")
                    app.repository.quickSyncCalendarEvents(calendarResult.events)
                } else {
                    // SAFETY: Full sync with 0 events would delete everything.
                    // Only proceed if we actually fetched events, otherwise skip.
                    val existingCount = app.repository.getCalendarEventCount()
                    if (calendarResult.events.isEmpty() && existingCount > 0) {
                        Log.w(TAG, "  ⚠️ CALENDAR_SAFETY: Full sync returned 0 events but $existingCount exist locally — SKIPPING delete-sync to prevent data loss")
                    } else {
                        Log.i(TAG, "  CALENDAR: writing via fullSync (replace+merge)")
                        app.repository.syncCalendarEvents(calendarResult.events)
                    }
                }
                Log.i(TAG, "  ✅ CALENDAR_WRITTEN_TO_ROOM: ${calendarResult.events.size} events")
            } catch (e: Throwable) {
                Log.e(TAG, "  ❌ CALENDAR_SYNC_FAILED (non-fatal): ${e.message}", e)
            }
        } else {
            Log.d(TAG, "  CALENDAR: not linked, skipping")
        }

        Log.i(TAG, "■ SYNC_DONE reason='$reason' calEvents=$calendarEvents tasks=$googleTasks " +
            "incrementalCal=$usedIncrementalCalendar incrementalTasks=$usedIncrementalTasks")
        Log.i(TAG, "══════════════════════════════════════════════════════")

        return GoogleSyncSummary(
            calendarEvents = calendarEvents,
            googleTasks = googleTasks,
            usedIncrementalCalendar = usedIncrementalCalendar,
            usedIncrementalTasks = usedIncrementalTasks
        )
    }

    suspend fun syncLinkedDataIfStale(
        context: Context,
        staleAfterMs: Long,
        reason: String = "unspecified"
    ): GoogleSyncSummary? {
        val now = System.currentTimeMillis()
        val calLastSync = GoogleCalendarManager.lastSyncTime.value
        val tasksLastSync = GoogleTasksManager.lastSyncTime.value
        val calStaleSecs = if (calLastSync > 0) (now - calLastSync) / 1000 else -1L
        val tasksStaleSecs = if (tasksLastSync > 0) (now - tasksLastSync) / 1000 else -1L

        val calendarStale = GoogleCalendarManager.isLinked.value &&
            (calLastSync <= 0L || now - calLastSync > staleAfterMs)
        val tasksStale = GoogleTasksManager.isLinked.value &&
            (tasksLastSync <= 0L || now - tasksLastSync > staleAfterMs)

        Log.d(TAG, "STALE_CHECK reason='$reason' cooldown=${staleAfterMs / 1000}s " +
            "calStale=$calendarStale(${calStaleSecs}s ago) " +
            "tasksStale=$tasksStale(${tasksStaleSecs}s ago)")

        if (!calendarStale && !tasksStale) {
            Log.d(TAG, "  → SKIPPED (within cooldown, no sync needed)")
            return null
        }

        return syncLinkedData(
            context = context,
            forceFull = false,
            isManual = false,
            isBackground = false,
            reason = reason
        )
    }
}
