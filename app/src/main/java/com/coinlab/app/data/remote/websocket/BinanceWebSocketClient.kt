package com.coinlab.app.data.remote.websocket

import android.util.Log
import com.coinlab.app.BuildConfig
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

data class TickerUpdate(
    val symbol: String,
    val price: Double,
    val priceChangePercent: Double,
    val volume: Double
)

@Singleton
class BinanceWebSocketClient @Inject constructor(
    @javax.inject.Named("binance_ws") private val okHttpClient: OkHttpClient
) {
    private var webSocket: WebSocket? = null
    private val gson = Gson()
    private val TAG = "BinanceWS"
    private val isConnected = AtomicBoolean(false)

    fun connectToTicker(symbols: List<String>): Flow<TickerUpdate> = callbackFlow {
        // Disconnect existing connection before creating new one
        webSocket?.close(1000, "Reconnecting")
        webSocket = null
        isConnected.set(false)

        val streams = symbols.joinToString("/") { "${it.lowercase()}usdt@miniTicker" }
        val url = "${BuildConfig.BINANCE_WS_URL}stream?streams=$streams"
        val shouldReconnect = AtomicBoolean(true)

        fun createConnection() {
            if (!isActive || !shouldReconnect.get()) return

            val request = Request.Builder().url(url).build()

            webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    isConnected.set(true)
                    Log.d(TAG, "WebSocket connected — raw USDT prices")
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    try {
                        val json = gson.fromJson(text, JsonObject::class.java)
                        val data = json.getAsJsonObject("data")
                        if (data != null) {
                            val closePrice = data.get("c")?.asString?.toDoubleOrNull() ?: 0.0
                            val openPrice = data.get("o")?.asString?.toDoubleOrNull() ?: 0.0
                            val changePercent = if (openPrice > 0) ((closePrice - openPrice) / openPrice) * 100.0 else 0.0
                            val ticker = TickerUpdate(
                                symbol = data.get("s")?.asString ?: "",
                                price = closePrice,
                                priceChangePercent = changePercent,
                                volume = data.get("v")?.asString?.toDoubleOrNull() ?: 0.0
                            )
                            trySend(ticker)
                        }
                    } catch (_: Exception) { }
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    Log.w(TAG, "WebSocket failure: ${t.message}")
                    isConnected.set(false)
                    // Let SharedWebSocketManager handle reconnection
                    channel.close()
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closed: $reason")
                    isConnected.set(false)
                    // Let SharedWebSocketManager handle reconnection
                    channel.close()
                }
            })
        }

        createConnection()

        awaitClose {
            shouldReconnect.set(false)
            webSocket?.close(1000, "User disconnected")
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
