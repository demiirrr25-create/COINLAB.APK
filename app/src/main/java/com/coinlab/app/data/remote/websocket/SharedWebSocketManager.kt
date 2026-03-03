package com.coinlab.app.data.remote.websocket

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared WebSocket manager that maintains a single Binance WebSocket connection
 * shared across all subscribers (HomeViewModel, MarketViewModel, etc.).
 *
 * Uses reference counting: connects on first subscriber, disconnects 5s after last unsubscribes.
 * Broadcasts raw USDT prices via SharedFlow.
 */
@Singleton
class SharedWebSocketManager @Inject constructor(
    private val webSocketClient: BinanceWebSocketClient
) {
    private val TAG = "SharedWSManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _tickerFlow = MutableSharedFlow<TickerUpdate>(
        replay = 0,
        extraBufferCapacity = 256 // v7.7: increased buffer for 250+ coins
    )

    private val subscriberCount = AtomicInteger(0)
    private var connectionJob: Job? = null
    private var disconnectJob: Job? = null
    private val activeSymbols = mutableSetOf<String>()
    private val symbolLock = Any()
    private var reconnectAttempt = 0

    /**
     * Subscribe to real-time ticker updates for given symbols.
     * Connection is shared — multiple subscribers share the same WebSocket.
     */
    fun observeTicker(symbols: List<String>): Flow<TickerUpdate> {
        return _tickerFlow.asSharedFlow()
            .onStart {
                val count = subscriberCount.incrementAndGet()
                Log.d(TAG, "Subscriber added (total=$count)")
                // Cancel any pending disconnect
                disconnectJob?.cancel()
                disconnectJob = null
                // Merge symbols and reconnect if needed
                updateSymbols(symbols)
            }
            .onCompletion {
                val count = subscriberCount.decrementAndGet()
                Log.d(TAG, "Subscriber removed (total=$count)")
                if (count <= 0) {
                    subscriberCount.set(0)
                    scheduleDisconnect()
                }
            }
    }

    private fun updateSymbols(newSymbols: List<String>) {
        synchronized(symbolLock) {
            val added = newSymbols.any { it.lowercase() !in activeSymbols }
            activeSymbols.addAll(newSymbols.map { it.lowercase() })
            if (connectionJob == null || connectionJob?.isActive != true || added) {
                reconnect()
            }
        }
    }

    private fun reconnect() {
        connectionJob?.cancel()
        connectionJob = scope.launch {
            val symbols: List<String>
            synchronized(symbolLock) {
                symbols = activeSymbols.toList()
            }
            if (symbols.isEmpty()) return@launch
            Log.d(TAG, "Connecting WebSocket for ${symbols.size} symbols")
            try {
                webSocketClient.connectToTicker(symbols).collect { ticker ->
                    reconnectAttempt = 0 // Reset on successful data
                    _tickerFlow.emit(ticker)
                }
            } catch (e: Exception) {
                Log.w(TAG, "WebSocket collection ended: ${e.message}")
                // Auto-reconnect with exponential backoff if still have subscribers
                if (subscriberCount.get() > 0 && reconnectAttempt < 15) {
                    // Fast first reconnect (1s), then exponential backoff
                    val delayMs = if (reconnectAttempt == 0) 1000L
                                  else minOf(3000L * (1L shl minOf(reconnectAttempt, 5)), 60_000L)
                    reconnectAttempt++
                    Log.d(TAG, "Reconnecting in ${delayMs}ms (attempt=$reconnectAttempt)")
                    delay(delayMs)
                    reconnect()
                }
            }
        }
    }

    private fun scheduleDisconnect() {
        disconnectJob = scope.launch {
            delay(5000) // Wait 5s before actually disconnecting
            if (subscriberCount.get() <= 0) {
                Log.d(TAG, "No subscribers, disconnecting WebSocket")
                connectionJob?.cancel()
                connectionJob = null
                webSocketClient.disconnect()
                synchronized(symbolLock) {
                    activeSymbols.clear()
                }
            }
        }
    }

    fun forceDisconnect() {
        disconnectJob?.cancel()
        connectionJob?.cancel()
        connectionJob = null
        webSocketClient.disconnect()
        subscriberCount.set(0)
        synchronized(symbolLock) {
            activeSymbols.clear()
        }
    }
}
