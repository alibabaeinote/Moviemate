package com.moviemate.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives FCM messages and keeps the device's token registered.
 *
 * Tokens change on reinstall and on "clear data", so onNewToken has to write
 * back — registering once at sign-up is not enough.
 */
class MovieMateMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        registerToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val notification = message.notification ?: return
        val target = message.data["deepLinkTarget"]
        val essential = message.data["type"] in ESSENTIAL_TYPES

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_DEEP_LINK_TARGET, target)
            putExtra(EXTRA_PAIR_ID, message.data["pairId"])
            putExtra(EXTRA_MATCH_ID, message.data["matchId"])
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val channelId = if (essential) CHANNEL_ESSENTIAL else CHANNEL_ACTIVITY
        ensureChannels(this)

        val built = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(
                if (essential) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT,
            )
            .build()

        // POST_NOTIFICATIONS may have been revoked since onboarding.
        runCatching {
            NotificationManagerCompat.from(this).notify(notification.hashCode(), built)
        }
    }

    private fun registerToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(uid).update(
            mapOf(
                "fcmTokens" to FieldValue.arrayUnion(token),
                "fcmTokenUpdatedAt" to Timestamp.now(),
            ),
        )
    }

    companion object {
        const val EXTRA_DEEP_LINK_TARGET = "deepLinkTarget"
        const val EXTRA_PAIR_ID = "pairId"
        const val EXTRA_MATCH_ID = "matchId"

        const val CHANNEL_ESSENTIAL = "moviemate_essential"
        const val CHANNEL_ACTIVITY = "moviemate_activity"

        /** Must match the `essential` flags in functions/src/notifications/types.ts. */
        private val ESSENTIAL_TYPES = setOf(
            "daily_match",
            "partner_joined",
            "both_confirmed",
            "scheduled_reminder",
        )

        fun ensureChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ESSENTIAL,
                    "Daily match & reminders",
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ACTIVITY,
                    "Partner activity",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
    }
}
