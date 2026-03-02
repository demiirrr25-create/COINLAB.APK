package com.coinlab.app.ui.web3

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coinlab.app.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class Web3UiState(
    val isWalletConnected: Boolean = false,
    val walletAddress: String? = null,
    val walletBalance: Double = 0.0,
    val nftCount: Int = 0,
    val gasPrice: GasPrice? = null,
    val defiPositions: List<DeFiPosition> = emptyList(),
    val tokenBalances: List<TokenBalance> = emptyList(),
    val selectedChain: Chain = Chain.ETHEREUM,
    val isLoading: Boolean = false,
    val currency: String = "USD",
    val error: String? = null
)

data class GasPrice(
    val low: Double,
    val average: Double,
    val high: Double,
    val unit: String = "Gwei"
)

data class DeFiPosition(
    val protocol: String,
    val protocolIcon: String? = null,
    val type: String, // "Staking", "Lending", "LP", "Farming"
    val tokenPair: String,
    val valueUsd: Double,
    val apy: Double?,
    val chain: Chain
)

data class TokenBalance(
    val symbol: String,
    val name: String,
    val image: String? = null,
    val balance: Double,
    val valueUsd: Double,
    val chain: Chain
)

enum class Chain(val displayName: String, val chainId: Int, val symbol: String) {
    ETHEREUM("Ethereum", 1, "ETH"),
    BSC("BNB Chain", 56, "BNB"),
    POLYGON("Polygon", 137, "MATIC"),
    ARBITRUM("Arbitrum", 42161, "ETH"),
    OPTIMISM("Optimism", 10, "ETH"),
    AVALANCHE("Avalanche", 43114, "AVAX"),
    SOLANA("Solana", 0, "SOL")
}

@HiltViewModel
class Web3ViewModel @Inject constructor(
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(Web3UiState())
    val uiState: StateFlow<Web3UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val currency = userPreferences.currency.first()
            _uiState.update { it.copy(currency = currency) }
            loadGasPrice()
        }
    }

    fun selectChain(chain: Chain) {
        _uiState.update { it.copy(selectedChain = chain) }
        if (_uiState.value.isWalletConnected) {
            loadTokenBalances()
        }
    }

    fun connectWallet(address: String) {
        _uiState.update {
            it.copy(
                isWalletConnected = true,
                walletAddress = address,
                isLoading = true
            )
        }
        loadTokenBalances()
        loadDeFiPositions()
    }

    fun disconnectWallet() {
        _uiState.update {
            it.copy(
                isWalletConnected = false,
                walletAddress = null,
                walletBalance = 0.0,
                tokenBalances = emptyList(),
                defiPositions = emptyList(),
                nftCount = 0
            )
        }
    }

    private fun loadGasPrice() {
        viewModelScope.launch {
            try {
                // Simulated gas prices - in production, use etherscan/alchemy API
                _uiState.update {
                    it.copy(
                        gasPrice = GasPrice(
                            low = 15.0,
                            average = 25.0,
                            high = 40.0
                        )
                    )
                }
            } catch (e: Exception) {
                // Gas price is optional
            }
        }
    }

    private fun loadTokenBalances() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // In production, use Alchemy/Moralis API for on-chain data
                // For now, this shows the structure
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }

    private fun loadDeFiPositions() {
        viewModelScope.launch {
            // In production, use DeFi Llama or Zapper API
        }
    }

    fun enterWalletAddress(address: String) {
        if (address.startsWith("0x") && address.length == 42) {
            connectWallet(address)
        }
    }
}
