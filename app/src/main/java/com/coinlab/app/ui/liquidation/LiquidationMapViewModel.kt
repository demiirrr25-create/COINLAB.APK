package com.coinlab.app.ui.liquidation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coinlab.app.data.remote.firebase.*
import com.coinlab.app.data.remote.websocket.FuturesKlineUpdate
import com.coinlab.app.data.remote.websocket.FuturesWebSocketClient
import com.coinlab.app.data.remote.websocket.FuturesWsEvent
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * v12.2 — CoinGlass-Grade Liquidation Heatmap ViewModel
 *
 * Architecture:
 *   - Firebase RTDB listener for real-time heatmap data (backend-computed)
 *   - Binance Futures WebSocket for real-time kline/mark price (<500ms)
 *   - Cloud Function callable for initial data load
 *   - User preferences saved to Firebase RTDB
 *
 * Timeframes: 24H, 48H, 1W, 1M, 3M
 * Models: Standard, Aggressive, Conservative
 */

data class LiquidationUiState(
    val selectedCoin: String = "BTC",
    val timeFilter: String = "24H",
    val threshold: Float = 0.5f,
    val selectedModel: String = "Standard",
    val searchQuery: String = "",
    val aggregatedData: AggregatedLiquidationData? = null,
    val oiHistory: List<OIHistoryPoint> = emptyList(),
    val lsHistory: List<LongShortPoint> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val candleJson: String = "",
    val heatmapJson: String = "",
    val markPrice: Double = 0.0,
    val wsConnected: Boolean = false,
    val isFullscreen: Boolean = false
)

/** JS evaluation commands sent to WebView */
sealed class ChartCommand {
    data class SetCandleData(val json: String) : ChartCommand()
    data class UpdateCandle(val json: String) : ChartCommand()
    data class SetHeatmap(val json: String) : ChartCommand()
    data class SetMarkPrice(val price: Double) : ChartCommand()
    data class SetPrecision(val precision: Int, val minMove: Double) : ChartCommand()
    data class SetThreshold(val value: Float) : ChartCommand()
    data class SetModel(val model: String) : ChartCommand()
}

@HiltViewModel
class LiquidationMapViewModel @Inject constructor(
    private val repository: LiquidationRepository,
    private val futuresWs: FuturesWebSocketClient
) : ViewModel() {

    companion object {
        private const val TAG = "LiqMapVM"
    }

    private val _uiState = MutableStateFlow(LiquidationUiState())
    val uiState: StateFlow<LiquidationUiState> = _uiState.asStateFlow()

    private val _chartCommands = MutableSharedFlow<ChartCommand>(extraBufferCapacity = 64)
    val chartCommands: SharedFlow<ChartCommand> = _chartCommands.asSharedFlow()

    private val gson = Gson()
    private var wsJob: Job? = null
    private var rtdbJob: Job? = null

    val availableCoins = listOf(
        "BTC", "ETH", "SOL", "BNB", "XRP",
        "DOGE", "ADA", "AVAX", "MATIC", "DOT",
        "LINK", "UNI", "APT", "ARB", "OP"
    )

    val timeFilters = listOf("24H", "48H", "1W", "1M", "3M")
    val availableModels = listOf("Standard", "Aggressive", "Conservative")

    private val coinPrecision = mapOf(
        "BTC" to Pair(2, 0.01), "ETH" to Pair(2, 0.01),
        "BNB" to Pair(2, 0.01), "SOL" to Pair(2, 0.01),
        "XRP" to Pair(4, 0.0001), "DOGE" to Pair(5, 0.00001),
        "ADA" to Pair(4, 0.0001), "AVAX" to Pair(2, 0.01),
        "MATIC" to Pair(4, 0.0001), "DOT" to Pair(3, 0.001),
        "LINK" to Pair(3, 0.001), "UNI" to Pair(3, 0.001),
        "APT" to Pair(3, 0.001), "ARB" to Pair(4, 0.0001),
        "OP" to Pair(4, 0.0001)
    )

    // Map timeframes to kline intervals for chart
    private val timeframeToKlineInterval = mapOf(
        "24H" to "1h", "48H" to "2h", "1W" to "4h", "1M" to "1d", "3M" to "1d"
    )
    private val timeframeToKlineLimit = mapOf(
        "24H" to 24, "48H" to 24, "1W" to 42, "1M" to 30, "3M" to 90
    )

    val filteredCoins: List<String>
        get() {
            val query = _uiState.value.searchQuery
            return if (query.isBlank()) availableCoins
            else availableCoins.filter { it.contains(query, ignoreCase = true) }
        }

    init {
        loadUserPreferences()
        loadData()
    }

    // ─── Public Actions ─────────────────────────────────────────────

    fun selectCoin(coin: String) {
        if (coin != _uiState.value.selectedCoin) {
            _uiState.update { it.copy(selectedCoin = coin, isLoading = true, error = null) }
            loadData()
        }
    }

    fun setTimeFilter(filter: String) {
        if (filter != _uiState.value.timeFilter) {
            _uiState.update { it.copy(timeFilter = filter) }
            loadChartData()
            startRtdbListener()
        }
    }

    fun setThreshold(value: Float) {
        _uiState.update { it.copy(threshold = value) }
        _chartCommands.tryEmit(ChartCommand.SetThreshold(value))
    }

    fun setModel(model: String) {
        _uiState.update { it.copy(selectedModel = model) }
        _chartCommands.tryEmit(ChartCommand.SetModel(model))
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleFullscreen() {
        _uiState.update { it.copy(isFullscreen = !it.isFullscreen) }
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        loadData()
    }

    /** Called from WebView JS bridge when chart is ready */
    fun onChartReady() {
        val state = _uiState.value
        val (prec, minMove) = coinPrecision[state.selectedCoin] ?: Pair(2, 0.01)
        _chartCommands.tryEmit(ChartCommand.SetPrecision(prec, minMove))
        _chartCommands.tryEmit(ChartCommand.SetThreshold(state.threshold))
        _chartCommands.tryEmit(ChartCommand.SetModel(state.selectedModel))

        if (state.candleJson.isNotEmpty()) {
            _chartCommands.tryEmit(ChartCommand.SetCandleData(state.candleJson))
        }
        if (state.heatmapJson.isNotEmpty()) {
            _chartCommands.tryEmit(ChartCommand.SetHeatmap(state.heatmapJson))
        }
        if (state.markPrice > 0) {
            _chartCommands.tryEmit(ChartCommand.SetMarkPrice(state.markPrice))
        }
        startWebSocket()
    }

    // ─── Data Loading ───────────────────────────────────────────────

    private fun loadData() {
        viewModelScope.launch {
            try {
                val coin = _uiState.value.selectedCoin
                val tf = _uiState.value.timeFilter

                // Parallel: kline + on-demand heatmap + OI + L/S
                val klineJob = launch { loadKlineData(coin, tf) }
                val heatmapJob = launch { loadHeatmapData(coin, tf) }
                val oiJob = launch { loadOIHistory(coin) }
                val lsJob = launch { loadLSHistory(coin) }

                klineJob.join()
                heatmapJob.join()
                oiJob.join()
                lsJob.join()

                _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = null) }

                // Start real-time listeners
                startRtdbListener()
                startWebSocket()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, error = "Veri yüklenemedi: ${e.message}")
                }
            }
        }
    }

    private suspend fun loadKlineData(coin: String, tf: String) {
        try {
            val interval = timeframeToKlineInterval[tf] ?: "1h"
            val limit = timeframeToKlineLimit[tf] ?: 100
            val klines = repository.getKlineData(coin, interval, limit)
            val json = gson.toJson(klines)
            _uiState.update { it.copy(candleJson = json) }
            _chartCommands.tryEmit(ChartCommand.SetCandleData(json))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Kline load failed", e)
        }
    }

    private suspend fun loadHeatmapData(coin: String, tf: String) {
        try {
            val data = repository.getHeatmapOnDemand(coin, tf)
            processHeatmapData(data, coin)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _uiState.update { it.copy(error = "Heatmap yüklenemedi: ${e.message}") }
        }
    }

    private suspend fun loadOIHistory(coin: String) {
        try {
            val history = repository.getOpenInterestHistory(coin, "5m")
            _uiState.update { it.copy(oiHistory = history) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }

    private suspend fun loadLSHistory(coin: String) {
        try {
            val history = repository.getLongShortHistory(coin, "5m")
            _uiState.update { it.copy(lsHistory = history) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }

    private fun loadChartData() {
        viewModelScope.launch {
            val coin = _uiState.value.selectedCoin
            val tf = _uiState.value.timeFilter
            loadKlineData(coin, tf)
        }
    }

    // ─── Firebase RTDB Real-Time Listener ────────────────────────────

    private fun startRtdbListener() {
        rtdbJob?.cancel()
        val coin = _uiState.value.selectedCoin
        val tf = _uiState.value.timeFilter

        rtdbJob = viewModelScope.launch {
            repository.observeHeatmapData(coin, tf)
                .catch { e -> Log.w(TAG, "RTDB error: ${e.message}") }
                .collect { data ->
                    processHeatmapData(data, coin)
                }
        }
    }

    private fun processHeatmapData(data: AggregatedLiquidationData, coin: String) {
        _uiState.update { it.copy(aggregatedData = data, markPrice = data.markPrice) }

        val heatmapList = data.heatmapBuckets.map { b ->
            mapOf(
                "priceLevel" to b.priceLevel,
                "longUsd" to b.longLiquidationUsd,
                "shortUsd" to b.shortLiquidationUsd,
                "totalUsd" to b.totalLiquidationUsd
            )
        }
        val hJson = gson.toJson(heatmapList)
        _uiState.update { it.copy(heatmapJson = hJson) }
        _chartCommands.tryEmit(ChartCommand.SetHeatmap(hJson))

        if (data.markPrice > 0) {
            _chartCommands.tryEmit(ChartCommand.SetMarkPrice(data.markPrice))
        }

        val (prec, minMove) = coinPrecision[coin] ?: Pair(2, 0.01)
        _chartCommands.tryEmit(ChartCommand.SetPrecision(prec, minMove))
    }

    // ─── WebSocket (Real-time kline + mark price) ────────────────────

    private fun startWebSocket() {
        wsJob?.cancel()
        val coin = _uiState.value.selectedCoin
        val tf = _uiState.value.timeFilter
        val symbol = "${coin}USDT"
        val interval = timeframeToKlineInterval[tf] ?: "1h"

        wsJob = viewModelScope.launch {
            futuresWs.connectStreams(symbol, interval)
                .catch { e ->
                    Log.w(TAG, "WS error: ${e.message}")
                    _uiState.update { it.copy(wsConnected = false) }
                }
                .collect { event ->
                    if (!_uiState.value.wsConnected) {
                        _uiState.update { it.copy(wsConnected = true) }
                    }
                    when (event) {
                        is FuturesWsEvent.Kline -> handleKlineUpdate(event.data)
                        is FuturesWsEvent.MarkPriceUpdate -> {
                            val mp = event.data.markPrice
                            if (mp > 0) {
                                _uiState.update { it.copy(markPrice = mp) }
                                _chartCommands.tryEmit(ChartCommand.SetMarkPrice(mp))
                            }
                        }
                        is FuturesWsEvent.Liquidation -> {
                            // Real-time liquidation events — heatmap updates via RTDB listener
                            Log.d(TAG, "Liq: ${event.data.side} $${event.data.usdValue}")
                        }
                    }
                }
        }
    }

    private fun handleKlineUpdate(kline: FuturesKlineUpdate) {
        val candleMap = mapOf(
            "time" to kline.time,
            "open" to kline.open,
            "high" to kline.high,
            "low" to kline.low,
            "close" to kline.close,
            "volume" to kline.volume
        )
        val json = gson.toJson(candleMap)
        _chartCommands.tryEmit(ChartCommand.UpdateCandle(json))
    }

    // ─── User Preferences ────────────────────────────────────────────

    private fun loadUserPreferences() {
        viewModelScope.launch {
            try {
                val prefs = repository.loadUserPreferences()
                _uiState.update {
                    it.copy(
                        selectedCoin = prefs.lastCoin,
                        timeFilter = prefs.timeframe,
                        threshold = prefs.threshold
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    private fun saveUserPreferences() {
        viewModelScope.launch {
            val state = _uiState.value
            repository.saveUserPreferences(state.timeFilter, state.threshold, state.selectedCoin)
        }
    }

    // ─── Cleanup ─────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        saveUserPreferences()
        wsJob?.cancel()
        rtdbJob?.cancel()
        futuresWs.disconnect()
    }
}
