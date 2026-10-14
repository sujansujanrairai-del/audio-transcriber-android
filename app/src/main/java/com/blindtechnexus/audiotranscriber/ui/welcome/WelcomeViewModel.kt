package com.blindtechnexus.audiotranscriber.ui.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blindtechnexus.audiotranscriber.data.local.security.SecureKeyStorage
import com.blindtechnexus.audiotranscriber.data.remote.ApiValidationResult
import com.blindtechnexus.audiotranscriber.data.remote.GeminiTranscribeApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class KeyValidationUiState {
    object Idle : KeyValidationUiState()
    object Validating : KeyValidationUiState()
    object Valid : KeyValidationUiState()
    data class Error(val message: String) : KeyValidationUiState()
}

class WelcomeViewModel(
    private val secureKeyStorage: SecureKeyStorage,
    private val userPreferencesManager: com.blindtechnexus.audiotranscriber.data.local.preferences.UserPreferencesManager,
    private val apiClient: GeminiTranscribeApiClient = GeminiTranscribeApiClient()
) : ViewModel() {

    private val _apiKeyInput = MutableStateFlow(secureKeyStorage.getApiKey())
    val apiKeyInput: StateFlow<String> = _apiKeyInput.asStateFlow()

    private val _validationState = MutableStateFlow<KeyValidationUiState>(
        if (secureKeyStorage.hasValidApiKey()) KeyValidationUiState.Valid else KeyValidationUiState.Idle
    )
    val validationState: StateFlow<KeyValidationUiState> = _validationState.asStateFlow()

    private val _isHowToExpanded = MutableStateFlow(false)
    val isHowToExpanded: StateFlow<Boolean> = _isHowToExpanded.asStateFlow()

    fun onApiKeyChanged(newKey: String) {
        _apiKeyInput.value = newKey
        if (_validationState.value !is KeyValidationUiState.Idle) {
            _validationState.value = KeyValidationUiState.Idle
        }
    }

    fun toggleHowTo() {
        _isHowToExpanded.value = !_isHowToExpanded.value
    }

    fun validateAndSaveKey(onSuccess: (() -> Unit)? = null) {
        val key = _apiKeyInput.value.trim()
        if (key.isBlank()) {
            _validationState.value = KeyValidationUiState.Error("Please enter a valid Gemini API key")
            return
        }

        viewModelScope.launch {
            _validationState.value = KeyValidationUiState.Validating
            val result = apiClient.validateApiKey(key)
            when (result) {
                is ApiValidationResult.Success -> {
                    secureKeyStorage.saveApiKey(key)
                    userPreferencesManager.setWelcomeCompleted(true)
                    _validationState.value = KeyValidationUiState.Valid
                    onSuccess?.invoke()
                }
                is ApiValidationResult.Error -> {
                    _validationState.value = KeyValidationUiState.Error(result.message)
                }
            }
        }
    }

    fun onSkipForNow(onNavigate: () -> Unit) {
        userPreferencesManager.setWelcomeCompleted(true)
        onNavigate()
    }
}
