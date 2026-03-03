package com.coinlab.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.coinlab.app.data.local.dao.PriceAlertDao
import com.coinlab.app.data.remote.DynamicCoinRegistry
import com.coinlab.app.data.remote.cache.BinanceTickerCache
import com.coinlab.app.notification.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class PriceAlertWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val priceAlertDao: PriceAlertDao,
    private val tickerCache: BinanceTickerCache,
    private val notificationHelper: NotificationHelper,
    private val coinRegistry: DynamicCoinRegistry
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val activeAlerts = priceAlertDao.getActiveAlerts()
            if (activeAlerts.isEmpty()) return Result.success()

            // Fetch prices from centralized cache
            val tickerMap = tickerCache.getTickerMap(forceRefresh = true)

            // Check each alert
            for (alert in activeAlerts) {
                val binanceSymbol = coinRegistry.getBinanceSymbolByCoinId(alert.coinId) ?: continue
                val ticker = tickerMap[binanceSymbol] ?: continue
                val currentPrice = ticker.lastPrice?.toDoubleOrNull() ?: continue

                val triggered = if (alert.isAbove) {
                    currentPrice >= alert.targetPrice
                } else {
                    currentPrice <= alert.targetPrice
                }

                if (triggered) {
                    // Send notification
                    notificationHelper.showPriceAlert(
                        coinName = alert.coinName,
                        coinSymbol = alert.coinSymbol,
                        currentPrice = currentPrice,
                        targetPrice = alert.targetPrice,
                        isAbove = alert.isAbove
                    )

                    // Mark alert as triggered
                    priceAlertDao.update(
                        alert.copy(
                            isActive = false,
                            isTriggered = true
                        )
                    )
                }
            }

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        const val WORK_NAME = "price_alert_check"
    }
}
