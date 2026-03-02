package com.coinlab.app.data.remote.dto

import com.coinlab.app.domain.model.Coin
import com.google.gson.annotations.SerializedName

data class CoinDto(
    val id: String,
    val symbol: String,
    val name: String,
    val image: String,
    @SerializedName("current_price") val currentPrice: Double?,
    @SerializedName("market_cap") val marketCap: Long?,
    @SerializedName("market_cap_rank") val marketCapRank: Int?,
    @SerializedName("total_volume") val totalVolume: Double?,
    @SerializedName("price_change_percentage_24h") val priceChangePercentage24h: Double?,
    @SerializedName("price_change_percentage_7d_in_currency") val priceChangePercentage7d: Double?,
    @SerializedName("circulating_supply") val circulatingSupply: Double?,
    @SerializedName("total_supply") val totalSupply: Double?,
    @SerializedName("max_supply") val maxSupply: Double?,
    val ath: Double?,
    @SerializedName("ath_change_percentage") val athChangePercentage: Double?,
    @SerializedName("ath_date") val athDate: String?,
    val atl: Double?,
    @SerializedName("atl_change_percentage") val atlChangePercentage: Double?,
    @SerializedName("atl_date") val atlDate: String?,
    @SerializedName("sparkline_in_7d") val sparklineIn7d: SparklineDto?,
    @SerializedName("last_updated") val lastUpdated: String?
)

data class SparklineDto(
    val price: List<Double>?
)

fun CoinDto.toDomain(): Coin {
    return Coin(
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
        sparklineIn7d = sparklineIn7d?.price,
        lastUpdated = lastUpdated ?: ""
    )
}
