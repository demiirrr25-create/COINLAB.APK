package com.coinlab.app.data.repository

import com.coinlab.app.data.local.dao.CoinDao
import com.coinlab.app.data.local.dao.WatchlistDao
import com.coinlab.app.data.local.entity.CoinEntity
import com.coinlab.app.data.local.entity.WatchlistEntity
import com.coinlab.app.data.remote.BinanceCoinMapper
import com.coinlab.app.data.remote.api.BinanceApi
import com.coinlab.app.data.remote.api.CoinGeckoApi
import com.coinlab.app.data.remote.cache.BinanceTickerCache
import com.coinlab.app.data.remote.dto.toDomain
import com.coinlab.app.domain.model.Coin
import com.coinlab.app.domain.model.CoinDetail
import com.coinlab.app.domain.model.CoinLinks
import com.coinlab.app.domain.model.MarketChart
import com.coinlab.app.domain.repository.CoinRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class CoinRepositoryImpl @Inject constructor(
    private val api: CoinGeckoApi,
    private val binanceApi: BinanceApi,
    private val tickerCache: BinanceTickerCache,
    private val coinDao: CoinDao,
    private val watchlistDao: WatchlistDao
) : CoinRepository {

    companion object {
        private const val CACHE_DURATION_MS = 2 * 60 * 1000L // 2 minutes
    }

    override fun getCoins(
        currency: String,
        orderBy: String,
        perPage: Int,
        page: Int,
        sparkline: Boolean
    ): Flow<Result<List<Coin>>> = flow {
        try {
            // Check cache first
            val lastCached = coinDao.getLastCachedTime()
            val isCacheValid = lastCached != null &&
                    (System.currentTimeMillis() - lastCached) < CACHE_DURATION_MS

            if (isCacheValid && page == 1) {
                val entities = coinDao.getAllCoins().first()
                if (entities.isNotEmpty()) {
                    emit(Result.success(entities.map { it.toDomain() }))
                    return@flow // Cache valid — don't fetch from network (avoid double-emit)
                }
            }

            // PRIMARY: Fetch from Binance (no API key, fast, reliable)
            val coins = fetchCoinsFromBinance(perPage)
            if (coins.isNotEmpty()) {
                // Cache results
                if (page == 1) {
                    coinDao.deleteAll()
                    coinDao.insertAll(coins.map { it.toEntity() })
                }
                emit(Result.success(coins))
                return@flow
            }

            // FALLBACK: CoinGecko
            val response = api.getCoins(
                currency = currency,
                orderBy = orderBy,
                perPage = perPage,
                page = page,
                sparkline = sparkline
            )
            val geckoCoins = response.map { it.toDomain() }
            if (page == 1) {
                coinDao.deleteAll()
                coinDao.insertAll(response.map { it.toEntity() })
            }
            emit(Result.success(geckoCoins))
        } catch (e: Exception) {
            // Fallback to cache on error
            try {
                val entities = coinDao.getAllCoins().first()
                if (entities.isNotEmpty()) {
                    emit(Result.success(entities.map { it.toDomain() }))
                } else {
                    emit(Result.failure(e))
                }
            } catch (_: Exception) {
                emit(Result.failure(e))
            }
        }
    }

    /**
     * Fetch coin data from Binance 24hr ticker — no API key needed, very fast
     */
    private suspend fun fetchCoinsFromBinance(limit: Int): List<Coin> {
        return try {
            val tickers = tickerCache.getTickers()

            tickers.mapNotNull { ticker ->
                    val meta = BinanceCoinMapper.getMetaByBinanceSymbol(ticker.symbol ?: return@mapNotNull null)
                        ?: return@mapNotNull null
                    val price = ticker.lastPrice?.toDoubleOrNull() ?: return@mapNotNull null
                    val volume = ticker.quoteVolume?.toDoubleOrNull() ?: 0.0
                    val change24h = ticker.priceChangePercent?.toDoubleOrNull() ?: 0.0

                    Coin(
                        id = meta.id,
                        symbol = meta.symbol,
                        name = meta.name,
                        image = meta.image,
                        currentPrice = price,
                        marketCap = (price * getApproxCirculatingSupply(meta.id)).toLong(),
                        marketCapRank = BinanceCoinMapper.getMarketCapRank(meta.id),
                        totalVolume = volume,
                        priceChangePercentage24h = change24h,
                        priceChangePercentage7d = null,
                        circulatingSupply = getApproxCirculatingSupply(meta.id),
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
                .take(limit)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Approximate circulating supply for market cap calculation
     */
    private fun getApproxCirculatingSupply(coinId: String): Double = when (coinId) {
        "bitcoin" -> 19_800_000.0
        "ethereum" -> 120_500_000.0
        "binancecoin" -> 145_000_000.0
        "solana" -> 470_000_000.0
        "ripple" -> 57_000_000_000.0
        "cardano" -> 36_000_000_000.0
        "dogecoin" -> 147_000_000_000.0
        "tron" -> 86_000_000_000.0
        "polkadot" -> 1_500_000_000.0
        "avalanche-2" -> 410_000_000.0
        "chainlink" -> 630_000_000.0
        "shiba-inu" -> 589_000_000_000_000.0
        "matic-network" -> 10_000_000_000.0
        "litecoin" -> 75_000_000.0
        "uniswap" -> 600_000_000.0
        "cosmos" -> 390_000_000.0
        "near" -> 1_200_000_000.0
        "aptos" -> 500_000_000.0
        "sui" -> 3_000_000_000.0
        "internet-computer" -> 520_000_000.0
        "ethereum-classic" -> 148_000_000.0
        "filecoin" -> 580_000_000.0
        "stellar" -> 30_000_000_000.0
        "vechain" -> 73_000_000_000.0
        "hedera-hashgraph" -> 40_000_000_000.0
        "aave" -> 15_000_000.0
        "algorand" -> 8_200_000_000.0
        "injective-protocol" -> 97_000_000.0
        "celestia" -> 250_000_000.0
        "arbitrum" -> 3_400_000_000.0
        "optimism" -> 1_400_000_000.0
        "maker" -> 900_000.0
        "pepe" -> 420_690_000_000_000.0
        else -> 1_000_000_000.0
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
            emit(Result.success(emptyList()))
        }
    }

    override fun getCoinDetail(coinId: String): Flow<Result<CoinDetail>> = flow {
        try {
            // Try Binance ticker for basic data first
            val binanceSymbol = BinanceCoinMapper.getBinanceSymbolByCoinId(coinId)
            if (binanceSymbol != null) {
                try {
                    val ticker = binanceApi.getTickerBySymbol(binanceSymbol)
                    val meta = BinanceCoinMapper.getMetaByCoinId(coinId)
                    if (meta != null && ticker.lastPrice != null) {
                        val price = ticker.lastPrice.toDoubleOrNull() ?: 0.0
                        val change = ticker.priceChangePercent?.toDoubleOrNull() ?: 0.0
                        val high = ticker.highPrice?.toDoubleOrNull() ?: 0.0
                        val low = ticker.lowPrice?.toDoubleOrNull() ?: 0.0
                        val volume = ticker.quoteVolume?.toDoubleOrNull() ?: 0.0

                        val detail = CoinDetail(
                            id = meta.id,
                            symbol = meta.symbol,
                            name = meta.name,
                            image = meta.image,
                            description = "",
                            currentPrice = mapOf("usd" to price),
                            marketCap = mapOf("usd" to (price * getApproxCirculatingSupply(meta.id)).toLong()),
                            marketCapRank = BinanceCoinMapper.getMarketCapRank(meta.id),
                            totalVolume = mapOf("usd" to volume),
                            high24h = mapOf("usd" to high),
                            low24h = mapOf("usd" to low),
                            priceChange24h = price * change / 100.0,
                            priceChangePercentage24h = change,
                            priceChangePercentage7d = 0.0,
                            priceChangePercentage30d = 0.0,
                            priceChangePercentage1y = 0.0,
                            circulatingSupply = getApproxCirculatingSupply(meta.id),
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
                } catch (_: Exception) { /* fall through to CoinGecko */ }
            }

            // Fallback to CoinGecko for detailed data
            val response = api.getCoinDetail(coinId)
            emit(Result.success(response.toDomain()))
        } catch (e: Exception) {
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
            emit(Result.failure(e))
        }
    }

    override fun getTrendingCoins(): Flow<Result<List<Coin>>> = flow {
        try {
            // Use cached tickers (shared, ~50 items)
            val tickers = tickerCache.getTickers()

            val trending = tickers
                .sortedByDescending { it.priceChangePercent?.toDoubleOrNull() ?: 0.0 }
                .take(10)
                .mapNotNull { ticker ->
                    val meta = BinanceCoinMapper.getMetaByBinanceSymbol(ticker.symbol ?: return@mapNotNull null)
                        ?: return@mapNotNull null
                    val price = ticker.lastPrice?.toDoubleOrNull() ?: return@mapNotNull null
                    Coin(
                        id = meta.id,
                        symbol = meta.symbol,
                        name = meta.name,
                        image = meta.image,
                        currentPrice = price,
                        marketCap = (price * getApproxCirculatingSupply(meta.id)).toLong(),
                        marketCapRank = BinanceCoinMapper.getMarketCapRank(meta.id),
                        totalVolume = ticker.quoteVolume?.toDoubleOrNull() ?: 0.0,
                        priceChangePercentage24h = ticker.priceChangePercent?.toDoubleOrNull() ?: 0.0,
                        priceChangePercentage7d = null,
                        circulatingSupply = getApproxCirculatingSupply(meta.id),
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
            emit(Result.failure(e))
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
            val coins = watchlistIds.mapNotNull { coinId ->
                val binanceSymbol = BinanceCoinMapper.getBinanceSymbolByCoinId(coinId) ?: return@mapNotNull null
                val ticker = tickerMap[binanceSymbol] ?: return@mapNotNull null
                val meta = BinanceCoinMapper.getMetaByCoinId(coinId) ?: return@mapNotNull null
                val price = ticker.lastPrice?.toDoubleOrNull() ?: return@mapNotNull null

                Coin(
                    id = meta.id,
                    symbol = meta.symbol,
                    name = meta.name,
                    image = meta.image,
                    currentPrice = price,
                    marketCap = (price * getApproxCirculatingSupply(meta.id)).toLong(),
                    marketCapRank = BinanceCoinMapper.getMarketCapRank(meta.id),
                    totalVolume = ticker.quoteVolume?.toDoubleOrNull() ?: 0.0,
                    priceChangePercentage24h = ticker.priceChangePercent?.toDoubleOrNull() ?: 0.0,
                    priceChangePercentage7d = null,
                    circulatingSupply = getApproxCirculatingSupply(meta.id),
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
            emit(Result.failure(e))
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
