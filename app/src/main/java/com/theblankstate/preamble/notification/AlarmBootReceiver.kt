package com.theblankstate.preamble.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.theblankstate.preamble.PreambleApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Restores all task alarms after:
 * - Device reboot (BOOT_COMPLETED)
 * - Timezone change (TIMEZONE_CHANGED)
 * - Manual time change (TIME_SET)
 *
 * Also handles "just missed" alarms — if an alarm was due within the last
 * MISSED_WINDOW_MS of boot, fire it immediately instead of silently skipping.
 */
class AlarmBootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PreambleAlarm"
        private const val MISSED_WINDOW_MS = 2 * 60 * 1000L // 2 minutes
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_TIMEZONE_CHANGED &&
            action != "android.intent.action.TIME_SET") {
            return
        }

        Log.d(TAG, "AlarmBootReceiver triggered: action=$action")
        val pendingResult = goAsync()
        val app = context.applicationContext as PreambleApplication

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val allTasks = app.database.taskDao().getAllTasks()
                var restoredCount = 0
                var firedImmediately = 0
                val now = System.currentTimeMillis()

                for (task in allTasks) {
                    if (task.isCompleted || task.isAlarmPaused) continue
                    if (task.deadlineTime == null && !task.hasReminders) continue

                    // Use multi-reminder system if task has reminders
                    if (task.hasReminders) {
                        val triggers = task.computeReminderTriggerMs()
                        val hasFuture = triggers.any { it.second > now }
                        val justMissed = triggers.any { it.second in (now - MISSED_WINDOW_MS)..now }

                        if (hasFuture) {
                            TaskAlarmManager.scheduleReminders(context, task)
                            restoredCount++
                        } else if (justMissed) {
                            Log.d(TAG, "Firing missed reminder for '${task.title}'")
                            AlarmRingtoneService.startAlarm(context, task.id, task.title)
                            firedImmediately++
                        }
                        continue
                    }

                    // Legacy: single alarm from deadlineTime
                    val triggerMs = task.customAlarmTimeMs ?: run {
                        try {
                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                            sdf.parse("${task.createdDate} ${task.deadlineTime}")?.time
                        } catch (e: Exception) { null }
                    }

                    if (triggerMs == null) continue

                    when {
                        triggerMs > now -> {
                            TaskAlarmManager.scheduleAlarm(context, task.id, task.title, triggerMs)
                            restoredCount++
                        }
                        triggerMs > now - MISSED_WINDOW_MS -> {
                            Log.d(TAG, "Firing missed alarm for '${task.title}' (was due ${(now - triggerMs) / 1000}s ago)")
                            AlarmRingtoneService.startAlarm(context, task.id, task.title)
                            firedImmediately++
                        }
                    }
                }

                Log.d(TAG, "AlarmBootReceiver: restored=$restoredCount, firedImmediately=$firedImmediately (total tasks=${allTasks.size})")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore alarms", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
