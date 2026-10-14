package com.example.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.TranscendApplication
import com.example.data.local.preferences.UserPreferencesManager
import com.example.data.local.security.SecureKeyStorage
import com.example.data.remote.ApiValidationResult
import com.example.data.remote.GeminiTranscribeApiClient
import com.example.data.repository.TranscriptRepository
import com.example.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    application: Application,
    private val preferencesManager: UserPreferencesManager = (application as TranscendApplication).userPreferencesManager,
    private val secureKeyStorage: SecureKeyStorage = (application as TranscendApplication).secureKeyStorage,
    private val transcriptRepository: TranscriptRepository = (application as TranscendApplication).transcriptRepository,
    private val apiClient: GeminiTranscribeApiClient = GeminiTranscribeApiClient()
) : AndroidViewModel(application) {

    val themeSettings: StateFlow<ThemeSettings> = preferencesManager.themeSettings
    val transcriptionConfig: StateFlow<TranscriptionConfig> = preferencesManager.transcriptionConfig
    val audioSettings: StateFlow<AudioSettings> = preferencesManager.audioSettings
    val accessibilitySettings: StateFlow<AccessibilitySettings> = preferencesManager.accessibilitySettings

    private val _maskedApiKey = MutableStateFlow(secureKeyStorage.getMaskedApiKey())
    val maskedApiKey: StateFlow<String> = _maskedApiKey.asStateFlow()

    private val _apiKeyUpdateInput = MutableStateFlow("")
    val apiKeyUpdateInput: StateFlow<String> = _apiKeyUpdateInput.asStateFlow()

    private val _keyValidationStatus = MutableStateFlow<String?>(null)
    val keyValidationStatus: StateFlow<String?> = _keyValidationStatus.asStateFlow()

    fun updateTheme(transform: (ThemeSettings) -> ThemeSettings) {
        preferencesManager.updateThemeSettings(transform)
    }

    fun updateTranscriptionConfig(transform: (TranscriptionConfig) -> TranscriptionConfig) {
        preferencesManager.updateTranscriptionConfig(transform)
    }

    fun updateAudioSettings(transform: (AudioSettings) -> AudioSettings) {
        preferencesManager.updateAudioSettings(transform)
    }

    fun updateAccessibilitySettings(transform: (AccessibilitySettings) -> AccessibilitySettings) {
        preferencesManager.updateAccessibilitySettings(transform)
    }

    fun onApiKeyUpdateInputChanged(input: String) {
        _apiKeyUpdateInput.value = input
        _keyValidationStatus.value = null
    }

    fun validateAndSaveNewApiKey(onComplete: () -> Unit) {
        val key = _apiKeyUpdateInput.value.trim()
        if (key.isBlank()) {
            _keyValidationStatus.value = "API key cannot be empty"
            return
        }

        viewModelScope.launch {
            _keyValidationStatus.value = "Validating key with Gemini..."
            val result = apiClient.validateApiKey(key)
            when (result) {
                is ApiValidationResult.Success -> {
                    secureKeyStorage.saveApiKey(key)
                    _maskedApiKey.value = secureKeyStorage.getMaskedApiKey()
                    _apiKeyUpdateInput.value = ""
                    _keyValidationStatus.value = "Key successfully updated and encrypted!"
                    onComplete()
                }
                is ApiValidationResult.Error -> {
                    _keyValidationStatus.value = result.message
                }
            }
        }
    }

    fun clearApiKey() {
        secureKeyStorage.clearApiKey()
        _maskedApiKey.value = "Not Set"
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            transcriptRepository.deleteAll()
        }
    }
}
