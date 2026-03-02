package com.coinlab.app.data.remote

import com.coinlab.app.domain.model.Coin

/**
 * Hardcoded fallback coin data shown when ALL network sources fail
 * AND the Room cache is empty (first install in a blocked-network region).
 *
 * Prices are approximate (March 2026 estimates). The UI should indicate
 * that data may be outdated when using this fallback.
 *
 * This ensures users NEVER see "Yükleme hatası" on first launch.
 */
object StaticFallbackData {

    private data class FallbackCoin(
        val id: String,
        val symbol: String,
        val name: String,
        val price: Double,
        val change24h: Double,
        val marketCapRank: Int
    )

    private val fallbackCoins = listOf(
        FallbackCoin("bitcoin", "BTC", "Bitcoin", 85000.0, 1.2, 1),
        FallbackCoin("ethereum", "ETH", "Ethereum", 3200.0, 0.8, 2),
        FallbackCoin("binancecoin", "BNB", "BNB", 620.0, 0.5, 3),
        FallbackCoin("solana", "SOL", "Solana", 175.0, 2.1, 4),
        FallbackCoin("ripple", "XRP", "XRP", 2.50, -0.3, 5),
        FallbackCoin("cardano", "ADA", "Cardano", 0.75, 1.5, 6),
        FallbackCoin("dogecoin", "DOGE", "Dogecoin", 0.22, 3.2, 7),
        FallbackCoin("tron", "TRX", "TRON", 0.25, 0.1, 8),
        FallbackCoin("polkadot", "DOT", "Polkadot", 7.50, -1.0, 9),
        FallbackCoin("avalanche-2", "AVAX", "Avalanche", 35.0, 1.8, 10),
        FallbackCoin("chainlink", "LINK", "Chainlink", 18.0, 0.9, 11),
        FallbackCoin("shiba-inu", "SHIB", "Shiba Inu", 0.000025, 2.5, 12),
        FallbackCoin("polygon-ecosystem-token", "POL", "Polygon", 0.45, -0.5, 13),
        FallbackCoin("litecoin", "LTC", "Litecoin", 110.0, 0.3, 14),
        FallbackCoin("uniswap", "UNI", "Uniswap", 12.0, 1.1, 15),
        FallbackCoin("cosmos", "ATOM", "Cosmos", 9.0, -0.7, 16),
        FallbackCoin("near", "NEAR", "NEAR Protocol", 5.50, 2.0, 17),
        FallbackCoin("aptos", "APT", "Aptos", 10.0, 1.3, 18),
        FallbackCoin("sui", "SUI", "Sui", 3.20, 2.8, 19),
        FallbackCoin("internet-computer", "ICP", "Internet Computer", 12.0, -0.2, 20)
    )

    /**
     * Returns a list of top 20 coins with approximate/static prices.
     * Images are sourced from BinanceCoinMapper (CoinGecko CDN).
     * `lastUpdated` is set to "static" to allow UI to detect stale data.
     */
    fun getDefaultCoins(): List<Coin> {
        return fallbackCoins.map { fc ->
            val meta = BinanceCoinMapper.getMetaByCoinId(fc.id)
            val supply = getApproxSupply(fc.id)
            Coin(
                id = fc.id,
                symbol = fc.symbol,
                name = fc.name,
                image = meta?.image ?: "",
                currentPrice = fc.price,
                marketCap = (fc.price * supply).toLong(),
                marketCapRank = fc.marketCapRank,
                totalVolume = 0.0,
                priceChangePercentage24h = fc.change24h,
                priceChangePercentage7d = null,
                circulatingSupply = supply,
                totalSupply = null,
                maxSupply = null,
                ath = 0.0,
                athChangePercentage = 0.0,
                athDate = "",
                atl = 0.0,
                atlChangePercentage = 0.0,
                atlDate = "",
                sparklineIn7d = null,
                lastUpdated = "static"
            )
        }
    }

    private fun getApproxSupply(coinId: String): Double = when (coinId) {
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
        "polygon-ecosystem-token" -> 10_000_000_000.0
        "litecoin" -> 75_000_000.0
        "uniswap" -> 600_000_000.0
        "cosmos" -> 390_000_000.0
        "near" -> 1_200_000_000.0
        "aptos" -> 500_000_000.0
        "sui" -> 3_000_000_000.0
        "internet-computer" -> 520_000_000.0
        else -> 1_000_000_000.0
    }
}
