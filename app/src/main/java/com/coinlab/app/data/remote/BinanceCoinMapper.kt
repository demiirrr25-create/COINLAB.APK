package com.coinlab.app.data.remote

/**
 * Maps Binance USDT trading pairs to CoinGecko-compatible coin IDs and metadata.
 * This provides offline mapping so we don't depend on CoinGecko for basic coin info.
 */
object BinanceCoinMapper {

    data class CoinMeta(
        val id: String,       // CoinGecko-compatible ID
        val symbol: String,   // e.g. "BTC"
        val name: String,
        val image: String     // CoinGecko CDN image URL
    )

    private val coinMap = mapOf(
        "BTCUSDT" to CoinMeta("bitcoin", "BTC", "Bitcoin", "https://assets.coingecko.com/coins/images/1/large/bitcoin.png"),
        "ETHUSDT" to CoinMeta("ethereum", "ETH", "Ethereum", "https://assets.coingecko.com/coins/images/279/large/ethereum.png"),
        "BNBUSDT" to CoinMeta("binancecoin", "BNB", "BNB", "https://assets.coingecko.com/coins/images/825/large/bnb-icon2_2x.png"),
        "SOLUSDT" to CoinMeta("solana", "SOL", "Solana", "https://assets.coingecko.com/coins/images/4128/large/solana.png"),
        "XRPUSDT" to CoinMeta("ripple", "XRP", "XRP", "https://assets.coingecko.com/coins/images/44/large/xrp-symbol-white-128.png"),
        "ADAUSDT" to CoinMeta("cardano", "ADA", "Cardano", "https://assets.coingecko.com/coins/images/975/large/cardano.png"),
        "DOGEUSDT" to CoinMeta("dogecoin", "DOGE", "Dogecoin", "https://assets.coingecko.com/coins/images/5/large/dogecoin.png"),
        "TRXUSDT" to CoinMeta("tron", "TRX", "TRON", "https://assets.coingecko.com/coins/images/1094/large/tron-logo.png"),
        "DOTUSDT" to CoinMeta("polkadot", "DOT", "Polkadot", "https://assets.coingecko.com/coins/images/12171/large/polkadot.png"),
        "AVAXUSDT" to CoinMeta("avalanche-2", "AVAX", "Avalanche", "https://assets.coingecko.com/coins/images/12559/large/Avalanche_Circle_RedWhite_Trans.png"),
        "LINKUSDT" to CoinMeta("chainlink", "LINK", "Chainlink", "https://assets.coingecko.com/coins/images/877/large/chainlink-new-logo.png"),
        "SHIBUSDT" to CoinMeta("shiba-inu", "SHIB", "Shiba Inu", "https://assets.coingecko.com/coins/images/11939/large/shiba.png"),
        "MATICUSDT" to CoinMeta("matic-network", "MATIC", "Polygon", "https://assets.coingecko.com/coins/images/4713/large/polygon.png"),
        "LTCUSDT" to CoinMeta("litecoin", "LTC", "Litecoin", "https://assets.coingecko.com/coins/images/2/large/litecoin.png"),
        "UNIUSDT" to CoinMeta("uniswap", "UNI", "Uniswap", "https://assets.coingecko.com/coins/images/12504/large/uniswap.png"),
        "ATOMUSDT" to CoinMeta("cosmos", "ATOM", "Cosmos", "https://assets.coingecko.com/coins/images/1481/large/cosmos_hub.png"),
        "NEARUSDT" to CoinMeta("near", "NEAR", "NEAR Protocol", "https://assets.coingecko.com/coins/images/10365/large/near.jpg"),
        "APTUSDT" to CoinMeta("aptos", "APT", "Aptos", "https://assets.coingecko.com/coins/images/26455/large/aptos_round.png"),
        "SUIUSDT" to CoinMeta("sui", "SUI", "Sui", "https://assets.coingecko.com/coins/images/26375/large/sui-ocean-square.png"),
        "ICPUSDT" to CoinMeta("internet-computer", "ICP", "Internet Computer", "https://assets.coingecko.com/coins/images/14495/large/Internet_Computer_logo.png"),
        "ETCUSDT" to CoinMeta("ethereum-classic", "ETC", "Ethereum Classic", "https://assets.coingecko.com/coins/images/453/large/ethereum-classic-logo.png"),
        "FILUSDT" to CoinMeta("filecoin", "FIL", "Filecoin", "https://assets.coingecko.com/coins/images/12817/large/filecoin.png"),
        "XLMUSDT" to CoinMeta("stellar", "XLM", "Stellar", "https://assets.coingecko.com/coins/images/100/large/Stellar_symbol_black_RGB.png"),
        "VETUSDT" to CoinMeta("vechain", "VET", "VeChain", "https://assets.coingecko.com/coins/images/1167/large/VeChain-Logo-768x725.png"),
        "HBARUSDT" to CoinMeta("hedera-hashgraph", "HBAR", "Hedera", "https://assets.coingecko.com/coins/images/3688/large/hbar.png"),
        "AAVEUSDT" to CoinMeta("aave", "AAVE", "Aave", "https://assets.coingecko.com/coins/images/12645/large/aave-token-round.png"),
        "ALGOUSDT" to CoinMeta("algorand", "ALGO", "Algorand", "https://assets.coingecko.com/coins/images/4380/large/download.png"),
        "FTMUSDT" to CoinMeta("fantom", "FTM", "Fantom", "https://assets.coingecko.com/coins/images/4001/large/Fantom_round.png"),
        "SANDUSDT" to CoinMeta("the-sandbox", "SAND", "The Sandbox", "https://assets.coingecko.com/coins/images/12129/large/sandbox_logo.jpg"),
        "MANAUSDT" to CoinMeta("decentraland", "MANA", "Decentraland", "https://assets.coingecko.com/coins/images/878/large/decentraland-mana.png"),
        "AXSUSDT" to CoinMeta("axie-infinity", "AXS", "Axie Infinity", "https://assets.coingecko.com/coins/images/13029/large/axie_infinity_logo.png"),
        "THETAUSDT" to CoinMeta("theta-token", "THETA", "Theta Network", "https://assets.coingecko.com/coins/images/2538/large/theta-token-logo.png"),
        "EOSUSDT" to CoinMeta("eos", "EOS", "EOS", "https://assets.coingecko.com/coins/images/738/large/eos-eos-logo.png"),
        "FLOWUSDT" to CoinMeta("flow", "FLOW", "Flow", "https://assets.coingecko.com/coins/images/13446/large/5f6294c0c7a8cda55cb1c936_Flow_Wordmark.png"),
        "XTZUSDT" to CoinMeta("tezos", "XTZ", "Tezos", "https://assets.coingecko.com/coins/images/976/large/Tezos-logo.png"),
        "INJUSDT" to CoinMeta("injective-protocol", "INJ", "Injective", "https://assets.coingecko.com/coins/images/12882/large/Secondary_Symbol.png"),
        "TIAUSDT" to CoinMeta("celestia", "TIA", "Celestia", "https://assets.coingecko.com/coins/images/31967/large/tia.jpg"),
        "ARBUSDT" to CoinMeta("arbitrum", "ARB", "Arbitrum", "https://assets.coingecko.com/coins/images/16547/large/photo_2023-03-29_21.47.00.jpeg"),
        "OPUSDT" to CoinMeta("optimism", "OP", "Optimism", "https://assets.coingecko.com/coins/images/25244/large/Optimism.png"),
        "MKRUSDT" to CoinMeta("maker", "MKR", "Maker", "https://assets.coingecko.com/coins/images/1364/large/Mark_Maker.png"),
        "GRTUSDT" to CoinMeta("the-graph", "GRT", "The Graph", "https://assets.coingecko.com/coins/images/13397/large/Graph_Token.png"),
        "IMXUSDT" to CoinMeta("immutable-x", "IMX", "Immutable", "https://assets.coingecko.com/coins/images/17233/large/immutableX-symbol-BLK-RGB.png"),
        "RUNEUSDT" to CoinMeta("thorchain", "RUNE", "THORChain", "https://assets.coingecko.com/coins/images/6595/large/Rune200x200.png"),
        "LDOUSDT" to CoinMeta("lido-dao", "LDO", "Lido DAO", "https://assets.coingecko.com/coins/images/13573/large/Lido_DAO.png"),
        "SEIUSDT" to CoinMeta("sei-network", "SEI", "Sei", "https://assets.coingecko.com/coins/images/28205/large/Sei_Logo_-_Transparent.png"),
        "STXUSDT" to CoinMeta("blockstack", "STX", "Stacks", "https://assets.coingecko.com/coins/images/2069/large/Stacks_logo_full.png"),
        "RENDERUSDT" to CoinMeta("render-token", "RENDER", "Render", "https://assets.coingecko.com/coins/images/11636/large/rndr.png"),
        "FETUSDT" to CoinMeta("fetch-ai", "FET", "Fetch.ai", "https://assets.coingecko.com/coins/images/5681/large/Fetch.jpg"),
        "PEPEUSDT" to CoinMeta("pepe", "PEPE", "Pepe", "https://assets.coingecko.com/coins/images/29850/large/pepe-token.jpeg"),
        "WIFUSDT" to CoinMeta("dogwifcoin", "WIF", "dogwifhat", "https://assets.coingecko.com/coins/images/33566/large/dogwifhat.jpg"),
        "POLUSDT" to CoinMeta("matic-network", "POL", "Polygon", "https://assets.coingecko.com/coins/images/4713/large/polygon.png")
    )

    // Reverse map: coinId → Binance symbol
    private val idToSymbol: Map<String, String> = coinMap.entries.associate { (binanceSymbol, meta) ->
        meta.id to binanceSymbol
    }

    // Reverse map: symbol (e.g. "BTC") → Binance symbol (e.g. "BTCUSDT")
    private val symbolToBinance: Map<String, String> = coinMap.entries.associate { (binanceSymbol, meta) ->
        meta.symbol to binanceSymbol
    }

    // Hardcoded approximate market caps for ranking (updated periodically)
    // These are used for initial ranking only; actual prices come from Binance
    private val marketCapRanks: Map<String, Int> = mapOf(
        "bitcoin" to 1, "ethereum" to 2, "binancecoin" to 3, "solana" to 4,
        "ripple" to 5, "cardano" to 6, "dogecoin" to 7, "tron" to 8,
        "polkadot" to 9, "avalanche-2" to 10, "chainlink" to 11, "shiba-inu" to 12,
        "matic-network" to 13, "litecoin" to 14, "uniswap" to 15, "cosmos" to 16,
        "near" to 17, "aptos" to 18, "sui" to 19, "internet-computer" to 20,
        "ethereum-classic" to 21, "filecoin" to 22, "stellar" to 23, "vechain" to 24,
        "hedera-hashgraph" to 25, "aave" to 26, "algorand" to 27, "fantom" to 28,
        "the-sandbox" to 29, "decentraland" to 30, "axie-infinity" to 31,
        "theta-token" to 32, "eos" to 33, "flow" to 34, "tezos" to 35,
        "injective-protocol" to 36, "celestia" to 37, "arbitrum" to 38,
        "optimism" to 39, "maker" to 40, "the-graph" to 41, "immutable-x" to 42,
        "thorchain" to 43, "lido-dao" to 44, "sei-network" to 45, "blockstack" to 46,
        "render-token" to 47, "fetch-ai" to 48, "pepe" to 49, "dogwifcoin" to 50
    )

    fun getMetaByBinanceSymbol(binanceSymbol: String): CoinMeta? = coinMap[binanceSymbol]

    fun getBinanceSymbolByCoinId(coinId: String): String? = idToSymbol[coinId]

    fun getBinanceSymbolBySymbol(symbol: String): String? = symbolToBinance[symbol.uppercase()]

    fun getMarketCapRank(coinId: String): Int = marketCapRanks[coinId] ?: 999

    fun getAllBinanceSymbols(): Set<String> = coinMap.keys

    fun getAllCoinIds(): Set<String> = idToSymbol.keys

    /**
     * Get CoinMeta by CoinGecko-compatible ID
     */
    fun getMetaByCoinId(coinId: String): CoinMeta? {
        val binanceSymbol = idToSymbol[coinId] ?: return null
        return coinMap[binanceSymbol]
    }
}
