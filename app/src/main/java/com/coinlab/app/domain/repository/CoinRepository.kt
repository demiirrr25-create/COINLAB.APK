package com.coinlab.app.domain.repository

import androidx.paging.PagingData
import com.coinlab.app.domain.model.Coin
import com.coinlab.app.domain.model.CoinDetail
import com.coinlab.app.domain.model.MarketChart
import kotlinx.coroutines.flow.Flow

interface CoinRepository {
    fun getCoins(
        currency: String = "usd",
        orderBy: String = "market_cap_desc",
        perPage: Int = 1000,
        page: Int = 1,
        sparkline: Boolean = true
    ): Flow<Result<List<Coin>>>

    /**
     * Get paginated coins from Room database.
     * Data is pre-fetched from Binance and cached in Room.
     */
    fun getPagedCoins(): Flow<PagingData<Coin>>

    fun searchCoins(query: String): Flow<Result<List<Coin>>>

    fun getCoinDetail(coinId: String): Flow<Result<CoinDetail>>

    fun getMarketChart(
        coinId: String,
        currency: String = "usd",
        days: String = "7"
    ): Flow<Result<MarketChart>>

    fun getMarketChartFromBinance(
        symbol: String,
        days: String = "7"
    ): Flow<Result<MarketChart>>

    fun getTrendingCoins(): Flow<Result<List<Coin>>>

    fun getWatchlistCoins(currency: String = "usd"): Flow<Result<List<Coin>>>

    suspend fun addToWatchlist(coinId: String)

    suspend fun removeFromWatchlist(coinId: String)

    fun isInWatchlist(coinId: String): Flow<Boolean>
}
