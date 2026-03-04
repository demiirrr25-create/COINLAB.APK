package com.coinlab.app.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coinlab.app.data.local.dao.PriceAlertDao
import com.coinlab.app.data.local.entity.PriceAlertEntity
import com.coinlab.app.data.preferences.UserPreferences
import com.coinlab.app.data.remote.firebase.PriceAlertFirebaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PriceAlertsUiState(
    val alerts: List<PriceAlertEntity> = emptyList(),
    val activeAlerts: List<PriceAlertEntity> = emptyList(),
    val triggeredAlerts: List<PriceAlertEntity> = emptyList(),
    val isLoading: Boolean = true,
    val selectedTab: AlertTab = AlertTab.ALL,
    val showCreateDialog: Boolean = false,
    val currency: String = "USD"
)

enum class AlertTab { ALL, ACTIVE, TRIGGERED }

@HiltViewModel
class PriceAlertsViewModel @Inject constructor(
    private val priceAlertDao: PriceAlertDao,
    private val userPreferences: UserPreferences,
    private val firebaseRepository: PriceAlertFirebaseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PriceAlertsUiState())
    val uiState: StateFlow<PriceAlertsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val currency = userPreferences.currency.first()
            _uiState.update { it.copy(currency = currency) }
            loadAlerts()
        }
    }

    private fun loadAlerts() {
        viewModelScope.launch {
            priceAlertDao.getAllAlerts().collectLatest { alerts ->
                _uiState.update {
                    it.copy(
                        alerts = alerts,
                        activeAlerts = alerts.filter { a -> a.isActive && !a.isTriggered },
                        triggeredAlerts = alerts.filter { a -> a.isTriggered },
                        isLoading = false
                    )
                }
            }
        }
    }

    fun onTabSelected(tab: AlertTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun createAlert(
        coinId: String,
        coinSymbol: String,
        coinName: String,
        coinImage: String,
        targetPrice: Double,
        isAbove: Boolean
    ) {
        viewModelScope.launch {
            val entity = PriceAlertEntity(
                coinId = coinId,
                coinSymbol = coinSymbol,
                coinName = coinName,
                coinImage = coinImage,
                targetPrice = targetPrice,
                currency = _uiState.value.currency,
                isAbove = isAbove
            )
            priceAlertDao.insert(entity)
            _uiState.update { it.copy(showCreateDialog = false) }
            try { firebaseRepository.syncAllToFirebase() } catch (_: Exception) {}
        }
    }

    fun deleteAlert(alertId: Long) {
        viewModelScope.launch {
            priceAlertDao.deleteById(alertId)
            try { firebaseRepository.deleteAlertFromFirebase(alertId) } catch (_: Exception) {}
        }
    }

    fun toggleAlertActive(alert: PriceAlertEntity) {
        viewModelScope.launch {
            priceAlertDao.update(alert.copy(isActive = !alert.isActive))
        }
    }

    fun showCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = true) }
    }

    fun hideCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = false) }
    }
}
