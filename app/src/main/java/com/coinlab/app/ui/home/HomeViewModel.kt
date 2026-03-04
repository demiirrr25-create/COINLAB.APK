package com.coinlab.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coinlab.app.data.remote.DynamicCoinRegistry
import com.coinlab.app.data.remote.cache.BinanceTickerCache
import com.coinlab.app.data.remote.api.CoinGeckoApi
import com.coinlab.app.data.remote.api.FearGreedApi
import com.coinlab.app.data.remote.websocket.SharedWebSocketManager
import com.coinlab.app.domain.model.Coin
import com.coinlab.app.domain.repository.CoinRepository
import com.coinlab.app.data.preferences.AuthPreferences
import com.coinlab.app.data.remote.api.FearGreedDataItem
import com.coinlab.app.data.remote.dto.TrendingCoinDto
import com.coinlab.app.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val top5Coins: List<Coin> = emptyList(),
    val topGainers: List<Coin> = emptyList(),
    val topLosers: List<Coin> = emptyList(),
    val trendingCoins: List<TrendingCoinDto> = emptyList(),
    val fearGreedValue: Int = 0,
    val fearGreedLabel: String = "",
    val fearGreedHistory: List<FearGreedDataItem> = emptyList(),
    val totalMarketCap: Double = 0.0,
    val totalVolume24h: Double = 0.0,
    val btcDominance: Double = 0.0,
    val ethDominance: Double = 0.0,
    val marketCapChangePercent24h: Double = 0.0,
    val activeCryptos: Int = 0,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val currency: String = "USD",
    val displayName: String = "",
    val isWebSocketConnected: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val coinRepository: CoinRepository,
    private val tickerCache: BinanceTickerCache,
    private val fearGreedApi: FearGreedApi,
    private val coinGeckoApi: CoinGeckoApi,
    private val userPreferences: UserPreferences,
    private val sharedWebSocketManager: SharedWebSocketManager,
    private val authPreferences: AuthPreferences,
    private val coinRegistry: DynamicCoinRegistry
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var webSocketJob: Job? = null
    private var loadJob: Job? = null

    // Index map for O(1) updates
    private var symbolIndexMap: Map<String, Int> = emptyMap()

    init {
        // Load preferences and data in parallel for faster startup
        viewModelScope.launch {
            try {
                // Fire both pref reads in parallel using coroutineScope
                coroutineScope {
                    val nameDeferred = async {
                        try { authPreferences.displayName.first() } catch (_: Exception) { "" }
                    }
                    val currencyDeferred = async {
                        try { userPreferences.currency.first() } catch (_: Exception) { "USD" }
                    }
                    val name = nameDeferred.await()
                    val currency = currencyDeferred.await()
                    _uiState.update { it.copy(displayName = name, currency = currency) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
            loadData()
        }
    }

    fun loadData() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                // Load coins FIRST — this is what the user sees
                // Don't wait for FearGreed/GlobalData to finish before showing coins
                loadCoins()
                _uiState.update { it.copy(isLoading = false) }
                // Load secondary data in background — non-blocking
                supervisorScope {
                    launch { loadFearGreedIndex() }
                    launch { loadGlobalData() }
                    launch { loadTrending() }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(isRefreshing = true) }
                tickerCache.invalidate()
                // Load coins first, then secondary data
                loadCoins()
                _uiState.update { it.copy(isRefreshing = false) }
                supervisorScope {
                    launch { loadFearGreedIndex() }
                    launch { loadGlobalData() }
                    launch { loadTrending() }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private suspend fun loadTrending() {
        try {
            val trending = coinGeckoApi.getTrending()
            val coins = trending.coins?.take(7)?.mapNotNull { it.item } ?: emptyList()
            _uiState.update { it.copy(trendingCoins = coins) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.w("HomeVM", "Trending load failed", e)
        }
    }

    private suspend fun loadCoins() {
        try {
            val result = coinRepository.getCoins(
                currency = "usd",
                orderBy = "market_cap_desc",
                perPage = 50,
                sparkline = false
            ).first()
            result.onSuccess { coins ->
                val top5 = coins.take(5)
                // Top Movers: sort by absolute 24h change
                val sorted = coins.filter { it.priceChangePercentage24h != 0.0 }
                    .sortedByDescending { kotlin.math.abs(it.priceChangePercentage24h) }
                val gainers = sorted.filter { it.priceChangePercentage24h > 0 }.take(5)
                val losers = sorted.filter { it.priceChangePercentage24h < 0 }.take(5)
                symbolIndexMap = top5.withIndex()
                    .associate { (i, coin) -> coin.symbol.lowercase() to i }
                _uiState.update {
                    it.copy(top5Coins = top5, topGainers = gainers, topLosers = losers, error = null)
                }
                connectWebSocket()
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _uiState.update { it.copy(error = e.message) }
        }
    }

    private suspend fun loadGlobalData() {
        try {
            // Primary: CoinGecko /global endpoint for accurate market data
            val globalData = coinGeckoApi.getGlobalData()
            val data = globalData.data
            if (data != null) {
                val totalMcap = data.total_market_cap?.get("usd") ?: 0.0
                val totalVol = data.total_volume?.get("usd") ?: 0.0
                val btcDom = data.market_cap_percentage?.get("btc") ?: 0.0
                val ethDom = data.market_cap_percentage?.get("eth") ?: 0.0
                val mcapChange = data.market_cap_change_percentage_24h_usd ?: 0.0
                val activeCryptos = data.active_cryptocurrencies ?: 0

                _uiState.update {
                    it.copy(
                        totalMarketCap = totalMcap,
                        totalVolume24h = totalVol,
                        btcDominance = btcDom,
                        ethDominance = ethDom,
                        marketCapChangePercent24h = mcapChange,
                        activeCryptos = activeCryptos
                    )
                }
                return
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.w("HomeVM", "CoinGecko global failed, falling back to Binance")
        }

        // Fallback: Calculate from Binance tickers
        try {
            val tickers = tickerCache.getTickers()
            var totalMarketCap = 0.0
            var totalVolume = 0.0
            var btcMarketCap = 0.0
            var ethMarketCap = 0.0

            for (ticker in tickers) {
                val meta = coinRegistry.getMetaByBinanceSymbol(ticker.symbol ?: continue) ?: continue
                val price = ticker.lastPrice?.toDoubleOrNull() ?: continue
                val volume = ticker.quoteVolume?.toDoubleOrNull() ?: 0.0
                val supply = coinRegistry.getCirculatingSupply(meta.id)
                val mcap = price * supply
                totalMarketCap += mcap
                totalVolume += volume
                if (meta.id == "bitcoin") btcMarketCap = mcap
                if (meta.id == "ethereum") ethMarketCap = mcap
            }

            val btcDom = if (totalMarketCap > 0) (btcMarketCap / totalMarketCap) * 100 else 0.0
            val ethDom = if (totalMarketCap > 0) (ethMarketCap / totalMarketCap) * 100 else 0.0

            _uiState.update {
                it.copy(
                    totalMarketCap = totalMarketCap,
                    totalVolume24h = totalVolume,
                    btcDominance = btcDom,
                    ethDominance = ethDom,
                    marketCapChangePercent24h = 0.0,
                    activeCryptos = tickers.size
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }

    private fun connectWebSocket() {
        if (webSocketJob?.isActive == true) return
        val symbols = _uiState.value.top5Coins.map { it.symbol.lowercase() }
        if (symbols.isEmpty()) return

        webSocketJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(isWebSocketConnected = true) }
                sharedWebSocketManager.observeTicker(symbols).collect { ticker ->
                    try {
                        val symbol = ticker.symbol.removeSuffix("USDT").lowercase()
                        val index = symbolIndexMap[symbol] ?: return@collect
                        _uiState.update { state ->
                            val currentCoins = state.top5Coins
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
                            state.copy(top5Coins = updatedCoins)
                        }
                    } catch (_: Exception) { }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isWebSocketConnected = false) }
            }
        }
    }

    private suspend fun loadFearGreedIndex() {
        try {
            // Load current value and 30-day history in parallel
            coroutineScope {
                val currentDeferred = async {
                    try { fearGreedApi.getFearGreedIndex() } catch (_: Exception) { null }
                }
                val historyDeferred = async {
                    try { fearGreedApi.getFearGreedHistory(limit = 30) } catch (_: Exception) { null }
                }
                val currentResponse = currentDeferred.await()
                val historyResponse = historyDeferred.await()

                val currentData = currentResponse?.data?.firstOrNull()
                val historyData = historyResponse?.data ?: emptyList()

                _uiState.update {
                    it.copy(
                        fearGreedValue = currentData?.value?.toIntOrNull() ?: it.fearGreedValue,
                        fearGreedLabel = currentData?.value_classification ?: it.fearGreedLabel,
                        fearGreedHistory = historyData
                    )
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }

    override fun onCleared() {
        super.onCleared()
        webSocketJob?.cancel()
    }
}
