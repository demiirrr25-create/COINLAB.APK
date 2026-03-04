package com.coinlab.app.data.remote.api

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * v12.0 — Multi-exchange liquidation & futures data API interfaces.
 *
 * Uses public, no-auth-required endpoints from:
 *   - Binance Futures (fapi.binance.com)
 *   - Bybit (api.bybit.com)
 *   - OKX (www.okx.com)
 *   - Bitget (api.bitget.com)
 *   - Gate.io (api.gateio.ws)
 */

// ─── Binance Futures ────────────────────────────────────────────────────

interface BinanceFuturesApi {

    /** Open Interest for a symbol */
    @GET("fapi/v1/openInterest")
    suspend fun getOpenInterest(
        @Query("symbol") symbol: String
    ): BinanceOpenInterest

    /** Open Interest history (kline-style) */
    @GET("futures/data/openInterestHist")
    suspend fun getOpenInterestHistory(
        @Query("symbol") symbol: String,
        @Query("period") period: String = "5m",
        @Query("limit") limit: Int = 30
    ): List<BinanceOIHistory>

    /** Long/Short Ratio (Top Trader Accounts) */
    @GET("futures/data/topLongShortAccountRatio")
    suspend fun getTopLongShortRatio(
        @Query("symbol") symbol: String,
        @Query("period") period: String = "5m",
        @Query("limit") limit: Int = 30
    ): List<BinanceLongShortRatio>

    /** Global Long/Short Account Ratio */
    @GET("futures/data/globalLongShortAccountRatio")
    suspend fun getGlobalLongShortRatio(
        @Query("symbol") symbol: String,
        @Query("period") period: String = "5m",
        @Query("limit") limit: Int = 30
    ): List<BinanceLongShortRatio>

    /** Funding rate */
    @GET("fapi/v1/fundingRate")
    suspend fun getFundingRate(
        @Query("symbol") symbol: String,
        @Query("limit") limit: Int = 1
    ): List<BinanceFundingRate>

    /** Recent liquidation orders (force orders) — public */
    @GET("fapi/v1/allForceOrders")
    suspend fun getForceOrders(
        @Query("symbol") symbol: String? = null,
        @Query("limit") limit: Int = 100
    ): List<BinanceForceOrder>

    /** 24hr ticker */
    @GET("fapi/v1/ticker/24hr")
    suspend fun get24hrTicker(
        @Query("symbol") symbol: String
    ): BinanceFutures24hrTicker

    /** Mark Price & Funding */
    @GET("fapi/v1/premiumIndex")
    suspend fun getPremiumIndex(
        @Query("symbol") symbol: String
    ): BinancePremiumIndex
}

// ─── Bybit ──────────────────────────────────────────────────────────────

interface BybitApi {

    @GET("v5/market/tickers")
    suspend fun getTickers(
        @Query("category") category: String = "linear",
        @Query("symbol") symbol: String? = null
    ): BybitResponse<BybitTickerList>

    @GET("v5/market/open-interest")
    suspend fun getOpenInterest(
        @Query("category") category: String = "linear",
        @Query("symbol") symbol: String,
        @Query("intervalTime") intervalTime: String = "5min",
        @Query("limit") limit: Int = 30
    ): BybitResponse<BybitOIList>

    @GET("v5/market/funding/history")
    suspend fun getFundingHistory(
        @Query("category") category: String = "linear",
        @Query("symbol") symbol: String,
        @Query("limit") limit: Int = 1
    ): BybitResponse<BybitFundingList>
}

// ─── OKX ────────────────────────────────────────────────────────────────

interface OkxApi {

    @GET("api/v5/public/open-interest")
    suspend fun getOpenInterest(
        @Query("instType") instType: String = "SWAP",
        @Query("instId") instId: String
    ): OkxResponse<List<OkxOpenInterest>>

    @GET("api/v5/public/funding-rate")
    suspend fun getFundingRate(
        @Query("instId") instId: String
    ): OkxResponse<List<OkxFundingRate>>

    @GET("api/v5/public/liquidation-orders")
    suspend fun getLiquidationOrders(
        @Query("instType") instType: String = "SWAP",
        @Query("instId") instId: String? = null,
        @Query("state") state: String = "filled",
        @Query("limit") limit: String = "100"
    ): OkxResponse<List<OkxLiquidationWrapper>>

    @GET("api/v5/public/mark-price")
    suspend fun getMarkPrice(
        @Query("instType") instType: String = "SWAP",
        @Query("instId") instId: String
    ): OkxResponse<List<OkxMarkPrice>>
}

// ─── Bitget ─────────────────────────────────────────────────────────────

interface BitgetApi {

    @GET("api/v2/mix/market/open-interest")
    suspend fun getOpenInterest(
        @Query("productType") productType: String = "USDT-FUTURES",
        @Query("symbol") symbol: String
    ): BitgetResponse<BitgetOpenInterest>

    @GET("api/v2/mix/market/current-fund-rate")
    suspend fun getFundingRate(
        @Query("productType") productType: String = "USDT-FUTURES",
        @Query("symbol") symbol: String
    ): BitgetResponse<List<BitgetFundingRate>>

    @GET("api/v2/mix/market/tickers")
    suspend fun getTickers(
        @Query("productType") productType: String = "USDT-FUTURES"
    ): BitgetResponse<List<BitgetTicker>>
}

// ─── Gate.io ────────────────────────────────────────────────────────────

interface GateioApi {

    @GET("api/v4/futures/usdt/contracts/{contract}")
    suspend fun getContract(
        @Path("contract") contract: String
    ): GateioContract

    @GET("api/v4/futures/usdt/liq_orders")
    suspend fun getLiquidationOrders(
        @Query("contract") contract: String? = null,
        @Query("limit") limit: Int = 100
    ): List<GateioLiqOrder>

    @GET("api/v4/futures/usdt/tickers")
    suspend fun getTickers(
        @Query("contract") contract: String? = null
    ): List<GateioTicker>
}

// ═══════════════════════════════════════════════════════════════════════
// DTO Classes
// ═══════════════════════════════════════════════════════════════════════

// ─── Binance DTOs ────────────────────────────────────────────────────

data class BinanceOpenInterest(
    val symbol: String = "",
    val openInterest: String = "0",
    val time: Long = 0
)

data class BinanceOIHistory(
    val symbol: String = "",
    val sumOpenInterest: String = "0",
    val sumOpenInterestValue: String = "0",
    val timestamp: Long = 0
)

data class BinanceLongShortRatio(
    val symbol: String = "",
    val longAccount: String = "0",
    val shortAccount: String = "0",
    val longShortRatio: String = "0",
    val timestamp: Long = 0
)

data class BinanceFundingRate(
    val symbol: String = "",
    val fundingRate: String = "0",
    val fundingTime: Long = 0,
    val markPrice: String = "0"
)

data class BinanceForceOrder(
    val symbol: String = "",
    val price: String = "0",
    val origQty: String = "0",
    val executedQty: String = "0",
    val averagePrice: String = "0",
    val status: String = "",
    val side: String = "", // BUY = short liquidated, SELL = long liquidated
    val time: Long = 0
)

data class BinanceFutures24hrTicker(
    val symbol: String = "",
    val lastPrice: String = "0",
    val priceChangePercent: String = "0",
    val volume: String = "0",
    val quoteVolume: String = "0",
    val highPrice: String = "0",
    val lowPrice: String = "0"
)

data class BinancePremiumIndex(
    val symbol: String = "",
    val markPrice: String = "0",
    val indexPrice: String = "0",
    val lastFundingRate: String = "0",
    val nextFundingTime: Long = 0
)

// ─── Bybit DTOs ──────────────────────────────────────────────────────

data class BybitResponse<T>(
    val retCode: Int = 0,
    val retMsg: String = "",
    val result: T? = null
)

data class BybitTickerList(val list: List<BybitTicker>? = null)
data class BybitOIList(val list: List<BybitOIEntry>? = null)
data class BybitFundingList(val list: List<BybitFunding>? = null)

data class BybitTicker(
    val symbol: String = "",
    val lastPrice: String = "0",
    val openInterest: String = "0",
    val openInterestValue: String = "0",
    val fundingRate: String = "0",
    val price24hPcnt: String = "0",
    val volume24h: String = "0",
    val turnover24h: String = "0",
    val highPrice24h: String = "0",
    val lowPrice24h: String = "0"
)

data class BybitOIEntry(
    val openInterest: String = "0",
    val timestamp: String = "0"
)

data class BybitFunding(
    val symbol: String = "",
    val fundingRate: String = "0",
    val fundingRateTimestamp: String = "0"
)

// ─── OKX DTOs ────────────────────────────────────────────────────────

data class OkxResponse<T>(
    val code: String = "",
    val msg: String = "",
    val data: T? = null
)

data class OkxOpenInterest(
    val instId: String = "",
    val oi: String = "0",
    val oiCcy: String = "0",
    val ts: String = "0"
)

data class OkxFundingRate(
    val instId: String = "",
    val fundingRate: String = "0",
    val nextFundingRate: String = "0",
    val fundingTime: String = "0"
)

data class OkxLiquidationWrapper(
    val details: List<OkxLiqDetail>? = null
)

data class OkxLiqDetail(
    val side: String = "",
    val sz: String = "0",
    val px: String = "0",
    val ts: String = "0"
)

data class OkxMarkPrice(
    val instId: String = "",
    val markPx: String = "0",
    val ts: String = "0"
)

// ─── Bitget DTOs ─────────────────────────────────────────────────────

data class BitgetResponse<T>(
    val code: String = "",
    val msg: String = "",
    val data: T? = null
)

data class BitgetOpenInterest(
    val symbol: String = "",
    val amount: String = "0"
)

data class BitgetFundingRate(
    val symbol: String = "",
    val fundingRate: String = "0"
)

data class BitgetTicker(
    val symbol: String = "",
    val lastPr: String = "0",
    val openUtc: String = "0",
    val chgUtc: String = "0",
    val high24h: String = "0",
    val low24h: String = "0",
    val baseVolume: String = "0",
    val quoteVolume: String = "0",
    val openInterest: String = "0",
    val fundingRate: String = "0"
)

// ─── Gate.io DTOs ────────────────────────────────────────────────────

data class GateioContract(
    val name: String = "",
    val funding_rate: String = "0",
    val mark_price: String = "0",
    val index_price: String = "0",
    val open_interest: String = "0",
    val trade_size: Long = 0
)

data class GateioLiqOrder(
    val time: Long = 0,
    val contract: String = "",
    val size: Long = 0,
    val order_price: String = "0",
    val fill_price: String = "0",
    val left: Long = 0
)

data class GateioTicker(
    val contract: String = "",
    val last: String = "0",
    val open_interest: String = "0",
    val funding_rate: String = "0",
    val volume_24h: String = "0",
    val change_percentage: String = "0",
    val high_24h: String = "0",
    val low_24h: String = "0"
)
