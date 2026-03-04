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
import kotlinx.coroutines.delay
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
 * v12.1 — Professional Liquidation Map ViewModel
 *
 * WebView chart engine integration with real-time WebSocket data.
 * Kline + heatmap data formatted as JSON for TradingView Lightweight Charts.
 */

data class LiquidationUiState(
    val selectedCoin: String = "BTC",
    val timeFilter: String = "1h",
    val aggregatedData: AggregatedLiquidationData? = null,
    val oiHistory: List<OIHistoryPoint> = emptyList(),
    val lsHistory: List<LongShortPoint> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val candleJson: String = "",
    val heatmapJson: String = "",
    val markPrice: Double = 0.0,
    val wsConnected: Boolean = false
)

/** JS evaluation commands sent to WebView */
sealed class ChartCommand {
    data class SetCandleData(val json: String) : ChartCommand()
    data class UpdateCandle(val json: String) : ChartCommand()
    data class SetHeatmap(val json: String) : ChartCommand()
    data class SetMarkPrice(val price: Double) : ChartCommand()
    data class SetPrecision(val precision: Int, val minMove: Double) : ChartCommand()
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

    private var refreshJob: Job? = null
    private var wsJob: Job? = null

    val availableCoins = listOf(
        "BTC", "ETH", "SOL", "BNB", "XRP",
        "DOGE", "ADA", "AVAX", "MATIC", "DOT",
        "LINK", "UNI", "APT", "ARB", "OP"
    )

    val timeFilters = listOf("1m", "5m", "15m", "1h", "4h", "1d")

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

    init {
        loadData()
        startAutoRefresh()
    }

    fun selectCoin(coin: String) {
        if (coin != _uiState.value.selectedCoin) {
            _uiState.update { it.copy(selectedCoin = coin, isLoading = true) }
            loadData()
        }
    }

    fun setTimeFilter(filter: String) {
        if (filter != _uiState.value.timeFilter) {
            _uiState.update { it.copy(timeFilter = filter) }
            loadChartData()
            restartWebSocket()
        }
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

    private fun loadData() {
        viewModelScope.launch {
            try {
                val coin = _uiState.value.selectedCoin
                val tf = _uiState.value.timeFilter

                // Parallel: kline + aggregated + OI + L/S
                val klineJob = launch {
                    try {
                        val klines = repository.getKlineData(coin, tf, 500)
                        val json = gson.toJson(klines)
                        _uiState.update { it.copy(candleJson = json) }
                        _chartCommands.tryEmit(ChartCommand.SetCandleData(json))
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.w(TAG, "Kline load failed", e)
                    }
                }

                val aggregatedJob = launch {
                    try {
                        val data = repository.getAggregatedData(coin)
                        _uiState.update { it.copy(aggregatedData = data, markPrice = data.markPrice) }

                        // Send heatmap data to chart
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

                        // Set precision
                        val (prec, minMove) = coinPrecision[coin] ?: Pair(2, 0.01)
                        _chartCommands.tryEmit(ChartCommand.SetPrecision(prec, minMove))
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        _uiState.update { it.copy(error = "Veri yüklenemedi: ${e.message}") }
                    }
                }

                val oiJob = launch {
                    try {
                        val history = repository.getOpenInterestHistory(coin, tf)
                        _uiState.update { it.copy(oiHistory = history) }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                    }
                }

                val lsJob = launch {
                    try {
                        val history = repository.getLongShortHistory(coin, tf)
                        _uiState.update { it.copy(lsHistory = history) }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                    }
                }

                klineJob.join()
                aggregatedJob.join()
                oiJob.join()
                lsJob.join()

                _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = null) }
                startWebSocket()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, error = "Veri yüklenemedi: ${e.message}")
                }
            }
        }
    }

    private fun loadChartData() {
        viewModelScope.launch {
            try {
                val coin = _uiState.value.selectedCoin
                val tf = _uiState.value.timeFilter
                val klines = repository.getKlineData(coin, tf, 500)
                val json = gson.toJson(klines)
                _uiState.update { it.copy(candleJson = json) }
                _chartCommands.tryEmit(ChartCommand.SetCandleData(json))

                val oiHistory = repository.getOpenInterestHistory(coin, tf)
                val lsHistory = repository.getLongShortHistory(coin, tf)
                _uiState.update { it.copy(oiHistory = oiHistory, lsHistory = lsHistory) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    private fun startWebSocket() {
        wsJob?.cancel()
        val coin = _uiState.value.selectedCoin
        val tf = _uiState.value.timeFilter
        val symbol = "${coin}USDT"

        wsJob = viewModelScope.launch {
            futuresWs.connectStreams(symbol, tf)
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
                            // Real-time liquidation — refresh heatmap periodically
                        }
                    }
                }
        }
    }

    private fun restartWebSocket() {
        wsJob?.cancel()
        startWebSocket()
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

    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                delay(60_000) // 60 seconds — heatmap + stats refresh
                try {
                    val coin = _uiState.value.selectedCoin
                    val data = repository.getAggregatedData(coin)
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
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
        wsJob?.cancel()
        futuresWs.disconnect()
    }
}
