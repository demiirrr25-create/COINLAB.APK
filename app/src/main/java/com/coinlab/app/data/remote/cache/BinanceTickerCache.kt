package com.coinlab.app.data.remote.cache

import com.coinlab.app.data.remote.DynamicCoinRegistry
import com.coinlab.app.data.remote.api.BinanceApi
import com.coinlab.app.data.remote.api.BinanceTicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized cache for Binance 24hr ticker data.
 * Prevents redundant API calls: all ViewModels share the same cached data.
 *
 * - Fetches up to 1000 supported symbols in batches of 200 (parallel)
 * - 15-second TTL — balance between freshness and API efficiency
 * - Mutex prevents duplicate network calls when multiple coroutines request simultaneously
 * - Stale-while-revalidate: returns stale cache immediately, refreshes in background
 */
@Singleton
class BinanceTickerCache @Inject constructor(
    private val binanceApi: BinanceApi,
    private val coinRegistry: DynamicCoinRegistry
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
        private const val BATCH_SIZE = 200 // Max symbols per Binance API call (URL length safe)
    }

    /**
     * Build symbols JSON for a batch of symbols.
     * Format: ["BTCUSDT","ETHUSDT",...]
     */
    private fun buildSymbolsJson(symbols: List<String>): String {
        return symbols.joinToString(",", prefix = "[", postfix = "]") { "\"$it\"" }
    }

    /**
     * Get all supported tickers (cached, up to ~1000 items).
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
     * Actually fetch from Binance API in batches of 200. Mutex-protected.
     * Uses parallel batch requests for speed: 5×200 symbols ≈ 1 second.
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
                val allSymbols = coinRegistry.getAllBinanceSymbols().toList()
                if (allSymbols.isEmpty()) {
                    return@withLock cachedTickers.ifEmpty { emptyList() }
                }

                // Split into batches and fetch in parallel
                val batches = allSymbols.chunked(BATCH_SIZE)
                val allTickers = mutableListOf<BinanceTicker>()

                coroutineScope {
                    val deferredResults = batches.map { batch ->
                        async(Dispatchers.IO) {
                            try {
                                val symbolsJson = buildSymbolsJson(batch)
                                binanceApi.getTickersBySymbols(symbolsJson)
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                emptyList()
                            }
                        }
                    }
                    for (result in deferredResults.awaitAll()) {
                        allTickers.addAll(result)
                    }
                }

                cachedTickers = allTickers
                cachedTickerMap = allTickers.associateBy { it.symbol ?: "" }
                lastFetchTime = System.currentTimeMillis()
                allTickers
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Return stale cache on error
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
