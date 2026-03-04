package com.coinlab.app.ui.liquidation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coinlab.app.data.remote.firebase.AggregatedLiquidationData
import com.coinlab.app.data.remote.firebase.LiquidationRepository
import com.coinlab.app.data.remote.firebase.LongShortPoint
import com.coinlab.app.data.remote.firebase.OIHistoryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LiquidationUiState(
    val selectedCoin: String = "BTC",
    val timeFilter: String = "5m",
    val threshold: Float = 0.3f,
    val aggregatedData: AggregatedLiquidationData? = null,
    val oiHistory: List<OIHistoryPoint> = emptyList(),
    val lsHistory: List<LongShortPoint> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val selectedBucketIndex: Int = -1
)

@HiltViewModel
class LiquidationMapViewModel @Inject constructor(
    private val repository: LiquidationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiquidationUiState())
    val uiState: StateFlow<LiquidationUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    val availableCoins = listOf(
        "BTC", "ETH", "SOL", "BNB", "XRP",
        "DOGE", "ADA", "AVAX", "MATIC", "DOT",
        "LINK", "UNI", "APT", "ARB", "OP"
    )

    val timeFilters = listOf("1m", "5m", "15m", "1h", "4h")

    init {
        loadData()
        startAutoRefresh()
    }

    fun selectCoin(coin: String) {
        if (coin != _uiState.value.selectedCoin) {
            _uiState.update { it.copy(selectedCoin = coin, isLoading = true, selectedBucketIndex = -1) }
            loadData()
        }
    }

    fun setTimeFilter(filter: String) {
        if (filter != _uiState.value.timeFilter) {
            _uiState.update { it.copy(timeFilter = filter) }
            loadHistoryData()
        }
    }

    fun setThreshold(value: Float) {
        _uiState.update { it.copy(threshold = value) }
    }

    fun selectBucket(index: Int) {
        _uiState.update { it.copy(selectedBucketIndex = index) }
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                val coin = _uiState.value.selectedCoin
                val period = _uiState.value.timeFilter

                // Parallel: aggregated + OI history + L/S history
                val aggregatedJob = launch {
                    try {
                        val data = repository.getAggregatedData(coin)
                        _uiState.update { it.copy(aggregatedData = data) }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        _uiState.update { it.copy(error = "Veri yüklenemedi: ${e.message}") }
                    }
                }

                val oiJob = launch {
                    try {
                        val history = repository.getOpenInterestHistory(coin, period)
                        _uiState.update { it.copy(oiHistory = history) }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                    }
                }

                val lsJob = launch {
                    try {
                        val history = repository.getLongShortHistory(coin, period)
                        _uiState.update { it.copy(lsHistory = history) }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                    }
                }

                aggregatedJob.join()
                oiJob.join()
                lsJob.join()

                _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = null) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = "Veri yüklenemedi: ${e.message}"
                    )
                }
            }
        }
    }

    private fun loadHistoryData() {
        viewModelScope.launch {
            try {
                val coin = _uiState.value.selectedCoin
                val period = _uiState.value.timeFilter
                val oiHistory = repository.getOpenInterestHistory(coin, period)
                val lsHistory = repository.getLongShortHistory(coin, period)
                _uiState.update { it.copy(oiHistory = oiHistory, lsHistory = lsHistory) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                delay(30_000) // 30 seconds
                try {
                    val coin = _uiState.value.selectedCoin
                    val data = repository.getAggregatedData(coin)
                    _uiState.update { it.copy(aggregatedData = data) }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
    }
}
