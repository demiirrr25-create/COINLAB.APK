package com.coinlab.app.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.coinlab.app.data.local.dao.CoinDao
import com.coinlab.app.data.local.dao.WatchlistDao
import com.coinlab.app.data.local.entity.CoinEntity
import com.coinlab.app.data.local.entity.WatchlistEntity
import com.coinlab.app.data.paging.CoinPagingSource
import com.coinlab.app.data.remote.DynamicCoinRegistry
import com.coinlab.app.data.remote.StaticFallbackData
import com.coinlab.app.data.remote.api.BinanceApi
import com.coinlab.app.data.remote.api.CoinGeckoApi
import com.coinlab.app.data.remote.cache.BinanceTickerCache
import com.coinlab.app.data.remote.dto.toDomain
import com.coinlab.app.domain.model.Coin
import com.coinlab.app.domain.model.CoinDetail
import com.coinlab.app.domain.model.CoinLinks
import com.coinlab.app.domain.model.MarketChart
import com.coinlab.app.domain.repository.CoinRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class CoinRepositoryImpl @Inject constructor(
    private val api: CoinGeckoApi,
    private val binanceApi: BinanceApi,
    private val tickerCache: BinanceTickerCache,
    private val coinDao: CoinDao,
    private val watchlistDao: WatchlistDao,
    private val coinRegistry: DynamicCoinRegistry
) : CoinRepository {

    companion object {
        private const val CACHE_DURATION_MS = 2 * 60 * 1000L // 2 minutes
        private const val STALE_OK_MS = 10 * 60 * 1000L // Stale cache OK up to 10 minutes
    }

    override fun getCoins(
        currency: String,
        orderBy: String,
        perPage: Int,
        page: Int,
        sparkline: Boolean
    ): Flow<Result<List<Coin>>> = flow {
        try {
            // Ensure DynamicCoinRegistry is initialized
            if (!coinRegistry.isReady()) {
                try { coinRegistry.initialize() } catch (_: Exception) {}
            }

            // Check cache first — if fresh enough, use it and skip network
            val lastCached = coinDao.getLastCachedTime()
            val cacheAge = if (lastCached != null) System.currentTimeMillis() - lastCached else Long.MAX_VALUE

            if (page == 1 && cacheAge < CACHE_DURATION_MS) {
                val entities = coinDao.getAllCoins().first()
                if (entities.isNotEmpty()) {
                    val cachedCoins = entities.map { it.toDomain() }
                    emit(Result.success(if (perPage < cachedCoins.size) cachedCoins.take(perPage) else cachedCoins))
                    return@flow // Cache is fresh, done
                }
            }

            // PRIMARY: Fetch from Binance (no API key, fast, reliable)
            // Always fetch ALL available coins, cache all, then trim for caller
            val allCoins = fetchCoinsFromBinance(Int.MAX_VALUE)
            if (allCoins != null && allCoins.isNotEmpty()) {
                // Cache ALL coins for efficiency — other screens benefit
                if (page == 1) {
                    try {
                        coinDao.upsertAll(allCoins.map { it.toEntity() })
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        android.util.Log.w("CoinRepo", "Room cache write failed: ${e.message}")
                    }
                }
                // Return only requested amount
                val result = if (perPage < allCoins.size) allCoins.take(perPage) else allCoins
                emit(Result.success(result))
                return@flow
            }

            // If Binance failed but we have stale cache, use it while trying CoinGecko
            if (page == 1 && cacheAge < STALE_OK_MS) {
                val entities = coinDao.getAllCoins().first()
                if (entities.isNotEmpty()) {
                    val cachedCoins = entities.map { it.toDomain() }
                    emit(Result.success(if (perPage < cachedCoins.size) cachedCoins.take(perPage) else cachedCoins))
                    // Don't return — try CoinGecko in background to update cache
                }
            }

            // FALLBACK: CoinGecko (public API, no key needed)
            // CoinGecko max per_page is 250, so we fetch multiple pages
            val allGeckoCoins = mutableListOf<com.coinlab.app.data.remote.dto.CoinDto>()
            val geckoPageSize = minOf(perPage, 250)
            val geckoPages = (perPage + geckoPageSize - 1) / geckoPageSize

            for (gPage in 1..geckoPages) {
                try {
                    val pageResponse = api.getCoins(
                        currency = currency,
                        orderBy = orderBy,
                        perPage = geckoPageSize,
                        page = gPage,
                        sparkline = sparkline
                    )
                    allGeckoCoins.addAll(pageResponse)
                    if (pageResponse.size < geckoPageSize) break // No more pages
                } catch (e2: Exception) {
                    if (e2 is CancellationException) throw e2
                    android.util.Log.w("CoinRepo", "CoinGecko page $gPage failed: ${e2.message}")
                    break // Use what we have
                }
            }

            if (allGeckoCoins.isNotEmpty()) {
                val geckoCoins = allGeckoCoins.map { it.toDomain() }
                if (page == 1) {
                    coinDao.upsertAll(allGeckoCoins.map { it.toEntity() })
                }
                emit(Result.success(geckoCoins))
            } else {
                // CoinGecko also failed, emit empty to trigger catch block
                throw Exception("Both Binance and CoinGecko returned no data")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Fallback to cache on error
            try {
                val entities = coinDao.getAllCoins().first()
                if (entities.isNotEmpty()) {
                    emit(Result.success(entities.map { it.toDomain() }))
                } else {
                    // Last resort: static hardcoded data so user never sees empty/error screen
                    android.util.Log.w("CoinRepo", "All sources failed, using static fallback: ${e.message}")
                    emit(Result.success(StaticFallbackData.getDefaultCoins()))
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                // Even Room failed — serve static data
                android.util.Log.w("CoinRepo", "Room + all APIs failed, static fallback: ${e.message}")
                emit(Result.success(StaticFallbackData.getDefaultCoins()))
            }
        }
    }

    /**
     * Get paginated coins from Room database.
     * Data is pre-fetched from Binance and cached in Room.
     * Uses Paging 3 for smooth infinite scroll of up to 1000 coins.
     */
    override fun getPagedCoins(): Flow<PagingData<Coin>> {
        return Pager(
            config = PagingConfig(
                pageSize = 50,
                prefetchDistance = 20,
                enablePlaceholders = false,
                initialLoadSize = 50
            ),
            pagingSourceFactory = { CoinPagingSource(coinDao) }
        ).flow
    }

    /**
     * Fetch coin data from Binance 24hr ticker — no API key needed, very fast.
     * Uses DynamicCoinRegistry for metadata (supports up to 1000 coins).
     * Returns null on network/parsing failure so the caller can distinguish
     * "no data" from "Binance not available" and act accordingly.
     */
    private suspend fun fetchCoinsFromBinance(limit: Int): List<Coin>? {
        return try {
            val tickers = tickerCache.getTickers()
            if (tickers.isEmpty()) return null // Binance unreachable — signal caller

            tickers.mapNotNull { ticker ->
                    val meta = coinRegistry.getMetaByBinanceSymbol(ticker.symbol ?: return@mapNotNull null)
                        ?: return@mapNotNull null
                    val price = ticker.lastPrice?.toDoubleOrNull() ?: return@mapNotNull null
                    val volume = ticker.quoteVolume?.toDoubleOrNull() ?: 0.0
                    val change24h = ticker.priceChangePercent?.toDoubleOrNull() ?: 0.0
                    val supply = coinRegistry.getCirculatingSupply(meta.id)

                    Coin(
                        id = meta.id,
                        symbol = meta.symbol,
                        name = meta.name,
                        image = meta.image,
                        currentPrice = price,
                        marketCap = (price * supply).toLong(),
                        marketCapRank = coinRegistry.getMarketCapRank(meta.id),
                        totalVolume = volume,
                        priceChangePercentage24h = change24h,
                        priceChangePercentage7d = null,
                        circulatingSupply = supply,
                        totalSupply = null,
                        maxSupply = null,
                        ath = 0.0,
                        athChangePercentage = 0.0,
                        athDate = "",
                        atl = 0.0,
                        atlChangePercentage = 0.0,
                        atlDate = "",
                        sparklineIn7d = null,
                        lastUpdated = System.currentTimeMillis().toString()
                    )
                }
                .distinctBy { it.id }
                .sortedBy { it.marketCapRank }
                .let { sortedCoins ->
                    // Guarantee BTC and ETH are ALWAYS present — use hardcoded fallback if needed
                    val hasBtc = sortedCoins.any { it.id == "bitcoin" }
                    val hasEth = sortedCoins.any { it.id == "ethereum" }
                    val guaranteedCoins = mutableListOf<Coin>()

                    if (!hasBtc) {
                        // Try from tickers first
                        val btcTicker = tickers.firstOrNull { it.symbol == "BTCUSDT" }
                        val btcPrice = btcTicker?.lastPrice?.toDoubleOrNull()
                        if (btcPrice != null && btcPrice > 0) {
                            val supply = try { coinRegistry.getCirculatingSupply("bitcoin") } catch (_: Exception) { 19_800_000.0 }
                            guaranteedCoins.add(
                                Coin(id = "bitcoin", symbol = "BTC", name = "Bitcoin",
                                    image = "https://assets.coingecko.com/coins/images/1/large/bitcoin.png",
                                    currentPrice = btcPrice, marketCap = (btcPrice * supply).toLong(), marketCapRank = 1,
                                    totalVolume = btcTicker.quoteVolume?.toDoubleOrNull() ?: 0.0,
                                    priceChangePercentage24h = btcTicker.priceChangePercent?.toDoubleOrNull() ?: 0.0,
                                    priceChangePercentage7d = null, circulatingSupply = supply, totalSupply = null,
                                    maxSupply = 21_000_000.0, ath = 0.0, athChangePercentage = 0.0, athDate = "",
                                    atl = 0.0, atlChangePercentage = 0.0, atlDate = "", sparklineIn7d = null,
                                    lastUpdated = System.currentTimeMillis().toString())
                            )
                        } else {
                            // Last resort: hardcoded BTC entry so it's NEVER missing
                            guaranteedCoins.add(
                                Coin(id = "bitcoin", symbol = "BTC", name = "Bitcoin",
                                    image = "https://assets.coingecko.com/coins/images/1/large/bitcoin.png",
                                    currentPrice = 0.0, marketCap = 0L, marketCapRank = 1,
                                    totalVolume = 0.0, priceChangePercentage24h = 0.0,
                                    priceChangePercentage7d = null, circulatingSupply = 19_800_000.0, totalSupply = null,
                                    maxSupply = 21_000_000.0, ath = 0.0, athChangePercentage = 0.0, athDate = "",
                                    atl = 0.0, atlChangePercentage = 0.0, atlDate = "", sparklineIn7d = null,
                                    lastUpdated = System.currentTimeMillis().toString())
                            )
                        }
                    }
                    if (!hasEth) {
                        val ethTicker = tickers.firstOrNull { it.symbol == "ETHUSDT" }
                        val ethPrice = ethTicker?.lastPrice?.toDoubleOrNull()
                        if (ethPrice != null && ethPrice > 0) {
                            val supply = try { coinRegistry.getCirculatingSupply("ethereum") } catch (_: Exception) { 120_000_000.0 }
                            guaranteedCoins.add(
                                Coin(id = "ethereum", symbol = "ETH", name = "Ethereum",
                                    image = "https://assets.coingecko.com/coins/images/279/large/ethereum.png",
                                    currentPrice = ethPrice, marketCap = (ethPrice * supply).toLong(), marketCapRank = 2,
                                    totalVolume = ethTicker.quoteVolume?.toDoubleOrNull() ?: 0.0,
                                    priceChangePercentage24h = ethTicker.priceChangePercent?.toDoubleOrNull() ?: 0.0,
                                    priceChangePercentage7d = null, circulatingSupply = supply, totalSupply = null,
                                    maxSupply = null, ath = 0.0, athChangePercentage = 0.0, athDate = "",
                                    atl = 0.0, atlChangePercentage = 0.0, atlDate = "", sparklineIn7d = null,
                                    lastUpdated = System.currentTimeMillis().toString())
                            )
                        }
                    }
                    (guaranteedCoins + sortedCoins).distinctBy { it.id }.sortedBy { it.marketCapRank }
                }
                .take(limit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.w("CoinRepo", "Binance fetch failed: ${e.message}")
            null // Signal failure so CoinGecko fallback is tried
        }
    }

    override fun searchCoins(query: String): Flow<Result<List<Coin>>> = flow {
        try {
            // Search in local cache first (very fast)
            val entities = coinDao.searchCoins(query).first()
            if (entities.isNotEmpty()) {
                emit(Result.success(entities.map { it.toDomain() }))
                return@flow
            }
            // If nothing in cache, return empty
            emit(Result.success(emptyList()))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emit(Result.success(emptyList()))
        }
    }

    override fun getCoinDetail(coinId: String): Flow<Result<CoinDetail>> = flow {
        try {
            // Try cached ticker first (instant, no network call)
            val binanceSymbol = coinRegistry.getBinanceSymbolByCoinId(coinId)
            if (binanceSymbol != null) {
                try {
                    // Use shared cache first for instant response
                    val cachedTicker = tickerCache.getTickerBySymbol(binanceSymbol)
                    val ticker = cachedTicker ?: binanceApi.getTickerBySymbol(binanceSymbol)
                    val meta = coinRegistry.getMetaByCoinId(coinId)
                    if (meta != null && ticker.lastPrice != null) {
                        val price = ticker.lastPrice.toDoubleOrNull() ?: 0.0
                        val change = ticker.priceChangePercent?.toDoubleOrNull() ?: 0.0
                        val high = ticker.highPrice?.toDoubleOrNull() ?: 0.0
                        val low = ticker.lowPrice?.toDoubleOrNull() ?: 0.0
                        val volume = ticker.quoteVolume?.toDoubleOrNull() ?: 0.0
                        val supply = coinRegistry.getCirculatingSupply(meta.id)

                        val detail = CoinDetail(
                            id = meta.id,
                            symbol = meta.symbol,
                            name = meta.name,
                            image = meta.image,
                            description = "",
                            currentPrice = mapOf("usd" to price),
                            marketCap = mapOf("usd" to (price * supply).toLong()),
                            marketCapRank = coinRegistry.getMarketCapRank(meta.id),
                            totalVolume = mapOf("usd" to volume),
                            high24h = mapOf("usd" to high),
                            low24h = mapOf("usd" to low),
                            priceChange24h = price * change / 100.0,
                            priceChangePercentage24h = change,
                            priceChangePercentage7d = 0.0,
                            priceChangePercentage30d = 0.0,
                            priceChangePercentage1y = 0.0,
                            circulatingSupply = supply,
                            totalSupply = null,
                            maxSupply = null,
                            ath = emptyMap(),
                            athChangePercentage = emptyMap(),
                            atl = emptyMap(),
                            atlChangePercentage = emptyMap(),
                            genesisDate = null,
                            homepageUrl = null,
                            blockchainSite = null,
                            categories = emptyList(),
                            links = CoinLinks(
                                homepage = emptyList(),
                                blockchain = emptyList(),
                                reddit = null,
                                twitter = null,
                                telegram = null,
                                github = emptyList()
                            )
                        )
                        emit(Result.success(detail))
                        return@flow
                    }
                } catch (ce: CancellationException) { throw ce } catch (_: Exception) { /* fall through to CoinGecko */ }
            }

            // Fallback to CoinGecko for detailed data
            val response = api.getCoinDetail(coinId)
            emit(Result.success(response.toDomain()))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emit(Result.failure(e))
        }
    }

    override fun getMarketChart(
        coinId: String,
        currency: String,
        days: String
    ): Flow<Result<MarketChart>> = flow {
        try {
            val interval = when {
                days.toIntOrNull()?.let { it <= 1 } == true -> "minutely"
                days.toIntOrNull()?.let { it <= 90 } == true -> "hourly"
                else -> "daily"
            }
            val response = api.getMarketChart(
                coinId = coinId,
                currency = currency,
                days = days,
                interval = if (days == "1") null else interval
            )
            emit(Result.success(response.toDomain()))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emit(Result.failure(e))
        }
    }

    override fun getMarketChartFromBinance(
        symbol: String,
        days: String
    ): Flow<Result<MarketChart>> = flow {
        try {
            val (interval, limit) = when (days) {
                "1" -> "5m" to 288
                "7" -> "1h" to 168
                "30" -> "4h" to 180
                "90" -> "1d" to 90
                "365" -> "1d" to 365
                else -> "1h" to 168
            }
            val binanceSymbol = symbol.uppercase() + "USDT"
            val klines = binanceApi.getKlines(
                symbol = binanceSymbol,
                interval = interval,
                limit = limit
            )
            val prices = klines.mapNotNull { candle ->
                if (candle.size >= 5) {
                    val timestamp = (candle[0] as? Number)?.toLong() ?: return@mapNotNull null
                    val close = (candle[4] as? String)?.toDoubleOrNull()
                        ?: (candle[4] as? Number)?.toDouble()
                        ?: return@mapNotNull null
                    Pair(timestamp, close)
                } else null
            }
            val volumes = klines.mapNotNull { candle ->
                if (candle.size >= 6) {
                    val timestamp = (candle[0] as? Number)?.toLong() ?: return@mapNotNull null
                    val volume = (candle[5] as? String)?.toDoubleOrNull()
                        ?: (candle[5] as? Number)?.toDouble()
                        ?: return@mapNotNull null
                    Pair(timestamp, volume)
                } else null
            }
            if (prices.isEmpty()) {
                emit(Result.failure(Exception("Binance grafik verisi boş")))
            } else {
                emit(Result.success(MarketChart(
                    prices = prices,
                    marketCaps = emptyList(),
                    totalVolumes = volumes
                )))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emit(Result.failure(e))
        }
    }

    override fun getTrendingCoins(): Flow<Result<List<Coin>>> = flow {
        try {
            // Use cached tickers (shared, ~50 items)
            val tickers = tickerCache.getTickers()

            // If Binance is unreachable, fall back to Room cache sorted by change%
            if (tickers.isEmpty()) {
                val entities = coinDao.getAllCoins().first()
                if (entities.isNotEmpty()) {
                    val coins = entities.map { it.toDomain() }
                        .sortedByDescending { it.priceChangePercentage24h }
                        .take(10)
                    emit(Result.success(coins))
                } else {
                    emit(Result.success(StaticFallbackData.getDefaultCoins().take(10)))
                }
                return@flow
            }

            val trending = tickers
                .sortedByDescending { it.priceChangePercent?.toDoubleOrNull() ?: 0.0 }
                .take(10)
                .mapNotNull { ticker ->
                    val meta = coinRegistry.getMetaByBinanceSymbol(ticker.symbol ?: return@mapNotNull null)
                        ?: return@mapNotNull null
                    val price = ticker.lastPrice?.toDoubleOrNull() ?: return@mapNotNull null
                    val supply = coinRegistry.getCirculatingSupply(meta.id)
                    Coin(
                        id = meta.id,
                        symbol = meta.symbol,
                        name = meta.name,
                        image = meta.image,
                        currentPrice = price,
                        marketCap = (price * supply).toLong(),
                        marketCapRank = coinRegistry.getMarketCapRank(meta.id),
                        totalVolume = ticker.quoteVolume?.toDoubleOrNull() ?: 0.0,
                        priceChangePercentage24h = ticker.priceChangePercent?.toDoubleOrNull() ?: 0.0,
                        priceChangePercentage7d = null,
                        circulatingSupply = supply,
                        totalSupply = null,
                        maxSupply = null,
                        ath = 0.0,
                        athChangePercentage = 0.0,
                        athDate = "",
                        atl = 0.0,
                        atlChangePercentage = 0.0,
                        atlDate = "",
                        sparklineIn7d = null,
                        lastUpdated = System.currentTimeMillis().toString()
                    )
                }
            emit(Result.success(trending))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emit(Result.success(emptyList()))
        }
    }

    override fun getWatchlistCoins(currency: String): Flow<Result<List<Coin>>> = flow {
        try {
            val watchlistIds = watchlistDao.getAllCoinIds()
            if (watchlistIds.isEmpty()) {
                emit(Result.success(emptyList()))
                return@flow
            }
            // Get from cached tickers
            val tickerMap = tickerCache.getTickerMap()

            // If Binance is unreachable, fall back to Room cache for watchlist coins
            if (tickerMap.isEmpty()) {
                val entities = coinDao.getAllCoins().first()
                val cached = entities.map { it.toDomain() }.filter { it.id in watchlistIds }
                emit(Result.success(cached))
                return@flow
            }

            val coins = watchlistIds.mapNotNull { coinId ->
                val binanceSymbol = coinRegistry.getBinanceSymbolByCoinId(coinId) ?: return@mapNotNull null
                val ticker = tickerMap[binanceSymbol] ?: return@mapNotNull null
                val meta = coinRegistry.getMetaByCoinId(coinId) ?: return@mapNotNull null
                val price = ticker.lastPrice?.toDoubleOrNull() ?: return@mapNotNull null
                val supply = coinRegistry.getCirculatingSupply(meta.id)

                Coin(
                    id = meta.id,
                    symbol = meta.symbol,
                    name = meta.name,
                    image = meta.image,
                    currentPrice = price,
                    marketCap = (price * supply).toLong(),
                    marketCapRank = coinRegistry.getMarketCapRank(meta.id),
                    totalVolume = ticker.quoteVolume?.toDoubleOrNull() ?: 0.0,
                    priceChangePercentage24h = ticker.priceChangePercent?.toDoubleOrNull() ?: 0.0,
                    priceChangePercentage7d = null,
                    circulatingSupply = supply,
                    totalSupply = null,
                    maxSupply = null,
                    ath = 0.0,
                    athChangePercentage = 0.0,
                    athDate = "",
                    atl = 0.0,
                    atlChangePercentage = 0.0,
                    atlDate = "",
                    sparklineIn7d = null,
                    lastUpdated = System.currentTimeMillis().toString()
                )
            }
            emit(Result.success(coins))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emit(Result.success(emptyList()))
        }
    }

    override suspend fun addToWatchlist(coinId: String) {
        watchlistDao.insert(WatchlistEntity(coinId = coinId))
    }

    override suspend fun removeFromWatchlist(coinId: String) {
        watchlistDao.delete(coinId)
    }

    override fun isInWatchlist(coinId: String): Flow<Boolean> {
        return watchlistDao.isInWatchlist(coinId)
    }
}

// Extension: Coin domain model → CoinEntity for Room caching
private fun Coin.toEntity(): CoinEntity {
    return CoinEntity(
        id = id,
        symbol = symbol.uppercase(),
        name = name,
        image = image,
        currentPrice = currentPrice,
        marketCap = marketCap,
        marketCapRank = marketCapRank,
        totalVolume = totalVolume,
        priceChangePercentage24h = priceChangePercentage24h,
        priceChangePercentage7d = priceChangePercentage7d,
        circulatingSupply = circulatingSupply,
        totalSupply = totalSupply,
        maxSupply = maxSupply,
        ath = ath,
        athChangePercentage = athChangePercentage,
        athDate = athDate,
        atl = atl,
        atlChangePercentage = atlChangePercentage,
        atlDate = atlDate,
        lastUpdated = lastUpdated,
        sparklineData = sparklineIn7d?.joinToString(",")
    )
}

// Extension: CoinEntity → Coin domain model
private fun CoinEntity.toDomain(): Coin {
    val sparkline = sparklineData?.let { data ->
        try {
            data.split(",").mapNotNull { it.trim().toDoubleOrNull() }
        } catch (_: Exception) { null }
    }
    return Coin(
        id = id,
        symbol = symbol,
        name = name,
        image = image,
        currentPrice = currentPrice,
        marketCap = marketCap,
        marketCapRank = marketCapRank,
        totalVolume = totalVolume,
        priceChangePercentage24h = priceChangePercentage24h,
        priceChangePercentage7d = priceChangePercentage7d,
        circulatingSupply = circulatingSupply,
        totalSupply = totalSupply,
        maxSupply = maxSupply,
        ath = ath,
        athChangePercentage = athChangePercentage,
        athDate = athDate,
        atl = atl,
        atlChangePercentage = atlChangePercentage,
        atlDate = atlDate,
        sparklineIn7d = sparkline,
        lastUpdated = lastUpdated
    )
}

// Extension: CoinDto → CoinEntity (for CoinGecko fallback)
private fun com.coinlab.app.data.remote.dto.CoinDto.toEntity(): CoinEntity {
    return CoinEntity(
        id = id,
        symbol = symbol.uppercase(),
        name = name,
        image = image,
        currentPrice = currentPrice ?: 0.0,
        marketCap = marketCap ?: 0L,
        marketCapRank = marketCapRank ?: 0,
        totalVolume = totalVolume ?: 0.0,
        priceChangePercentage24h = priceChangePercentage24h ?: 0.0,
        priceChangePercentage7d = priceChangePercentage7d,
        circulatingSupply = circulatingSupply ?: 0.0,
        totalSupply = totalSupply,
        maxSupply = maxSupply,
        ath = ath ?: 0.0,
        athChangePercentage = athChangePercentage ?: 0.0,
        athDate = athDate ?: "",
        atl = atl ?: 0.0,
        atlChangePercentage = atlChangePercentage ?: 0.0,
        atlDate = atlDate ?: "",
        lastUpdated = lastUpdated ?: "",
        sparklineData = sparklineIn7d?.price?.joinToString(",")
    )
}
