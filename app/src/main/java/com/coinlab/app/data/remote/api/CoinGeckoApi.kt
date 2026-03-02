package com.coinlab.app.data.remote.api

import com.coinlab.app.data.remote.dto.CoinDetailDto
import com.coinlab.app.data.remote.dto.CoinDto
import com.coinlab.app.data.remote.dto.MarketChartDto
import com.coinlab.app.data.remote.dto.SearchResultDto
import com.coinlab.app.data.remote.dto.TrendingDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface CoinGeckoApi {

    @GET("coins/markets")
    suspend fun getCoins(
        @Query("vs_currency") currency: String = "usd",
        @Query("order") orderBy: String = "market_cap_desc",
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1,
        @Query("sparkline") sparkline: Boolean = false,
        @Query("price_change_percentage") priceChangePercentage: String = "7d",
        @Query("ids") ids: String? = null
    ): List<CoinDto>

    @GET("coins/{id}")
    suspend fun getCoinDetail(
        @Path("id") coinId: String,
        @Query("localization") localization: Boolean = false,
        @Query("tickers") tickers: Boolean = false,
        @Query("market_data") marketData: Boolean = true,
        @Query("community_data") communityData: Boolean = false,
        @Query("developer_data") developerData: Boolean = false,
        @Query("sparkline") sparkline: Boolean = true
    ): CoinDetailDto

    @GET("coins/{id}/market_chart")
    suspend fun getMarketChart(
        @Path("id") coinId: String,
        @Query("vs_currency") currency: String = "usd",
        @Query("days") days: String = "7",
        @Query("interval") interval: String? = null
    ): MarketChartDto

    @GET("search")
    suspend fun searchCoins(
        @Query("query") query: String
    ): SearchResultDto

    @GET("search/trending")
    suspend fun getTrending(): TrendingDto

    @GET("simple/price")
    suspend fun getSimplePrice(
        @Query("ids") ids: String,
        @Query("vs_currencies") currencies: String = "usd",
        @Query("include_24hr_change") include24hChange: Boolean = true,
        @Query("include_market_cap") includeMarketCap: Boolean = true
    ): Map<String, Map<String, Double>>

    @GET("coins/{id}/ohlc")
    suspend fun getOhlc(
        @Path("id") coinId: String,
        @Query("vs_currency") currency: String = "usd",
        @Query("days") days: String = "7"
    ): List<List<Double>>

    @GET("global")
    suspend fun getGlobalData(): GlobalDataDto
}

data class GlobalDataDto(
    val data: GlobalDataInner?
)

data class GlobalDataInner(
    val total_market_cap: Map<String, Double>?,
    val total_volume: Map<String, Double>?,
    val market_cap_percentage: Map<String, Double>?,
    val market_cap_change_percentage_24h_usd: Double?,
    val active_cryptocurrencies: Int?
)
