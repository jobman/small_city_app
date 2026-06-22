package com.example.smallcityapp.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.smallcityapp.MainActivity
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
            ?: PushMessagePayload.titleFromData(message.data)
            ?: getString(R.string.app_name)
        val body = message.notification?.body
            ?: PushMessagePayload.bodyFromData(message.data)
            ?: message.data.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        val receivedAt = System.currentTimeMillis()

        LocalPushStore(applicationContext).saveMessage(
            LocalPushMessage(
                title = title,
                body = body,
                receivedAt = receivedAt,
            ),
        )

        showNotification(title = title, body = body, receivedAt = receivedAt)
    }

    private fun showNotification(title: String, body: String, receivedAt: Long) {
        ensureChannel()

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(PushMessagePayload.EXTRA_TITLE, title)
            putExtra(PushMessagePayload.EXTRA_BODY, body)
            putExtra(PushMessagePayload.EXTRA_RECEIVED_AT, receivedAt)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            receivedAt.toInt(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val canPostNotifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

        if (canPostNotifications) {
            NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), notification)
        }
    }

    private fun ensureChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.app_name),
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
