package com.coinlab.app.ui.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coinlab.app.data.preferences.UserPreferences
import com.coinlab.app.data.remote.BinanceCoinMapper
import com.coinlab.app.data.remote.cache.BinanceTickerCache
import com.coinlab.app.domain.model.PortfolioEntry
import com.coinlab.app.domain.model.TransactionType
import com.coinlab.app.domain.repository.PortfolioRepository
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

data class PortfolioUiState(
    val entries: List<PortfolioEntry> = emptyList(),
    val totalValue: Double = 0.0,
    val totalCost: Double = 0.0,
    val totalPnl: Double = 0.0,
    val totalPnlPercentage: Double = 0.0,
    val distribution: Map<String, Double> = emptyMap(),
    val currentPrices: Map<String, Double> = emptyMap(),
    val isLoading: Boolean = true,
    val currency: String = "USD"
)

data class PortfolioHolding(
    val coinId: String,
    val coinSymbol: String,
    val coinName: String,
    val coinImage: String,
    val totalAmount: Double,
    val averageBuyPrice: Double,
    val currentPrice: Double,
    val totalValue: Double,
    val totalCost: Double,
    val pnl: Double,
    val pnlPercentage: Double
)

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    private val portfolioRepository: PortfolioRepository,
    private val tickerCache: BinanceTickerCache,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(PortfolioUiState())
    val uiState: StateFlow<PortfolioUiState> = _uiState.asStateFlow()

    private var portfolioJob: Job? = null

    private val _holdings = MutableStateFlow<List<PortfolioHolding>>(emptyList())
    val holdings: StateFlow<List<PortfolioHolding>> = _holdings.asStateFlow()

    init {
        viewModelScope.launch {
            val currency = userPreferences.currency.first()
            _uiState.update { it.copy(currency = currency) }
            loadPortfolio()
        }
    }

    fun loadPortfolio() {
        portfolioJob?.cancel()
        portfolioJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            portfolioRepository.getAllEntries().collectLatest { entries ->
                _uiState.update { it.copy(entries = entries) }
                calculateHoldings(entries)
            }
        }
    }

    private suspend fun calculateHoldings(entries: List<PortfolioEntry>) {
        try {
            val grouped = entries.groupBy { it.coinId }
            val coinIds = grouped.keys.toList()

            if (coinIds.isEmpty()) {
                _holdings.value = emptyList()
                _uiState.update {
                    it.copy(totalValue = 0.0, totalCost = 0.0, totalPnl = 0.0, totalPnlPercentage = 0.0, isLoading = false)
                }
                return
            }

            // Fetch prices from centralized cache (fast, shared)
            val tickerMap = tickerCache.getTickerMap()

            val holdingsList = mutableListOf<PortfolioHolding>()
            var totalValue = 0.0
            var totalCost = 0.0

            for ((coinId, coinEntries) in grouped) {
                val buyEntries = coinEntries.filter { it.type == TransactionType.BUY }
                val sellEntries = coinEntries.filter { it.type == TransactionType.SELL }

                val totalBought = buyEntries.sumOf { it.amount }
                val totalSold = sellEntries.sumOf { it.amount }
                val netAmount = totalBought - totalSold

                if (netAmount <= 0) continue

                val avgBuyPrice = if (totalBought > 0) {
                    buyEntries.sumOf { it.amount * it.buyPrice } / totalBought
                } else 0.0

                val binanceSymbol = BinanceCoinMapper.getBinanceSymbolByCoinId(coinId)
                val currentPrice = binanceSymbol?.let { tickerMap[it]?.lastPrice?.toDoubleOrNull() } ?: 0.0
                val holdingValue = netAmount * currentPrice
                val holdingCost = netAmount * avgBuyPrice
                val pnl = holdingValue - holdingCost
                val pnlPct = if (holdingCost > 0) (pnl / holdingCost) * 100 else 0.0

                val firstEntry = coinEntries.first()

                holdingsList.add(
                    PortfolioHolding(
                        coinId = coinId,
                        coinSymbol = firstEntry.coinSymbol,
                        coinName = firstEntry.coinName,
                        coinImage = firstEntry.coinImage,
                        totalAmount = netAmount,
                        averageBuyPrice = avgBuyPrice,
                        currentPrice = currentPrice,
                        totalValue = holdingValue,
                        totalCost = holdingCost,
                        pnl = pnl,
                        pnlPercentage = pnlPct
                    )
                )

                totalValue += holdingValue
                totalCost += holdingCost
            }

            val totalPnl = totalValue - totalCost
            val totalPnlPct = if (totalCost > 0) (totalPnl / totalCost) * 100 else 0.0

            _holdings.value = holdingsList.sortedByDescending { it.totalValue }
            _uiState.update {
                it.copy(
                    totalValue = totalValue,
                    totalCost = totalCost,
                    totalPnl = totalPnl,
                    totalPnlPercentage = totalPnlPct,
                    isLoading = false
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun deleteEntry(entryId: Long) {
        viewModelScope.launch {
            portfolioRepository.deleteEntry(entryId)
        }
    }
}
