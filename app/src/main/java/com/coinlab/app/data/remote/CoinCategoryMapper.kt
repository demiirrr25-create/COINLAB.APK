package com.coinlab.app.data.remote

/**
 * Comprehensive coin category mapping for CoinLab v7.7+.
 * Maps CoinGecko coin IDs to their primary category.
 * Used by MarketScreen for category-based filtering.
 *
 * Categories:
 * - LAYER_1: Base blockchain protocols (BTC, ETH, SOL, ADA, etc.)
 * - LAYER_2: Scaling solutions built on top of L1s (ARB, OP, MATIC, etc.)
 * - DEFI: Decentralized finance protocols (UNI, AAVE, CRV, etc.)
 * - MEME: Community/meme coins (DOGE, SHIB, PEPE, etc.)
 * - NFT_GAMING: NFT platforms and blockchain games (AXS, SAND, IMX, etc.)
 * - AI: AI/ML focused crypto projects (FET, RENDER, TAO, etc.)
 * - INFRASTRUCTURE: Oracles, storage, middleware (LINK, FIL, AR, etc.)
 * - EXCHANGE: Exchange tokens (BNB, CRO, etc.)
 * - PRIVACY: Privacy-focused coins (ZEC, DASH, etc.)
 * - STABLECOIN_DEFI: Stablecoin ecosystem coins (MKR, LQTY, etc.)
 */
object CoinCategoryMapper {

    enum class CoinCategory(val displayNameTr: String, val displayNameEn: String, val emoji: String) {
        ALL("Tümü", "All", "🌐"),
        LAYER_1("Layer 1", "Layer 1", "⛓️"),
        LAYER_2("Layer 2", "Layer 2", "🔗"),
        DEFI("DeFi", "DeFi", "🏦"),
        MEME("Meme", "Meme", "🐕"),
        NFT_GAMING("NFT/Oyun", "NFT/Gaming", "🎮"),
        AI("Yapay Zeka", "AI", "🤖"),
        INFRASTRUCTURE("Altyapı", "Infra", "🔧"),
        EXCHANGE("Borsa", "Exchange", "💱"),
        PRIVACY("Gizlilik", "Privacy", "🔒"),
        STABLECOIN_DEFI("Stabil DeFi", "Stable DeFi", "💵");
    }

    private val categoryMap: Map<String, CoinCategory> = buildMap {
        // ===== LAYER 1 — Base blockchain protocols =====
        val layer1 = listOf(
            "bitcoin", "ethereum", "solana", "cardano", "polkadot",
            "avalanche-2", "tron", "cosmos", "near", "aptos",
            "sui", "internet-computer", "ethereum-classic", "stellar",
            "hedera-hashgraph", "algorand", "fantom", "tezos",
            "eos", "flow", "neo", "waves", "zilliqa", "qtum",
            "iota", "icon", "nervos-network", "conflux-token",
            "astar", "oasis-network", "terra-luna-2", "terra-luna",
            "elrond-erd-2", "bitcoin-cash", "litecoin", "ravencoin",
            "digibyte", "ecash", "kaspa", "sei-network", "celestia",
            "dymension", "mina-protocol", "celo", "iotex",
            "vechain", "theta-token", "ontology", "iostoken",
            "stacks", "kava", "saga-2", "the-open-network",
            "mantra-dao", "vanar-chain", "wax"
        )
        layer1.forEach { put(it, CoinCategory.LAYER_1) }

        // ===== LAYER 2 — Scaling & rollup solutions =====
        val layer2 = listOf(
            "arbitrum", "optimism", "polygon-ecosystem-token",
            "matic-network", "starknet", "zksync", "scroll",
            "immutable-x", "skale", "moonbeam", "manta-network",
            "altlayer", "loopring", "boba-network", "xai-blockchain",
            "axelar", "omni-network"
        )
        layer2.forEach { put(it, CoinCategory.LAYER_2) }

        // ===== DEFI — Decentralized Finance protocols =====
        val defi = listOf(
            "uniswap", "aave", "maker", "curve-dao-token",
            "compound-governance-token", "lido-dao", "thorchain",
            "the-graph", "1inch", "sushi", "balancer",
            "yearn-finance", "synthetix-network-token", "havven",
            "pancakeswap-token", "dydx-chain", "pendle",
            "radiant-capital", "convex-finance", "liquity",
            "stargate-finance", "joe", "venus", "linear-finance",
            "coin98", "uma", "api3", "woo-network", "renzo",
            "ether-fi", "jito-governance-token", "ethena",
            "bella-protocol", "alpha-finance", "leverfi",
            "perpetual-protocol", "truefi", "0x",
            "ondo-finance", "hashflow", "reserve-rights-token"
        )
        defi.forEach { put(it, CoinCategory.DEFI) }

        // ===== MEME — Community/meme coins =====
        val meme = listOf(
            "dogecoin", "shiba-inu", "pepe", "dogwifcoin",
            "bonk", "floki", "book-of-meme", "memecoin-2",
            "constitutiondao", "hamster-kombat", "dogs-2",
            "catizen", "notcoin", "spell-token", "sats-ordinals"
        )
        meme.forEach { put(it, CoinCategory.MEME) }

        // ===== NFT/GAMING — NFT platforms & blockchain games =====
        val nftGaming = listOf(
            "axie-infinity", "the-sandbox", "decentraland",
            "gala", "apecoin", "enjincoin", "magic",
            "immutable-x", "blur", "binaryx",
            "pixels", "beam-2", "alien-worlds", "mines-of-dalarnia",
            "highstreet", "fusionist", "smooth-love-potion",
            "adventure-gold", "hooked-protocol", "nfprompt",
            "stepn", "portal-2", "superrare"
        )
        nftGaming.forEach { putIfAbsent(it, CoinCategory.NFT_GAMING) }

        // ===== AI — Artificial Intelligence projects =====
        val ai = listOf(
            "fetch-ai", "render-token", "bittensor",
            "worldcoin-wld", "arkham", "numeraire",
            "sleepless-ai", "io-net", "pyth-network",
            "eigenlayer", "measurable-data-token", "automata"
        )
        ai.forEach { putIfAbsent(it, CoinCategory.AI) }

        // ===== INFRASTRUCTURE — Oracles, storage, middleware =====
        val infra = listOf(
            "chainlink", "filecoin", "arweave", "storj",
            "ankr", "celer-network", "ssv-network",
            "space-id", "mask-network", "cartesi",
            "origin-protocol", "gitcoin", "audius",
            "radicle", "selfkey", "dock", "vite",
            "request-network", "nkn", "band-protocol",
            "bluzelle", "litentry", "safepal", "iexec-rlc",
            "power-ledger", "polymesh", "wormhole",
            "acala", "reef", "alchemy-pay", "amber",
            "openanx", "arpa", "vidt-dao", "phoenix-global",
            "flamingo-finance", "airdao", "aevo", "tellor",
            "cyberconnect", "lista-dao", "bouncebit",
            "banana-gun", "gravity-finance"
        )
        infra.forEach { putIfAbsent(it, CoinCategory.INFRASTRUCTURE) }

        // ===== EXCHANGE — Exchange/CEX tokens =====
        val exchange = listOf(
            "binancecoin", "trust-wallet-token", "chiliz",
            "swipe", "gas", "holotoken"
        )
        exchange.forEach { putIfAbsent(it, CoinCategory.EXCHANGE) }

        // ===== PRIVACY — Privacy-focused =====
        val privacy = listOf(
            "zcash", "dash", "horizen", "basic-attention-token",
            "secret", "threshold-network-token"
        )
        privacy.forEach { putIfAbsent(it, CoinCategory.PRIVACY) }

        // ===== STABLECOIN DEFI — Stablecoin ecosystem =====
        val stableDefi = listOf(
            "dai", "frax-share"
        )
        stableDefi.forEach { putIfAbsent(it, CoinCategory.STABLECOIN_DEFI) }

        // Injective, Sei → already assigned as L1
        // Some coins fit multiple categories — primary category wins
    }

    /**
     * Get the primary category for a coin.
     * Returns LAYER_1 as default for unknown coins (most traded coins are L1s).
     */
    fun getCategory(coinId: String): CoinCategory {
        return categoryMap[coinId] ?: CoinCategory.LAYER_1
    }

    /**
     * Get all coins belonging to a specific category.
     */
    fun getCoinIdsByCategory(category: CoinCategory): Set<String> {
        if (category == CoinCategory.ALL) return emptySet()
        return categoryMap.filterValues { it == category }.keys
    }

    /**
     * Get all available categories (excluding ALL).
     */
    fun getAllCategories(): List<CoinCategory> = CoinCategory.entries

    /**
     * Check if a coin belongs to a specific category.
     */
    fun isInCategory(coinId: String, category: CoinCategory): Boolean {
        if (category == CoinCategory.ALL) return true
        return getCategory(coinId) == category
    }
}
