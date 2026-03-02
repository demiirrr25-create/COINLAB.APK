package com.coinlab.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coinlab.app.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: String = "system",
    val currency: String = "USD",
    val language: String = "tr",
    val notificationsEnabled: Boolean = true,
    val priceAlertsEnabled: Boolean = true,
    val biometricEnabled: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            launch { userPreferences.themeMode.collectLatest { v -> _uiState.update { it.copy(themeMode = v) } } }
            launch { userPreferences.currency.collectLatest { v -> _uiState.update { it.copy(currency = v) } } }
            launch { userPreferences.language.collectLatest { v -> _uiState.update { it.copy(language = v) } } }
            launch { userPreferences.notificationsEnabled.collectLatest { v -> _uiState.update { it.copy(notificationsEnabled = v) } } }
            launch { userPreferences.priceAlertsEnabled.collectLatest { v -> _uiState.update { it.copy(priceAlertsEnabled = v) } } }
            launch { userPreferences.biometricEnabled.collectLatest { v -> _uiState.update { it.copy(biometricEnabled = v) } } }
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { userPreferences.setThemeMode(mode) }
    }

    fun setCurrency(currency: String) {
        viewModelScope.launch { userPreferences.setCurrency(currency) }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch { userPreferences.setLanguage(lang) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setNotificationsEnabled(enabled) }
    }

    fun setPriceAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setPriceAlertsEnabled(enabled) }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setBiometricEnabled(enabled) }
    }
}
