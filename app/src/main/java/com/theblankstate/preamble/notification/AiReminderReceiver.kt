package com.theblankstate.preamble.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.theblankstate.preamble.ai.AiReminderEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AiReminderReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION = "com.theblankstate.preamble.AI_REMINDER"
        private const val TAG = "AiReminderReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        Log.d(TAG, "AI reminder alarm fired")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AiReminderEngine.run(context)
            } catch (e: Exception) {
                Log.e(TAG, "AI reminder failed", e)
            } finally {
                // AI-driven planner picks tomorrow's slot. If disabled, falls back to user-set time.
                try {
                    val decision = com.theblankstate.preamble.ai.ReminderPlanner.plan(context)
                    AiReminderScheduler.setTime(context, decision.hour, decision.minute)
                    // Persist tone for the engine to read on the next firing
                    context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE).edit()
                        .putString(AiReminderEngine.PREF_TONE, decision.tone.key)
                        .apply()
                } catch (e: Exception) {
                    Log.w(TAG, "Planner failed, fallback to fixed schedule", e)
                    AiReminderScheduler.schedule(context)
                }
                pendingResult.finish()
            }
        }
    }
}
