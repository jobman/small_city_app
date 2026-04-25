package com.example.smallcityapp.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.smallcityapp.R
import com.example.smallcityapp.data.LocalPushMessage
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class DigitalTownFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        LocalPushStore(applicationContext).saveToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "Digital Town"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: message.data.entries.joinToString("\n") { "${it.key}: ${it.value}" }

        LocalPushStore(applicationContext).saveMessage(
            LocalPushMessage(
                title = title,
                body = body,
                receivedAt = System.currentTimeMillis(),
            ),
        )

        showNotification(title = title, body = body)
    }

    private fun showNotification(title: String, body: String) {
        ensureChannel()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), notification)
        }
    }

    private fun ensureChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Digital Town Alerts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Міські сповіщення та важливі повідомлення"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "digital_town_alerts"
    }
}
