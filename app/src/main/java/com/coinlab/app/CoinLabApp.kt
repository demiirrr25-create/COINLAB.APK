package com.coinlab.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.coinlab.app.data.remote.DynamicCoinRegistry
import com.coinlab.app.worker.PriceAlertWorker
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class CoinLabApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var coinRegistry: DynamicCoinRegistry

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    /**
     * v7.7 — Optimized Coil ImageLoader for 250+ coin logos.
     * 50MB memory cache + 100MB disk cache + crossfade.
     * Coin icons rarely change, so aggressive caching is safe.
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20) // ~50MB on most devices
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_cache"))
                    .maxSizeBytes(100L * 1024 * 1024) // 100MB
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .crossfade(200)
            .respectCacheHeaders(false) // Always use our cache policy
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        schedulePriceAlertWorker()
        initializeFirebaseServices()
        // v7.9: Early-init DynamicCoinRegistry so market data is ready when user opens the screen
        appScope.launch {
            try {
                coinRegistry.initialize()
                android.util.Log.d("CoinLabApp", "DynamicCoinRegistry pre-initialized: ${coinRegistry.getCoinCount()} coins")
            } catch (_: Exception) { }
        }
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

        val communityChannel = NotificationChannel(
            CHANNEL_COMMUNITY,
            getString(R.string.channel_community),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.channel_community_desc)
        }

        val messagesChannel = NotificationChannel(
            CHANNEL_MESSAGES,
            "Mesajlar",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "P2P mesaj bildirimleri"
            enableVibration(true)
        }

        val tradingChannel = NotificationChannel(
            CHANNEL_TRADING_SIGNALS,
            "Trading Sinyalleri",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Sosyal trading sinyal bildirimleri"
        }

        manager.createNotificationChannels(
            listOf(priceAlertChannel, portfolioChannel, newsChannel, communityChannel, messagesChannel, tradingChannel)
        )
        } catch (_: Exception) { }
    }

    companion object {
        const val CHANNEL_PRICE_ALERTS = "price_alerts"
        const val CHANNEL_PORTFOLIO = "portfolio_updates"
        const val CHANNEL_NEWS = "crypto_news"
        const val CHANNEL_COMMUNITY = "community_notifications"
        const val CHANNEL_MESSAGES = "messages"
        const val CHANNEL_TRADING_SIGNALS = "trading_signals"
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

    /**
     * v9.5 — Initialize Firebase Remote Config + FCM topic subscriptions.
     */
    private fun initializeFirebaseServices() {
        appScope.launch {
            try {
                // Fetch Remote Config (contains gemini_api_key etc.)
                FirebaseRemoteConfig.getInstance().fetchAndActivate()
            } catch (_: Exception) { }
            try {
                // Subscribe to general crypto alerts topic
                FirebaseMessaging.getInstance().subscribeToTopic("crypto_alerts")
                FirebaseMessaging.getInstance().subscribeToTopic("trading_signals")
            } catch (_: Exception) { }
        }
    }
}
