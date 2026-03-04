package com.coinlab.app.data.remote.websocket

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * v12.1 — Binance Futures WebSocket client for real-time liquidation data.
 *
 * Streams:
 *   - forceOrder: Real-time liquidation events
 *   - kline: Real-time candlestick updates
 *   - markPrice: Real-time mark price
 *   - aggTrade: Aggregated trade stream
 */

data class FuturesLiquidation(
    val symbol: String,
    val side: String,       // BUY = short liquidated, SELL = long liquidated
    val price: Double,
    val quantity: Double,
    val tradeTime: Long,
    val usdValue: Double
)

data class FuturesKlineUpdate(
    val symbol: String,
    val time: Long,         // kline open time in seconds
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val isClosed: Boolean
)

data class FuturesMarkPrice(
    val symbol: String,
    val markPrice: Double,
    val fundingRate: Double,
    val nextFundingTime: Long
)

@Singleton
class FuturesWebSocketClient @Inject constructor(
    @Named("binance_ws") private val okHttpClient: OkHttpClient
) {
    private val TAG = "FuturesWS"
    private val gson = Gson()
    private var webSocket: WebSocket? = null
    private val isConnected = AtomicBoolean(false)

    /**
     * Connect to multiple Binance Futures WebSocket streams.
     * Returns a Flow that emits sealed FuturesWsEvent objects.
     */
    fun connectStreams(
        symbol: String,
        interval: String = "1h"
    ): Flow<FuturesWsEvent> = callbackFlow {
        webSocket?.close(1000, "Reconnecting")
        webSocket = null
        isConnected.set(false)

        val sym = symbol.lowercase()
        val streams = listOf(
            "${sym}@forceOrder",
            "${sym}@kline_${interval}",
            "${sym}@markPrice@1s"
        )
        val url = "wss://fstream.binance.com/stream?streams=${streams.joinToString("/")}"
        val shouldReconnect = AtomicBoolean(true)

        val request = Request.Builder().url(url).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                isConnected.set(true)
                Log.d(TAG, "Futures WS connected: $symbol streams=${streams.size}")
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val json = gson.fromJson(text, JsonObject::class.java)
                    val stream = json.get("stream")?.asString ?: return
                    val data = json.getAsJsonObject("data") ?: return

                    when {
                        stream.contains("forceOrder") -> {
                            val order = data.getAsJsonObject("o") ?: return
                            val event = FuturesLiquidation(
                                symbol = order.get("s")?.asString ?: "",
                                side = order.get("S")?.asString ?: "",
                                price = order.get("p")?.asString?.toDoubleOrNull() ?: 0.0,
                                quantity = order.get("q")?.asString?.toDoubleOrNull() ?: 0.0,
                                tradeTime = order.get("T")?.asLong ?: 0L,
                                usdValue = (order.get("p")?.asString?.toDoubleOrNull() ?: 0.0) *
                                        (order.get("q")?.asString?.toDoubleOrNull() ?: 0.0)
                            )
                            trySend(FuturesWsEvent.Liquidation(event))
                        }
                        stream.contains("kline") -> {
                            val k = data.getAsJsonObject("k") ?: return
                            val kline = FuturesKlineUpdate(
                                symbol = k.get("s")?.asString ?: "",
                                time = (k.get("t")?.asLong ?: 0L) / 1000, // ms → seconds
                                open = k.get("o")?.asString?.toDoubleOrNull() ?: 0.0,
                                high = k.get("h")?.asString?.toDoubleOrNull() ?: 0.0,
                                low = k.get("l")?.asString?.toDoubleOrNull() ?: 0.0,
                                close = k.get("c")?.asString?.toDoubleOrNull() ?: 0.0,
                                volume = k.get("v")?.asString?.toDoubleOrNull() ?: 0.0,
                                isClosed = k.get("x")?.asBoolean ?: false
                            )
                            trySend(FuturesWsEvent.Kline(kline))
                        }
                        stream.contains("markPrice") -> {
                            val mp = FuturesMarkPrice(
                                symbol = data.get("s")?.asString ?: "",
                                markPrice = data.get("p")?.asString?.toDoubleOrNull() ?: 0.0,
                                fundingRate = data.get("r")?.asString?.toDoubleOrNull() ?: 0.0,
                                nextFundingTime = data.get("T")?.asLong ?: 0L
                            )
                            trySend(FuturesWsEvent.MarkPriceUpdate(mp))
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Parse error: ${e.message}")
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Futures WS failure: ${t.message}")
                isConnected.set(false)
                channel.close()
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Futures WS closed: $reason")
                isConnected.set(false)
                channel.close()
            }
        })

        awaitClose {
            shouldReconnect.set(false)
            webSocket?.close(1000, "Disconnected")
            webSocket = null
            isConnected.set(false)
        }
    }

    fun disconnect() {
        isConnected.set(false)
        webSocket?.close(1000, "User disconnected")
        webSocket = null
    }

    fun isCurrentlyConnected(): Boolean = isConnected.get()
}

/** Sealed interface for WebSocket events */
sealed class FuturesWsEvent {
    data class Liquidation(val data: FuturesLiquidation) : FuturesWsEvent()
    data class Kline(val data: FuturesKlineUpdate) : FuturesWsEvent()
    data class MarkPriceUpdate(val data: FuturesMarkPrice) : FuturesWsEvent()
}
