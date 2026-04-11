package com.theblankstate.preamble.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.theblankstate.preamble.MainActivity
import com.theblankstate.preamble.R

/**
 * Handles incoming FCM push notifications sent by the admin panel.
 *
 * Data payload keys:
 *   - title: Notification title
 *   - body: Notification body text
 *   - deepLink: Optional deep link (preamble://settings/theme)
 *   - url: Optional external URL
 *   - imageUrl: Optional large image URL (future use)
 *   - channelType: "broadcast" (default) or "promo"
 */
class PreambleFcmService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "PreambleFcm"
        private const val CHANNEL_BROADCAST = "preamble_broadcasts"
        private const val CHANNEL_PROMO = "preamble_promos"
        private const val FIRESTORE_DB_ID = "preamble"

        fun createChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(NotificationManager::class.java)

                val broadcastChannel = NotificationChannel(
                    CHANNEL_BROADCAST,
                    "Updates & Announcements",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Important updates, new features, and announcements from Preamble"
                }

                val promoChannel = NotificationChannel(
                    CHANNEL_PROMO,
                    "Offers & Tips",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Special offers, tips, and feature suggestions"
                }

                nm.createNotificationChannel(broadcastChannel)
                nm.createNotificationChannel(promoChannel)
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed: ${token.take(10)}...")
        persistTokenToFirestore(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "FCM message received: ${message.data}")

        // Prefer data payload (admin panel sends data-only messages)
        val data = message.data
        val title = data["title"] ?: message.notification?.title ?: "Preamble"
        val body = data["body"] ?: message.notification?.body ?: return
        val deepLink = data["deepLink"]
        val externalUrl = data["url"]
        val channelType = data["channelType"] ?: "broadcast"

        showNotification(title, body, deepLink, externalUrl, channelType)
    }

    private fun showNotification(
        title: String,
        body: String,
        deepLink: String?,
        externalUrl: String?,
        channelType: String
    ) {
        val channelId = if (channelType == "promo") CHANNEL_PROMO else CHANNEL_BROADCAST

        // Build intent: deep link > external URL > open app at home
        val intent = when {
            !deepLink.isNullOrBlank() -> {
                // Deep links go to MainActivity explicitly (not ACTION_VIEW which may not resolve)
                Intent(this, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = Uri.parse(deepLink)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            }
            !externalUrl.isNullOrBlank() -> {
                // External URLs open in browser — do NOT set package so system resolves it
                Intent(Intent.ACTION_VIEW, Uri.parse(externalUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            else -> {
                // Default: open home screen via deep link so admin tasks are visible
                Intent(this, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = Uri.parse("preamble://home")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(
                if (channelType == "promo") NotificationCompat.PRIORITY_LOW
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(System.currentTimeMillis().toInt(), notification)
    }

    /**
     * Store the FCM token in Firestore so the admin panel can send
     * targeted or broadcast messages.
     */
    private fun persistTokenToFirestore(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        try {
            FirebaseFirestore.getInstance(FIRESTORE_DB_ID)
                .collection("users")
                .document(uid)
                .update(
                    mapOf(
                        "fcmToken" to token,
                        "fcmTokenUpdatedAt" to System.currentTimeMillis()
                    )
                )
                .addOnFailureListener { e ->
                    Log.w(TAG, "Failed to persist FCM token", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error persisting FCM token", e)
        }
    }
}
