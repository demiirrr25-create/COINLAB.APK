package com.coinlab.app.data.remote.cache

import com.coinlab.app.data.remote.BinanceCoinMapper
import com.coinlab.app.data.remote.api.BinanceApi
import com.coinlab.app.data.remote.api.BinanceTicker
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized cache for Binance 24hr ticker data.
 * Prevents redundant API calls: all ViewModels share the same cached data.
 *
 * - Only fetches our ~50 supported symbols (not all 2000+)
 * - 30-second TTL — fresh enough for non-WebSocket screens
 * - Mutex prevents duplicate network calls when multiple coroutines request simultaneously
 */
@Singleton
class BinanceTickerCache @Inject constructor(
    private val binanceApi: BinanceApi
) {
    private val mutex = Mutex()

    @Volatile
    private var cachedTickers: List<BinanceTicker> = emptyList()

    @Volatile
    private var cachedTickerMap: Map<String, BinanceTicker> = emptyMap()

    @Volatile
    private var lastFetchTime: Long = 0L

    companion object {
        private const val CACHE_TTL_MS = 30_000L // 30 seconds
    }

    /**
     * Get all supported tickers (cached, ~50 items).
     * Thread-safe; only one network call at a time.
     */
    suspend fun getTickers(forceRefresh: Boolean = false): List<BinanceTicker> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedTickers.isNotEmpty() && (now - lastFetchTime) < CACHE_TTL_MS) {
            return cachedTickers
        }
        return mutex.withLock {
            // Double-check after acquiring lock
            val nowInner = System.currentTimeMillis()
            if (!forceRefresh && cachedTickers.isNotEmpty() && (nowInner - lastFetchTime) < CACHE_TTL_MS) {
                return@withLock cachedTickers
            }
            try {
                val symbolsJson = BinanceCoinMapper.getAllBinanceSymbols()
                    .joinToString(",", prefix = "[", postfix = "]") { "\"$it\"" }
                val tickers = binanceApi.getTickersBySymbols(symbolsJson)
                cachedTickers = tickers
                cachedTickerMap = tickers.associateBy { it.symbol ?: "" }
                lastFetchTime = System.currentTimeMillis()
                tickers
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Return stale cache on error; return empty list if no cache (never throw on first load)
                if (cachedTickers.isNotEmpty()) cachedTickers
                else emptyList()
            }
        }
    }

    /**
     * Get ticker map (symbol → BinanceTicker) for O(1) lookups.
     */
    suspend fun getTickerMap(forceRefresh: Boolean = false): Map<String, BinanceTicker> {
        getTickers(forceRefresh)
        return cachedTickerMap
    }

    /**
     * Get a single ticker by Binance symbol (e.g. "BTCUSDT").
     */
    suspend fun getTickerBySymbol(symbol: String, forceRefresh: Boolean = false): BinanceTicker? {
        getTickerMap(forceRefresh)
        return cachedTickerMap[symbol]
    }

    /**
     * Invalidate cache (e.g. on pull-to-refresh).
     */
    fun invalidate() {
        lastFetchTime = 0L
    }
}
