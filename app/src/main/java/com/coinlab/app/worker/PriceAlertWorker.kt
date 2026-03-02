package com.coinlab.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.coinlab.app.data.local.dao.PriceAlertDao
import com.coinlab.app.data.remote.api.CoinGeckoApi
import com.coinlab.app.notification.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class PriceAlertWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val priceAlertDao: PriceAlertDao,
    private val coinGeckoApi: CoinGeckoApi,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val activeAlerts = priceAlertDao.getActiveAlerts()
            if (activeAlerts.isEmpty()) return Result.success()

            // Get unique coin IDs
            val coinIds = activeAlerts.map { it.coinId }.distinct()
            val idsParam = coinIds.joinToString(",")

            // Fetch current prices
            val priceResponse = coinGeckoApi.getSimplePrice(
                ids = idsParam,
                currencies = "usd"
            )

            // Check each alert
            for (alert in activeAlerts) {
                val coinPrices = priceResponse[alert.coinId] ?: continue
                val currentPrice = coinPrices["usd"] ?: continue

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
