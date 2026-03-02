package com.coinlab.app.data.remote.dto

import com.coinlab.app.domain.model.CoinDetail
import com.coinlab.app.domain.model.CoinLinks
import com.google.gson.annotations.SerializedName

data class CoinDetailDto(
    val id: String,
    val symbol: String,
    val name: String,
    val image: ImageDto?,
    val description: Map<String, String>?,
    val links: LinksDto?,
    val categories: List<String>?,
    @SerializedName("genesis_date") val genesisDate: String?,
    @SerializedName("market_cap_rank") val marketCapRank: Int?,
    @SerializedName("market_data") val marketData: MarketDataDto?
)

data class ImageDto(
    val thumb: String?,
    val small: String?,
    val large: String?
)

data class LinksDto(
    val homepage: List<String>?,
    @SerializedName("blockchain_site") val blockchainSite: List<String>?,
    @SerializedName("subreddit_url") val subredditUrl: String?,
    @SerializedName("twitter_screen_name") val twitterScreenName: String?,
    @SerializedName("telegram_channel_identifier") val telegramChannelId: String?,
    @SerializedName("repos_url") val reposUrl: ReposUrlDto?
)

data class ReposUrlDto(
    val github: List<String>?
)

data class MarketDataDto(
    @SerializedName("current_price") val currentPrice: Map<String, Double>?,
    @SerializedName("market_cap") val marketCap: Map<String, Double>?,
    @SerializedName("total_volume") val totalVolume: Map<String, Double>?,
    @SerializedName("high_24h") val high24h: Map<String, Double>?,
    @SerializedName("low_24h") val low24h: Map<String, Double>?,
    @SerializedName("price_change_24h") val priceChange24h: Double?,
    @SerializedName("price_change_percentage_24h") val priceChangePercentage24h: Double?,
    @SerializedName("price_change_percentage_7d") val priceChangePercentage7d: Double?,
    @SerializedName("price_change_percentage_30d") val priceChangePercentage30d: Double?,
    @SerializedName("price_change_percentage_1y") val priceChangePercentage1y: Double?,
    @SerializedName("circulating_supply") val circulatingSupply: Double?,
    @SerializedName("total_supply") val totalSupply: Double?,
    @SerializedName("max_supply") val maxSupply: Double?,
    val ath: Map<String, Double>?,
    @SerializedName("ath_change_percentage") val athChangePercentage: Map<String, Double>?,
    val atl: Map<String, Double>?,
    @SerializedName("atl_change_percentage") val atlChangePercentage: Map<String, Double>?
)

fun CoinDetailDto.toDomain(): CoinDetail {
    return CoinDetail(
        id = id,
        symbol = symbol.uppercase(),
        name = name,
        image = image?.large ?: "",
        description = description?.get("en") ?: description?.get("tr") ?: "",
        currentPrice = marketData?.currentPrice ?: emptyMap(),
        marketCap = marketData?.marketCap?.mapValues { it.value.toLong() } ?: emptyMap(),
        marketCapRank = marketCapRank ?: 0,
        totalVolume = marketData?.totalVolume ?: emptyMap(),
        high24h = marketData?.high24h ?: emptyMap(),
        low24h = marketData?.low24h ?: emptyMap(),
        priceChange24h = marketData?.priceChange24h ?: 0.0,
        priceChangePercentage24h = marketData?.priceChangePercentage24h ?: 0.0,
        priceChangePercentage7d = marketData?.priceChangePercentage7d ?: 0.0,
        priceChangePercentage30d = marketData?.priceChangePercentage30d ?: 0.0,
        priceChangePercentage1y = marketData?.priceChangePercentage1y ?: 0.0,
        circulatingSupply = marketData?.circulatingSupply ?: 0.0,
        totalSupply = marketData?.totalSupply,
        maxSupply = marketData?.maxSupply,
        ath = marketData?.ath ?: emptyMap(),
        athChangePercentage = marketData?.athChangePercentage ?: emptyMap(),
        atl = marketData?.atl ?: emptyMap(),
        atlChangePercentage = marketData?.atlChangePercentage ?: emptyMap(),
        genesisDate = genesisDate,
        homepageUrl = links?.homepage?.firstOrNull(),
        blockchainSite = links?.blockchainSite?.firstOrNull(),
        categories = categories ?: emptyList(),
        links = CoinLinks(
            homepage = links?.homepage ?: emptyList(),
            blockchain = links?.blockchainSite ?: emptyList(),
            reddit = links?.subredditUrl,
            twitter = links?.twitterScreenName,
            telegram = links?.telegramChannelId,
            github = links?.reposUrl?.github ?: emptyList()
        )
    )
}
