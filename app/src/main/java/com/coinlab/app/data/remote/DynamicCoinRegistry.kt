package com.coinlab.app.data.remote

import android.util.Log
import com.coinlab.app.data.local.dao.CoinMetadataDao
import com.coinlab.app.data.local.entity.CoinMetadataEntity
import com.coinlab.app.data.remote.api.BinanceApi
import com.coinlab.app.data.remote.api.CoinGeckoApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dynamic Coin Registry — dynamic discovery supplemented by BinanceCoinMapper offline data.
 *
 * v7.9 Initialization flow:
 * 1. Load cached metadata from Room (instant, offline-capable)
 * 2. Fetch Binance exchangeInfo → find all TRADING USDT pairs
 * 3. Fetch CoinGecko /coins/markets (4×250) → get name, image, marketCap, rank, supply
 * 4. Match Binance symbols with CoinGecko data via baseAsset/symbol matching
 * 5. SUPPLEMENT: Fill unmatched coins from BinanceCoinMapper (250+ offline entries)
 * 6. Cache results in Room (12h TTL) + in-memory ConcurrentHashMap
 *
 * Falls back to hardcoded top-250 if both APIs fail on first load.
 * BinanceCoinMapper ensures 250+ coins are ALWAYS available regardless of CoinGecko rate limits.
 */
@Singleton
class DynamicCoinRegistry @Inject constructor(
    private val binanceApi: BinanceApi,
    private val coinGeckoApi: CoinGeckoApi,
    private val coinMetadataDao: CoinMetadataDao
) {
    private val TAG = "DynamicCoinRegistry"
    private val mutex = Mutex()

    // In-memory maps for fast O(1) lookups — use @Volatile for atomic swap
    @Volatile
    private var _binanceSymbolToMeta = ConcurrentHashMap<String, CoinMeta>()     // "BTCUSDT" → CoinMeta
    @Volatile
    private var _coinIdToMeta = ConcurrentHashMap<String, CoinMeta>()            // "bitcoin" → CoinMeta
    @Volatile
    private var _coinIdToBinanceSymbol = ConcurrentHashMap<String, String>()     // "bitcoin" → "BTCUSDT"
    @Volatile
    private var _symbolToBinanceSymbol = ConcurrentHashMap<String, String>()     // "BTC" → "BTCUSDT"
    @Volatile
    private var _coinIdToRank = ConcurrentHashMap<String, Int>()                 // "bitcoin" → 1
    @Volatile
    private var _coinIdToSupply = ConcurrentHashMap<String, Double>()            // "bitcoin" → 19800000.0

    @Volatile
    private var isInitialized = false

    @Volatile
    private var lastInitTime = 0L

    companion object {
        private const val MAX_COINS = 1000
        private const val METADATA_CACHE_TTL_MS = 12 * 60 * 60 * 1000L // 12 hours — v7.7 fresher metadata
        private const val COINGECKO_PAGE_SIZE = 250 // CoinGecko max per_page
    }

    data class CoinMeta(
        val id: String,
        val symbol: String,
        val name: String,
        val image: String
    )

    /**
     * Initialize the registry. Call on app startup.
     * Loads cached data first (instant), then refreshes from network in background.
     */
    suspend fun initialize() {
        mutex.withLock {
            try {
                // Step 1: Load from Room cache (instant, works offline)
                val cached = coinMetadataDao.getAllMetadata()
                if (cached.isNotEmpty()) {
                    populateFromEntities(cached)
                    isInitialized = true
                    Log.d(TAG, "Loaded ${cached.size} coins from cache")

                    // Check if cache is fresh enough
                    val cacheAge = System.currentTimeMillis() - (cached.firstOrNull()?.cachedAt ?: 0L)
                    if (cacheAge < METADATA_CACHE_TTL_MS) {
                        Log.d(TAG, "Cache is fresh (${cacheAge / 1000}s old), skipping network refresh")
                        return
                    }
                }

                // Step 2: Fetch from network
                refreshFromNetwork()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "Initialization failed: ${e.message}")
                // If nothing loaded yet, use hardcoded fallback
                if (!isInitialized || _binanceSymbolToMeta.isEmpty()) {
                    loadHardcodedFallback()
                }
            }
        }
    }

    /**
     * Force refresh metadata from network APIs.
     */
    suspend fun refreshFromNetwork() {
        try {
            // Step 1: Get all USDT trading pairs from Binance
            val exchangeInfo = binanceApi.getExchangeInfo()
            val usdtPairs = exchangeInfo.symbols
                ?.filter { it.status == "TRADING" && it.quoteAsset == "USDT" }
                ?.associate { (it.symbol ?: "") to (it.baseAsset?.uppercase() ?: "") }
                ?: emptyMap()

            if (usdtPairs.isEmpty()) {
                Log.w(TAG, "No USDT pairs from Binance exchangeInfo")
                if (!isInitialized) loadHardcodedFallback()
                return
            }

            Log.d(TAG, "Found ${usdtPairs.size} USDT trading pairs on Binance")

            // Step 2: Fetch CoinGecko markets data (4 pages × 250 = 1000 coins)
            val allGeckoCoins = mutableListOf<GeckoCoinData>()
            val pagesToFetch = (MAX_COINS + COINGECKO_PAGE_SIZE - 1) / COINGECKO_PAGE_SIZE

            for (page in 1..pagesToFetch) {
                try {
                    val coins = coinGeckoApi.getCoins(
                        currency = "usd",
                        orderBy = "market_cap_desc",
                        perPage = COINGECKO_PAGE_SIZE,
                        page = page,
                        sparkline = false
                    )
                    allGeckoCoins.addAll(coins.map { dto ->
                        GeckoCoinData(
                            id = dto.id,
                            symbol = dto.symbol.uppercase(),
                            name = dto.name,
                            image = dto.image,
                            marketCap = dto.marketCap ?: 0L,
                            marketCapRank = dto.marketCapRank ?: (allGeckoCoins.size + 1),
                            circulatingSupply = dto.circulatingSupply ?: 0.0
                        )
                    })
                    Log.d(TAG, "CoinGecko page $page: ${coins.size} coins")
                    // Rate limit mitigation: 800ms delay between pages
                    if (page < pagesToFetch) delay(800L)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.w(TAG, "CoinGecko page $page failed: ${e.message}, continuing with partial data")
                    // Don't break — try remaining pages after a delay
                    delay(1200L)
                }
            }

            if (allGeckoCoins.isEmpty()) {
                Log.w(TAG, "No data from CoinGecko, using Binance+Mapper mode")
                // Use BinanceCoinMapper supplement for metadata instead of bare symbols
                populateBinanceWithMapper(usdtPairs)
                return
            }

            // Step 3: Match Binance symbols with CoinGecko data
            val geckoBySymbol = allGeckoCoins.groupBy { it.symbol }
            val matchedCoins = mutableListOf<MatchedCoin>()
            val matchedBinanceSymbols = mutableSetOf<String>()

            for ((binanceSymbol, baseAsset) in usdtPairs) {
                val geckoCandidates = geckoBySymbol[baseAsset]
                if (geckoCandidates != null && geckoCandidates.isNotEmpty()) {
                    // Pick the one with highest market cap (most likely the correct match)
                    val gecko = geckoCandidates.maxByOrNull { it.marketCap }!!
                    matchedCoins.add(
                        MatchedCoin(
                            binanceSymbol = binanceSymbol,
                            coinId = gecko.id,
                            symbol = gecko.symbol,
                            name = gecko.name,
                            image = gecko.image,
                            marketCapRank = gecko.marketCapRank,
                            circulatingSupply = gecko.circulatingSupply
                        )
                    )
                    matchedBinanceSymbols.add(binanceSymbol)
                }
            }

            // Step 3.5: SUPPLEMENT — fill unmatched Binance coins from BinanceCoinMapper
            val mapperEntries = BinanceCoinMapper.getAllEntries()
            var supplementCount = 0
            val maxGeckoRank = (allGeckoCoins.maxOfOrNull { it.marketCapRank } ?: 500)

            for ((binanceSymbol, _) in usdtPairs) {
                if (binanceSymbol in matchedBinanceSymbols) continue
                val mapperMeta = mapperEntries[binanceSymbol] ?: continue
                supplementCount++
                matchedCoins.add(
                    MatchedCoin(
                        binanceSymbol = binanceSymbol,
                        coinId = mapperMeta.id,
                        symbol = mapperMeta.symbol,
                        name = mapperMeta.name,
                        image = mapperMeta.image,
                        marketCapRank = BinanceCoinMapper.getRank(mapperMeta.id) + maxGeckoRank,
                        circulatingSupply = 1_000_000_000.0 // Default estimate
                    )
                )
                matchedBinanceSymbols.add(binanceSymbol)
            }

            // Also add mapper coins that exist on Binance but weren't in our usdtPairs snapshot
            for ((mapperSymbol, mapperMeta) in mapperEntries) {
                if (mapperSymbol in matchedBinanceSymbols) continue
                // Only add if it looks like a valid USDT pair on Binance
                if (!mapperSymbol.endsWith("USDT")) continue
                matchedCoins.add(
                    MatchedCoin(
                        binanceSymbol = mapperSymbol,
                        coinId = mapperMeta.id,
                        symbol = mapperMeta.symbol,
                        name = mapperMeta.name,
                        image = mapperMeta.image,
                        marketCapRank = BinanceCoinMapper.getRank(mapperMeta.id) + maxGeckoRank,
                        circulatingSupply = 1_000_000_000.0
                    )
                )
                matchedBinanceSymbols.add(mapperSymbol)
            }

            // Sort by market cap rank and take top 1000
            val topCoins = matchedCoins
                .distinctBy { it.coinId }
                .sortedBy { it.marketCapRank }
                .take(MAX_COINS)

            Log.d(TAG, "Matched ${topCoins.size} coins (${topCoins.size - supplementCount} CoinGecko + $supplementCount supplemented from mapper)")

            // Step 4: Populate in-memory maps using ATOMIC SWAP to prevent race conditions
            val newBinanceSymbolToMeta = ConcurrentHashMap<String, CoinMeta>()
            val newCoinIdToMeta = ConcurrentHashMap<String, CoinMeta>()
            val newCoinIdToBinanceSymbol = ConcurrentHashMap<String, String>()
            val newSymbolToBinanceSymbol = ConcurrentHashMap<String, String>()
            val newCoinIdToRank = ConcurrentHashMap<String, Int>()
            val newCoinIdToSupply = ConcurrentHashMap<String, Double>()

            var rank = 0
            for (coin in topCoins) {
                rank++
                val meta = CoinMeta(
                    id = coin.coinId,
                    symbol = coin.symbol,
                    name = coin.name,
                    image = coin.image
                )
                newBinanceSymbolToMeta[coin.binanceSymbol] = meta
                newCoinIdToMeta[coin.coinId] = meta
                newCoinIdToBinanceSymbol[coin.coinId] = coin.binanceSymbol
                newSymbolToBinanceSymbol[coin.symbol] = coin.binanceSymbol
                newCoinIdToRank[coin.coinId] = coin.marketCapRank
                newCoinIdToSupply[coin.coinId] = if (coin.circulatingSupply > 0) coin.circulatingSupply else 1_000_000_000.0
            }

            // Atomic swap: readers always see either old complete set or new complete set
            _binanceSymbolToMeta = newBinanceSymbolToMeta
            _coinIdToMeta = newCoinIdToMeta
            _coinIdToBinanceSymbol = newCoinIdToBinanceSymbol
            _symbolToBinanceSymbol = newSymbolToBinanceSymbol
            _coinIdToRank = newCoinIdToRank
            _coinIdToSupply = newCoinIdToSupply

            isInitialized = true
            lastInitTime = System.currentTimeMillis()

            // Step 5: Persist to Room
            val entities = topCoins.map { coin ->
                CoinMetadataEntity(
                    coinId = coin.coinId,
                    symbol = coin.symbol,
                    name = coin.name,
                    image = coin.image,
                    binanceSymbol = coin.binanceSymbol,
                    marketCapRank = coin.marketCapRank,
                    circulatingSupply = coin.circulatingSupply,
                    cachedAt = System.currentTimeMillis()
                )
            }
            try {
                coinMetadataDao.replaceAll(entities)
                Log.d(TAG, "Cached ${entities.size} coin metadata to Room")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to cache metadata: ${e.message}")
            }

        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "Network refresh failed: ${e.message}")
            if (!isInitialized || _binanceSymbolToMeta.isEmpty()) {
                loadHardcodedFallback()
            }
        }
    }

    // Populate maps from Room entities — atomic swap
    private fun populateFromEntities(entities: List<CoinMetadataEntity>) {
        val newBSM = ConcurrentHashMap<String, CoinMeta>()
        val newCIM = ConcurrentHashMap<String, CoinMeta>()
        val newCIBS = ConcurrentHashMap<String, String>()
        val newSTBS = ConcurrentHashMap<String, String>()
        val newCIR = ConcurrentHashMap<String, Int>()
        val newCIS = ConcurrentHashMap<String, Double>()

        for (entity in entities) {
            val meta = CoinMeta(
                id = entity.coinId,
                symbol = entity.symbol,
                name = entity.name,
                image = entity.image
            )
            newBSM[entity.binanceSymbol] = meta
            newCIM[entity.coinId] = meta
            newCIBS[entity.coinId] = entity.binanceSymbol
            newSTBS[entity.symbol] = entity.binanceSymbol
            newCIR[entity.coinId] = entity.marketCapRank
            newCIS[entity.coinId] = if (entity.circulatingSupply > 0) entity.circulatingSupply else 1_000_000_000.0
        }

        _binanceSymbolToMeta = newBSM
        _coinIdToMeta = newCIM
        _coinIdToBinanceSymbol = newCIBS
        _symbolToBinanceSymbol = newSTBS
        _coinIdToRank = newCIR
        _coinIdToSupply = newCIS
    }

    /**
     * Binance + BinanceCoinMapper mode: CoinGecko completely failed.
     * Use BinanceCoinMapper for rich metadata (name, image, rank) whenever available,
     * fall back to bare symbol names for truly unknown coins.
     */
    private fun populateBinanceWithMapper(usdtPairs: Map<String, String>) {
        val newBSM = ConcurrentHashMap<String, CoinMeta>()
        val newCIM = ConcurrentHashMap<String, CoinMeta>()
        val newCIBS = ConcurrentHashMap<String, String>()
        val newSTBS = ConcurrentHashMap<String, String>()
        val newCIR = ConcurrentHashMap<String, Int>()
        val newCIS = ConcurrentHashMap<String, Double>()

        val mapperEntries = BinanceCoinMapper.getAllEntries()
        var rank = 0

        // First pass: add all Binance USDT pairs with mapper metadata
        for ((binanceSymbol, baseAsset) in usdtPairs.entries.take(MAX_COINS)) {
            rank++
            val mapperMeta = mapperEntries[binanceSymbol]
            val coinId = mapperMeta?.id ?: baseAsset.lowercase()
            val meta = CoinMeta(
                id = coinId,
                symbol = mapperMeta?.symbol ?: baseAsset,
                name = mapperMeta?.name ?: baseAsset,
                image = mapperMeta?.image ?: ""
            )
            newBSM[binanceSymbol] = meta
            newCIM[coinId] = meta
            newCIBS[coinId] = binanceSymbol
            newSTBS[meta.symbol] = binanceSymbol
            newCIR[coinId] = if (mapperMeta != null) BinanceCoinMapper.getRank(coinId) else rank
            newCIS[coinId] = 1_000_000_000.0
        }

        // Second pass: add any mapper coins not already from exchangeInfo
        for ((mapperSymbol, mapperMeta) in mapperEntries) {
            if (newBSM.containsKey(mapperSymbol)) continue
            rank++
            val meta = CoinMeta(
                id = mapperMeta.id,
                symbol = mapperMeta.symbol,
                name = mapperMeta.name,
                image = mapperMeta.image
            )
            newBSM[mapperSymbol] = meta
            newCIM[mapperMeta.id] = meta
            newCIBS[mapperMeta.id] = mapperSymbol
            newSTBS[mapperMeta.symbol] = mapperSymbol
            newCIR[mapperMeta.id] = BinanceCoinMapper.getRank(mapperMeta.id)
            newCIS[mapperMeta.id] = 1_000_000_000.0
        }

        // Atomic swap
        _binanceSymbolToMeta = newBSM
        _coinIdToMeta = newCIM
        _coinIdToBinanceSymbol = newCIBS
        _symbolToBinanceSymbol = newSTBS
        _coinIdToRank = newCIR
        _coinIdToSupply = newCIS

        isInitialized = true
        Log.d(TAG, "Loaded ${newBSM.size} coins from Binance+Mapper (no CoinGecko)")
    }

    /**
     * Hardcoded top-200 fallback for offline first launch.
     * Includes the most popular coins with CoinGecko CDN image URLs.
     */
    private fun loadHardcodedFallback() {
        val fallback = HardcodedCoinFallback.getTop200()
        val newBSM = ConcurrentHashMap<String, CoinMeta>()
        val newCIM = ConcurrentHashMap<String, CoinMeta>()
        val newCIBS = ConcurrentHashMap<String, String>()
        val newSTBS = ConcurrentHashMap<String, String>()
        val newCIR = ConcurrentHashMap<String, Int>()
        val newCIS = ConcurrentHashMap<String, Double>()

        for (coin in fallback) {
            val meta = CoinMeta(
                id = coin.coinId,
                symbol = coin.symbol,
                name = coin.name,
                image = coin.image
            )
            newBSM[coin.binanceSymbol] = meta
            newCIM[coin.coinId] = meta
            newCIBS[coin.coinId] = coin.binanceSymbol
            newSTBS[coin.symbol] = coin.binanceSymbol
            newCIR[coin.coinId] = coin.rank
            newCIS[coin.coinId] = coin.circulatingSupply
        }

        // Atomic swap
        _binanceSymbolToMeta = newBSM
        _coinIdToMeta = newCIM
        _coinIdToBinanceSymbol = newCIBS
        _symbolToBinanceSymbol = newSTBS
        _coinIdToRank = newCIR
        _coinIdToSupply = newCIS

        isInitialized = true
        Log.d(TAG, "Loaded ${fallback.size} hardcoded fallback coins")
    }

    // ===== Public API (replaces BinanceCoinMapper) =====

    fun getMetaByBinanceSymbol(binanceSymbol: String): CoinMeta? = _binanceSymbolToMeta[binanceSymbol]

    fun getBinanceSymbolByCoinId(coinId: String): String? = _coinIdToBinanceSymbol[coinId]

    fun getBinanceSymbolBySymbol(symbol: String): String? = _symbolToBinanceSymbol[symbol.uppercase()]

    fun getMarketCapRank(coinId: String): Int = _coinIdToRank[coinId] ?: when(coinId) {
        "bitcoin" -> 1
        "ethereum" -> 2
        "binancecoin" -> 3
        "solana" -> 4
        "ripple" -> 5
        "cardano" -> 6
        "dogecoin" -> 7
        "tron" -> 8
        "polkadot" -> 9
        "avalanche-2" -> 10
        else -> 9999
    }

    fun getCirculatingSupply(coinId: String): Double = _coinIdToSupply[coinId] ?: 1_000_000_000.0

    fun getAllBinanceSymbols(): Set<String> = _binanceSymbolToMeta.keys.toSet()

    fun getAllCoinIds(): Set<String> = _coinIdToMeta.keys.toSet()

    fun getMetaByCoinId(coinId: String): CoinMeta? = _coinIdToMeta[coinId]

    fun getCoinCount(): Int = _binanceSymbolToMeta.size

    fun isReady(): Boolean = isInitialized && _binanceSymbolToMeta.isNotEmpty()

    /**
     * Get all Binance symbols chunked into batches for batch API calls.
     * Each batch contains max [batchSize] symbols to stay under URL length limits.
     */
    fun getSymbolBatches(batchSize: Int = 200): List<List<String>> {
        return _binanceSymbolToMeta.keys.toList().chunked(batchSize)
    }

    // Internal data classes
    private data class GeckoCoinData(
        val id: String,
        val symbol: String,
        val name: String,
        val image: String,
        val marketCap: Long,
        val marketCapRank: Int,
        val circulatingSupply: Double
    )

    private data class MatchedCoin(
        val binanceSymbol: String,
        val coinId: String,
        val symbol: String,
        val name: String,
        val image: String,
        val marketCapRank: Int,
        val circulatingSupply: Double
    )
}
