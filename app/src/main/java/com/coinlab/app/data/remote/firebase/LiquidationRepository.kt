package com.coinlab.app.data.remote.firebase

import android.util.Log
import com.coinlab.app.data.remote.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.exp

/**
 * v12.1 — Professional Liquidation Repository
 *
 * Multi-exchange weighted aggregation with Coinglass-grade density model.
 *
 * Weight model:
 *   Binance  35%
 *   Bybit    25%
 *   OKX      20%
 *   Bitget   10%
 *   Gate.io  10%
 *
 * Density formula:
 *   LiqDensity = (OI * LeverageCluster * VolatilityFactor) / PriceDistance
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

        private val EXCHANGE_WEIGHTS = mapOf(
            "Binance" to 0.35,
            "Bybit" to 0.25,
            "OKX" to 0.20,
            "Bitget" to 0.10,
            "Gate.io" to 0.10
        )

        private val LEVERAGE_TIERS = listOf(
            LeverageTier(2.0, 0.05),
            LeverageTier(3.0, 0.08),
            LeverageTier(5.0, 0.15),
            LeverageTier(10.0, 0.25),
            LeverageTier(20.0, 0.20),
            LeverageTier(25.0, 0.10),
            LeverageTier(50.0, 0.10),
            LeverageTier(75.0, 0.04),
            LeverageTier(100.0, 0.02),
            LeverageTier(125.0, 0.01)
        )
    }

    private fun binanceSymbol(base: String) = "${base}USDT"
    private fun bybitSymbol(base: String) = "${base}USDT"
    private fun okxSymbol(base: String) = "${base}-USDT-SWAP"
    private fun bitgetSymbol(base: String) = "${base}USDT"
    private fun gateioSymbol(base: String) = "${base}_USDT"

    // ─── Candlestick Data ───────────────────────────────────────────

    suspend fun getKlineData(
        baseCoin: String,
        interval: String = "1h",
        limit: Int = 500
    ): List<CandleData> {
        return try {
            val raw = binanceFutures.getKlines(
                symbol = binanceSymbol(baseCoin),
                interval = interval,
                limit = limit
            )
            raw.mapNotNull { kline ->
                if (kline.size < 12) return@mapNotNull null
                CandleData(
                    time = ((kline[0] as? Number)?.toLong() ?: 0L) / 1000,
                    open = (kline[1] as? String)?.toDoubleOrNull() ?: 0.0,
                    high = (kline[2] as? String)?.toDoubleOrNull() ?: 0.0,
                    low = (kline[3] as? String)?.toDoubleOrNull() ?: 0.0,
                    close = (kline[4] as? String)?.toDoubleOrNull() ?: 0.0,
                    volume = (kline[5] as? String)?.toDoubleOrNull() ?: 0.0
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Kline fetch failed", e)
            emptyList()
        }
    }

    // ─── Aggregated Liquidation Data ─────────────────────────────────

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

            val totalOI = exchanges.sumOf { it.openInterestUsd }
            val weightedFunding = exchanges.sumOf { ex ->
                val weight = EXCHANGE_WEIGHTS[ex.exchange] ?: 0.1
                ex.fundingRate * weight
            }
            val weightedLong = if (totalOI > 0) {
                exchanges.sumOf { it.longRatio * it.openInterestUsd } / totalOI
            } else 0.5

            val allLiquidations = exchanges.flatMap { it.recentLiquidations }
                .sortedByDescending { it.timestamp }

            val markPrice = exchanges.firstOrNull { it.exchange == "Binance" && it.markPrice > 0 }?.markPrice
                ?: exchanges.firstOrNull { it.markPrice > 0 }?.markPrice ?: 0.0

            val orderbookDepth = try { fetchOrderbookDepth(baseCoin) } catch (_: Exception) { emptyList() }

            val heatmapBuckets = buildProfessionalHeatmap(
                exchangeDataList = exchanges,
                liquidations = allLiquidations,
                markPrice = markPrice,
                baseCoin = baseCoin,
                orderbookDepth = orderbookDepth
            )

            AggregatedLiquidationData(
                baseCoin = baseCoin,
                markPrice = markPrice,
                totalOpenInterestUsd = totalOI,
                aggregatedFundingRate = weightedFunding,
                longRatio = weightedLong,
                shortRatio = 1.0 - weightedLong,
                exchangeBreakdowns = exchanges,
                recentLiquidations = allLiquidations.take(200),
                heatmapBuckets = heatmapBuckets,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }

    private suspend fun fetchOrderbookDepth(baseCoin: String): List<DepthLevel> {
        return try {
            val book = binanceFutures.getOrderbookDepth(symbol = binanceSymbol(baseCoin), limit = 50)
            val result = mutableListOf<DepthLevel>()
            book.bids.forEach { bid ->
                val price = bid.getOrNull(0)?.toDoubleOrNull() ?: return@forEach
                val qty = bid.getOrNull(1)?.toDoubleOrNull() ?: return@forEach
                result.add(DepthLevel(price, qty * price, isBid = true))
            }
            book.asks.forEach { ask ->
                val price = ask.getOrNull(0)?.toDoubleOrNull() ?: return@forEach
                val qty = ask.getOrNull(1)?.toDoubleOrNull() ?: return@forEach
                result.add(DepthLevel(price, qty * price, isBid = false))
            }
            result
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    suspend fun getOpenInterestHistory(
        baseCoin: String, period: String = "5m", limit: Int = 30
    ): List<OIHistoryPoint> {
        return try {
            binanceFutures.getOpenInterestHistory(
                symbol = binanceSymbol(baseCoin), period = period, limit = limit
            ).map {
                OIHistoryPoint(it.timestamp, it.sumOpenInterest.toDoubleOrNull() ?: 0.0,
                    it.sumOpenInterestValue.toDoubleOrNull() ?: 0.0)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    suspend fun getLongShortHistory(
        baseCoin: String, period: String = "5m", limit: Int = 30
    ): List<LongShortPoint> {
        return try {
            binanceFutures.getTopLongShortRatio(
                symbol = binanceSymbol(baseCoin), period = period, limit = limit
            ).map {
                LongShortPoint(it.timestamp, it.longAccount.toDoubleOrNull() ?: 0.5,
                    it.shortAccount.toDoubleOrNull() ?: 0.5, it.longShortRatio.toDoubleOrNull() ?: 1.0)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    // ─── Professional Heatmap Builder ────────────────────────────────

    private fun buildProfessionalHeatmap(
        exchangeDataList: List<ExchangeData>,
        liquidations: List<LiquidationEvent>,
        markPrice: Double,
        baseCoin: String,
        orderbookDepth: List<DepthLevel>
    ): List<HeatmapBucket> {
        if (markPrice <= 0) return emptyList()

        val rangePercent = when (baseCoin.uppercase()) {
            "BTC" -> 0.12; "ETH" -> 0.15; else -> 0.15
        }
        val range = markPrice * rangePercent
        val bucketCount = 80
        val bucketSize = (range * 2) / bucketCount
        val minPrice = markPrice - range

        val totalWeightedOI = exchangeDataList.sumOf { ex ->
            (EXCHANGE_WEIGHTS[ex.exchange] ?: 0.1) * ex.openInterestUsd
        }
        val avgFunding = exchangeDataList.map { it.fundingRate }.average()
        val volatilityFactor = 1.0 + abs(avgFunding) * 100.0

        return (0 until bucketCount).map { i ->
            val low = minPrice + (i * bucketSize)
            val high = low + bucketSize
            val mid = (low + high) / 2
            val priceDistPct = abs(mid - markPrice) / markPrice

            val realLongLiq = liquidations.filter { it.side == LiqSide.LONG && it.price in low..high }.sumOf { it.usdValue }
            val realShortLiq = liquidations.filter { it.side == LiqSide.SHORT && it.price in low..high }.sumOf { it.usdValue }

            var estLong = 0.0; var estShort = 0.0
            for (tier in LEVERAGE_TIERS) {
                val longLiqPrice = markPrice * (1.0 - 1.0 / tier.leverage)
                val shortLiqPrice = markPrice * (1.0 + 1.0 / tier.leverage)
                val sigma = bucketSize * 2.5
                val longW = gaussianDensity(abs(mid - longLiqPrice), sigma)
                val shortW = gaussianDensity(abs(mid - shortLiqPrice), sigma)
                val contrib = totalWeightedOI * tier.weight * volatilityFactor
                estLong += contrib * longW * tier.leverage
                estShort += contrib * shortW * tier.leverage
            }

            val depthBoost = orderbookDepth.filter { it.price in low..high }.sumOf { it.usdValue } * 0.1
            val decay = if (priceDistPct > 0.001) 1.0 / (1.0 + priceDistPct * 5.0) else 1.0

            val totalLong = realLongLiq + estLong * decay + depthBoost * 0.4
            val totalShort = realShortLiq + estShort * decay + depthBoost * 0.6

            HeatmapBucket(
                priceLevel = mid, priceLow = low, priceHigh = high,
                longLiquidationUsd = totalLong, shortLiquidationUsd = totalShort,
                totalLiquidationUsd = (totalLong + totalShort) * decay,
                eventCount = liquidations.count { it.price in low..high },
                isEstimated = realLongLiq + realShortLiq < 1.0
            )
        }
    }

    private fun gaussianDensity(distance: Double, sigma: Double): Double {
        if (sigma <= 0) return 0.0
        return exp(-(distance * distance) / (2.0 * sigma * sigma))
    }

    // ─── Per-Exchange Fetchers ────────────────────────────────────────

    private suspend fun fetchBinanceData(baseCoin: String): ExchangeData? {
        return try {
            val symbol = binanceSymbol(baseCoin)
            val oi = binanceFutures.getOpenInterest(symbol)
            val funding = binanceFutures.getFundingRate(symbol, limit = 1).firstOrNull()
            val premium = binanceFutures.getPremiumIndex(symbol)
            val lsRatio = binanceFutures.getGlobalLongShortRatio(symbol, limit = 1).firstOrNull()
            val liquidations = try { binanceFutures.getForceOrders(symbol, limit = 100) } catch (_: Exception) { emptyList() }

            val markPrice = premium.markPrice.toDoubleOrNull() ?: 0.0
            val oiUsd = oi.openInterest.toDoubleOrNull()?.let { it * markPrice } ?: 0.0

            ExchangeData(
                exchange = "Binance", openInterestUsd = oiUsd,
                fundingRate = funding?.fundingRate?.toDoubleOrNull() ?: 0.0,
                markPrice = markPrice,
                longRatio = lsRatio?.longAccount?.toDoubleOrNull() ?: 0.5,
                shortRatio = lsRatio?.shortAccount?.toDoubleOrNull() ?: 0.5,
                recentLiquidations = liquidations.map {
                    LiquidationEvent("Binance", it.averagePrice.toDoubleOrNull() ?: 0.0,
                        it.executedQty.toDoubleOrNull() ?: 0.0,
                        if (it.side == "BUY") LiqSide.SHORT else LiqSide.LONG, it.time,
                        (it.averagePrice.toDoubleOrNull() ?: 0.0) * (it.executedQty.toDoubleOrNull() ?: 0.0))
                }
            )
        } catch (e: Exception) { if (e is CancellationException) throw e; Log.w(TAG, "Binance failed", e); null }
    }

    private suspend fun fetchBybitData(baseCoin: String): ExchangeData? {
        return try {
            val symbol = bybitSymbol(baseCoin)
            val ticker = bybitApi.getTickers(symbol = symbol).result?.list?.firstOrNull() ?: return null
            val funding = bybitApi.getFundingHistory(symbol = symbol, limit = 1).result?.list?.firstOrNull()
            ExchangeData("Bybit", ticker.openInterestValue.toDoubleOrNull() ?: 0.0,
                funding?.fundingRate?.toDoubleOrNull() ?: ticker.fundingRate.toDoubleOrNull() ?: 0.0,
                ticker.lastPrice.toDoubleOrNull() ?: 0.0, 0.5, 0.5, emptyList())
        } catch (e: Exception) { if (e is CancellationException) throw e; Log.w(TAG, "Bybit failed", e); null }
    }

    private suspend fun fetchOkxData(baseCoin: String): ExchangeData? {
        return try {
            val instId = okxSymbol(baseCoin)
            val oi = okxApi.getOpenInterest(instId = instId).data?.firstOrNull()
            val fund = okxApi.getFundingRate(instId = instId).data?.firstOrNull()
            val mark = okxApi.getMarkPrice(instId = instId).data?.firstOrNull()
            val liqs = try {
                okxApi.getLiquidationOrders(instId = instId).data?.flatMap { w ->
                    w.details?.map { d ->
                        LiquidationEvent("OKX", d.px.toDoubleOrNull() ?: 0.0, d.sz.toDoubleOrNull() ?: 0.0,
                            if (d.side == "buy") LiqSide.SHORT else LiqSide.LONG,
                            d.ts.toLongOrNull() ?: 0L,
                            (d.px.toDoubleOrNull() ?: 0.0) * (d.sz.toDoubleOrNull() ?: 0.0))
                    } ?: emptyList()
                } ?: emptyList()
            } catch (_: Exception) { emptyList() }
            val markPrice = mark?.markPx?.toDoubleOrNull() ?: 0.0
            ExchangeData("OKX", oi?.oi?.toDoubleOrNull()?.let { it * markPrice } ?: 0.0,
                fund?.fundingRate?.toDoubleOrNull() ?: 0.0, markPrice, 0.5, 0.5, liqs)
        } catch (e: Exception) { if (e is CancellationException) throw e; Log.w(TAG, "OKX failed", e); null }
    }

    private suspend fun fetchBitgetData(baseCoin: String): ExchangeData? {
        return try {
            val symbol = bitgetSymbol(baseCoin)
            val oiResp = bitgetApi.getOpenInterest(symbol = symbol)
            val fund = bitgetApi.getFundingRate(symbol = symbol).data?.firstOrNull()
            val ticker = bitgetApi.getTickers().data?.find { it.symbol.contains(baseCoin, ignoreCase = true) }
            val markPrice = ticker?.lastPr?.toDoubleOrNull() ?: 0.0
            ExchangeData("Bitget", oiResp.data?.amount?.toDoubleOrNull()?.let { it * markPrice } ?: 0.0,
                fund?.fundingRate?.toDoubleOrNull() ?: 0.0, markPrice, 0.5, 0.5, emptyList())
        } catch (e: Exception) { if (e is CancellationException) throw e; Log.w(TAG, "Bitget failed", e); null }
    }

    private suspend fun fetchGateioData(baseCoin: String): ExchangeData? {
        return try {
            val contract = gateioSymbol(baseCoin)
            val info = gateioApi.getContract(contract)
            val liqs = try {
                gateioApi.getLiquidationOrders(contract = contract, limit = 100).map {
                    LiquidationEvent("Gate.io", it.fill_price.toDoubleOrNull() ?: 0.0,
                        abs(it.size.toDouble()), if (it.size > 0) LiqSide.LONG else LiqSide.SHORT,
                        it.time * 1000L, (it.fill_price.toDoubleOrNull() ?: 0.0) * abs(it.size.toDouble()))
                }
            } catch (_: Exception) { emptyList() }
            ExchangeData("Gate.io", info.open_interest.toDoubleOrNull() ?: 0.0,
                info.funding_rate.toDoubleOrNull() ?: 0.0, info.mark_price.toDoubleOrNull() ?: 0.0,
                0.5, 0.5, liqs)
        } catch (e: Exception) { if (e is CancellationException) throw e; Log.w(TAG, "Gate.io failed", e); null }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Domain Models
// ═══════════════════════════════════════════════════════════════════════

enum class LiqSide { LONG, SHORT }
data class LeverageTier(val leverage: Double, val weight: Double)
data class DepthLevel(val price: Double, val usdValue: Double, val isBid: Boolean)
data class CandleData(val time: Long, val open: Double, val high: Double, val low: Double, val close: Double, val volume: Double = 0.0)
data class LiquidationEvent(val exchange: String, val price: Double, val quantity: Double, val side: LiqSide, val timestamp: Long, val usdValue: Double)
data class ExchangeData(val exchange: String, val openInterestUsd: Double = 0.0, val fundingRate: Double = 0.0, val markPrice: Double = 0.0, val longRatio: Double = 0.5, val shortRatio: Double = 0.5, val recentLiquidations: List<LiquidationEvent> = emptyList())
data class HeatmapBucket(val priceLevel: Double, val priceLow: Double, val priceHigh: Double, val longLiquidationUsd: Double = 0.0, val shortLiquidationUsd: Double = 0.0, val totalLiquidationUsd: Double = 0.0, val eventCount: Int = 0, val isEstimated: Boolean = false)
data class AggregatedLiquidationData(val baseCoin: String = "BTC", val markPrice: Double = 0.0, val totalOpenInterestUsd: Double = 0.0, val aggregatedFundingRate: Double = 0.0, val longRatio: Double = 0.5, val shortRatio: Double = 0.5, val exchangeBreakdowns: List<ExchangeData> = emptyList(), val recentLiquidations: List<LiquidationEvent> = emptyList(), val heatmapBuckets: List<HeatmapBucket> = emptyList(), val lastUpdated: Long = 0)
data class OIHistoryPoint(val timestamp: Long, val openInterest: Double, val openInterestValue: Double)
data class LongShortPoint(val timestamp: Long, val longRatio: Double, val shortRatio: Double, val ratio: Double)
