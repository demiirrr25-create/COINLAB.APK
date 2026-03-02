package com.coinlab.app.data.remote.cache

import com.coinlab.app.data.remote.BinanceCoinMapper
import com.coinlab.app.data.remote.api.BinanceApi
import com.coinlab.app.data.remote.api.BinanceTicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized cache for Binance 24hr ticker data.
 * Prevents redundant API calls: all ViewModels share the same cached data.
 *
 * - Only fetches our ~50 supported symbols (not all 2000+)
 * - 15-second TTL — balance between freshness and API efficiency
 * - Mutex prevents duplicate network calls when multiple coroutines request simultaneously
 * - Stale-while-revalidate: returns stale cache immediately, refreshes in background
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

    @Volatile
    private var isFetching: Boolean = false

    companion object {
        private const val CACHE_TTL_MS = 15_000L // 15 seconds
        private const val STALE_TTL_MS = 60_000L // Return stale up to 60s, refresh in background
    }

    // Pre-built symbols JSON — never changes at runtime, build once
    private val symbolsJson: String by lazy {
        BinanceCoinMapper.getAllBinanceSymbols()
            .joinToString(",", prefix = "[", postfix = "]") { "\"$it\"" }
    }

    /**
     * Get all supported tickers (cached, ~50 items).
     * Thread-safe; only one network call at a time.
     * Uses stale-while-revalidate: returns stale data instantly while refreshing in background.
     */
    suspend fun getTickers(forceRefresh: Boolean = false): List<BinanceTicker> {
        val now = System.currentTimeMillis()
        val age = now - lastFetchTime

        // Fresh cache — return immediately
        if (!forceRefresh && cachedTickers.isNotEmpty() && age < CACHE_TTL_MS) {
            return cachedTickers
        }

        // Stale but usable — return stale data, don't block
        // (fetchFresh will be called by the caller or next request)
        if (!forceRefresh && cachedTickers.isNotEmpty() && age < STALE_TTL_MS) {
            // Trigger background refresh if not already fetching
            if (!isFetching) {
                CoroutineScope(Dispatchers.IO).launch {
                    fetchFresh()
                }
            }
            return cachedTickers
        }

        // No cache or too stale — must fetch synchronously
        return fetchFresh()
    }

    /**
     * Actually fetch from Binance API. Mutex-protected.
     */
    private suspend fun fetchFresh(): List<BinanceTicker> {
        return mutex.withLock {
            // Double-check after acquiring lock
            val nowInner = System.currentTimeMillis()
            if (cachedTickers.isNotEmpty() && (nowInner - lastFetchTime) < CACHE_TTL_MS) {
                return@withLock cachedTickers
            }
            isFetching = true
            try {
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
            } finally {
                isFetching = false
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
