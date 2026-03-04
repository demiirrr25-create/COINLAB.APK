package com.coinlab.app.data.remote.firebase

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.coinlab.app.CoinLabApp
import com.coinlab.app.MainActivity
import com.coinlab.app.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * v9.5 — Firebase Cloud Messaging Service
 *
 * Handles incoming push notifications and token refresh.
 * Notification types: messages, trading_signals, price_alerts, general
 */
class FCMService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        saveTokenToDatabase(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        val notificationType = data["type"] ?: "general"
        val title = data["title"] ?: message.notification?.title ?: "CoinLab"
        val body = data["body"] ?: message.notification?.body ?: ""

        val channelId = when (notificationType) {
            "message" -> CHANNEL_MESSAGES
            "trading_signal" -> CHANNEL_TRADING_SIGNALS
            "price_alert" -> CoinLabApp.CHANNEL_PRICE_ALERTS
            "community" -> CoinLabApp.CHANNEL_COMMUNITY
            else -> CoinLabApp.CHANNEL_COMMUNITY
        }

        val navigateTo = when (notificationType) {
            "message" -> "chat_list"
            "trading_signal" -> "social_trading"
            "price_alert" -> "price_alerts"
            "community" -> "community"
            else -> "home"
        }

        showNotification(title, body, channelId, navigateTo, notificationType.hashCode())
    }

    private fun showNotification(
        title: String,
        body: String,
        channelId: String,
        navigateTo: String,
        notificationId: Int
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", navigateTo)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }

    private fun saveTokenToDatabase(token: String) {
        try {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val database = FirebaseDatabase.getInstance(
                "https://com-coinlab-app-default-rtdb.europe-west1.firebasedatabase.app/"
            )
            database.reference
                .child("user_tokens")
                .child(userId)
                .setValue(mapOf(
                    "token" to token,
                    "platform" to "android",
                    "updatedAt" to com.google.firebase.database.ServerValue.TIMESTAMP
                ))
        } catch (_: Exception) { }
    }

    companion object {
        const val CHANNEL_MESSAGES = "messages"
        const val CHANNEL_TRADING_SIGNALS = "trading_signals"
    }
}
