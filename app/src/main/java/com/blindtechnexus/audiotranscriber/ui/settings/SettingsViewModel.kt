package com.blindtechnexus.audiotranscriber.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blindtechnexus.audiotranscriber.AudioTranscriberApplication
import com.blindtechnexus.audiotranscriber.data.local.preferences.UserPreferencesManager
import com.blindtechnexus.audiotranscriber.data.local.security.SecureKeyStorage
import com.blindtechnexus.audiotranscriber.data.remote.ApiValidationResult
import com.blindtechnexus.audiotranscriber.data.remote.GeminiTranscribeApiClient
import com.blindtechnexus.audiotranscriber.data.repository.TranscriptRepository
import com.blindtechnexus.audiotranscriber.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    application: Application,
    private val preferencesManager: UserPreferencesManager = (application as AudioTranscriberApplication).userPreferencesManager,
    private val secureKeyStorage: SecureKeyStorage = (application as AudioTranscriberApplication).secureKeyStorage,
    private val transcriptRepository: TranscriptRepository = (application as AudioTranscriberApplication).transcriptRepository,
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

    private val _isUpdatingModels = MutableStateFlow(false)
    val isUpdatingModels: StateFlow<Boolean> = _isUpdatingModels.asStateFlow()

    private val _updateModelsMessage = MutableStateFlow<String?>(null)
    val updateModelsMessage: StateFlow<String?> = _updateModelsMessage.asStateFlow()

    fun updateTheme(transform: (ThemeSettings) -> ThemeSettings) {
        preferencesManager.updateThemeSettings(transform)
    }

    fun updateTranscriptionConfig(transform: (TranscriptionConfig) -> TranscriptionConfig) {
        preferencesManager.updateTranscriptionConfig(transform)
    }

    fun selectFileTranscriptionModel(model: String) {
        preferencesManager.updateTranscriptionConfig { it.copy(selectedFileModel = model) }
    }

    fun updateModelsFromApi() {
        val key = secureKeyStorage.getApiKey()
        if (key.isBlank()) {
            _updateModelsMessage.value = "Please save a valid Gemini API key first to fetch models."
            return
        }

        viewModelScope.launch {
            _isUpdatingModels.value = true
            _updateModelsMessage.value = "Fetching available models from Gemini API..."
            val result = apiClient.fetchAvailableModels(key)
            result.fold(
                onSuccess = { models ->
                    preferencesManager.updateTranscriptionConfig {
                        it.copy(availableModels = models)
                    }
                    _isUpdatingModels.value = false
                    _updateModelsMessage.value = "Successfully fetched ${models.size} models from Gemini API!"
                },
                onFailure = { err ->
                    _isUpdatingModels.value = false
                    _updateModelsMessage.value = "Failed to update models: ${err.localizedMessage ?: "Unknown error"}"
                }
            )
        }
    }

    fun clearUpdateModelsMessage() {
        _updateModelsMessage.value = null
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
                    // Also auto refresh available models with the newly validated key
                    updateModelsFromApi()
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
