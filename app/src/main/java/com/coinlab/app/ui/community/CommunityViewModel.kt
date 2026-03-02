package com.coinlab.app.ui.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coinlab.app.data.preferences.UserPreferences
import com.coinlab.app.data.remote.api.CoinGeckoApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

data class CommunityUiState(
    val selectedTab: CommunityTab = CommunityTab.CHANNELS,
    val feedPosts: List<FeedPost> = emptyList(),
    val signals: List<TradeSignal> = emptyList(),
    val leaderboard: List<LeaderboardEntry> = emptyList(),
    val channels: List<CommunityChannel> = emptyList(),
    val selectedChannel: CommunityChannel? = null,
    val isLoading: Boolean = false,
    val isSignalsLoading: Boolean = false,
    val showCreatePost: Boolean = false
)

enum class CommunityTab(val title: String) {
    CHANNELS("Kanallar"),
    FEED("Akış"),
    SIGNALS("Sinyaller"),
    LEADERBOARD("Lider Tablosu")
}

data class CommunityChannel(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val memberCount: Int,
    val isJoined: Boolean = false,
    val posts: List<FeedPost> = emptyList()
)

data class FeedPost(
    val id: String,
    val author: String,
    val authorAvatar: String? = null,
    val authorBadge: String? = null,
    val content: String,
    val coinTag: String? = null,
    val sentiment: Sentiment? = null,
    val likes: Int = 0,
    val comments: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val isLiked: Boolean = false,
    val channelId: String? = null
)

enum class Sentiment(val emoji: String, val label: String) {
    BULLISH("\uD83D\uDC02", "Yükseliş"),
    BEARISH("\uD83D\uDC3B", "Düşüş"),
    NEUTRAL("\uD83D\uDE10", "Nötr")
}

data class TradeSignal(
    val id: String,
    val author: String,
    val coin: String,
    val coinImage: String? = null,
    val direction: SignalDirection,
    val entryPrice: Double,
    val targetPrice: Double,
    val stopLoss: Double,
    val leverage: Int? = null,
    val confidence: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val pnlPercent: Double? = null,
    val rsiValue: Double? = null,
    val signalSource: String = "API"
)

enum class SignalDirection(val label: String, val emoji: String) {
    LONG("Long", "\uD83D\uDFE2"),
    SHORT("Short", "\uD83D\uDD34")
}

data class LeaderboardEntry(
    val rank: Int,
    val username: String,
    val avatar: String? = null,
    val badge: String? = null,
    val totalPnl: Double,
    val winRate: Double,
    val totalTrades: Int,
    val followers: Int
)

@HiltViewModel
class CommunityViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val coinGeckoApi: CoinGeckoApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommunityUiState())
    val uiState: StateFlow<CommunityUiState> = _uiState.asStateFlow()

    init {
        loadChannels()
        loadSampleData()
        loadApiSignals()
    }

    fun selectTab(tab: CommunityTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun toggleLike(postId: String) {
        _uiState.update { state ->
            state.copy(
                feedPosts = state.feedPosts.map { post ->
                    if (post.id == postId) {
                        post.copy(
                            isLiked = !post.isLiked,
                            likes = if (post.isLiked) post.likes - 1 else post.likes + 1
                        )
                    } else post
                }
            )
        }
    }

    fun joinChannel(channelId: String) {
        _uiState.update { state ->
            state.copy(
                channels = state.channels.map { channel ->
                    if (channel.id == channelId) {
                        channel.copy(
                            isJoined = !channel.isJoined,
                            memberCount = if (channel.isJoined) channel.memberCount - 1 else channel.memberCount + 1
                        )
                    } else channel
                }
            )
        }
    }

    fun selectChannel(channel: CommunityChannel?) {
        _uiState.update { it.copy(selectedChannel = channel) }
    }

    fun showCreatePost() {
        _uiState.update { it.copy(showCreatePost = true) }
    }

    fun hideCreatePost() {
        _uiState.update { it.copy(showCreatePost = false) }
    }

    fun createPost(content: String, coinTag: String?, sentiment: Sentiment?) {
        viewModelScope.launch {
            try {
                val name = userPreferences.displayName.first()
                val avatar = userPreferences.avatarUri.first()
                val channelId = _uiState.value.selectedChannel?.id
                val newPost = FeedPost(
                    id = "post_${System.currentTimeMillis()}",
                    author = name.ifEmpty { "Sen" },
                    authorAvatar = avatar.ifEmpty { null },
                    authorBadge = "OG",
                    content = content,
                    coinTag = coinTag,
                    sentiment = sentiment,
                    timestamp = System.currentTimeMillis(),
                    channelId = channelId
                )
                _uiState.update { state ->
                    state.copy(
                        feedPosts = listOf(newPost) + state.feedPosts,
                        showCreatePost = false
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(showCreatePost = false) }
            }
        }
    }

    private fun loadApiSignals() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isSignalsLoading = true) }
                val signalCoins = listOf(
                    "bitcoin" to "BTC",
                    "ethereum" to "ETH",
                    "solana" to "SOL",
                    "binancecoin" to "BNB",
                    "ripple" to "XRP",
                    "cardano" to "ADA"
                )

                val apiSignals = mutableListOf<TradeSignal>()

                for ((coinId, symbol) in signalCoins) {
                    try {
                        val ohlcData = coinGeckoApi.getOhlc(
                            coinId = coinId,
                            currency = "usd",
                            days = "14"
                        )

                        if (ohlcData.size >= 14) {
                            val closes = ohlcData.map { it.getOrNull(4) ?: it.last() }
                            val rsi = calculateRSI(closes)
                            val currentPrice = closes.last()
                            val prevPrice = closes[closes.size - 2]

                            if (rsi != null) {
                                val signal = when {
                                    rsi < 30 -> {
                                        TradeSignal(
                                            id = "api_${coinId}",
                                            author = "CoinLab AI",
                                            coin = symbol,
                                            direction = SignalDirection.LONG,
                                            entryPrice = currentPrice,
                                            targetPrice = currentPrice * 1.08,
                                            stopLoss = currentPrice * 0.95,
                                            confidence = if (rsi < 20) 5 else 4,
                                            rsiValue = rsi,
                                            signalSource = "RSI Oversold",
                                            timestamp = System.currentTimeMillis()
                                        )
                                    }
                                    rsi > 70 -> {
                                        TradeSignal(
                                            id = "api_${coinId}",
                                            author = "CoinLab AI",
                                            coin = symbol,
                                            direction = SignalDirection.SHORT,
                                            entryPrice = currentPrice,
                                            targetPrice = currentPrice * 0.92,
                                            stopLoss = currentPrice * 1.05,
                                            confidence = if (rsi > 80) 5 else 4,
                                            rsiValue = rsi,
                                            signalSource = "RSI Overbought",
                                            timestamp = System.currentTimeMillis()
                                        )
                                    }
                                    rsi < 45 && currentPrice > prevPrice -> {
                                        TradeSignal(
                                            id = "api_${coinId}",
                                            author = "CoinLab AI",
                                            coin = symbol,
                                            direction = SignalDirection.LONG,
                                            entryPrice = currentPrice,
                                            targetPrice = currentPrice * 1.05,
                                            stopLoss = currentPrice * 0.97,
                                            confidence = 3,
                                            rsiValue = rsi,
                                            signalSource = "RSI Recovery",
                                            timestamp = System.currentTimeMillis()
                                        )
                                    }
                                    rsi > 55 && currentPrice < prevPrice -> {
                                        TradeSignal(
                                            id = "api_${coinId}",
                                            author = "CoinLab AI",
                                            coin = symbol,
                                            direction = SignalDirection.SHORT,
                                            entryPrice = currentPrice,
                                            targetPrice = currentPrice * 0.95,
                                            stopLoss = currentPrice * 1.03,
                                            confidence = 3,
                                            rsiValue = rsi,
                                            signalSource = "RSI Divergence",
                                            timestamp = System.currentTimeMillis()
                                        )
                                    }
                                    else -> null
                                }
                                signal?.let { apiSignals.add(it) }
                            }
                        }
                    } catch (_: Exception) { }
                }

                _uiState.update { state ->
                    state.copy(
                        signals = apiSignals + state.signals.filter { it.signalSource != "API" },
                        isSignalsLoading = false
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isSignalsLoading = false) }
            }
        }
    }

    private fun calculateRSI(prices: List<Double>, period: Int = 14): Double? {
        if (prices.size < period + 1) return null

        val changes = prices.zipWithNext { a, b -> b - a }
        val recentChanges = changes.takeLast(period)

        val gains = recentChanges.filter { it > 0 }
        val losses = recentChanges.filter { it < 0 }.map { abs(it) }

        val avgGain = if (gains.isEmpty()) 0.0 else gains.average()
        val avgLoss = if (losses.isEmpty()) 0.001 else losses.average()

        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }

    private fun loadChannels() {
        val defaultChannels = listOf(
            CommunityChannel(
                id = "general",
                name = "Genel Sohbet",
                description = "Kripto dünyası hakkında genel tartışmalar",
                icon = "\uD83D\uDCAC",
                memberCount = 12450,
                isJoined = true
            ),
            CommunityChannel(
                id = "bitcoin",
                name = "Bitcoin Kulübü",
                description = "Bitcoin analiz ve haberleri",
                icon = "\u20BF",
                memberCount = 8920
            ),
            CommunityChannel(
                id = "altcoins",
                name = "Altcoin Avcıları",
                description = "Altcoin fırsatları ve analizleri",
                icon = "\uD83D\uDC8E",
                memberCount = 6340
            ),
            CommunityChannel(
                id = "defi",
                name = "DeFi & Yield",
                description = "DeFi protokolleri ve yield farming",
                icon = "\uD83C\uDF3E",
                memberCount = 4210
            ),
            CommunityChannel(
                id = "nft",
                name = "NFT & GameFi",
                description = "NFT koleksiyonları ve GameFi projeleri",
                icon = "\uD83C\uDFA8",
                memberCount = 3580
            ),
            CommunityChannel(
                id = "signals",
                name = "Trade Sinyalleri",
                description = "Topluluk trade sinyalleri",
                icon = "\uD83D\uDCCA",
                memberCount = 9870,
                isJoined = true
            ),
            CommunityChannel(
                id = "turkish-market",
                name = "Türk Piyasası",
                description = "Türkiye kripto piyasası tartışmaları",
                icon = "\uD83C\uDDF9\uD83C\uDDF7",
                memberCount = 5620,
                isJoined = true
            )
        )
        _uiState.update { it.copy(channels = defaultChannels) }
    }

    private fun loadSampleData() {
        val samplePosts = listOf(
            FeedPost(
                id = "1",
                author = "CryptoTR",
                authorBadge = "Pro",
                content = "Bitcoin'de 100K üzeri konsolidasyon devam ediyor. Haftalık kapanış kritik.",
                coinTag = "BTC",
                sentiment = Sentiment.BULLISH,
                likes = 42,
                comments = 8,
                timestamp = System.currentTimeMillis() - 3600000,
                channelId = "bitcoin"
            ),
            FeedPost(
                id = "2",
                author = "DeFiMaster",
                authorBadge = "Whale",
                content = "Ethereum Dencun sonrası L2 maliyetleri %90 düştü. Arbitrum ve Optimism yükselişte.",
                coinTag = "ETH",
                sentiment = Sentiment.BULLISH,
                likes = 38,
                comments = 12,
                timestamp = System.currentTimeMillis() - 7200000,
                channelId = "defi"
            ),
            FeedPost(
                id = "3",
                author = "AltcoinHunter",
                content = "Bu hafta DeFi tokenlerinde ciddi hareketlenme bekliyorum. TVL pozitif sinyal veriyor.",
                sentiment = Sentiment.BULLISH,
                likes = 15,
                comments = 3,
                timestamp = System.currentTimeMillis() - 14400000,
                channelId = "altcoins"
            ),
            FeedPost(
                id = "4",
                author = "TraderMehmet",
                authorBadge = "Pro",
                content = "SOL/USDT kısa vadede düşüş riski. RSI aşırı alım bölgesinde. Dikkatli olun.",
                coinTag = "SOL",
                sentiment = Sentiment.BEARISH,
                likes = 22,
                comments = 6,
                timestamp = System.currentTimeMillis() - 28800000,
                channelId = "signals"
            )
        )

        val sampleLeaderboard = listOf(
            LeaderboardEntry(1, "CryptoTR", badge = "Pro", totalPnl = 47250.0, winRate = 72.5, totalTrades = 156, followers = 2840),
            LeaderboardEntry(2, "DeFiMaster", badge = "Whale", totalPnl = 38900.0, winRate = 68.0, totalTrades = 203, followers = 1950),
            LeaderboardEntry(3, "TraderMehmet", badge = "Pro", totalPnl = 31200.0, winRate = 65.3, totalTrades = 178, followers = 1420),
            LeaderboardEntry(4, "AltcoinHunter", totalPnl = 22800.0, winRate = 60.2, totalTrades = 145, followers = 890),
            LeaderboardEntry(5, "BlockchainAli", totalPnl = 18500.0, winRate = 58.7, totalTrades = 120, followers = 650),
            LeaderboardEntry(6, "CryptoAyse", badge = "OG", totalPnl = 15200.0, winRate = 55.0, totalTrades = 98, followers = 520),
            LeaderboardEntry(7, "DeFiTurk", totalPnl = 12000.0, winRate = 53.4, totalTrades = 88, followers = 380),
            LeaderboardEntry(8, "SatoshiTR", totalPnl = 9800.0, winRate = 51.2, totalTrades = 75, followers = 290)
        )

        _uiState.update {
            it.copy(
                feedPosts = samplePosts,
                leaderboard = sampleLeaderboard
            )
        }
    }
}
