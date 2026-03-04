package com.coinlab.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coinlab.app.data.local.dao.PriceAlertDao
import com.coinlab.app.data.local.entity.PriceAlertEntity
import com.coinlab.app.data.preferences.UserPreferences
import com.coinlab.app.data.remote.websocket.SharedWebSocketManager
import com.coinlab.app.domain.model.CoinDetail
import com.coinlab.app.domain.model.MarketChart
import com.coinlab.app.domain.repository.CoinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CoinDetailUiState(
    val coinDetail: CoinDetail? = null,
    val marketChart: MarketChart? = null,
    val isLoading: Boolean = true,
    val isChartLoading: Boolean = true,
    val error: String? = null,
    val chartError: String? = null,
    val selectedTimeRange: String = "7",
    val chartType: ChartType = ChartType.LINE,
    val currency: String = "USD",
    val isInWatchlist: Boolean = false,
    val livePrice: Double? = null,
    val livePriceChange: Double? = null,
    val showAlertDialog: Boolean = false,
    val alertCreated: Boolean = false
)

enum class ChartType { LINE, CANDLE }

@HiltViewModel
class CoinDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val coinRepository: CoinRepository,
    private val userPreferences: UserPreferences,
    private val sharedWebSocketManager: SharedWebSocketManager,
    private val priceAlertDao: PriceAlertDao
) : ViewModel() {

    private val coinId: String = savedStateHandle.get<String>("coinId") ?: ""

    private val _uiState = MutableStateFlow(CoinDetailUiState())
    val uiState: StateFlow<CoinDetailUiState> = _uiState.asStateFlow()

    private var webSocketJob: Job? = null

    // In-memory chart cache: key = "${coinId}_${days}" → MarketChart
    private val chartCache = mutableMapOf<String, MarketChart>()

    init {
        viewModelScope.launch {
            try {
                val currency = userPreferences.currency.first()
                _uiState.update { it.copy(currency = currency) }
                loadCoinDetail()
                observeWatchlist()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun loadCoinDetail() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                coinRepository.getCoinDetail(coinId).collectLatest { result ->
                    result.fold(
                        onSuccess = { detail ->
                            _uiState.update {
                                it.copy(coinDetail = detail, isLoading = false, error = null)
                            }
                            connectLivePrice(detail.symbol)
                            // Load chart AFTER we have the real symbol for Binance
                            loadMarketChart()
                        },
                        onFailure = { error ->
                            _uiState.update {
                                it.copy(isLoading = false, error = error.localizedMessage)
                            }
                            // Still try chart with coinId as fallback
                            loadMarketChart()
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
                loadMarketChart()
            }
        }
    }

    fun loadMarketChart(days: String = _uiState.value.selectedTimeRange) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isChartLoading = true, selectedTimeRange = days, chartError = null) }

                // Check in-memory cache first
                val cacheKey = "${coinId}_${days}"
                chartCache[cacheKey]?.let { cached ->
                    _uiState.update { it.copy(marketChart = cached, isChartLoading = false, chartError = null) }
                    return@launch
                }

                // Get coin symbol for Binance
                val symbol = _uiState.value.coinDetail?.symbol ?: coinId

                // Try Binance first (no API key needed, faster)
                var success = false
                try {
                    val binanceResult = coinRepository.getMarketChartFromBinance(
                        symbol = symbol,
                        days = days
                    ).first()
                    binanceResult.fold(
                        onSuccess = { chart ->
                            if (chart.prices.isNotEmpty()) {
                                chartCache[cacheKey] = chart
                                _uiState.update { it.copy(marketChart = chart, isChartLoading = false, chartError = null) }
                                success = true
                            }
                        },
                        onFailure = { /* Will try CoinGecko fallback */ }
                    )
                } catch (_: Exception) { /* Binance failed, try CoinGecko */ }

                // Fallback to CoinGecko if Binance failed
                if (!success) {
                    try {
                        val geckoResult = coinRepository.getMarketChart(
                            coinId = coinId,
                            currency = _uiState.value.currency.lowercase(),
                            days = days
                        ).first()
                        geckoResult.fold(
                            onSuccess = { chart ->
                                if (chart.prices.isEmpty()) {
                                    _uiState.update { it.copy(isChartLoading = false, chartError = "Grafik verisi bulunamadı. Tekrar deneyin.") }
                                } else {
                                    chartCache[cacheKey] = chart
                                    _uiState.update { it.copy(marketChart = chart, isChartLoading = false, chartError = null) }
                                }
                            },
                            onFailure = { e ->
                                _uiState.update { it.copy(isChartLoading = false, chartError = e.localizedMessage ?: "Grafik yüklenemedi") }
                            }
                        )
                    } catch (e: Exception) {
                        _uiState.update { it.copy(isChartLoading = false, chartError = e.message ?: "Grafik yüklenemedi") }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isChartLoading = false, chartError = e.message ?: "Grafik yüklenemedi") }
            }
        }
    }

    fun retryChart() {
        loadMarketChart(_uiState.value.selectedTimeRange)
    }

    fun toggleChartType() {
        _uiState.update {
            it.copy(chartType = if (it.chartType == ChartType.LINE) ChartType.CANDLE else ChartType.LINE)
        }
    }

    private fun connectLivePrice(symbol: String) {
        if (webSocketJob?.isActive == true) return
        webSocketJob = viewModelScope.launch {
            try {
                sharedWebSocketManager.observeTicker(listOf(symbol.lowercase())).collect { ticker ->
                    val tickerSymbol = ticker.symbol.removeSuffix("USDT").lowercase()
                    if (tickerSymbol.equals(symbol, ignoreCase = true)) {
                        _uiState.update {
                            it.copy(
                                livePrice = ticker.price,
                                livePriceChange = ticker.priceChangePercent
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    private fun observeWatchlist() {
        viewModelScope.launch {
            try {
                coinRepository.isInWatchlist(coinId).collectLatest { isIn ->
                    _uiState.update { it.copy(isInWatchlist = isIn) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun toggleWatchlist() {
        viewModelScope.launch {
            try {
                if (_uiState.value.isInWatchlist) {
                    coinRepository.removeFromWatchlist(coinId)
                } else {
                    coinRepository.addToWatchlist(coinId)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun showAlertDialog() {
        _uiState.update { it.copy(showAlertDialog = true, alertCreated = false) }
    }

    fun hideAlertDialog() {
        _uiState.update { it.copy(showAlertDialog = false) }
    }

    fun createAlert(targetPrice: Double, isAbove: Boolean) {
        val coin = _uiState.value.coinDetail ?: return
        viewModelScope.launch {
            try {
                val entity = PriceAlertEntity(
                    coinId = coin.id,
                    coinSymbol = coin.symbol,
                    coinName = coin.name,
                    coinImage = coin.image,
                    targetPrice = targetPrice,
                    currency = _uiState.value.currency,
                    isAbove = isAbove
                )
                priceAlertDao.insert(entity)
                _uiState.update { it.copy(showAlertDialog = false, alertCreated = true) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        webSocketJob?.cancel()
    }
}
