package com.coinlab.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coinlab.app.data.preferences.AuthPreferences
import com.coinlab.app.data.remote.api.GitHubApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = true,
    val githubUsername: String = "",
    val rememberMe: Boolean = false,
    val displayName: String = "",
    val avatarUrl: String = "",
    val usernameError: String? = null,
    val generalError: String? = null,
    val loginSuccess: Boolean = false,
    val registerSuccess: Boolean = false,
    // Legacy fields kept for compatibility
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val nameError: String? = null,
    val passwordStrength: Int = 0
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authPreferences: AuthPreferences,
    private val gitHubApi: GitHubApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val loggedIn = authPreferences.isLoggedIn.first()
                _uiState.update { it.copy(isLoggedIn = loggedIn, isLoading = false) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onGitHubUsernameChange(username: String) {
        _uiState.update { it.copy(githubUsername = username, usernameError = null, generalError = null) }
    }

    fun onRememberMeChange(checked: Boolean) {
        _uiState.update { it.copy(rememberMe = checked) }
    }

    // Legacy handlers for compatibility
    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, emailError = null, generalError = null) }
    }
    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, passwordError = null, generalError = null) }
    }
    fun onConfirmPasswordChange(confirmPassword: String) {
        _uiState.update { it.copy(confirmPassword = confirmPassword, confirmPasswordError = null) }
    }
    fun onDisplayNameChange(name: String) {
        _uiState.update { it.copy(displayName = name, nameError = null) }
    }

    fun loginWithGitHub() {
        val username = _uiState.value.githubUsername.trim()

        if (username.isBlank()) {
            _uiState.update { it.copy(usernameError = "GitHub kullanıcı adı girin") }
            return
        }

        if (username.length < 1 || username.contains(" ")) {
            _uiState.update { it.copy(usernameError = "Geçerli bir GitHub kullanıcı adı girin") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, generalError = null) }
            try {
                val user = gitHubApi.getUser(username)
                val displayName = user.name ?: user.login
                val avatarUrl = user.avatar_url

                authPreferences.loginWithGitHub(
                    username = user.login,
                    displayName = displayName,
                    avatarUrl = avatarUrl,
                    rememberMe = _uiState.value.rememberMe
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        loginSuccess = true,
                        displayName = displayName,
                        avatarUrl = avatarUrl
                    )
                }
            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("404") == true -> "GitHub kullanıcısı bulunamadı: @$username"
                    e.message?.contains("403") == true -> "GitHub API limiti aşıldı, lütfen biraz bekleyin"
                    else -> "Bağlantı hatası. İnternet bağlantınızı kontrol edin."
                }
                _uiState.update {
                    it.copy(isLoading = false, generalError = errorMsg)
                }
            }
        }
    }

    // Keep legacy login for skip functionality
    fun login() {
        loginWithGitHub()
    }

    fun register() {
        loginWithGitHub()
    }

    fun logout() {
        viewModelScope.launch {
            try {
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
                githubUsername = "", generalError = null, usernameError = null,
                loginSuccess = false, registerSuccess = false,
                email = "", password = "", confirmPassword = "", displayName = "",
                emailError = null, passwordError = null, confirmPasswordError = null,
                nameError = null
            )
        }
    }
}
