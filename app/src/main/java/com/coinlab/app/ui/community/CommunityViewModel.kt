package com.coinlab.app.ui.community

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coinlab.app.data.preferences.UserPreferences
import com.coinlab.app.data.remote.DynamicCoinRegistry
import com.coinlab.app.data.remote.api.BinanceApi
import com.coinlab.app.data.remote.firebase.CommunityRealtimeRepository
import com.coinlab.app.data.remote.firebase.FirebaseAuthManager
import com.coinlab.app.data.remote.firebase.PredictionGameRepository
import com.coinlab.app.data.remote.firebase.model.RealtimeComment
import com.coinlab.app.data.remote.firebase.model.RealtimePost
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject
import kotlin.math.abs

// ─── DATA MODELS ─────────────────────────────────────────────────────────

data class CommunityUiState(
    val selectedTab: CommunityTab = CommunityTab.CHANNELS,
    val feedPosts: List<FeedPost> = emptyList(),
    val signals: List<TradeSignal> = emptyList(),
    val leaderboard: List<LeaderboardEntry> = emptyList(),
    val channels: List<CommunityChannel> = emptyList(),
    val selectedChannel: CommunityChannel? = null,
    val isLoading: Boolean = false,
    val isSignalsLoading: Boolean = false,
    val showCreatePost: Boolean = false,
    // v8.2.2 — New fields
    val currentUserId: String = "",
    val currentUserName: String = "",
    val currentUserAvatar: String = "",
    val comments: Map<String, List<CommentItem>> = emptyMap(),
    val expandedCommentPostId: String? = null,
    val isCommenting: Boolean = false,
    val editingPostId: String? = null,
    val editingContent: String = "",
    val showReportDialog: String? = null,
    val showShareDialog: String? = null,
    val mentionSuggestions: List<Pair<String, String>> = emptyList(),
    val selectedImageUri: Uri? = null,
    val isUploading: Boolean = false,
    val errorMessage: String? = null,
    // v8.8 — Post success state
    val postSuccess: Boolean = false
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
    val authorId: String = "",
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
    val channelId: String? = null,
    val imageUrl: String? = null,
    val isEdited: Boolean = false,
    val mentions: List<String> = emptyList(),
    val reportCount: Int = 0,
    val isOwnPost: Boolean = false
)

data class CommentItem(
    val id: String,
    val authorId: String = "",
    val authorName: String,
    val authorAvatar: String = "",
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val mentions: List<String> = emptyList(),
    val isOwnComment: Boolean = false
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

// ─── VIEWMODEL ───────────────────────────────────────────────────────────

@HiltViewModel
class CommunityViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val binanceApi: BinanceApi,
    private val coinRegistry: DynamicCoinRegistry,
    private val communityRepo: CommunityRealtimeRepository,
    private val authManager: FirebaseAuthManager,
    private val predictionGameRepo: PredictionGameRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommunityUiState())
    val uiState: StateFlow<CommunityUiState> = _uiState.asStateFlow()

    init {
        // Auth MUST complete before Firestore listeners start
        viewModelScope.launch {
            val authSuccess = initializeAuth()
            if (authSuccess) {
                // Only start Firestore listeners after auth is ready
                loadChannels()
                loadPosts()
            } else {
                android.util.Log.e("CommunityVM", "Skipping Firestore listeners — auth failed")
            }
            loadApiSignals()
            loadSampleLeaderboard()
        }
    }

    /**
     * Initialize Firebase Auth. Returns true if auth succeeded.
     * Retries up to 3 times on failure before giving up.
     */
    private suspend fun initializeAuth(): Boolean {
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                authManager.ensureAuthenticated()
                val userId = authManager.getCurrentUserId()
                val userName = authManager.getCurrentUserName()
                val userAvatar = authManager.getCurrentUserAvatar()
                android.util.Log.d("CommunityVM", "Auth initialized (attempt ${attempt + 1}): userId=$userId, userName=$userName")
                _uiState.update {
                    it.copy(
                        currentUserId = userId,
                        currentUserName = userName,
                        currentUserAvatar = userAvatar,
                        errorMessage = null
                    )
                }
                return true
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                lastError = e
                android.util.Log.e("CommunityVM", "Auth initialization failed (attempt ${attempt + 1})", e)
                if (attempt < 2) kotlinx.coroutines.delay(1500)
            }
        }
        _uiState.update {
            it.copy(errorMessage = "Kimlik doğrulama başarısız: ${lastError?.message ?: "Bilinmeyen hata"}. Lütfen internet bağlantınızı kontrol edip uygulamayı yeniden başlatın.")
        }
        return false
    }

    fun selectTab(tab: CommunityTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    // ─── POSTS (Firestore) ───────────────────────

    private fun loadPosts() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                communityRepo.getPosts().collect { realtimePosts ->
                    val userId = _uiState.value.currentUserId
                    android.util.Log.d("CommunityVM", "Received ${realtimePosts.size} posts from Realtime DB, userId=$userId")
                    val posts = realtimePosts.map { rp ->
                        FeedPost(
                            id = rp.id,
                            authorId = rp.authorId,
                            author = rp.authorName,
                            authorAvatar = rp.authorAvatar.ifEmpty { null },
                            authorBadge = rp.authorBadge.ifEmpty { null },
                            content = rp.content,
                            coinTag = rp.coinTag.ifEmpty { null },
                            sentiment = try { Sentiment.valueOf(rp.sentiment) } catch (_: Exception) { null },
                            likes = rp.likes.size,
                            comments = rp.commentCount,
                            timestamp = if (rp.createdAt > 0) rp.createdAt else System.currentTimeMillis(),
                            isLiked = rp.likes.containsKey(userId),
                            channelId = rp.channelId.ifEmpty { null },
                            imageUrl = rp.imageUrl.ifEmpty { null },
                            isEdited = rp.isEdited,
                            mentions = rp.mentions,
                            reportCount = rp.reportCount,
                            isOwnPost = rp.authorId == userId
                        )
                    }
                    _uiState.update { it.copy(feedPosts = posts, isLoading = false) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                android.util.Log.e("CommunityVM", "loadPosts failed", e)
                _uiState.update { it.copy(isLoading = false, errorMessage = "Gönderiler yüklenemedi: ${e.message}") }
            }
        }
    }

    fun createPost(content: String, coinTag: String?, sentiment: Sentiment?, imageUri: Uri? = null) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isUploading = true) }

                // Ensure auth is ready before posting
                if (_uiState.value.currentUserId.isEmpty()) {
                    initializeAuth()
                }

                val state = _uiState.value
                if (state.currentUserId.isEmpty()) {
                    _uiState.update {
                        it.copy(isUploading = false, errorMessage = "Gönderi paylaşmak için giriş yapmalısınız.")
                    }
                    return@launch
                }

                val mentions = extractMentions(content)

                val post = RealtimePost(
                    authorId = state.currentUserId,
                    authorName = state.currentUserName,
                    authorAvatar = state.currentUserAvatar,
                    authorBadge = getBadgeForUser(),
                    content = content,
                    coinTag = coinTag ?: "",
                    sentiment = sentiment?.name ?: "",
                    channelId = state.selectedChannel?.id ?: "",
                    mentions = mentions
                )

                communityRepo.createPost(post, imageUri)
                android.util.Log.d("CommunityVM", "Post created successfully")
                _uiState.update {
                    it.copy(
                        showCreatePost = false,
                        selectedImageUri = null,
                        isUploading = false,
                        postSuccess = true
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        errorMessage = "Gönderi paylaşılamadı: ${e.message}"
                    )
                }
            }
        }
    }

    fun toggleLike(postId: String) {
        viewModelScope.launch {
            try {
                val userId = _uiState.value.currentUserId
                if (userId.isEmpty()) return@launch
                communityRepo.toggleLike(postId, userId)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                android.util.Log.e("CommunityVM", "toggleLike failed", e)
            }
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            try {
                communityRepo.deletePost(postId)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(errorMessage = "Gönderi silinemedi: ${e.message}") }
            }
        }
    }

    fun startEditPost(postId: String) {
        val post = _uiState.value.feedPosts.find { it.id == postId }
        if (post != null) {
            _uiState.update {
                it.copy(editingPostId = postId, editingContent = post.content)
            }
        }
    }

    fun updateEditingContent(content: String) {
        _uiState.update { it.copy(editingContent = content) }
    }

    fun saveEditPost() {
        viewModelScope.launch {
            try {
                val postId = _uiState.value.editingPostId ?: return@launch
                val content = _uiState.value.editingContent
                communityRepo.updatePost(postId, content)
                _uiState.update { it.copy(editingPostId = null, editingContent = "") }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(errorMessage = "Düzenleme kaydedilemedi: ${e.message}") }
            }
        }
    }

    fun cancelEditPost() {
        _uiState.update { it.copy(editingPostId = null, editingContent = "") }
    }

    // ─── COMMENTS ────────────────────────────────

    fun toggleComments(postId: String) {
        val currentExpanded = _uiState.value.expandedCommentPostId
        if (currentExpanded == postId) {
            _uiState.update { it.copy(expandedCommentPostId = null) }
        } else {
            _uiState.update { it.copy(expandedCommentPostId = postId) }
            loadComments(postId)
        }
    }

    private fun loadComments(postId: String) {
        viewModelScope.launch {
            try {
                communityRepo.getComments(postId).collect { realtimeComments ->
                    val userId = _uiState.value.currentUserId
                    val comments = realtimeComments.map { rc ->
                        CommentItem(
                            id = rc.id,
                            authorId = rc.authorId,
                            authorName = rc.authorName,
                            authorAvatar = rc.authorAvatar,
                            content = rc.content,
                            timestamp = if (rc.createdAt > 0) rc.createdAt else System.currentTimeMillis(),
                            mentions = rc.mentions,
                            isOwnComment = rc.authorId == userId
                        )
                    }
                    _uiState.update { state ->
                        state.copy(comments = state.comments + (postId to comments))
                    }
                }
            } catch (_: Exception) { }
        }
    }

    fun addComment(postId: String, content: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isCommenting = true) }
                val state = _uiState.value
                val mentions = extractMentions(content)

                val comment = RealtimeComment(
                    authorId = state.currentUserId,
                    authorName = state.currentUserName,
                    authorAvatar = state.currentUserAvatar,
                    content = content,
                    mentions = mentions
                )
                communityRepo.addComment(postId, comment)
                _uiState.update { it.copy(isCommenting = false) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isCommenting = false) }
            }
        }
    }

    fun deleteComment(postId: String, commentId: String) {
        viewModelScope.launch {
            try {
                communityRepo.deleteComment(postId, commentId)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                android.util.Log.e("CommunityVM", "deleteComment failed", e)
            }
        }
    }

    // ─── CHANNELS ────────────────────────────────

    private fun loadChannels() {
        viewModelScope.launch {
            try {
                communityRepo.initializeDefaultChannels()
                communityRepo.getChannels().collect { realtimeChannels ->
                    val userId = _uiState.value.currentUserId
                    android.util.Log.d("CommunityVM", "Received ${realtimeChannels.size} channels")
                    val channels = realtimeChannels.map { rc ->
                        CommunityChannel(
                            id = rc.id,
                            name = rc.name,
                            description = rc.description,
                            icon = rc.icon,
                            memberCount = rc.memberCount,
                            isJoined = rc.members.containsKey(userId)
                        )
                    }
                    _uiState.update { it.copy(channels = channels) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                android.util.Log.e("CommunityVM", "loadChannels failed", e)
            }
        }
    }

    fun joinChannel(channelId: String) {
        viewModelScope.launch {
            try {
                val userId = _uiState.value.currentUserId
                if (userId.isEmpty()) return@launch
                communityRepo.toggleChannelMembership(channelId, userId)
            } catch (_: Exception) { }
        }
    }

    fun selectChannel(channel: CommunityChannel?) {
        _uiState.update { it.copy(selectedChannel = channel) }
    }

    // ─── REPORT ──────────────────────────────────

    fun showReportDialog(postId: String) {
        _uiState.update { it.copy(showReportDialog = postId) }
    }

    fun hideReportDialog() {
        _uiState.update { it.copy(showReportDialog = null) }
    }

    fun reportPost(reason: String) {
        viewModelScope.launch {
            try {
                val postId = _uiState.value.showReportDialog ?: return@launch
                val userId = _uiState.value.currentUserId
                communityRepo.reportPost(postId, userId, reason)
                _uiState.update { it.copy(showReportDialog = null) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(showReportDialog = null, errorMessage = "Rapor gönderilemedi") }
            }
        }
    }

    // ─── SHARE ───────────────────────────────────

    fun showShareDialog(postId: String) {
        _uiState.update { it.copy(showShareDialog = postId) }
    }

    fun hideShareDialog() {
        _uiState.update { it.copy(showShareDialog = null) }
    }

    // ─── MENTION ─────────────────────────────────

    fun searchMentions(query: String) {
        viewModelScope.launch {
            try {
                if (query.length < 2) {
                    _uiState.update { it.copy(mentionSuggestions = emptyList()) }
                    return@launch
                }
                val users = communityRepo.searchUsers(query)
                _uiState.update { it.copy(mentionSuggestions = users) }
            } catch (_: Exception) { }
        }
    }

    fun clearMentionSuggestions() {
        _uiState.update { it.copy(mentionSuggestions = emptyList()) }
    }

    // ─── IMAGE ───────────────────────────────────

    fun selectImage(uri: Uri?) {
        _uiState.update { it.copy(selectedImageUri = uri) }
    }

    fun showCreatePost() {
        _uiState.update { it.copy(showCreatePost = true, selectedImageUri = null) }
    }

    fun hideCreatePost() {
        _uiState.update { it.copy(showCreatePost = false, selectedImageUri = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearPostSuccess() {
        _uiState.update { it.copy(postSuccess = false) }
    }

    // ─── SIGNALS (API) ──────────────────────────

    private fun loadApiSignals() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isSignalsLoading = true) }
                val signalCoins = listOf(
                    "bitcoin" to "BTC", "ethereum" to "ETH", "solana" to "SOL",
                    "binancecoin" to "BNB", "ripple" to "XRP", "cardano" to "ADA"
                )
                val apiSignals = mutableListOf<TradeSignal>()
                supervisorScope {
                    val deferredSignals = signalCoins.map { (coinId, symbol) ->
                        async {
                            try {
                                val binanceSymbol = coinRegistry.getBinanceSymbolByCoinId(coinId) ?: return@async null
                                val klines = binanceApi.getKlines(symbol = binanceSymbol, interval = "1d", limit = 14)
                                if (klines.size >= 14) {
                                    val closes = klines.map { (it.getOrNull(4) as? String)?.toDoubleOrNull() ?: 0.0 }
                                    val rsi = calculateRSI(closes)
                                    val currentPrice = closes.last()
                                    val prevPrice = closes[closes.size - 2]
                                    if (rsi != null) {
                                        when {
                                            rsi < 30 -> TradeSignal(id = "api_$coinId", author = "CoinLab AI", coin = symbol, direction = SignalDirection.LONG, entryPrice = currentPrice, targetPrice = currentPrice * 1.08, stopLoss = currentPrice * 0.95, confidence = if (rsi < 20) 5 else 4, rsiValue = rsi, signalSource = "RSI Oversold")
                                            rsi > 70 -> TradeSignal(id = "api_$coinId", author = "CoinLab AI", coin = symbol, direction = SignalDirection.SHORT, entryPrice = currentPrice, targetPrice = currentPrice * 0.92, stopLoss = currentPrice * 1.05, confidence = if (rsi > 80) 5 else 4, rsiValue = rsi, signalSource = "RSI Overbought")
                                            rsi < 45 && currentPrice > prevPrice -> TradeSignal(id = "api_$coinId", author = "CoinLab AI", coin = symbol, direction = SignalDirection.LONG, entryPrice = currentPrice, targetPrice = currentPrice * 1.05, stopLoss = currentPrice * 0.97, confidence = 3, rsiValue = rsi, signalSource = "RSI Recovery")
                                            rsi > 55 && currentPrice < prevPrice -> TradeSignal(id = "api_$coinId", author = "CoinLab AI", coin = symbol, direction = SignalDirection.SHORT, entryPrice = currentPrice, targetPrice = currentPrice * 0.95, stopLoss = currentPrice * 1.03, confidence = 3, rsiValue = rsi, signalSource = "RSI Divergence")
                                            else -> null
                                        }
                                    } else null
                                } else null
                            } catch (_: Exception) { null }
                        }
                    }
                    apiSignals.addAll(deferredSignals.awaitAll().filterNotNull())
                }
                _uiState.update { state ->
                    state.copy(signals = apiSignals + state.signals.filter { it.signalSource != "API" }, isSignalsLoading = false)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
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

    private fun loadSampleLeaderboard() {
        viewModelScope.launch {
            try {
                predictionGameRepo.getLeaderboard().collect { scores ->
                    val entries = scores.mapIndexed { index, score ->
                        LeaderboardEntry(
                            rank = index + 1,
                            username = score.userName.ifEmpty { "Anonim" },
                            badge = when {
                                score.totalScore > 500 -> "Pro"
                                score.totalScore > 200 -> "OG"
                                score.streak > 5 -> "Streak"
                                else -> null
                            },
                            totalPnl = score.totalScore.toDouble(),
                            winRate = score.accuracy,
                            totalTrades = score.totalCount,
                            followers = 0
                        )
                    }
                    _uiState.update { it.copy(leaderboard = entries.ifEmpty { fallbackLeaderboard() }) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                android.util.Log.e("CommunityVM", "loadLeaderboard failed", e)
                _uiState.update { it.copy(leaderboard = fallbackLeaderboard()) }
            }
        }
    }

    private fun fallbackLeaderboard(): List<LeaderboardEntry> = listOf(
        LeaderboardEntry(1, "CryptoTR", badge = "Pro", totalPnl = 47250.0, winRate = 72.5, totalTrades = 156, followers = 2840),
        LeaderboardEntry(2, "DeFiMaster", badge = "Whale", totalPnl = 38900.0, winRate = 68.0, totalTrades = 203, followers = 1950),
        LeaderboardEntry(3, "TraderMehmet", badge = "Pro", totalPnl = 31200.0, winRate = 65.3, totalTrades = 178, followers = 1420),
        LeaderboardEntry(4, "AltcoinHunter", totalPnl = 22800.0, winRate = 60.2, totalTrades = 145, followers = 890),
        LeaderboardEntry(5, "BlockchainAli", totalPnl = 18500.0, winRate = 58.7, totalTrades = 120, followers = 650)
    )

    private fun extractMentions(content: String): List<String> {
        val mentionRegex = Regex("@(\\w+)")
        return mentionRegex.findAll(content).map { it.groupValues[1] }.toList()
    }

    private suspend fun getBadgeForUser(): String {
        return try {
            val name = authManager.getCurrentUserName()
            when {
                name.contains("Pro", ignoreCase = true) -> "Pro"
                name.contains("Whale", ignoreCase = true) -> "Whale"
                else -> "OG"
            }
        } catch (_: Exception) { "OG" }
    }
}
