package com.coinlab.app.domain.model

data class CoinDetail(
    val id: String,
    val symbol: String,
    val name: String,
    val image: String,
    val description: String,
    val currentPrice: Map<String, Double>,
    val marketCap: Map<String, Long>,
    val marketCapRank: Int,
    val totalVolume: Map<String, Double>,
    val high24h: Map<String, Double>,
    val low24h: Map<String, Double>,
    val priceChange24h: Double,
    val priceChangePercentage24h: Double,
    val priceChangePercentage7d: Double,
    val priceChangePercentage30d: Double,
    val priceChangePercentage1y: Double,
    val circulatingSupply: Double,
    val totalSupply: Double?,
    val maxSupply: Double?,
    val ath: Map<String, Double>,
    val athChangePercentage: Map<String, Double>,
    val atl: Map<String, Double>,
    val atlChangePercentage: Map<String, Double>,
    val genesisDate: String?,
    val homepageUrl: String?,
    val blockchainSite: String?,
    val categories: List<String>,
    val links: CoinLinks
)

data class CoinLinks(
    val homepage: List<String>,
    val blockchain: List<String>,
    val reddit: String?,
    val twitter: String?,
    val telegram: String?,
    val github: List<String>
)
