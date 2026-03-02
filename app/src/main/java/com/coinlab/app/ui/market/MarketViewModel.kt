package com.coinlab.app.ui.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coinlab.app.data.preferences.UserPreferences
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
    val currency: String = "USD",
    val sortOrder: String = "market_cap_desc",
    val isWebSocketConnected: Boolean = false
)

enum class MarketTab {
    ALL, WATCHLIST, TRENDING, TOP_GAINERS, TOP_LOSERS
}

@HiltViewModel
class MarketViewModel @Inject constructor(
    private val coinRepository: CoinRepository,
    private val userPreferences: UserPreferences,
    private val sharedWebSocketManager: SharedWebSocketManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MarketUiState())
    val uiState: StateFlow<MarketUiState> = _uiState.asStateFlow()

    private var webSocketJob: Job? = null
    private var loadCoinsJob: Job? = null
    private var loadWatchlistJob: Job? = null
    private var loadTrendingJob: Job? = null

    // Index map for O(1) WebSocket updates instead of O(n)
    private var symbolIndexMap: Map<String, Int> = emptyMap()

    init {
        viewModelScope.launch {
            try {
                val currency = userPreferences.currency.first()
                _uiState.update { it.copy(currency = currency) }
                loadCoins()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun loadCoins() {
        loadCoinsJob?.cancel()
        loadCoinsJob = viewModelScope.launch {
            try {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = coinRepository.getCoins(
                currency = "usd",
                orderBy = _uiState.value.sortOrder
            ).first()
            result.fold(
                onSuccess = { coins ->
                    // Build index map for O(1) WebSocket updates
                    symbolIndexMap = coins.withIndex()
                        .associate { (i, coin) -> coin.symbol.lowercase() to i }
                    _uiState.update {
                        it.copy(
                            coins = coins,
                            filteredCoins = filterCoins(coins, it.searchQuery, it.selectedTab),
                            isLoading = false,
                            isRefreshing = false,
                            error = null
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
                filteredCoins = filterCoins(it.coins, query, it.selectedTab)
            )
        }
    }

    fun onTabSelected(tab: MarketTab) {
        _uiState.update {
            it.copy(
                selectedTab = tab,
                filteredCoins = filterCoins(it.coins, it.searchQuery, tab)
            )
        }
        when (tab) {
            MarketTab.WATCHLIST -> loadWatchlist()
            MarketTab.TRENDING -> loadTrending()
            else -> {}
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

    private fun filterCoins(coins: List<Coin>, query: String, tab: MarketTab): List<Coin> {
        val filtered = if (query.isBlank()) coins else {
            coins.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.symbol.contains(query, ignoreCase = true)
            }
        }
        return when (tab) {
            MarketTab.ALL -> filtered
            MarketTab.WATCHLIST -> filtered
            MarketTab.TRENDING -> filtered
            MarketTab.TOP_GAINERS -> filtered.sortedByDescending { it.priceChangePercentage24h }.take(50)
            MarketTab.TOP_LOSERS -> filtered.sortedBy { it.priceChangePercentage24h }.take(50)
        }
    }

    fun connectWebSocket() {
        if (webSocketJob?.isActive == true) return
        val symbols = _uiState.value.coins.take(50).map { it.symbol.lowercase() }
        if (symbols.isEmpty()) return

        webSocketJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(isWebSocketConnected = true) }
                sharedWebSocketManager.observeTicker(symbols).collect { ticker ->
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
                                filteredCoins = filterCoins(updatedCoins, state.searchQuery, state.selectedTab)
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
