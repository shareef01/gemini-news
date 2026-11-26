package com.aus.gemini01.ui

import com.aus.gemini01.data.SettingsRepository
import com.aus.gemini01.data.ai.AiDiagnostics
import com.aus.gemini01.data.ai.AiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsManager(
    private val settingsRepository: SettingsRepository,
    aiRepository: AiRepository,
    private val scope: CoroutineScope
) {
    val countryCode: StateFlow<String> = settingsRepository.countryCode
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), "us")

    val notificationsEnabled: StateFlow<Boolean> = settingsRepository.notificationsEnabled
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), false)

    val remindersEnabled: StateFlow<Boolean> = settingsRepository.remindersEnabled
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), false)

    val newsApiFreeTier: StateFlow<Boolean> = settingsRepository.newsApiFreeTier
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), true)

    val preferredLanguage: StateFlow<String> = settingsRepository.preferredLanguage
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), "English")

    val aiDiagnostics: StateFlow<AiDiagnostics> = aiRepository.diagnostics
        .stateIn(
            scope,
            SharingStarted.WhileSubscribed(5000),
            AiDiagnostics(
                date = "",
                requestsByFeature = emptyMap(),
                cacheHits = 0,
                cacheMisses = 0,
                errorsByType = emptyMap(),
                lastRequestAt = null
            )
        )

    fun setCountryCode(code: String) {
        scope.launch {
            settingsRepository.setCountryCode(code)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        scope.launch {
            settingsRepository.setNotificationsEnabled(enabled)
        }
    }

    fun setRemindersEnabled(enabled: Boolean) {
        scope.launch {
            settingsRepository.setRemindersEnabled(enabled)
        }
    }

    fun setNewsApiFreeTier(enabled: Boolean) {
        scope.launch {
            settingsRepository.setNewsApiFreeTier(enabled)
        }
    }

    fun setPreferredLanguage(language: String) {
        scope.launch {
            settingsRepository.setPreferredLanguage(language)
        }
    }
}
