package com.coinlab.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coinlab.app.data.preferences.AuthPreferences
import com.coinlab.app.data.remote.firebase.FirebaseAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val rememberMe: Boolean = false,
    val displayName: String = "",
    val email: String = "",
    val avatarUrl: String = "",
    val generalError: String? = null,
    val loginSuccess: Boolean = false,
    val registerSuccess: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authPreferences: AuthPreferences,
    private val firebaseAuthManager: FirebaseAuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val loggedIn = authPreferences.isLoggedIn.first()
                _uiState.update { it.copy(isLoggedIn = loggedIn) }
            } catch (_: Exception) { }
        }
    }

    fun onRememberMeChange(checked: Boolean) {
        _uiState.update { it.copy(rememberMe = checked) }
    }

    /**
     * Process Google Sign-In result with the ID token from Credential Manager.
     * Tries Firebase auth first; if Firebase fails, falls back to local Google profile auth.
     */
    fun signInWithGoogle(
        idToken: String,
        googleEmail: String = "",
        googleDisplayName: String = "",
        googlePhotoUrl: String = ""
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, generalError = null) }
            try {
                // Try Firebase authentication first
                val firebaseUser = try {
                    firebaseAuthManager.signInWithGoogle(idToken)
                } catch (firebaseError: Exception) {
                    android.util.Log.w("AuthViewModel", "Firebase Google auth failed, using direct Google profile", firebaseError)
                    null
                }

                val displayName: String
                val email: String
                val photoUrl: String
                val uid: String

                if (firebaseUser != null) {
                    // Firebase auth succeeded — use Firebase user data
                    displayName = firebaseUser.displayName ?: googleDisplayName.ifEmpty { "Kullanıcı" }
                    email = firebaseUser.email ?: googleEmail
                    photoUrl = firebaseUser.photoUrl?.toString() ?: googlePhotoUrl
                    uid = firebaseUser.uid
                } else {
                    // Firebase failed — use Google credential data directly
                    displayName = googleDisplayName.ifEmpty { "Kullanıcı" }
                    email = googleEmail
                    photoUrl = googlePhotoUrl
                    uid = "google_${googleEmail.hashCode()}"
                }

                authPreferences.loginWithGoogle(
                    uid = uid,
                    email = email,
                    displayName = displayName,
                    photoUrl = photoUrl,
                    idToken = idToken,
                    rememberMe = _uiState.value.rememberMe
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        loginSuccess = true,
                        displayName = displayName,
                        email = email,
                        avatarUrl = photoUrl
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                val errorMsg = when {
                    e.message?.contains("network", ignoreCase = true) == true ->
                        "Bağlantı hatası. İnternet bağlantınızı kontrol edin."
                    e.message?.contains("canceled", ignoreCase = true) == true ||
                    e.message?.contains("cancelled", ignoreCase = true) == true ->
                        null // User cancelled, don't show error
                    else ->
                        "Giriş başarısız: ${e.localizedMessage ?: "Bilinmeyen hata"}"
                }
                _uiState.update {
                    it.copy(isLoading = false, generalError = errorMsg)
                }
            }
        }
    }

    /**
     * Handle Google Sign-In error from Credential Manager.
     */
    fun onGoogleSignInError(message: String?) {
        _uiState.update {
            it.copy(
                isLoading = false,
                generalError = message ?: "Google girişi başarısız oldu"
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                firebaseAuthManager.signOut()
                authPreferences.logout()
                _uiState.update {
                    AuthUiState(isLoggedIn = false, isLoading = false)
                }
            } catch (_: Exception) { }
        }
    }

    fun resetState() {
        _uiState.update {
            it.copy(
                generalError = null,
                loginSuccess = false,
                registerSuccess = false
            )
        }
    }
}
