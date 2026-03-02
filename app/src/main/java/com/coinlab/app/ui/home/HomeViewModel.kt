package com.coinlab.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coinlab.app.data.remote.BinanceCoinMapper
import com.coinlab.app.data.remote.api.BinanceApi
import com.coinlab.app.data.remote.api.FearGreedApi
import com.coinlab.app.data.remote.websocket.SharedWebSocketManager
import com.coinlab.app.domain.model.Coin
import com.coinlab.app.domain.repository.CoinRepository
import com.coinlab.app.data.preferences.AuthPreferences
import com.coinlab.app.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val top5Coins: List<Coin> = emptyList(),
    val fearGreedValue: Int = 0,
    val fearGreedLabel: String = "",
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
    private val binanceApi: BinanceApi,
    private val fearGreedApi: FearGreedApi,
    private val userPreferences: UserPreferences,
    private val sharedWebSocketManager: SharedWebSocketManager,
    private val authPreferences: AuthPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var webSocketJob: Job? = null
    private var loadJob: Job? = null

    // Index map for O(1) updates
    private var symbolIndexMap: Map<String, Int> = emptyMap()

    init {
        // Load display name once
        viewModelScope.launch {
            try {
                val name = authPreferences.displayName.first()
                _uiState.update { it.copy(displayName = name) }
            } catch (_: Exception) { }
        }
        viewModelScope.launch {
            try {
                userPreferences.currency.collect { currency ->
                    _uiState.update { it.copy(currency = currency) }
                    loadData()
                }
            } catch (_: Exception) {
                loadData()
            }
        }
    }

    fun loadData() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                val jobs = listOf(
                    launch { loadCoins() },
                    launch { loadFearGreedIndex() },
                    launch { loadGlobalData() }
                )
                jobs.joinAll()
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(isRefreshing = true) }
                val jobs = listOf(
                    launch { loadCoins() },
                    launch { loadFearGreedIndex() },
                    launch { loadGlobalData() }
                )
                jobs.joinAll()
            } catch (_: Exception) { }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private suspend fun loadCoins() {
        try {
            coinRepository.getCoins(
                currency = "usd",
                orderBy = "market_cap_desc",
                perPage = 10,
                sparkline = false
            ).collect { result ->
                result.onSuccess { coins ->
                    val top5 = coins.take(5)
                    symbolIndexMap = top5.withIndex()
                        .associate { (i, coin) -> coin.symbol.lowercase() to i }

                    _uiState.update {
                        it.copy(
                            top5Coins = top5,
                            error = null
                        )
                    }
                    connectWebSocket()
                }.onFailure { e ->
                    _uiState.update {
                        it.copy(error = e.message)
                    }
                }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = e.message) }
        }
    }

    private suspend fun loadGlobalData() {
        try {
            // Calculate global data from Binance tickers (no API key needed)
            val tickers = binanceApi.get24hrTicker()
            val supported = BinanceCoinMapper.getAllBinanceSymbols()
            val filteredTickers = tickers.filter { it.symbol in supported }

            var totalMarketCap = 0.0
            var totalVolume = 0.0
            var btcMarketCap = 0.0
            var ethMarketCap = 0.0

            for (ticker in filteredTickers) {
                val meta = BinanceCoinMapper.getMetaByBinanceSymbol(ticker.symbol ?: continue) ?: continue
                val price = ticker.lastPrice?.toDoubleOrNull() ?: continue
                val volume = ticker.quoteVolume?.toDoubleOrNull() ?: 0.0
                val supply = getApproxSupply(meta.id)
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
                    activeCryptos = filteredTickers.size
                )
            }
        } catch (_: Exception) { }
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
        else -> 1_000_000_000.0
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
            } catch (_: Exception) {
                _uiState.update { it.copy(isWebSocketConnected = false) }
            }
        }
    }

    private suspend fun loadFearGreedIndex() {
        try {
            val response = fearGreedApi.getFearGreedIndex()
            val data = response.data?.firstOrNull()
            if (data != null) {
                _uiState.update {
                    it.copy(
                        fearGreedValue = data.value?.toIntOrNull() ?: 0,
                        fearGreedLabel = data.value_classification ?: ""
                    )
                }
            }
        } catch (_: Exception) { }
    }

    override fun onCleared() {
        super.onCleared()
        webSocketJob?.cancel()
    }
}
