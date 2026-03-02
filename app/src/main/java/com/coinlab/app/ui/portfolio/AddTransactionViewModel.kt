package com.coinlab.app.ui.portfolio

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coinlab.app.domain.model.CoinDetail
import com.coinlab.app.domain.model.PortfolioEntry
import com.coinlab.app.domain.repository.CoinRepository
import com.coinlab.app.domain.repository.PortfolioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val coinRepository: CoinRepository,
    private val portfolioRepository: PortfolioRepository
) : ViewModel() {

    private val coinId: String = savedStateHandle.get<String>("coinId") ?: ""

    private val _coinDetail = MutableStateFlow<CoinDetail?>(null)
    val coinDetail: StateFlow<CoinDetail?> = _coinDetail.asStateFlow()

    init {
        loadCoinDetail()
    }

    private fun loadCoinDetail() {
        viewModelScope.launch {
            coinRepository.getCoinDetail(coinId).collectLatest { result ->
                result.fold(
                    onSuccess = { _coinDetail.value = it },
                    onFailure = { /* ignore */ }
                )
            }
        }
    }

    suspend fun addTransaction(entry: PortfolioEntry) {
        portfolioRepository.addEntry(entry)
    }
}
