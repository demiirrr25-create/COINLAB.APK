package com.coinlab.app.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.coinlab.app.MainActivity
import com.coinlab.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun showPriceAlert(
        coinName: String,
        coinSymbol: String,
        currentPrice: Double,
        targetPrice: Double,
        isAbove: Boolean
    ) {
        val direction = if (isAbove) "üstüne çıktı" else "altına düştü"
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_PRICE_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$coinName (${coinSymbol.uppercase()}) Fiyat Uyarısı")
            .setContentText("$coinName fiyatı ₺%.2f hedef fiyat ₺%.2f $direction".format(currentPrice, targetPrice))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(
            "$coinSymbol-$targetPrice".hashCode(),
            notification
        )
    }

    fun showPortfolioUpdate(
        totalValue: Double,
        changePercent: Double
    ) {
        val changeDirection = if (changePercent >= 0) "↑" else "↓"
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "portfolio")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_PORTFOLIO_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Portföy Güncelleme")
            .setContentText("Toplam: ₺%.2f %s%.1f%%".format(totalValue, changeDirection, kotlin.math.abs(changePercent)))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_PORTFOLIO, notification)
    }

    fun showNewsAlert(title: String, source: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "news")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 2, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_CRYPTO_NEWS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Kripto Haber")
            .setContentText(title)
            .setSubText(source)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(title.hashCode(), notification)
    }

    fun showCommunityNotification(title: String, message: String, notificationId: Int = NOTIFICATION_ID_COMMUNITY) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "community")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 3, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_COMMUNITY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    /**
     * v9.5 — Show notification for new P2P messages.
     */
    fun showMessageNotification(senderName: String, messageText: String, chatId: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "chat_list")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 4, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(senderName)
            .setContentText(messageText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(chatId.hashCode(), notification)
    }

    /**
     * v9.5 — Show notification for new trading signals.
     */
    fun showTradingSignalNotification(authorName: String, coinName: String, signalType: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "social_trading")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 5, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val typeText = if (signalType == "BUY") "AL" else "SAT"
        val notification = NotificationCompat.Builder(context, CHANNEL_TRADING_SIGNALS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Yeni Trading Sinyali")
            .setContentText("$authorName $coinName için $typeText sinyali paylaştı")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify("signal_$coinName".hashCode(), notification)
    }

    companion object {
        const val CHANNEL_PRICE_ALERTS = "price_alerts"
        const val CHANNEL_PORTFOLIO_UPDATES = "portfolio_updates"
        const val CHANNEL_CRYPTO_NEWS = "crypto_news"
        const val CHANNEL_COMMUNITY = "community_notifications"
        const val CHANNEL_MESSAGES = "messages"
        const val CHANNEL_TRADING_SIGNALS = "trading_signals"
        const val NOTIFICATION_ID_PORTFOLIO = 1001
        const val NOTIFICATION_ID_COMMUNITY = 2001
    }
}
