package com.coinlab.app.data.remote.firebase

import android.util.Log
import com.coinlab.app.data.remote.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v12.0 — Aggregates liquidation, open-interest, funding-rate & long/short data
 * from Binance Futures, Bybit, OKX, Bitget, and Gate.io.
 *
 * Fallback strategy: If an exchange fails, others continue. Data is merged
 * with weighted averaging (weight = exchange's OI share).
 */
@Singleton
class LiquidationRepository @Inject constructor(
    private val binanceFutures: BinanceFuturesApi,
    private val bybitApi: BybitApi,
    private val okxApi: OkxApi,
    private val bitgetApi: BitgetApi,
    private val gateioApi: GateioApi
) {
    companion object {
        private const val TAG = "LiquidationRepo"
    }

    // ─── Symbol mapping per exchange ──────────────────────────────────

    private fun binanceSymbol(base: String) = "${base}USDT"
    private fun bybitSymbol(base: String) = "${base}USDT"
    private fun okxSymbol(base: String) = "${base}-USDT-SWAP"
    private fun bitgetSymbol(base: String) = "${base}USDT"
    private fun gateioSymbol(base: String) = "${base}_USDT"

    // ─── Aggregated Liquidation Data ─────────────────────────────────

    /**
     * Fetch aggregated liquidation data for a coin (e.g. "BTC").
     * Returns multi-exchange combined analysis.
     */
    suspend fun getAggregatedData(baseCoin: String): AggregatedLiquidationData {
        return supervisorScope {
            val binanceDeferred = async { fetchBinanceData(baseCoin) }
            val bybitDeferred = async { fetchBybitData(baseCoin) }
            val okxDeferred = async { fetchOkxData(baseCoin) }
            val bitgetDeferred = async { fetchBitgetData(baseCoin) }
            val gateioDeferred = async { fetchGateioData(baseCoin) }

            val binance = binanceDeferred.await()
            val bybit = bybitDeferred.await()
            val okx = okxDeferred.await()
            val bitget = bitgetDeferred.await()
            val gateio = gateioDeferred.await()

            val exchanges = listOfNotNull(binance, bybit, okx, bitget, gateio)

            if (exchanges.isEmpty()) {
                return@supervisorScope AggregatedLiquidationData(baseCoin = baseCoin)
            }

            // Aggregate values
            val totalOI = exchanges.sumOf { it.openInterestUsd }
            val weightedFunding = if (totalOI > 0) {
                exchanges.sumOf { it.fundingRate * it.openInterestUsd } / totalOI
            } else exchanges.map { it.fundingRate }.average()

            val totalLongRatio = exchanges.map { it.longRatio }.average()
            val totalShortRatio = exchanges.map { it.shortRatio }.average()

            // Merge all liquidation events
            val allLiquidations = exchanges.flatMap { it.recentLiquidations }
                .sortedByDescending { it.timestamp }

            // Build price-level aggregation for heatmap
            val markPrice = exchanges.firstOrNull { it.markPrice > 0 }?.markPrice ?: 0.0
            val heatmapBuckets = buildHeatmapBuckets(allLiquidations, markPrice, baseCoin)

            AggregatedLiquidationData(
                baseCoin = baseCoin,
                markPrice = markPrice,
                totalOpenInterestUsd = totalOI,
                aggregatedFundingRate = weightedFunding,
                longRatio = totalLongRatio,
                shortRatio = totalShortRatio,
                exchangeBreakdowns = exchanges,
                recentLiquidations = allLiquidations.take(200),
                heatmapBuckets = heatmapBuckets,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }

    /**
     * Fetch Open Interest history from Binance for heatmap time-series.
     */
    suspend fun getOpenInterestHistory(
        baseCoin: String,
        period: String = "5m",
        limit: Int = 30
    ): List<OIHistoryPoint> {
        return try {
            val data = binanceFutures.getOpenInterestHistory(
                symbol = binanceSymbol(baseCoin),
                period = period,
                limit = limit
            )
            data.map {
                OIHistoryPoint(
                    timestamp = it.timestamp,
                    openInterest = it.sumOpenInterest.toDoubleOrNull() ?: 0.0,
                    openInterestValue = it.sumOpenInterestValue.toDoubleOrNull() ?: 0.0
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "OI history failed", e)
            emptyList()
        }
    }

    /**
     * Fetch long/short ratio history from Binance for chart overlay.
     */
    suspend fun getLongShortHistory(
        baseCoin: String,
        period: String = "5m",
        limit: Int = 30
    ): List<LongShortPoint> {
        return try {
            val data = binanceFutures.getTopLongShortRatio(
                symbol = binanceSymbol(baseCoin),
                period = period,
                limit = limit
            )
            data.map {
                LongShortPoint(
                    timestamp = it.timestamp,
                    longRatio = it.longAccount.toDoubleOrNull() ?: 0.5,
                    shortRatio = it.shortAccount.toDoubleOrNull() ?: 0.5,
                    ratio = it.longShortRatio.toDoubleOrNull() ?: 1.0
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "L/S history failed", e)
            emptyList()
        }
    }

    // ─── Per-exchange Fetchers ────────────────────────────────────────

    private suspend fun fetchBinanceData(baseCoin: String): ExchangeData? {
        return try {
            val symbol = binanceSymbol(baseCoin)
            val oi = binanceFutures.getOpenInterest(symbol)
            val funding = binanceFutures.getFundingRate(symbol, limit = 1).firstOrNull()
            val premium = binanceFutures.getPremiumIndex(symbol)
            val lsRatio = binanceFutures.getGlobalLongShortRatio(symbol, limit = 1).firstOrNull()
            val liquidations = try {
                binanceFutures.getForceOrders(symbol, limit = 100)
            } catch (_: Exception) { emptyList() }

            val markPrice = premium.markPrice.toDoubleOrNull() ?: 0.0
            val oiUsd = oi.openInterest.toDoubleOrNull()?.let { it * markPrice } ?: 0.0

            ExchangeData(
                exchange = "Binance",
                openInterestUsd = oiUsd,
                fundingRate = funding?.fundingRate?.toDoubleOrNull() ?: 0.0,
                markPrice = markPrice,
                longRatio = lsRatio?.longAccount?.toDoubleOrNull() ?: 0.5,
                shortRatio = lsRatio?.shortAccount?.toDoubleOrNull() ?: 0.5,
                recentLiquidations = liquidations.map {
                    LiquidationEvent(
                        exchange = "Binance",
                        price = it.averagePrice.toDoubleOrNull() ?: 0.0,
                        quantity = it.executedQty.toDoubleOrNull() ?: 0.0,
                        side = if (it.side == "BUY") LiqSide.SHORT else LiqSide.LONG,
                        timestamp = it.time,
                        usdValue = (it.averagePrice.toDoubleOrNull() ?: 0.0) *
                                (it.executedQty.toDoubleOrNull() ?: 0.0)
                    )
                }
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Binance fetch failed", e)
            null
        }
    }

    private suspend fun fetchBybitData(baseCoin: String): ExchangeData? {
        return try {
            val symbol = bybitSymbol(baseCoin)
            val tickerResp = bybitApi.getTickers(symbol = symbol)
            val ticker = tickerResp.result?.list?.firstOrNull() ?: return null
            val fundingResp = bybitApi.getFundingHistory(symbol = symbol, limit = 1)
            val funding = fundingResp.result?.list?.firstOrNull()

            ExchangeData(
                exchange = "Bybit",
                openInterestUsd = ticker.openInterestValue.toDoubleOrNull() ?: 0.0,
                fundingRate = funding?.fundingRate?.toDoubleOrNull()
                    ?: ticker.fundingRate.toDoubleOrNull() ?: 0.0,
                markPrice = ticker.lastPrice.toDoubleOrNull() ?: 0.0,
                longRatio = 0.5,
                shortRatio = 0.5,
                recentLiquidations = emptyList() // Bybit doesn't expose public liq
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Bybit fetch failed", e)
            null
        }
    }

    private suspend fun fetchOkxData(baseCoin: String): ExchangeData? {
        return try {
            val instId = okxSymbol(baseCoin)
            val oiResp = okxApi.getOpenInterest(instId = instId)
            val oi = oiResp.data?.firstOrNull()
            val fundResp = okxApi.getFundingRate(instId = instId)
            val fund = fundResp.data?.firstOrNull()
            val markResp = okxApi.getMarkPrice(instId = instId)
            val mark = markResp.data?.firstOrNull()

            val liqs = try {
                val liqResp = okxApi.getLiquidationOrders(instId = instId)
                liqResp.data?.flatMap { wrapper ->
                    wrapper.details?.map { d ->
                        LiquidationEvent(
                            exchange = "OKX",
                            price = d.px.toDoubleOrNull() ?: 0.0,
                            quantity = d.sz.toDoubleOrNull() ?: 0.0,
                            side = if (d.side == "buy") LiqSide.SHORT else LiqSide.LONG,
                            timestamp = d.ts.toLongOrNull() ?: 0L,
                            usdValue = (d.px.toDoubleOrNull() ?: 0.0) * (d.sz.toDoubleOrNull() ?: 0.0)
                        )
                    } ?: emptyList()
                } ?: emptyList()
            } catch (_: Exception) { emptyList() }

            val markPrice = mark?.markPx?.toDoubleOrNull() ?: 0.0
            val oiVal = oi?.oi?.toDoubleOrNull()?.let { it * markPrice } ?: 0.0

            ExchangeData(
                exchange = "OKX",
                openInterestUsd = oiVal,
                fundingRate = fund?.fundingRate?.toDoubleOrNull() ?: 0.0,
                markPrice = markPrice,
                longRatio = 0.5,
                shortRatio = 0.5,
                recentLiquidations = liqs
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "OKX fetch failed", e)
            null
        }
    }

    private suspend fun fetchBitgetData(baseCoin: String): ExchangeData? {
        return try {
            val symbol = bitgetSymbol(baseCoin)
            val oiResp = bitgetApi.getOpenInterest(symbol = symbol)
            val fundResp = bitgetApi.getFundingRate(symbol = symbol)
            val fund = fundResp.data?.firstOrNull()

            val tickers = bitgetApi.getTickers()
            val ticker = tickers.data?.find { it.symbol.contains(baseCoin, ignoreCase = true) }
            val markPrice = ticker?.lastPr?.toDoubleOrNull() ?: 0.0

            ExchangeData(
                exchange = "Bitget",
                openInterestUsd = oiResp.data?.amount?.toDoubleOrNull()?.let { it * markPrice } ?: 0.0,
                fundingRate = fund?.fundingRate?.toDoubleOrNull() ?: 0.0,
                markPrice = markPrice,
                longRatio = 0.5,
                shortRatio = 0.5,
                recentLiquidations = emptyList()
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Bitget fetch failed", e)
            null
        }
    }

    private suspend fun fetchGateioData(baseCoin: String): ExchangeData? {
        return try {
            val contract = gateioSymbol(baseCoin)
            val contractInfo = gateioApi.getContract(contract)
            val liqs = try {
                gateioApi.getLiquidationOrders(contract = contract, limit = 100).map {
                    LiquidationEvent(
                        exchange = "Gate.io",
                        price = it.fill_price.toDoubleOrNull() ?: 0.0,
                        quantity = kotlin.math.abs(it.size.toDouble()),
                        side = if (it.size > 0) LiqSide.LONG else LiqSide.SHORT,
                        timestamp = it.time * 1000L,
                        usdValue = (it.fill_price.toDoubleOrNull() ?: 0.0) * kotlin.math.abs(it.size.toDouble())
                    )
                }
            } catch (_: Exception) { emptyList() }

            ExchangeData(
                exchange = "Gate.io",
                openInterestUsd = contractInfo.open_interest.toDoubleOrNull() ?: 0.0,
                fundingRate = contractInfo.funding_rate.toDoubleOrNull() ?: 0.0,
                markPrice = contractInfo.mark_price.toDoubleOrNull() ?: 0.0,
                longRatio = 0.5,
                shortRatio = 0.5,
                recentLiquidations = liqs
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Gate.io fetch failed", e)
            null
        }
    }

    // ─── Heatmap Bucket Builder ──────────────────────────────────────

    private fun buildHeatmapBuckets(
        liquidations: List<LiquidationEvent>,
        markPrice: Double,
        baseCoin: String
    ): List<HeatmapBucket> {
        if (markPrice <= 0 || liquidations.isEmpty()) {
            return generateEstimatedBuckets(markPrice, baseCoin)
        }

        // Create price buckets around current price (±10% range)
        val range = markPrice * 0.10
        val bucketCount = 40
        val bucketSize = (range * 2) / bucketCount
        val minPrice = markPrice - range
        val buckets = mutableListOf<HeatmapBucket>()

        for (i in 0 until bucketCount) {
            val low = minPrice + (i * bucketSize)
            val high = low + bucketSize
            val mid = (low + high) / 2

            val inBucket = liquidations.filter { it.price in low..high }
            val longLiqs = inBucket.filter { it.side == LiqSide.LONG }.sumOf { it.usdValue }
            val shortLiqs = inBucket.filter { it.side == LiqSide.SHORT }.sumOf { it.usdValue }

            buckets.add(
                HeatmapBucket(
                    priceLevel = mid,
                    priceLow = low,
                    priceHigh = high,
                    longLiquidationUsd = longLiqs,
                    shortLiquidationUsd = shortLiqs,
                    totalLiquidationUsd = longLiqs + shortLiqs,
                    eventCount = inBucket.size
                )
            )
        }

        // If real data is sparse, supplement with estimates
        if (buckets.all { it.totalLiquidationUsd == 0.0 }) {
            return generateEstimatedBuckets(markPrice, baseCoin)
        }

        return buckets
    }

    /**
     * Generate estimated liquidation zones when real data is sparse.
     * Based on common leverage levels (2x, 3x, 5x, 10x, 25x, 50x, 100x).
     */
    private fun generateEstimatedBuckets(markPrice: Double, baseCoin: String): List<HeatmapBucket> {
        if (markPrice <= 0) return emptyList()

        val leverages = listOf(2.0, 3.0, 5.0, 10.0, 25.0, 50.0, 100.0)
        val buckets = mutableListOf<HeatmapBucket>()

        // Estimated volume multiplier based on coin
        val volumeMultiplier = when (baseCoin.uppercase()) {
            "BTC" -> 50_000_000.0
            "ETH" -> 25_000_000.0
            "SOL" -> 10_000_000.0
            "BNB" -> 8_000_000.0
            "XRP" -> 5_000_000.0
            else -> 3_000_000.0
        }

        val range = markPrice * 0.10
        val bucketCount = 40
        val bucketSize = (range * 2) / bucketCount
        val minPrice = markPrice - range

        for (i in 0 until bucketCount) {
            val low = minPrice + (i * bucketSize)
            val high = low + bucketSize
            val mid = (low + high) / 2

            // Calculate intensity based on proximity to leverage liquidation levels
            var longIntensity = 0.0
            var shortIntensity = 0.0

            for (lev in leverages) {
                val longLiqPrice = markPrice * (1 - 1.0 / lev)
                val shortLiqPrice = markPrice * (1 + 1.0 / lev)
                val weight = volumeMultiplier / lev // Higher leverage = less volume but more events

                if (longLiqPrice in low..high) {
                    longIntensity += weight
                }
                if (shortLiqPrice in low..high) {
                    shortIntensity += weight
                }

                // Add bell-curve spillover around liquidation zones
                val longDist = kotlin.math.abs(mid - longLiqPrice) / (bucketSize * 3)
                val shortDist = kotlin.math.abs(mid - shortLiqPrice) / (bucketSize * 3)
                if (longDist < 1.0) longIntensity += weight * (1 - longDist) * 0.3
                if (shortDist < 1.0) shortIntensity += weight * (1 - shortDist) * 0.3
            }

            buckets.add(
                HeatmapBucket(
                    priceLevel = mid,
                    priceLow = low,
                    priceHigh = high,
                    longLiquidationUsd = longIntensity,
                    shortLiquidationUsd = shortIntensity,
                    totalLiquidationUsd = longIntensity + shortIntensity,
                    eventCount = 0,
                    isEstimated = true
                )
            )
        }

        return buckets
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Domain Models
// ═══════════════════════════════════════════════════════════════════════

enum class LiqSide { LONG, SHORT }

data class LiquidationEvent(
    val exchange: String,
    val price: Double,
    val quantity: Double,
    val side: LiqSide,
    val timestamp: Long,
    val usdValue: Double
)

data class ExchangeData(
    val exchange: String,
    val openInterestUsd: Double = 0.0,
    val fundingRate: Double = 0.0,
    val markPrice: Double = 0.0,
    val longRatio: Double = 0.5,
    val shortRatio: Double = 0.5,
    val recentLiquidations: List<LiquidationEvent> = emptyList()
)

data class HeatmapBucket(
    val priceLevel: Double,
    val priceLow: Double,
    val priceHigh: Double,
    val longLiquidationUsd: Double = 0.0,
    val shortLiquidationUsd: Double = 0.0,
    val totalLiquidationUsd: Double = 0.0,
    val eventCount: Int = 0,
    val isEstimated: Boolean = false
)

data class AggregatedLiquidationData(
    val baseCoin: String = "BTC",
    val markPrice: Double = 0.0,
    val totalOpenInterestUsd: Double = 0.0,
    val aggregatedFundingRate: Double = 0.0,
    val longRatio: Double = 0.5,
    val shortRatio: Double = 0.5,
    val exchangeBreakdowns: List<ExchangeData> = emptyList(),
    val recentLiquidations: List<LiquidationEvent> = emptyList(),
    val heatmapBuckets: List<HeatmapBucket> = emptyList(),
    val lastUpdated: Long = 0
)

data class OIHistoryPoint(
    val timestamp: Long,
    val openInterest: Double,
    val openInterestValue: Double
)

data class LongShortPoint(
    val timestamp: Long,
    val longRatio: Double,
    val shortRatio: Double,
    val ratio: Double
)
