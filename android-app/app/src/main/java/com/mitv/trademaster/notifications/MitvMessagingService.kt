package com.mitv.trademaster.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mitv.trademaster.MainActivity
import com.mitv.trademaster.R

/**
 * Handles incoming push notifications sent from the admin panel. Each
 * notification can carry a "deepLink" data field (e.g. "lessons",
 * "analyzer", "payment", "chat") that routes the user straight to that
 * screen when they tap the notification — see MainActivity's intent
 * handling for how the link is consumed.
 *
 * Also re-registers the FCM token in Firestore whenever it refreshes, so
 * the admin panel can target this device.
 */
class MitvMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID = "mitv_notifications"
        const val EXTRA_DEEP_LINK = "deep_link"
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title ?: message.data["title"] ?: "MI Trade Master"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val deepLink = message.data["deepLink"] ?: ""

        showNotification(title, body, deepLink)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Save token to Firestore so the admin panel can target this device.
        try {
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            val uid = auth.currentUser?.uid ?: return
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("students").document(uid)
                .update("fcmToken", token)
        } catch (e: Exception) {
            // Non-fatal — token will be saved next time the app opens if this fails.
        }
    }

    private fun showNotification(title: String, body: String, deepLink: String) {
        val manager = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "MI Trade Master", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DEEP_LINK, deepLink)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
