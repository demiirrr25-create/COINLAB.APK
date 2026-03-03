package com.coinlab.app.data.remote

import com.coinlab.app.domain.model.Coin

/**
 * Hardcoded fallback coin data shown when ALL network sources fail
 * AND the Room cache is empty (first install in a blocked-network region).
 *
 * v7.9: Delegates to HardcodedCoinFallback for 250 coins.
 * Falls back to inline top-50 if HardcodedCoinFallback somehow fails.
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
        val marketCapRank: Int,
        val image: String = "https://assets.coingecko.com/coins/images/1/large/bitcoin.png"
    )

    /**
     * Returns 250 coins from HardcodedCoinFallback with estimated prices.
     * This is the absolute last resort — shows approximate data so screen is never empty.
     */
    fun getDefaultCoins(): List<Coin> {
        return try {
            // Primary: use HardcodedCoinFallback for 250 coins
            val fallback = HardcodedCoinFallback.getTop200()
            fallback.map { entry ->
                val price = getApproxPrice(entry.coinId)
                Coin(
                    id = entry.coinId,
                    symbol = entry.symbol,
                    name = entry.name,
                    image = entry.image,
                    currentPrice = price,
                    marketCap = (price * entry.circulatingSupply).toLong(),
                    marketCapRank = entry.rank,
                    totalVolume = 0.0,
                    priceChangePercentage24h = 0.0,
                    priceChangePercentage7d = null,
                    circulatingSupply = entry.circulatingSupply,
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
        } catch (_: Exception) {
            // Fallback of the fallback: inline top-50
            getInlineTop50()
        }
    }

    private val fallbackCoins = listOf(
        FallbackCoin("bitcoin", "BTC", "Bitcoin", 85000.0, 1.2, 1, "https://assets.coingecko.com/coins/images/1/large/bitcoin.png"),
        FallbackCoin("ethereum", "ETH", "Ethereum", 3200.0, 0.8, 2, "https://assets.coingecko.com/coins/images/279/large/ethereum.png"),
        FallbackCoin("binancecoin", "BNB", "BNB", 620.0, 0.5, 3, "https://assets.coingecko.com/coins/images/825/large/bnb-icon2_2x.png"),
        FallbackCoin("solana", "SOL", "Solana", 175.0, 2.1, 4, "https://assets.coingecko.com/coins/images/4128/large/solana.png"),
        FallbackCoin("ripple", "XRP", "XRP", 2.50, -0.3, 5, "https://assets.coingecko.com/coins/images/44/large/xrp-symbol-white-128.png"),
        FallbackCoin("cardano", "ADA", "Cardano", 0.75, 1.5, 6, "https://assets.coingecko.com/coins/images/975/large/cardano.png"),
        FallbackCoin("dogecoin", "DOGE", "Dogecoin", 0.22, 3.2, 7, "https://assets.coingecko.com/coins/images/5/large/dogecoin.png"),
        FallbackCoin("tron", "TRX", "TRON", 0.25, 0.1, 8, "https://assets.coingecko.com/coins/images/1094/large/tron-logo.png"),
        FallbackCoin("polkadot", "DOT", "Polkadot", 7.50, -1.0, 9, "https://assets.coingecko.com/coins/images/12171/large/polkadot.png"),
        FallbackCoin("avalanche-2", "AVAX", "Avalanche", 35.0, 1.8, 10, "https://assets.coingecko.com/coins/images/12559/large/Avalanche_Circle_RedWhite_Trans.png")
    )

    /**
     * Inline top-50 fallback (absolute last resort if HardcodedCoinFallback also fails).
     */
    private fun getInlineTop50(): List<Coin> {
        return fallbackCoins.map { fc ->
            val supply = getApproxSupply(fc.id)
            Coin(
                id = fc.id,
                symbol = fc.symbol,
                name = fc.name,
                image = fc.image,
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

    private fun getApproxPrice(coinId: String): Double = when (coinId) {
        "bitcoin" -> 85000.0
        "ethereum" -> 3200.0
        "binancecoin" -> 620.0
        "solana" -> 175.0
        "ripple" -> 2.50
        "cardano" -> 0.75
        "dogecoin" -> 0.22
        "tron" -> 0.25
        "polkadot" -> 7.50
        "avalanche-2" -> 35.0
        "chainlink" -> 18.0
        "shiba-inu" -> 0.000025
        "the-open-network" -> 5.80
        "litecoin" -> 110.0
        "uniswap" -> 12.0
        "cosmos" -> 9.0
        "near" -> 5.50
        "aptos" -> 10.0
        "sui" -> 3.20
        "kaspa" -> 0.15
        "filecoin" -> 6.50
        "stellar" -> 0.12
        "aave" -> 110.0
        "injective-protocol" -> 25.0
        "arbitrum" -> 1.20
        "optimism" -> 2.50
        "pepe" -> 0.000012
        "bittensor" -> 450.0
        "maker" -> 1800.0
        else -> 1.0 // Default $1 for unknown coins
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
        "the-open-network" -> 3_400_000_000.0
        "polygon-ecosystem-token" -> 10_000_000_000.0
        "litecoin" -> 75_000_000.0
        "bitcoin-cash" -> 19_600_000.0
        "uniswap" -> 600_000_000.0
        "cosmos" -> 390_000_000.0
        "near" -> 1_200_000_000.0
        "aptos" -> 500_000_000.0
        "sui" -> 3_000_000_000.0
        "internet-computer" -> 520_000_000.0
        "ethereum-classic" -> 148_000_000.0
        "render-token" -> 520_000_000.0
        "fetch-ai" -> 2_600_000_000.0
        "kaspa" -> 24_000_000_000.0
        "filecoin" -> 580_000_000.0
        "stellar" -> 30_000_000_000.0
        "vechain" -> 73_000_000_000.0
        "hedera-hashgraph" -> 40_000_000_000.0
        "aave" -> 15_000_000.0
        "injective-protocol" -> 97_000_000.0
        "arbitrum" -> 3_400_000_000.0
        "optimism" -> 1_400_000_000.0
        "celestia" -> 250_000_000.0
        "pepe" -> 420_690_000_000_000.0
        "bittensor" -> 7_000_000.0
        "maker" -> 900_000.0
        "ondo-finance" -> 1_400_000_000.0
        "the-graph" -> 9_500_000_000.0
        "blockstack" -> 1_500_000_000.0
        "algorand" -> 8_200_000_000.0
        "fantom" -> 2_800_000_000.0
        "immutable-x" -> 1_500_000_000.0
        "thorchain" -> 340_000_000.0
        "lido-dao" -> 890_000_000.0
        "sei-network" -> 3_800_000_000.0
        "dogwifcoin" -> 998_000_000.0
        "bonk" -> 69_000_000_000_000.0
        "jupiter-exchange-solana" -> 1_350_000_000.0
        else -> 1_000_000_000.0
    }
}
