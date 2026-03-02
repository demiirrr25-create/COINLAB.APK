package com.coinlab.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.coinlab.app.worker.PriceAlertWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class CoinLabApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        schedulePriceAlertWorker()
    }

    private fun createNotificationChannels() {
        try {
        val manager = getSystemService(NotificationManager::class.java)

        val priceAlertChannel = NotificationChannel(
            CHANNEL_PRICE_ALERTS,
            getString(R.string.channel_price_alerts),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.channel_price_alerts_desc)
            enableVibration(true)
        }

        val portfolioChannel = NotificationChannel(
            CHANNEL_PORTFOLIO,
            getString(R.string.channel_portfolio),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.channel_portfolio_desc)
        }

        val newsChannel = NotificationChannel(
            CHANNEL_NEWS,
            getString(R.string.channel_news),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_news_desc)
        }

        manager.createNotificationChannels(
            listOf(priceAlertChannel, portfolioChannel, newsChannel)
        )
        } catch (_: Exception) { }
    }

    companion object {
        const val CHANNEL_PRICE_ALERTS = "price_alerts"
        const val CHANNEL_PORTFOLIO = "portfolio_updates"
        const val CHANNEL_NEWS = "crypto_news"
    }

    private fun schedulePriceAlertWorker() {
        try {
            val workRequest = PeriodicWorkRequestBuilder<PriceAlertWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                PriceAlertWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        } catch (_: Exception) { }
    }
}
