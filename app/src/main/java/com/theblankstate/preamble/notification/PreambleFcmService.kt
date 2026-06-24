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
import com.theblankstate.preamble.analytics.AnalyticsManager

/**
 * Handles incoming FCM push notifications sent by the admin panel.
 *
 * Data payload keys:
 *   - title: Notification title
 *   - body: Notification body text
 *   - deepLink: Optional deep link (preamble://settings/theme)
 *   - url: Optional external URL
 *   - imageUrl: Optional large image URL (future use)
 *   - channelType: "broadcast" (default), "promo", "social_invites", "social_collab", or "social_kudos"
 */
class PreambleFcmService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "PreambleFcm"
        private const val CHANNEL_BROADCAST = "preamble_broadcasts"
        private const val CHANNEL_PROMO = "preamble_promos"
        private const val CHANNEL_SOCIAL_INVITES = "preamble_social_invites"
        private const val CHANNEL_SOCIAL_COLLAB = "preamble_social_collab"
        private const val CHANNEL_SOCIAL_KUDOS = "preamble_social_kudos"
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

                val socialInvitesChannel = NotificationChannel(
                    CHANNEL_SOCIAL_INVITES,
                    "Invites & Friends",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "When friends accept your invites"
                }

                val socialCollabChannel = NotificationChannel(
                    CHANNEL_SOCIAL_COLLAB,
                    "Shared Tasks",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Assignments, changes, and completions on your shared tasks"
                }

                val socialKudosChannel = NotificationChannel(
                    CHANNEL_SOCIAL_KUDOS,
                    "Kudos & Nudges",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Reactions and nudges from your teammates"
                }

                nm.createNotificationChannel(broadcastChannel)
                nm.createNotificationChannel(promoChannel)
                nm.createNotificationChannel(socialInvitesChannel)
                nm.createNotificationChannel(socialCollabChannel)
                nm.createNotificationChannel(socialKudosChannel)
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
        val campaignId = data["campaign_id"] ?: "unknown"
        val campaignVariant = data["campaign_variant"] ?: "default"

        // PostHog: Notification receive track karo — A/B testing ke liye
        AnalyticsManager.trackNotificationReceived(
            campaignId = campaignId,
            variant = campaignVariant,
            channelType = channelType
        )

        showNotification(title, body, deepLink, externalUrl, channelType, campaignId, campaignVariant)
    }

    private fun showNotification(
        title: String,
        body: String,
        deepLink: String?,
        externalUrl: String?,
        channelType: String,
        campaignId: String = "unknown",
        campaignVariant: String = "default"
    ) {
        val channelId = when (channelType) {
            "promo" -> CHANNEL_PROMO
            "social_invites" -> CHANNEL_SOCIAL_INVITES
            "social_collab" -> CHANNEL_SOCIAL_COLLAB
            "social_kudos" -> CHANNEL_SOCIAL_KUDOS
            else -> CHANNEL_BROADCAST
        }

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

        // Campaign ID intent mein daalo — click tracking ke liye
        intent.putExtra("campaign_id", campaignId)
        intent.putExtra("campaign_variant", campaignVariant)

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
