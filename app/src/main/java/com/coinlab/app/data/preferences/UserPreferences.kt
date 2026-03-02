package com.coinlab.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "coinlab_settings")

class UserPreferences(private val context: Context) {

    companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val CURRENCY = stringPreferencesKey("currency")
        val LANGUAGE = stringPreferencesKey("language")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val PRICE_ALERTS_ENABLED = booleanPreferencesKey("price_alerts_enabled")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val DEFAULT_SORT = stringPreferencesKey("default_sort")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val AVATAR_URI = stringPreferencesKey("avatar_uri")
    }

    val themeMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[THEME_MODE] ?: "system"
    }

    val currency: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[CURRENCY] ?: "USD"
    }

    val language: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[LANGUAGE] ?: "tr"
    }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[NOTIFICATIONS_ENABLED] ?: true
    }

    val priceAlertsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PRICE_ALERTS_ENABLED] ?: true
    }

    val biometricEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[BIOMETRIC_ENABLED] ?: false
    }

    val defaultSort: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DEFAULT_SORT] ?: "market_cap_desc"
    }

    val displayName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DISPLAY_NAME] ?: ""
    }

    val avatarUri: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[AVATAR_URI] ?: ""
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[THEME_MODE] = mode }
    }

    suspend fun setCurrency(currency: String) {
        context.dataStore.edit { it[CURRENCY] = currency }
    }

    suspend fun setLanguage(language: String) {
        context.dataStore.edit { it[LANGUAGE] = language }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setPriceAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PRICE_ALERTS_ENABLED] = enabled }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setDefaultSort(sort: String) {
        context.dataStore.edit { it[DEFAULT_SORT] = sort }
    }

    suspend fun setDisplayName(name: String) {
        context.dataStore.edit { it[DISPLAY_NAME] = name }
    }

    suspend fun setAvatarUri(uri: String) {
        context.dataStore.edit { it[AVATAR_URI] = uri }
    }
}
