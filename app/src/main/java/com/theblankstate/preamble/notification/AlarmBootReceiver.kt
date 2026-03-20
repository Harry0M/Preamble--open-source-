package com.theblankstate.preamble.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.theblankstate.preamble.PreambleApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("AlarmBootReceiver", "Device booted. Restoring task alarms...")
            val pendingResult = goAsync()
            val app = context.applicationContext as PreambleApplication

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val allTasks = app.database.taskDao().getAllTasks()
                    var restoredCount = 0
                    
                    for (task in allTasks) {
                        if (!task.isCompleted && !task.isAlarmPaused) {
                            val triggerMs = task.customAlarmTimeMs ?: run {
                                if (task.deadlineTime == null) return@run null
                                try {
                                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                                    sdf.parse("${task.createdDate} ${task.deadlineTime}")?.time
                                } catch (e: Exception) { null }
                            }
                            
                            if (triggerMs != null && triggerMs > System.currentTimeMillis()) {
                                TaskAlarmManager.scheduleAlarm(context, task.id, task.title, triggerMs)
                                restoredCount++
                            }
                        }
                    }
                    Log.d("AlarmBootReceiver", "Restored $restoredCount task alarms.")
                } catch (e: Exception) {
                    Log.e("AlarmBootReceiver", "Failed to restore alarms on boot", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
