package com.aus.gemini01.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

// Regions the configured NewsAPI plan is known to serve.
private val supportedCountries = setOf("us")

class SettingsRepository(private val context: Context) {
    private val countryKey = stringPreferencesKey("country_code")
    private val lastNotifiedUrlKey = stringPreferencesKey("last_notified_url")
    private val notificationsEnabledKey = booleanPreferencesKey("notifications_enabled")
    private val languageKey = stringPreferencesKey("preferred_language")
    private val remindersEnabledKey = booleanPreferencesKey("reminders_enabled")
    private val freeTierKey = booleanPreferencesKey("newsapi_free_tier")

    val countryCode: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[countryKey] ?: "us"
        }
        .map { code ->
            // Heal values stored by older app versions that offered countries the
            // current NewsAPI plan doesn't serve (e.g. a persisted "de").
            if (code in supportedCountries) code else "us"
        }

    val preferredLanguage: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[languageKey] ?: "English"
        }

    val remindersEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[remindersEnabledKey] ?: false
        }

    val lastNotifiedUrl: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[lastNotifiedUrlKey]
        }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[notificationsEnabledKey] ?: false
        }

    val newsApiFreeTier: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[freeTierKey] ?: true
        }

    suspend fun setCountryCode(code: String) {
        context.dataStore.edit { preferences ->
            preferences[countryKey] = code
        }
    }

    suspend fun setLastNotifiedUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[lastNotifiedUrlKey] = url
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[notificationsEnabledKey] = enabled
        }
    }

    suspend fun setPreferredLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[languageKey] = language
        }
    }

    suspend fun setRemindersEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[remindersEnabledKey] = enabled
        }
    }

    suspend fun setNewsApiFreeTier(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[freeTierKey] = enabled
        }
    }
}
