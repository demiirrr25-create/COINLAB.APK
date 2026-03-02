package com.coinlab.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class SearchResultDto(
    val coins: List<SearchCoinDto>?
)

data class SearchCoinDto(
    val id: String,
    val name: String,
    val symbol: String,
    val thumb: String?,
    val large: String?,
    @SerializedName("market_cap_rank") val marketCapRank: Int?
)

data class TrendingDto(
    val coins: List<TrendingCoinItemDto>?
)

data class TrendingCoinItemDto(
    val item: TrendingCoinDto
)

data class TrendingCoinDto(
    val id: String,
    val name: String,
    val symbol: String,
    val thumb: String?,
    val large: String?,
    @SerializedName("market_cap_rank") val marketCapRank: Int?,
    val score: Int?
)
