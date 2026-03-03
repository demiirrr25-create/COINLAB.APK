package com.coinlab.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "coinlab_auth")

class AuthPreferences(private val context: Context) {

    companion object {
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val DISPLAY_NAME = stringPreferencesKey("auth_display_name")
        val AVATAR_URL = stringPreferencesKey("auth_avatar_url")
        val AUTH_TOKEN = stringPreferencesKey("auth_token")
        val CONNECTED_WALLETS = stringPreferencesKey("connected_wallets")
        val REGISTERED_EMAILS = stringPreferencesKey("registered_emails")
        val GITHUB_USERNAME = stringPreferencesKey("github_username")
        val REMEMBER_ME = booleanPreferencesKey("remember_me")
        val GOOGLE_ID_TOKEN = stringPreferencesKey("google_id_token")
        val GOOGLE_UID = stringPreferencesKey("google_uid")
        val AUTH_PROVIDER = stringPreferencesKey("auth_provider")
    }

    val isLoggedIn: Flow<Boolean> = context.authDataStore.data.map { prefs ->
        prefs[IS_LOGGED_IN] ?: false
    }

    val userEmail: Flow<String> = context.authDataStore.data.map { prefs ->
        prefs[USER_EMAIL] ?: ""
    }

    val displayName: Flow<String> = context.authDataStore.data.map { prefs ->
        prefs[DISPLAY_NAME] ?: ""
    }

    val avatarUrl: Flow<String> = context.authDataStore.data.map { prefs ->
        prefs[AVATAR_URL] ?: ""
    }

    val authToken: Flow<String> = context.authDataStore.data.map { prefs ->
        prefs[AUTH_TOKEN] ?: ""
    }

    val connectedWallets: Flow<String> = context.authDataStore.data.map { prefs ->
        prefs[CONNECTED_WALLETS] ?: "[]"
    }

    val registeredEmails: Flow<String> = context.authDataStore.data.map { prefs ->
        prefs[REGISTERED_EMAILS] ?: ""
    }

    val githubUsername: Flow<String> = context.authDataStore.data.map { prefs ->
        prefs[GITHUB_USERNAME] ?: ""
    }

    val rememberMe: Flow<Boolean> = context.authDataStore.data.map { prefs ->
        prefs[REMEMBER_ME] ?: false
    }

    val authProvider: Flow<String> = context.authDataStore.data.map { prefs ->
        prefs[AUTH_PROVIDER] ?: ""
    }

    suspend fun login(email: String, displayName: String, token: String) {
        context.authDataStore.edit { prefs ->
            prefs[IS_LOGGED_IN] = true
            prefs[USER_EMAIL] = email
            prefs[DISPLAY_NAME] = displayName
            prefs[AUTH_TOKEN] = token
        }
    }

    suspend fun loginWithGitHub(
        username: String,
        displayName: String,
        avatarUrl: String,
        rememberMe: Boolean
    ) {
        context.authDataStore.edit { prefs ->
            prefs[IS_LOGGED_IN] = true
            prefs[GITHUB_USERNAME] = username
            prefs[DISPLAY_NAME] = displayName
            prefs[AVATAR_URL] = avatarUrl
            prefs[USER_EMAIL] = "$username@github.com"
            prefs[AUTH_TOKEN] = "github_${System.currentTimeMillis()}"
            prefs[REMEMBER_ME] = rememberMe
            prefs[AUTH_PROVIDER] = "github"
        }
    }

    suspend fun loginWithGoogle(
        uid: String,
        email: String,
        displayName: String,
        photoUrl: String,
        idToken: String,
        rememberMe: Boolean
    ) {
        context.authDataStore.edit { prefs ->
            prefs[IS_LOGGED_IN] = true
            prefs[GOOGLE_UID] = uid
            prefs[USER_EMAIL] = email
            prefs[DISPLAY_NAME] = displayName
            prefs[AVATAR_URL] = photoUrl
            prefs[GOOGLE_ID_TOKEN] = idToken
            prefs[AUTH_TOKEN] = "google_${System.currentTimeMillis()}"
            prefs[REMEMBER_ME] = rememberMe
            prefs[AUTH_PROVIDER] = "google"
        }
    }

    suspend fun register(email: String, passwordHash: String, displayName: String) {
        context.authDataStore.edit { prefs ->
            val existing = prefs[REGISTERED_EMAILS] ?: ""
            val emails = if (existing.isEmpty()) email else "$existing,$email"
            prefs[REGISTERED_EMAILS] = emails
            prefs[IS_LOGGED_IN] = true
            prefs[USER_EMAIL] = email
            prefs[DISPLAY_NAME] = displayName
            prefs[AUTH_TOKEN] = "local_${System.currentTimeMillis()}"
        }
    }

    suspend fun logout() {
        context.authDataStore.edit { prefs ->
            prefs[IS_LOGGED_IN] = false
            prefs[AUTH_TOKEN] = ""
            prefs[REMEMBER_ME] = false
        }
    }

    suspend fun updateProfile(displayName: String, avatarUrl: String) {
        context.authDataStore.edit { prefs ->
            prefs[DISPLAY_NAME] = displayName
            prefs[AVATAR_URL] = avatarUrl
        }
    }

    suspend fun setConnectedWallets(walletsJson: String) {
        context.authDataStore.edit { prefs ->
            prefs[CONNECTED_WALLETS] = walletsJson
        }
    }

    suspend fun shouldAutoLogin(): Boolean {
        val prefs = context.authDataStore.data.first()
        return (prefs[IS_LOGGED_IN] == true) && (prefs[REMEMBER_ME] == true)
    }
}
