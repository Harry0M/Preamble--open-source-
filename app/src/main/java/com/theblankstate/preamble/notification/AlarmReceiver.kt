package com.theblankstate.preamble.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Thin bridge from AlarmManager → AlarmRingtoneService.
 *
 * This receiver does NOTHING except start the foreground service.
 * All notification building, audio playback, and action handling
 * is done in AlarmRingtoneService (which survives process death).
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra("task_id") ?: return
        val taskTitle = intent.getStringExtra("task_title") ?: "Task Reminder"

        Log.d("PreambleAlarm", "AlarmReceiver fired for '$taskTitle' (id=$taskId)")

        // Delegate everything to the foreground service
        AlarmRingtoneService.startAlarm(context, taskId, taskTitle)
    }
}
