package com.coinlab.app.ui.trading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coinlab.app.data.remote.firebase.TradingSignalRepository
import com.coinlab.app.data.remote.firebase.model.TradingSignal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SocialTradingUiState(
    val signals: List<TradingSignal> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val showCreateDialog: Boolean = false,
    val filterType: String = "ALL"
)

@HiltViewModel
class SocialTradingViewModel @Inject constructor(
    private val repository: TradingSignalRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SocialTradingUiState())
    val uiState: StateFlow<SocialTradingUiState> = _uiState.asStateFlow()

    val currentUserId: String get() = repository.getCurrentUserId()

    init {
        loadSignals()
        viewModelScope.launch { repository.cleanupExpiredSignals() }
    }

    private fun loadSignals() {
        viewModelScope.launch {
            repository.getSignals().collect { signals ->
                _uiState.update { it.copy(signals = signals, isLoading = false) }
            }
        }
    }

    fun toggleLike(signalId: String) {
        viewModelScope.launch {
            try {
                repository.toggleLike(signalId)
            } catch (_: Exception) { }
        }
    }

    fun createSignal(
        coinId: String,
        coinName: String,
        coinSymbol: String,
        signalType: String,
        entryPrice: Double,
        targetPrice: Double,
        stopLoss: Double,
        confidence: Int,
        description: String
    ) {
        viewModelScope.launch {
            try {
                repository.createSignal(
                    coinId = coinId,
                    coinName = coinName,
                    coinSymbol = coinSymbol,
                    signalType = signalType,
                    entryPrice = entryPrice,
                    targetPrice = targetPrice,
                    stopLoss = stopLoss,
                    confidence = confidence,
                    description = description
                )
                _uiState.update { it.copy(showCreateDialog = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun toggleCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = !it.showCreateDialog) }
    }

    fun setFilter(type: String) {
        _uiState.update { it.copy(filterType = type) }
    }

    fun getFilteredSignals(): List<TradingSignal> {
        val signals = _uiState.value.signals
        return when (_uiState.value.filterType) {
            "BUY" -> signals.filter { it.isBuy }
            "SELL" -> signals.filter { !it.isBuy }
            else -> signals
        }
    }
}
