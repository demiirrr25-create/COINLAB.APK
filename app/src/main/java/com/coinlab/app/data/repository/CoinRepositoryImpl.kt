package com.coinlab.app.data.repository

import com.coinlab.app.data.local.dao.CoinDao
import com.coinlab.app.data.local.dao.WatchlistDao
import com.coinlab.app.data.local.entity.CoinEntity
import com.coinlab.app.data.local.entity.WatchlistEntity
import com.coinlab.app.data.remote.api.BinanceApi
import com.coinlab.app.data.remote.api.CoinGeckoApi
import com.coinlab.app.data.remote.dto.toDomain
import com.coinlab.app.domain.model.Coin
import com.coinlab.app.domain.model.CoinDetail
import com.coinlab.app.domain.model.MarketChart
import com.coinlab.app.domain.repository.CoinRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class CoinRepositoryImpl @Inject constructor(
    private val api: CoinGeckoApi,
    private val binanceApi: BinanceApi,
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
                    // Don't return — continue to refresh in background
                }
            }

            // Fetch from API (always, to keep data fresh)
            val response = api.getCoins(
                currency = currency,
                orderBy = orderBy,
                perPage = perPage,
                page = page,
                sparkline = sparkline
            )
            val coins = response.map { it.toDomain() }

            // Cache results
            if (page == 1) {
                coinDao.deleteAll()
            }
            coinDao.insertAll(response.map { it.toEntity() })

            emit(Result.success(coins))
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

    override fun searchCoins(query: String): Flow<Result<List<Coin>>> = flow {
        try {
            val response = api.searchCoins(query)
            val coinIds = response.coins?.take(20)?.map { it.id } ?: emptyList()

            if (coinIds.isNotEmpty()) {
                val marketData = api.getCoins(
                    currency = "usd",
                    perPage = 20,
                    page = 1,
                    sparkline = false
                ).filter { it.id in coinIds }

                emit(Result.success(marketData.map { it.toDomain() }))
            } else {
                emit(Result.success(emptyList()))
            }
        } catch (e: Exception) {
            // Fallback to local search
            try {
                val entities = coinDao.searchCoins(query).first()
                emit(Result.success(entities.map { it.toDomain() }))
            } catch (_: Exception) {
                emit(Result.success(emptyList()))
            }
        }
    }

    override fun getCoinDetail(coinId: String): Flow<Result<CoinDetail>> = flow {
        try {
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
            val trending = api.getTrending()
            val ids = trending.coins?.map { it.item.id } ?: emptyList()
            if (ids.isNotEmpty()) {
                val coins = api.getCoins(
                    currency = "usd",
                    perPage = ids.size,
                    sparkline = true
                ).filter { it.id in ids }
                emit(Result.success(coins.map { it.toDomain() }))
            } else {
                emit(Result.success(emptyList()))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    override fun getWatchlistCoins(currency: String): Flow<Result<List<Coin>>> = flow {
        try {
            val watchlistIds = watchlistDao.getAllCoinIds()
            if (watchlistIds.isNotEmpty()) {
                val idsParam = watchlistIds.joinToString(",")
                // Use ids parameter for efficient targeted fetch instead of fetching all 250
                val watchlistCoins = api.getCoins(
                    currency = currency,
                    ids = idsParam,
                    perPage = watchlistIds.size,
                    sparkline = true
                )
                emit(Result.success(watchlistCoins.map { it.toDomain() }))
            } else {
                emit(Result.success(emptyList()))
            }
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

// Extension functions for mapping
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
