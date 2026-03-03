package com.theblankstate.preamble.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager

class AlarmDismissReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra("notification_id", 0)

        // Stop any playing ringtone
        activeRingtone?.stop()
        activeRingtone = null

        // Cancel the notification
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(notificationId)
    }

    companion object {
        var activeRingtone: Ringtone? = null
    }
}
