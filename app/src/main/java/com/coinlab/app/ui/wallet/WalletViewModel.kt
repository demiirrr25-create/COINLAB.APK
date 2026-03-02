package com.coinlab.app.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coinlab.app.data.preferences.AuthPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WalletInfo(
    val id: String = "",
    val type: WalletType = WalletType.METAMASK,
    val address: String = "",
    val label: String = "",
    val isDefault: Boolean = false,
    val connectedAt: Long = System.currentTimeMillis()
)

enum class WalletType(val displayName: String, val icon: String, val color: Long) {
    METAMASK("MetaMask", "🦊", 0xFFF6851B),
    BINANCE("Binance Wallet", "💛", 0xFFF0B90B),
    TRUST_WALLET("Trust Wallet", "🔵", 0xFF3375BB),
    COINBASE("Coinbase Wallet", "🔵", 0xFF0052FF),
    PHANTOM("Phantom", "👻", 0xFFAB9FF2)
}

data class WalletUiState(
    val wallets: List<WalletInfo> = emptyList(),
    val isLoading: Boolean = true,
    val showAddDialog: Boolean = false,
    val selectedWalletType: WalletType = WalletType.METAMASK,
    val newAddress: String = "",
    val newLabel: String = "",
    val addressError: String? = null,
    val isLoggedIn: Boolean = false
)

@HiltViewModel
class WalletViewModel @Inject constructor(
    private val authPreferences: AuthPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(WalletUiState())
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    private val gson = Gson()

    init {
        loadWallets()
    }

    private fun loadWallets() {
        viewModelScope.launch {
            try {
                val isLoggedIn = authPreferences.isLoggedIn.first()
                val walletsJson = authPreferences.connectedWallets.first()
                val type = object : TypeToken<List<WalletInfo>>() {}.type
                val wallets: List<WalletInfo> = try {
                    gson.fromJson(walletsJson, type) ?: emptyList()
                } catch (_: Exception) { emptyList() }

                _uiState.update {
                    it.copy(wallets = wallets, isLoading = false, isLoggedIn = isLoggedIn)
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun showAddDialog() {
        _uiState.update {
            it.copy(showAddDialog = true, newAddress = "", newLabel = "", addressError = null)
        }
    }

    fun hideAddDialog() {
        _uiState.update { it.copy(showAddDialog = false) }
    }

    fun onWalletTypeSelect(type: WalletType) {
        _uiState.update { it.copy(selectedWalletType = type) }
    }

    fun onAddressChange(address: String) {
        _uiState.update { it.copy(newAddress = address, addressError = null) }
    }

    fun onLabelChange(label: String) {
        _uiState.update { it.copy(newLabel = label) }
    }

    fun addWallet() {
        val state = _uiState.value

        if (state.newAddress.isBlank() || state.newAddress.length < 10) {
            _uiState.update { it.copy(addressError = "Geçerli bir cüzdan adresi girin") }
            return
        }

        // Check duplicate
        if (state.wallets.any { it.address == state.newAddress }) {
            _uiState.update { it.copy(addressError = "Bu adres zaten ekli") }
            return
        }

        val wallet = WalletInfo(
            id = "wallet_${System.currentTimeMillis()}",
            type = state.selectedWalletType,
            address = state.newAddress,
            label = state.newLabel.ifBlank { state.selectedWalletType.displayName },
            isDefault = state.wallets.isEmpty()
        )

        val updated = state.wallets + wallet
        _uiState.update { it.copy(wallets = updated, showAddDialog = false) }
        saveWallets(updated)
    }

    fun removeWallet(walletId: String) {
        val updated = _uiState.value.wallets.filter { it.id != walletId }
        _uiState.update { it.copy(wallets = updated) }
        saveWallets(updated)
    }

    fun setDefaultWallet(walletId: String) {
        val updated = _uiState.value.wallets.map {
            it.copy(isDefault = it.id == walletId)
        }
        _uiState.update { it.copy(wallets = updated) }
        saveWallets(updated)
    }

    private fun saveWallets(wallets: List<WalletInfo>) {
        viewModelScope.launch {
            try {
                authPreferences.setConnectedWallets(gson.toJson(wallets))
            } catch (_: Exception) { }
        }
    }
}
