package com.coinlab.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coinlab.app.data.local.dao.PortfolioDao
import com.coinlab.app.data.local.dao.PriceAlertDao
import com.coinlab.app.data.local.dao.WatchlistDao
import com.coinlab.app.data.preferences.UserPreferences
import com.coinlab.app.data.remote.firebase.CommunityFirestoreRepository
import com.coinlab.app.data.remote.firebase.FirebaseAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val portfolioCoinCount: Int = 0,
    val watchlistCount: Int = 0,
    val activeAlertsCount: Int = 0,
    val communityPostCount: Int = 0,
    val displayName: String = "",
    val avatarEmoji: String = "",
    val isEditing: Boolean = false,
    val editingName: String = "",
    val editingAvatar: String = ""
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val portfolioDao: PortfolioDao,
    private val watchlistDao: WatchlistDao,
    private val priceAlertDao: PriceAlertDao,
    private val userPreferences: UserPreferences,
    private val communityRepo: CommunityFirestoreRepository,
    private val authManager: FirebaseAuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadStats()
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            try {
                userPreferences.displayName.collect { name ->
                    _uiState.update { it.copy(displayName = name) }
                }
            } catch (_: Exception) { }
        }
        viewModelScope.launch {
            try {
                userPreferences.avatarUri.collect { avatar ->
                    _uiState.update { it.copy(avatarEmoji = avatar) }
                }
            } catch (_: Exception) { }
        }
    }

    private fun loadStats() {
        viewModelScope.launch {
            try {
                val portfolioCoins = portfolioDao.getDistinctCoinIds()
                _uiState.update { it.copy(portfolioCoinCount = portfolioCoins.size) }
            } catch (_: Exception) { }
        }

        viewModelScope.launch {
            try {
                watchlistDao.getAllWatchlist().collect { watchlist ->
                    _uiState.update { it.copy(watchlistCount = watchlist.size) }
                }
            } catch (_: Exception) { }
        }

        viewModelScope.launch {
            try {
                val activeAlerts = priceAlertDao.getActiveAlerts()
                _uiState.update { it.copy(activeAlertsCount = activeAlerts.size) }
            } catch (_: Exception) { }
        }

        viewModelScope.launch {
            try {
                authManager.ensureAuthenticated()
                val userId = authManager.getCurrentUserId()
                val postCount = communityRepo.getUserPostCount(userId)
                _uiState.update { it.copy(communityPostCount = postCount) }
            } catch (_: Exception) { }
        }
    }

    fun startEditing() {
        _uiState.update {
            it.copy(
                isEditing = true,
                editingName = it.displayName,
                editingAvatar = it.avatarEmoji
            )
        }
    }

    fun cancelEditing() {
        _uiState.update { it.copy(isEditing = false) }
    }

    fun updateEditingName(name: String) {
        _uiState.update { it.copy(editingName = name) }
    }

    fun selectAvatar(emoji: String) {
        _uiState.update { it.copy(editingAvatar = emoji) }
    }

    fun saveProfile() {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                userPreferences.setDisplayName(state.editingName)
                userPreferences.setAvatarUri(state.editingAvatar)
                _uiState.update {
                    it.copy(
                        displayName = state.editingName,
                        avatarEmoji = state.editingAvatar,
                        isEditing = false
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isEditing = false) }
            }
        }
    }
}
