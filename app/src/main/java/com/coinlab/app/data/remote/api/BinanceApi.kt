package com.coinlab.app.data.remote.api

import retrofit2.http.GET
import retrofit2.http.Query

interface BinanceApi {

    @GET("api/v3/ticker/24hr")
    suspend fun get24hrTicker(): List<BinanceTicker>

    @GET("api/v3/ticker/24hr")
    suspend fun getTickerBySymbol(
        @Query("symbol") symbol: String
    ): BinanceTicker

    /**
     * Fetch tickers for specific symbols only (~50 instead of 2000+).
     * symbols format: ["BTCUSDT","ETHUSDT",...]
     */
    @GET("api/v3/ticker/24hr")
    suspend fun getTickersBySymbols(
        @Query("symbols") symbols: String
    ): List<BinanceTicker>

    @GET("api/v3/klines")
    suspend fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String = "1h",
        @Query("limit") limit: Int = 168
    ): List<List<Any>>

    @GET("api/v3/exchangeInfo")
    suspend fun getExchangeInfo(): BinanceExchangeInfo
}

data class BinanceTicker(
    val symbol: String? = null,
    val lastPrice: String? = null,
    val priceChangePercent: String? = null,
    val highPrice: String? = null,
    val lowPrice: String? = null,
    val volume: String? = null,
    val quoteVolume: String? = null
)

data class BinanceExchangeInfo(
    val symbols: List<BinanceSymbolInfo>? = null
)

data class BinanceSymbolInfo(
    val symbol: String? = null,
    val status: String? = null,
    val baseAsset: String? = null,
    val quoteAsset: String? = null
)
