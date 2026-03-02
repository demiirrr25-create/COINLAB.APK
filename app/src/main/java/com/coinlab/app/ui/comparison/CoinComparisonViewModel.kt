package com.coinlab.app.ui.comparison

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coinlab.app.data.preferences.UserPreferences
import com.coinlab.app.domain.model.Coin
import com.coinlab.app.domain.model.CoinDetail
import com.coinlab.app.domain.model.MarketChart
import com.coinlab.app.domain.repository.CoinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ComparisonUiState(
    val allCoins: List<Coin> = emptyList(),
    val coin1Id: String = "bitcoin",
    val coin2Id: String = "ethereum",
    val coin1Detail: CoinDetail? = null,
    val coin2Detail: CoinDetail? = null,
    val coin1Chart: MarketChart? = null,
    val coin2Chart: MarketChart? = null,
    val isLoading: Boolean = true,
    val selectedDays: String = "7",
    val currency: String = "USD",
    val showCoinPicker: Int = 0 // 0=closed, 1=picking coin1, 2=picking coin2
)

@HiltViewModel
class CoinComparisonViewModel @Inject constructor(
    private val coinRepository: CoinRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ComparisonUiState())
    val uiState: StateFlow<ComparisonUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val currency = userPreferences.currency.first()
            _uiState.update { it.copy(currency = currency) }
            loadCoinList()
            loadComparison()
        }
    }

    private fun loadCoinList() {
        viewModelScope.launch {
            coinRepository.getCoins(
                currency = _uiState.value.currency.lowercase(),
                perPage = 100
            ).collectLatest { result ->
                result.fold(
                    onSuccess = { coins -> _uiState.update { it.copy(allCoins = coins) } },
                    onFailure = { }
                )
            }
        }
    }

    fun loadComparison() {
        val coin1 = _uiState.value.coin1Id
        val coin2 = _uiState.value.coin2Id
        val days = _uiState.value.selectedDays
        val currency = _uiState.value.currency.lowercase()

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            launch {
                coinRepository.getCoinDetail(coin1).collectLatest { result ->
                    result.fold(
                        onSuccess = { detail -> _uiState.update { it.copy(coin1Detail = detail) } },
                        onFailure = { }
                    )
                }
            }
            launch {
                coinRepository.getCoinDetail(coin2).collectLatest { result ->
                    result.fold(
                        onSuccess = { detail -> _uiState.update { it.copy(coin2Detail = detail) } },
                        onFailure = { }
                    )
                }
            }
            launch {
                coinRepository.getMarketChart(coin1, currency, days).collectLatest { result ->
                    result.fold(
                        onSuccess = { chart -> _uiState.update { it.copy(coin1Chart = chart) } },
                        onFailure = { }
                    )
                }
            }
            launch {
                coinRepository.getMarketChart(coin2, currency, days).collectLatest { result ->
                    result.fold(
                        onSuccess = { chart -> _uiState.update { it.copy(coin2Chart = chart, isLoading = false) } },
                        onFailure = { _uiState.update { it.copy(isLoading = false) } }
                    )
                }
            }
        }
    }

    fun selectCoin(slot: Int, coinId: String) {
        if (slot == 1) _uiState.update { it.copy(coin1Id = coinId, showCoinPicker = 0) }
        else _uiState.update { it.copy(coin2Id = coinId, showCoinPicker = 0) }
        loadComparison()
    }

    fun showPicker(slot: Int) {
        _uiState.update { it.copy(showCoinPicker = slot) }
    }

    fun hidePicker() {
        _uiState.update { it.copy(showCoinPicker = 0) }
    }

    fun setDays(days: String) {
        _uiState.update { it.copy(selectedDays = days) }
        loadComparison()
    }
}
