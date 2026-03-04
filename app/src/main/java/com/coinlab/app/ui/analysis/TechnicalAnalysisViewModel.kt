package com.coinlab.app.ui.analysis

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coinlab.app.data.preferences.UserPreferences
import com.coinlab.app.data.remote.DynamicCoinRegistry
import com.coinlab.app.data.remote.api.BinanceApi
import com.coinlab.app.data.remote.api.FearGreedApi
import com.coinlab.app.domain.model.CoinDetail
import com.coinlab.app.domain.model.FearGreedIndex
import com.coinlab.app.domain.model.MarketChart
import com.coinlab.app.domain.model.OhlcData
import com.coinlab.app.domain.repository.CoinRepository
import com.coinlab.app.ui.components.TechnicalIndicators
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TechnicalAnalysisUiState(
    val coinId: String = "",
    val coinDetail: CoinDetail? = null,
    val ohlcData: List<OhlcData> = emptyList(),
    val marketChart: MarketChart? = null,
    val fearGreedIndex: FearGreedIndex? = null,
    val fearGreedHistory: List<FearGreedIndex> = emptyList(),
    val selectedTimeRange: String = "7",
    val selectedIndicators: Set<IndicatorType> = setOf(IndicatorType.SMA_20),
    val rsiValues: List<Double?> = emptyList(),
    val macdLine: List<Double?> = emptyList(),
    val macdSignal: List<Double?> = emptyList(),
    val macdHistogram: List<Double?> = emptyList(),
    val bollingerUpper: List<Double?> = emptyList(),
    val bollingerMiddle: List<Double?> = emptyList(),
    val bollingerLower: List<Double?> = emptyList(),
    val sma20: List<Double?> = emptyList(),
    val ema50: List<Double?> = emptyList(),
    val isLoading: Boolean = true,
    val currency: String = "USD",
    val error: String? = null,
    // Signal summary
    val signalSummary: String = "",
    val signalType: SignalType = SignalType.NEUTRAL
)

enum class IndicatorType {
    SMA_20, EMA_50, RSI, MACD, BOLLINGER, STOCH_RSI
}

enum class SignalType { BUY, SELL, NEUTRAL }

@HiltViewModel
class TechnicalAnalysisViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val coinRepository: CoinRepository,
    private val binanceApi: BinanceApi,
    private val fearGreedApi: FearGreedApi,
    private val userPreferences: UserPreferences,
    private val coinRegistry: DynamicCoinRegistry
) : ViewModel() {

    private val coinId: String = savedStateHandle.get<String>("coinId") ?: "bitcoin"

    private val _uiState = MutableStateFlow(TechnicalAnalysisUiState(coinId = coinId))
    val uiState: StateFlow<TechnicalAnalysisUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val currency = userPreferences.currency.first()
            _uiState.update { it.copy(currency = currency) }
            loadAll()
        }
    }

    private fun loadAll() {
        loadCoinDetail()
        loadOhlcData()
        loadFearGreed()
    }

    private fun loadCoinDetail() {
        viewModelScope.launch {
            coinRepository.getCoinDetail(coinId).collectLatest { result ->
                result.fold(
                    onSuccess = { detail ->
                        _uiState.update { it.copy(coinDetail = detail) }
                    },
                    onFailure = { }
                )
            }
        }
    }

    fun loadOhlcData(days: String = _uiState.value.selectedTimeRange) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, selectedTimeRange = days) }
            try {
                val binanceSymbol = coinRegistry.getBinanceSymbolByCoinId(coinId) ?: "BTCUSDT"
                // Map days to Binance kline interval + limit
                val pair: Pair<String, Int> = when (days) {
                    "1" -> "1h" to 24
                    "7" -> "4h" to 42
                    "14" -> "4h" to 84
                    "30" -> "1d" to 30
                    "90" -> "1d" to 90
                    "365" -> "1d" to 365
                    else -> "1d" to (days.toIntOrNull()?.coerceIn(1, 1000) ?: 30)
                }
                val interval = pair.first
                val limit = pair.second
                val klines = binanceApi.getKlines(
                    symbol = binanceSymbol,
                    interval = interval,
                    limit = limit
                )
                val ohlcData = klines.map { point ->
                    OhlcData(
                        time = (point[0] as? Number)?.toLong() ?: 0L,
                        open = (point[1] as? String)?.toDoubleOrNull() ?: 0.0,
                        high = (point[2] as? String)?.toDoubleOrNull() ?: 0.0,
                        low = (point[3] as? String)?.toDoubleOrNull() ?: 0.0,
                        close = (point[4] as? String)?.toDoubleOrNull() ?: 0.0,
                        volume = (point[5] as? String)?.toDoubleOrNull() ?: 0.0
                    )
                }
                _uiState.update { it.copy(ohlcData = ohlcData, isLoading = false) }
                calculateIndicators(ohlcData)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }

    private fun calculateIndicators(ohlcData: List<OhlcData>) {
        val closes = ohlcData.map { it.close }

        val sma20 = TechnicalIndicators.sma(closes, 20)
        val ema50 = TechnicalIndicators.ema(closes, 50)
        val rsi = TechnicalIndicators.rsi(closes, 14)
        val macd = TechnicalIndicators.macd(closes, 12, 26, 9)
        val bollinger = TechnicalIndicators.bollingerBands(closes, 20, 2.0)

        // Generate signal summary
        val latestRsi = rsi.lastOrNull { it != null }
        val latestMacd = macd.macdLine.lastOrNull { it != null }
        val latestSignal = macd.signalLine.lastOrNull { it != null }
        val latestClose = closes.lastOrNull()
        val latestSma = sma20.lastOrNull { it != null }
        val latestBollingerLower = bollinger.lower.lastOrNull { it != null }
        val latestBollingerUpper = bollinger.upper.lastOrNull { it != null }

        var buySignals = 0
        var sellSignals = 0

        // RSI signals
        latestRsi?.let {
            when {
                it < 30 -> buySignals++
                it > 70 -> sellSignals++
            }
        }

        // MACD signals
        if (latestMacd != null && latestSignal != null) {
            if (latestMacd > latestSignal) buySignals++ else sellSignals++
        }

        // Price vs SMA
        if (latestClose != null && latestSma != null) {
            if (latestClose > latestSma) buySignals++ else sellSignals++
        }

        // Bollinger signals
        if (latestClose != null) {
            latestBollingerLower?.let { if (latestClose <= it) buySignals++ }
            latestBollingerUpper?.let { if (latestClose >= it) sellSignals++ }
        }

        val signalType = when {
            buySignals > sellSignals + 1 -> SignalType.BUY
            sellSignals > buySignals + 1 -> SignalType.SELL
            else -> SignalType.NEUTRAL
        }

        val summary = when (signalType) {
            SignalType.BUY -> "Güçlü Al Sinyali ($buySignals/${ buySignals + sellSignals})"
            SignalType.SELL -> "Güçlü Sat Sinyali ($sellSignals/${buySignals + sellSignals})"
            SignalType.NEUTRAL -> "Nötr ($buySignals al / $sellSignals sat)"
        }

        _uiState.update {
            it.copy(
                sma20 = sma20,
                ema50 = ema50,
                rsiValues = rsi,
                macdLine = macd.macdLine,
                macdSignal = macd.signalLine,
                macdHistogram = macd.histogram,
                bollingerUpper = bollinger.upper,
                bollingerMiddle = bollinger.middle,
                bollingerLower = bollinger.lower,
                signalSummary = summary,
                signalType = signalType
            )
        }
    }

    private fun loadFearGreed() {
        viewModelScope.launch {
            try {
                val response = fearGreedApi.getFearGreedIndex(1)
                val current = response.data?.firstOrNull()
                current?.let { item ->
                    _uiState.update {
                        it.copy(
                            fearGreedIndex = FearGreedIndex(
                                value = item.value?.toIntOrNull() ?: 50,
                                valueClassification = item.value_classification ?: "Neutral",
                                timestamp = (item.timestamp?.toLongOrNull() ?: 0L) * 1000
                            )
                        )
                    }
                }
                // Load history
                val historyResponse = fearGreedApi.getFearGreedHistory(30)
                val history = historyResponse.data?.mapNotNull { item ->
                    val value = item.value?.toIntOrNull() ?: return@mapNotNull null
                    FearGreedIndex(
                        value = value,
                        valueClassification = item.value_classification ?: "Neutral",
                        timestamp = (item.timestamp?.toLongOrNull() ?: 0L) * 1000
                    )
                } ?: emptyList()
                _uiState.update { it.copy(fearGreedHistory = history) }
            } catch (e: Exception) {
                // Fear & Greed is optional, don't fail
            }
        }
    }

    fun toggleIndicator(indicator: IndicatorType) {
        _uiState.update {
            val current = it.selectedIndicators.toMutableSet()
            if (current.contains(indicator)) current.remove(indicator) else current.add(indicator)
            it.copy(selectedIndicators = current)
        }
    }
}
