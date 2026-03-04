package com.coinlab.app.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coinlab.app.data.local.dao.PortfolioDao
import com.coinlab.app.data.local.dao.PriceAlertDao
import com.coinlab.app.data.local.dao.WatchlistDao
import com.coinlab.app.data.preferences.AuthPreferences
import com.coinlab.app.data.preferences.UserPreferences
import com.coinlab.app.data.remote.firebase.CommunityRealtimeRepository
import com.coinlab.app.data.remote.firebase.FirebaseAuthManager
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class ProfileUiState(
    val portfolioCoinCount: Int = 0,
    val watchlistCount: Int = 0,
    val activeAlertsCount: Int = 0,
    val communityPostCount: Int = 0,
    val displayName: String = "",
    val avatarUrl: String = "",
    val isEditing: Boolean = false,
    val editingName: String = "",
    val isUploadingPhoto: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val portfolioDao: PortfolioDao,
    private val watchlistDao: WatchlistDao,
    private val priceAlertDao: PriceAlertDao,
    private val userPreferences: UserPreferences,
    private val authPreferences: AuthPreferences,
    private val communityRepo: CommunityRealtimeRepository,
    private val authManager: FirebaseAuthManager,
    private val storage: FirebaseStorage,
    private val database: FirebaseDatabase
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
                authPreferences.displayName.collect { name ->
                    _uiState.update { it.copy(displayName = name) }
                }
            } catch (_: Exception) { }
        }
        viewModelScope.launch {
            try {
                authPreferences.avatarUrl.collect { url ->
                    _uiState.update { it.copy(avatarUrl = url) }
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
                editingName = it.displayName
            )
        }
    }

    fun cancelEditing() {
        _uiState.update { it.copy(isEditing = false) }
    }

    fun updateEditingName(name: String) {
        _uiState.update { it.copy(editingName = name) }
    }

    /**
     * Upload photo from gallery to Firebase Storage, save URL to RTDB + preferences.
     */
    fun uploadPhoto(uri: Uri) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isUploadingPhoto = true) }
                val userId = authManager.getCurrentUserId()
                if (userId.isEmpty()) {
                    _uiState.update { it.copy(isUploadingPhoto = false) }
                    return@launch
                }

                // Upload to Firebase Storage
                val ref = storage.reference.child("avatars/$userId.jpg")
                ref.putFile(uri).await()
                val downloadUrl = ref.downloadUrl.await().toString()

                // Save to RTDB user profile node
                database.reference.child("users").child(userId).updateChildren(
                    mapOf("avatarUrl" to downloadUrl, "displayName" to _uiState.value.displayName)
                ).await()

                // Sync to both preference stores
                authPreferences.updateProfile(_uiState.value.displayName, downloadUrl)
                userPreferences.setAvatarUri(downloadUrl)

                _uiState.update { it.copy(avatarUrl = downloadUrl, isUploadingPhoto = false) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("ProfileVM", "Photo upload failed", e)
                _uiState.update { it.copy(isUploadingPhoto = false) }
            }
        }
    }

    fun saveProfile() {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                val userId = authManager.getCurrentUserId()

                // Sync to both preference stores
                userPreferences.setDisplayName(state.editingName)
                authPreferences.updateProfile(state.editingName, state.avatarUrl)

                // Sync to RTDB user profile node
                if (userId.isNotEmpty()) {
                    database.reference.child("users").child(userId).updateChildren(
                        mapOf("displayName" to state.editingName, "avatarUrl" to state.avatarUrl)
                    ).await()
                }

                _uiState.update {
                    it.copy(
                        displayName = state.editingName,
                        isEditing = false
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.update { it.copy(isEditing = false) }
            }
        }
    }
}
