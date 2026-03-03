package com.coinlab.app.ui.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.coinlab.app.data.preferences.UserPreferences
import com.coinlab.app.data.remote.api.CoinGeckoApi
import com.coinlab.app.data.remote.CoinCategoryMapper
import com.coinlab.app.data.remote.CoinCategoryMapper.CoinCategory
import com.coinlab.app.data.remote.websocket.SharedWebSocketManager
import com.coinlab.app.domain.model.Coin
import com.coinlab.app.domain.repository.CoinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MarketUiState(
    val coins: List<Coin> = emptyList(),
    val filteredCoins: List<Coin> = emptyList(),
    val watchlistCoins: List<Coin> = emptyList(),
    val trendingCoins: List<Coin> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val selectedTab: MarketTab = MarketTab.ALL,
    val selectedCategory: CoinCategory = CoinCategory.ALL,
    val currency: String = "USD",
    val sortOrder: String = "market_cap_desc",
    val isWebSocketConnected: Boolean = false,
    val totalCoinCount: Int = 0,
    val categoryCounts: Map<CoinCategory, Int> = emptyMap(),
    val totalMarketCap: Double = 0.0,
    val btcDominance: Double = 0.0,
    val marketCapChange24h: Double = 0.0
)

enum class MarketTab {
    ALL, WATCHLIST, TRENDING, TOP_GAINERS, TOP_LOSERS
}

@HiltViewModel
class MarketViewModel @Inject constructor(
    private val coinRepository: CoinRepository,
    private val userPreferences: UserPreferences,
    private val sharedWebSocketManager: SharedWebSocketManager,
    private val coinGeckoApi: CoinGeckoApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(MarketUiState())
    val uiState: StateFlow<MarketUiState> = _uiState.asStateFlow()

    /**
     * Paged coins flow for infinite scroll.
     * Reads from Room in pages of 50 items.
     */
    val pagedCoins = coinRepository.getPagedCoins().cachedIn(viewModelScope)

    private var webSocketJob: Job? = null
    private var loadCoinsJob: Job? = null
    private var loadWatchlistJob: Job? = null
    private var loadTrendingJob: Job? = null

    // Index map for O(1) WebSocket updates instead of O(n)
    private var symbolIndexMap: Map<String, Int> = emptyMap()

    // Currently visible symbols for lazy WebSocket
    private var currentVisibleSymbols: List<String> = emptyList()

    init {
        viewModelScope.launch {
            try {
                val currency = userPreferences.currency.first()
                _uiState.update { it.copy(currency = currency) }
                // Load coins and global market data in parallel for faster startup
                kotlinx.coroutines.supervisorScope {
                    launch { loadCoins() }
                    launch { loadGlobalMarketData() }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun loadGlobalMarketData() {
        viewModelScope.launch {
            try {
                val globalData = coinGeckoApi.getGlobalData()
                val data = globalData.data ?: return@launch
                _uiState.update {
                    it.copy(
                        totalMarketCap = data.total_market_cap?.get("usd") ?: 0.0,
                        btcDominance = data.market_cap_percentage?.get("btc") ?: 0.0,
                        marketCapChange24h = data.market_cap_change_percentage_24h_usd ?: 0.0
                    )
                }
            } catch (_: Exception) {}
        }
    }

    fun loadCoins() {
        loadCoinsJob?.cancel()
        loadCoinsJob = viewModelScope.launch {
            try {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = coinRepository.getCoins(
                currency = "usd",
                orderBy = _uiState.value.sortOrder,
                perPage = 1000
            ).first()
            result.fold(
                onSuccess = { coins ->
                    // Build index map for O(1) WebSocket updates
                    symbolIndexMap = coins.withIndex()
                        .associate { (i, coin) -> coin.symbol.lowercase() to i }
                    // Compute category counts for UI badges
                    val counts = mutableMapOf<CoinCategory, Int>()
                    coins.forEach { coin ->
                        val cat = CoinCategoryMapper.getCategory(coin.id)
                        counts[cat] = (counts[cat] ?: 0) + 1
                    }
                    _uiState.update {
                        it.copy(
                            coins = coins,
                            filteredCoins = filterCoins(coins, it.searchQuery, it.selectedTab, it.selectedCategory),
                            isLoading = false,
                            isRefreshing = false,
                            error = null,
                            totalCoinCount = coins.size,
                            categoryCounts = counts
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = error.localizedMessage ?: "Bir hata oluştu"
                        )
                    }
                }
            )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = e.message) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isRefreshing = true) }
                loadCoins()
                if (_uiState.value.selectedTab == MarketTab.WATCHLIST) loadWatchlist()
                if (_uiState.value.selectedTab == MarketTab.TRENDING) loadTrending()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                filteredCoins = filterCoins(it.coins, query, it.selectedTab, it.selectedCategory)
            )
        }
    }

    fun onTabSelected(tab: MarketTab) {
        _uiState.update {
            it.copy(
                selectedTab = tab,
                selectedCategory = CoinCategory.ALL, // Reset category when switching tabs
                filteredCoins = filterCoins(it.coins, it.searchQuery, tab, CoinCategory.ALL)
            )
        }
        when (tab) {
            MarketTab.WATCHLIST -> loadWatchlist()
            MarketTab.TRENDING -> loadTrending()
            else -> {}
        }
    }

    fun onCategorySelected(category: CoinCategory) {
        _uiState.update {
            it.copy(
                selectedCategory = category,
                selectedTab = MarketTab.ALL, // Category filter works under ALL tab
                filteredCoins = filterCoins(it.coins, it.searchQuery, MarketTab.ALL, category)
            )
        }
    }

    fun onSortOrderChange(sortOrder: String) {
        _uiState.update { it.copy(sortOrder = sortOrder) }
        loadCoins()
    }

    private fun loadWatchlist() {
        loadWatchlistJob?.cancel()
        loadWatchlistJob = viewModelScope.launch {
            try {
                val result = coinRepository.getWatchlistCoins("usd").first()
                result.fold(
                    onSuccess = { coins ->
                        _uiState.update { it.copy(watchlistCoins = coins) }
                    },
                    onFailure = { /* ignore */ }
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    private fun loadTrending() {
        loadTrendingJob?.cancel()
        loadTrendingJob = viewModelScope.launch {
            try {
                val result = coinRepository.getTrendingCoins().first()
                result.fold(
                    onSuccess = { coins ->
                        _uiState.update { it.copy(trendingCoins = coins) }
                    },
                    onFailure = { /* ignore */ }
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    private fun filterCoins(coins: List<Coin>, query: String, tab: MarketTab, category: CoinCategory = CoinCategory.ALL): List<Coin> {
        // Step 1: Apply search query filter
        val searched = if (query.isBlank()) coins else {
            coins.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.symbol.contains(query, ignoreCase = true)
            }
        }
        // Step 2: Apply category filter
        val categorized = if (category == CoinCategory.ALL) searched else {
            searched.filter { CoinCategoryMapper.isInCategory(it.id, category) }
        }
        // Step 3: Apply tab-specific sorting/filtering
        return when (tab) {
            MarketTab.ALL -> categorized
            MarketTab.WATCHLIST -> categorized
            MarketTab.TRENDING -> categorized
            MarketTab.TOP_GAINERS -> categorized.sortedByDescending { it.priceChangePercentage24h }.take(50)
            MarketTab.TOP_LOSERS -> categorized.sortedBy { it.priceChangePercentage24h }.take(50)
        }
    }

    /**
     * Update visible symbols for lazy WebSocket.
     * Called when the user scrolls the market list.
     * Only subscribes to symbols currently visible on screen (+ small buffer).
     */
    fun updateVisibleSymbols(visibleSymbols: List<String>) {
        if (visibleSymbols == currentVisibleSymbols) return
        currentVisibleSymbols = visibleSymbols
        connectWebSocket(visibleSymbols)
    }

    fun connectWebSocket(symbols: List<String>? = null) {
        val targetSymbols = symbols
            ?: _uiState.value.coins.take(50).map { it.symbol.lowercase() }
        if (targetSymbols.isEmpty()) return

        // Don't reconnect if already connected to same symbols
        if (webSocketJob?.isActive == true && symbols == null) return

        webSocketJob?.cancel()
        webSocketJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(isWebSocketConnected = true) }
                sharedWebSocketManager.observeTicker(targetSymbols).collect { ticker ->
                    try {
                        val symbol = ticker.symbol.removeSuffix("USDT").lowercase()
                        val index = symbolIndexMap[symbol] ?: return@collect
                        _uiState.update { state ->
                            val currentCoins = state.coins
                            if (index >= currentCoins.size) return@update state
                            val oldCoin = currentCoins[index]
                            if (oldCoin.currentPrice == ticker.price &&
                                oldCoin.priceChangePercentage24h == ticker.priceChangePercent
                            ) return@update state
                            val updatedCoins = currentCoins.toMutableList().apply {
                                this[index] = oldCoin.copy(
                                    currentPrice = ticker.price,
                                    priceChangePercentage24h = ticker.priceChangePercent
                                )
                            }
                            state.copy(
                                coins = updatedCoins,
                                filteredCoins = filterCoins(updatedCoins, state.searchQuery, state.selectedTab, state.selectedCategory)
                            )
                        }
                    } catch (_: Exception) { }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isWebSocketConnected = false) }
            }
        }
    }

    fun disconnectWebSocket() {
        webSocketJob?.cancel()
        webSocketJob = null
        _uiState.update { it.copy(isWebSocketConnected = false) }
    }

    override fun onCleared() {
        super.onCleared()
        disconnectWebSocket()
    }
}
