package com.coinlab.app.data.remote.firebase

import android.util.Log
import com.coinlab.app.data.remote.api.BinanceFuturesApi
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v12.2 — CoinGlass-Grade Liquidation Repository
 *
 * Hybrid architecture:
 *   - Firebase Cloud Functions compute heatmap data (backend)
 *   - Firebase RTDB provides real-time data stream to mobile
 *   - Binance REST API for kline chart data (direct)
 *   - Firebase Auth for user preferences
 *
 * RTDB paths:
 *   /heatmap/{symbol}/{timeframe}  — pre-computed heatmap data
 *   /users/{uid}/heatmapSettings   — user preferences
 */
@Singleton
class LiquidationRepository @Inject constructor(
    private val binanceFutures: BinanceFuturesApi
) {
    companion object {
        private const val TAG = "LiquidationRepo"
    }

    private val database: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance("https://com-coinlab-app-default-rtdb.europe-west1.firebasedatabase.app")
    }
    private val functions: FirebaseFunctions by lazy {
        FirebaseFunctions.getInstance("europe-west1")
    }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    // ─── Kline Data (Direct Binance REST — low latency for chart) ────

    suspend fun getKlineData(
        baseCoin: String,
        interval: String = "1h",
        limit: Int = 500
    ): List<CandleData> {
        return try {
            val raw = binanceFutures.getKlines(
                symbol = "${baseCoin}USDT",
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

    // ─── Firebase RTDB Real-Time Heatmap Stream ──────────────────────

    fun observeHeatmapData(
        symbol: String,
        timeframe: String
    ): Flow<AggregatedLiquidationData> = callbackFlow {
        val ref = database.getReference("heatmap/${symbol.uppercase()}/${timeframe.uppercase()}")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val data = parseHeatmapSnapshot(snapshot, symbol)
                    trySend(data)
                } catch (e: Exception) {
                    Log.w(TAG, "Parse RTDB error", e)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "RTDB cancelled: ${error.message}")
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // ─── On-Demand Heatmap (Cloud Function Call) ─────────────────────

    suspend fun getHeatmapOnDemand(
        symbol: String,
        timeframe: String
    ): AggregatedLiquidationData {
        return try {
            // First try RTDB cache
            val cached = tryGetCachedHeatmap(symbol, timeframe)
            if (cached != null && System.currentTimeMillis() - cached.lastUpdated < 30_000) {
                return cached
            }

            // Call Cloud Function for fresh data
            val result = functions
                .getHttpsCallable("onDemandHeatmap")
                .call(mapOf("symbol" to symbol.uppercase(), "timeframe" to timeframe.uppercase()))
                .await()

            @Suppress("UNCHECKED_CAST")
            val map = result.getData() as? Map<String, Any?> ?: return cached ?: AggregatedLiquidationData(baseCoin = symbol)

            parseHeatmapMap(map, symbol)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "On-demand heatmap failed: ${e.message}", e)

            // Fallback: try RTDB cache regardless of age
            tryGetCachedHeatmap(symbol, timeframe) ?: AggregatedLiquidationData(baseCoin = symbol)
        }
    }

    private suspend fun tryGetCachedHeatmap(symbol: String, timeframe: String): AggregatedLiquidationData? {
        return try {
            val snap = database.getReference("heatmap/${symbol.uppercase()}/${timeframe.uppercase()}")
                .get().await()
            if (snap.exists()) parseHeatmapSnapshot(snap, symbol) else null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    // ─── User Preferences (Firebase RTDB) ────────────────────────────

    suspend fun saveUserPreferences(timeframe: String, threshold: Float, coin: String) {
        val uid = auth.currentUser?.uid ?: return
        try {
            database.getReference("users/$uid/heatmapSettings")
                .setValue(mapOf(
                    "timeframe" to timeframe,
                    "threshold" to threshold.toDouble(),
                    "lastCoin" to coin
                )).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Save preferences failed", e)
        }
    }

    suspend fun loadUserPreferences(): HeatmapPreferences {
        val uid = auth.currentUser?.uid ?: return HeatmapPreferences()
        return try {
            val snap = database.getReference("users/$uid/heatmapSettings").get().await()
            if (!snap.exists()) return HeatmapPreferences()
            HeatmapPreferences(
                timeframe = snap.child("timeframe").getValue(String::class.java) ?: "24H",
                threshold = (snap.child("threshold").getValue(Double::class.java) ?: 0.5).toFloat(),
                lastCoin = snap.child("lastCoin").getValue(String::class.java) ?: "BTC"
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            HeatmapPreferences()
        }
    }

    // ─── OI History & Long/Short (Direct Binance REST) ───────────────

    suspend fun getOpenInterestHistory(
        baseCoin: String, period: String = "5m", limit: Int = 30
    ): List<OIHistoryPoint> {
        return try {
            binanceFutures.getOpenInterestHistory(
                symbol = "${baseCoin}USDT", period = period, limit = limit
            ).map {
                OIHistoryPoint(
                    it.timestamp,
                    it.sumOpenInterest.toDoubleOrNull() ?: 0.0,
                    it.sumOpenInterestValue.toDoubleOrNull() ?: 0.0
                )
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
                symbol = "${baseCoin}USDT", period = period, limit = limit
            ).map {
                LongShortPoint(
                    it.timestamp,
                    it.longAccount.toDoubleOrNull() ?: 0.5,
                    it.shortAccount.toDoubleOrNull() ?: 0.5,
                    it.longShortRatio.toDoubleOrNull() ?: 1.0
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    // ─── RTDB Data Parsers ───────────────────────────────────────────

    private fun parseHeatmapSnapshot(snapshot: DataSnapshot, baseCoin: String): AggregatedLiquidationData {
        val markPrice = snapshot.child("markPrice").getValue(Double::class.java) ?: 0.0
        val totalOI = snapshot.child("totalOI").getValue(Double::class.java) ?: 0.0
        val fundingRate = snapshot.child("fundingRate").getValue(Double::class.java) ?: 0.0
        val longRatio = snapshot.child("longRatio").getValue(Double::class.java) ?: 0.5
        val shortRatio = snapshot.child("shortRatio").getValue(Double::class.java) ?: 0.5
        val lastUpdated = snapshot.child("lastUpdated").getValue(Long::class.java) ?: 0L

        val exchanges = snapshot.child("exchanges").children.mapNotNull { exSnap ->
            ExchangeData(
                exchange = exSnap.child("name").getValue(String::class.java) ?: return@mapNotNull null,
                openInterestUsd = exSnap.child("oi").getValue(Double::class.java) ?: 0.0,
                fundingRate = exSnap.child("funding").getValue(Double::class.java) ?: 0.0,
                markPrice = exSnap.child("markPrice").getValue(Double::class.java) ?: 0.0
            )
        }

        val buckets = snapshot.child("buckets").children.mapNotNull { bSnap ->
            HeatmapBucket(
                priceLevel = bSnap.child("priceLevel").getValue(Double::class.java) ?: return@mapNotNull null,
                priceLow = bSnap.child("priceLow").getValue(Double::class.java) ?: 0.0,
                priceHigh = bSnap.child("priceHigh").getValue(Double::class.java) ?: 0.0,
                longLiquidationUsd = bSnap.child("longUsd").getValue(Double::class.java) ?: 0.0,
                shortLiquidationUsd = bSnap.child("shortUsd").getValue(Double::class.java) ?: 0.0,
                totalLiquidationUsd = bSnap.child("totalUsd").getValue(Double::class.java) ?: 0.0,
                eventCount = bSnap.child("events").getValue(Int::class.java) ?: 0
            )
        }

        val liquidations = snapshot.child("recentLiquidations").children.mapNotNull { lSnap ->
            LiquidationEvent(
                exchange = lSnap.child("exchange").getValue(String::class.java) ?: return@mapNotNull null,
                price = lSnap.child("price").getValue(Double::class.java) ?: 0.0,
                quantity = lSnap.child("qty").getValue(Double::class.java) ?: 0.0,
                side = if (lSnap.child("side").getValue(String::class.java) == "LONG") LiqSide.LONG else LiqSide.SHORT,
                timestamp = lSnap.child("time").getValue(Long::class.java) ?: 0L,
                usdValue = lSnap.child("usd").getValue(Double::class.java) ?: 0.0
            )
        }

        return AggregatedLiquidationData(
            baseCoin = baseCoin,
            markPrice = markPrice,
            totalOpenInterestUsd = totalOI,
            aggregatedFundingRate = fundingRate,
            longRatio = longRatio,
            shortRatio = shortRatio,
            exchangeBreakdowns = exchanges,
            recentLiquidations = liquidations,
            heatmapBuckets = buckets,
            lastUpdated = lastUpdated
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseHeatmapMap(map: Map<String, Any?>, baseCoin: String): AggregatedLiquidationData {
        val markPrice = (map["markPrice"] as? Number)?.toDouble() ?: 0.0
        val totalOI = (map["totalOI"] as? Number)?.toDouble() ?: 0.0
        val fundingRate = (map["fundingRate"] as? Number)?.toDouble() ?: 0.0
        val longRatio = (map["longRatio"] as? Number)?.toDouble() ?: 0.5
        val shortRatio = (map["shortRatio"] as? Number)?.toDouble() ?: 0.5
        val lastUpdated = (map["lastUpdated"] as? Number)?.toLong() ?: 0L

        val exchanges = (map["exchanges"] as? List<Map<String, Any?>>)?.mapNotNull { ex ->
            ExchangeData(
                exchange = ex["name"] as? String ?: return@mapNotNull null,
                openInterestUsd = (ex["oi"] as? Number)?.toDouble() ?: 0.0,
                fundingRate = (ex["funding"] as? Number)?.toDouble() ?: 0.0,
                markPrice = (ex["markPrice"] as? Number)?.toDouble() ?: 0.0
            )
        } ?: emptyList()

        val buckets = (map["buckets"] as? List<Map<String, Any?>>)?.mapNotNull { b ->
            HeatmapBucket(
                priceLevel = (b["priceLevel"] as? Number)?.toDouble() ?: return@mapNotNull null,
                priceLow = (b["priceLow"] as? Number)?.toDouble() ?: 0.0,
                priceHigh = (b["priceHigh"] as? Number)?.toDouble() ?: 0.0,
                longLiquidationUsd = (b["longUsd"] as? Number)?.toDouble() ?: 0.0,
                shortLiquidationUsd = (b["shortUsd"] as? Number)?.toDouble() ?: 0.0,
                totalLiquidationUsd = (b["totalUsd"] as? Number)?.toDouble() ?: 0.0,
                eventCount = (b["events"] as? Number)?.toInt() ?: 0
            )
        } ?: emptyList()

        val liquidations = (map["recentLiquidations"] as? List<Map<String, Any?>>)?.mapNotNull { l ->
            LiquidationEvent(
                exchange = l["exchange"] as? String ?: return@mapNotNull null,
                price = (l["price"] as? Number)?.toDouble() ?: 0.0,
                quantity = (l["qty"] as? Number)?.toDouble() ?: 0.0,
                side = if (l["side"] == "LONG") LiqSide.LONG else LiqSide.SHORT,
                timestamp = (l["time"] as? Number)?.toLong() ?: 0L,
                usdValue = (l["usd"] as? Number)?.toDouble() ?: 0.0
            )
        } ?: emptyList()

        return AggregatedLiquidationData(
            baseCoin = baseCoin,
            markPrice = markPrice,
            totalOpenInterestUsd = totalOI,
            aggregatedFundingRate = fundingRate,
            longRatio = longRatio,
            shortRatio = shortRatio,
            exchangeBreakdowns = exchanges,
            recentLiquidations = liquidations,
            heatmapBuckets = buckets,
            lastUpdated = lastUpdated
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Domain Models
// ═══════════════════════════════════════════════════════════════════════

enum class LiqSide { LONG, SHORT }

data class CandleData(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double = 0.0
)

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

data class HeatmapPreferences(
    val timeframe: String = "24H",
    val threshold: Float = 0.5f,
    val lastCoin: String = "BTC"
)
