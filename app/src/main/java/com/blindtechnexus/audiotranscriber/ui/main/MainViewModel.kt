package com.blindtechnexus.audiotranscriber.ui.main

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blindtechnexus.audiotranscriber.AudioTranscriberApplication
import com.blindtechnexus.audiotranscriber.data.audio.AudioRecordManager
import com.blindtechnexus.audiotranscriber.data.local.preferences.UserPreferencesManager
import com.blindtechnexus.audiotranscriber.data.local.security.SecureKeyStorage
import com.blindtechnexus.audiotranscriber.data.remote.FileTranscriptionResult
import com.blindtechnexus.audiotranscriber.data.remote.GeminiTranscribeApiClient
import com.blindtechnexus.audiotranscriber.data.remote.GeminiWebSocketClient
import com.blindtechnexus.audiotranscriber.data.remote.LiveConnectionState
import com.blindtechnexus.audiotranscriber.data.remote.model.LiveTranscriptionEvent
import com.blindtechnexus.audiotranscriber.data.repository.TranscriptRepository
import com.blindtechnexus.audiotranscriber.domain.model.*
import com.blindtechnexus.audiotranscriber.service.TranscriptionForegroundService
import com.blindtechnexus.audiotranscriber.ui.navigation.MainTab
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

data class LiveTranscriptionUiState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val elapsedSeconds: Long = 0L,
    val liveSegments: List<TranscriptSegment> = emptyList(),
    val currentStreamingText: String = "",
    val activeSpeaker: String = "Speaker 1",
    val showTimestamps: Boolean = true,
    val isLimitWarning: Boolean = false,
    val isLimitReached: Boolean = false,
    val connectionState: LiveConnectionState = LiveConnectionState.Disconnected,
    val currentAmplitude: Float = 0f,
    val isSpeechDetected: Boolean = false
)

data class FileTranscriptionUiState(
    val selectedFileName: String? = null,
    val selectedFileMimeType: String = "audio/mp3",
    val selectedFileBytes: ByteArray? = null,
    val isTranscribing: Boolean = false,
    val transcriptionProgress: Float = 0f,
    val result: FileTranscriptionResult? = null,
    val activeSummary: String? = null,
    val isSummarizing: Boolean = false,
    val activeTranslation: String? = null,
    val isTranslating: Boolean = false,
    val targetTranslationLanguage: String = "Spanish",
    val selectedKeyword: String? = null,
    val searchQuery: String = "",
    val errorMessage: String? = null
)

enum class HistoryFilter { ALL, LIVE, FILES }

data class WebSearchUiState(
    val isSearching: Boolean = false,
    val result: com.blindtechnexus.audiotranscriber.data.remote.WebSearchGroundingResult? = null,
    val activeTitle: String? = null,
    val errorMessage: String? = null
)

@OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainViewModel(
    application: Application,
    private val transcriptRepository: TranscriptRepository = (application as AudioTranscriberApplication).transcriptRepository,
    private val secureKeyStorage: SecureKeyStorage = (application as AudioTranscriberApplication).secureKeyStorage,
    private val preferencesManager: UserPreferencesManager = (application as AudioTranscriberApplication).userPreferencesManager
) : AndroidViewModel(application) {

    private val audioRecordManager = AudioRecordManager()
    private val webSocketClient = GeminiWebSocketClient()
    private val apiClient = GeminiTranscribeApiClient()

    private val _selectedTab = MutableStateFlow(MainTab.LIVE)
    val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

    private val _liveUiState = MutableStateFlow(LiveTranscriptionUiState())
    val liveUiState: StateFlow<LiveTranscriptionUiState> = _liveUiState.asStateFlow()

    private val _fileUiState = MutableStateFlow(FileTranscriptionUiState())
    val fileUiState: StateFlow<FileTranscriptionUiState> = _fileUiState.asStateFlow()

    private val _historySearchQuery = MutableStateFlow("")
    val historySearchQuery: StateFlow<String> = _historySearchQuery.asStateFlow()

    private val _historyFilter = MutableStateFlow(HistoryFilter.ALL)
    val historyFilter: StateFlow<HistoryFilter> = _historyFilter.asStateFlow()

    private val _showApiKeyMissingDialog = MutableStateFlow(false)
    val showApiKeyMissingDialog: StateFlow<Boolean> = _showApiKeyMissingDialog.asStateFlow()

    private val _webSearchState = MutableStateFlow(WebSearchUiState())
    val webSearchState: StateFlow<WebSearchUiState> = _webSearchState.asStateFlow()

    val userPreferences = preferencesManager
    private val _hasApiKey = MutableStateFlow(secureKeyStorage.hasValidApiKey())
    val hasApiKey: StateFlow<Boolean> = _hasApiKey.asStateFlow()

    private var timerJob: Job? = null
    private var streamingCollectionJob: Job? = null

    // Room Database backed history flow with filter and search
    val allTranscripts: StateFlow<List<Transcript>> = combine(
        _historySearchQuery.debounce(300),
        _historyFilter
    ) { query, filter ->
        query to filter
    }.flatMapLatest { (query, filter) ->
        val baseFlow = if (query.isBlank()) {
            transcriptRepository.allTranscripts
        } else {
            transcriptRepository.searchTranscripts(query)
        }
        baseFlow.map { list ->
            when (filter) {
                HistoryFilter.ALL -> list
                HistoryFilter.LIVE -> list.filter { it.isLive }
                HistoryFilter.FILES -> list.filter { !it.isLive }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Observe AudioRecord amplitude and speech detection
        viewModelScope.launch {
            audioRecordManager.currentAmplitude.collect { amp ->
                _liveUiState.update { it.copy(currentAmplitude = amp) }
            }
        }

        viewModelScope.launch {
            audioRecordManager.isSpeechDetected.collect { isSpeech ->
                _liveUiState.update { it.copy(isSpeechDetected = isSpeech) }
            }
        }

        // Observe WebSocket Connection State
        viewModelScope.launch {
            webSocketClient.connectionState.collect { state ->
                _liveUiState.update { it.copy(connectionState = state) }
            }
        }

        // Observe Audio chunks and forward to WebSocket when connected
        viewModelScope.launch {
            audioRecordManager.audioChunks.collect { chunk ->
                if (_liveUiState.value.isRecording && !_liveUiState.value.isPaused) {
                    webSocketClient.sendAudioChunk(chunk)
                }
            }
        }
    }

    fun refreshApiKeyStatus() {
        _hasApiKey.value = secureKeyStorage.hasValidApiKey()
    }

    fun dismissApiKeyMissingDialog() {
        _showApiKeyMissingDialog.value = false
    }

    fun selectTab(tab: MainTab) {
        _selectedTab.value = tab
    }

    fun setHistorySearchQuery(query: String) {
        _historySearchQuery.value = query
    }

    fun setHistoryFilter(filter: HistoryFilter) {
        _historyFilter.value = filter
    }

    // --- Live Transcription Actions ---

    fun toggleLiveRecording() {
        if (_liveUiState.value.isRecording) {
            stopLiveRecording()
        } else {
            startLiveRecording()
        }
    }

    fun startLiveRecording() {
        refreshApiKeyStatus()
        if (!_hasApiKey.value) {
            _showApiKeyMissingDialog.value = true
            return
        }

        val apiKey = secureKeyStorage.getApiKey()
        val config = preferencesManager.transcriptionConfig.value

        val started = audioRecordManager.startRecording(viewModelScope)
        if (!started) return

        _liveUiState.update {
            it.copy(
                isRecording = true,
                isPaused = false,
                isLimitWarning = false,
                isLimitReached = false
            )
        }

        // Start Foreground Service
        TranscriptionForegroundService.start(
            getApplication(),
            _liveUiState.value.elapsedSeconds,
            "Listening with Gemini 3.5 Transcribe Live..."
        )

        // Connect WebSocket
        webSocketClient.connect(
            apiKey = apiKey,
            languageCode = config.languageCode,
            customVocabulary = config.customVocabulary,
            scope = viewModelScope
        )

        // Listen for live transcription events
        streamingCollectionJob?.cancel()
        streamingCollectionJob = viewModelScope.launch {
            webSocketClient.events.collect { event ->
                when (event) {
                    is LiveTranscriptionEvent.TranscriptionChunk -> {
                        handleIncomingTranscriptionChunk(event.text, event.speaker, event.isFinal)
                    }
                    is LiveTranscriptionEvent.TurnComplete -> {
                        commitCurrentStreamingText()
                    }
                    is LiveTranscriptionEvent.Interrupted -> {
                        commitCurrentStreamingText()
                    }
                    is LiveTranscriptionEvent.ErrorEvent -> {
                        // Handled in connectionState
                    }
                }
            }
        }

        // Start Session Timer (10 minute limit handler)
        startSessionTimer()
    }

    private fun handleIncomingTranscriptionChunk(text: String, speaker: String, isFinal: Boolean) {
        val current = _liveUiState.value
        val config = preferencesManager.transcriptionConfig.value

        // Smart text formatting
        val cleanedText = if (config.smartTranscription) {
            cleanFillerWords(text)
        } else {
            text
        }

        if (isFinal) {
            val newSegment = TranscriptSegment(
                id = UUID.randomUUID().toString(),
                speaker = speaker.ifBlank { current.activeSpeaker },
                text = (current.currentStreamingText + " " + cleanedText).trim(),
                startTimeMs = (current.elapsedSeconds * 1000L).coerceAtLeast(0L),
                endTimeMs = ((current.elapsedSeconds + 3) * 1000L),
                isFillerWordCleaned = config.smartTranscription
            )
            _liveUiState.update {
                it.copy(
                    liveSegments = it.liveSegments + newSegment,
                    currentStreamingText = "",
                    activeSpeaker = speaker
                )
            }
        } else {
            _liveUiState.update {
                it.copy(
                    currentStreamingText = (it.currentStreamingText + " " + cleanedText).trim(),
                    activeSpeaker = speaker
                )
            }
        }
    }

    private fun commitCurrentStreamingText() {
        val current = _liveUiState.value
        if (current.currentStreamingText.isNotBlank()) {
            val newSegment = TranscriptSegment(
                id = UUID.randomUUID().toString(),
                speaker = current.activeSpeaker,
                text = current.currentStreamingText,
                startTimeMs = (current.elapsedSeconds * 1000L),
                endTimeMs = ((current.elapsedSeconds + 2) * 1000L)
            )
            _liveUiState.update {
                it.copy(
                    liveSegments = it.liveSegments + newSegment,
                    currentStreamingText = ""
                )
            }
        }
    }

    private fun cleanFillerWords(raw: String): String {
        return raw.replace("(?i)\\b(um|uh|er|ah|like, you know)\\b".toRegex(), "")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    private fun startSessionTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive && _liveUiState.value.isRecording) {
                delay(1000)
                if (!_liveUiState.value.isPaused) {
                    val newElapsed = _liveUiState.value.elapsedSeconds + 1
                    val isWarning = newElapsed in 540..599 // 9 minutes
                    val isLimitReached = newElapsed >= 600 // 10 minutes

                    _liveUiState.update {
                        it.copy(
                            elapsedSeconds = newElapsed,
                            isLimitWarning = isWarning,
                            isLimitReached = isLimitReached
                        )
                    }

                    if (isLimitReached) {
                        saveLiveTranscript()
                        stopLiveRecording()
                        break
                    }
                }
            }
        }
    }

    fun pauseLiveRecording() {
        audioRecordManager.pauseRecording()
        _liveUiState.update { it.copy(isPaused = true) }
    }

    fun resumeLiveRecording() {
        audioRecordManager.resumeRecording()
        _liveUiState.update { it.copy(isPaused = false) }
    }

    fun stopLiveRecording() {
        commitCurrentStreamingText()
        audioRecordManager.stopRecording()
        webSocketClient.disconnect()
        timerJob?.cancel()
        streamingCollectionJob?.cancel()

        TranscriptionForegroundService.stop(getApplication())

        _liveUiState.update {
            it.copy(
                isRecording = false,
                isPaused = false
            )
        }
    }

    fun clearLiveSession() {
        stopLiveRecording()
        _liveUiState.update {
            LiveTranscriptionUiState()
        }
    }

    fun toggleTimestamps() {
        _liveUiState.update { it.copy(showTimestamps = !it.showTimestamps) }
    }

    fun saveLiveTranscript(title: String? = null) {
        val current = _liveUiState.value
        if (current.liveSegments.isEmpty() && current.currentStreamingText.isEmpty()) return

        commitCurrentStreamingText()
        val allSegs = _liveUiState.value.liveSegments
        val fullText = allSegs.joinToString("\n") { "${it.speaker}: ${it.text}" }

        val transcript = Transcript(
            id = UUID.randomUUID().toString(),
            title = title ?: "Live Recording • ${java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}",
            fullText = fullText,
            segments = allSegs,
            language = preferencesManager.transcriptionConfig.value.languageCode.ifBlank { "Auto-detect" },
            durationSeconds = current.elapsedSeconds,
            isLive = true
        )

        viewModelScope.launch {
            transcriptRepository.saveTranscript(transcript)
        }
    }

    // --- File Transcription Actions ---

    fun onAudioFileSelected(name: String, mimeType: String, bytes: ByteArray) {
        _fileUiState.update {
            it.copy(
                selectedFileName = name,
                selectedFileMimeType = mimeType,
                selectedFileBytes = bytes,
                result = null,
                activeSummary = null,
                activeTranslation = null,
                errorMessage = null
            )
        }
    }

    fun transcribeSelectedFile() {
        refreshApiKeyStatus()
        if (!_hasApiKey.value) {
            _showApiKeyMissingDialog.value = true
            return
        }

        val state = _fileUiState.value
        val bytes = state.selectedFileBytes ?: return
        val fileName = state.selectedFileName ?: "audio_file"
        val apiKey = secureKeyStorage.getApiKey()
        val config = preferencesManager.transcriptionConfig.value

        viewModelScope.launch {
            _fileUiState.update { it.copy(isTranscribing = true, errorMessage = null) }

            val result = apiClient.transcribeAudioFile(
                apiKey = apiKey,
                audioBytes = bytes,
                mimeType = state.selectedFileMimeType,
                config = config,
                fileName = fileName
            )

            result.fold(
                onSuccess = { transcription ->
                    _fileUiState.update {
                        it.copy(
                            isTranscribing = false,
                            result = transcription
                        )
                    }

                    // Save to Room history
                    val transcript = Transcript(
                        id = UUID.randomUUID().toString(),
                        title = transcription.title,
                        fullText = transcription.fullText,
                        segments = transcription.segments,
                        language = transcription.detectedLanguage,
                        durationSeconds = (transcription.segments.maxOfOrNull { it.endTimeMs } ?: 0L) / 1000,
                        isLive = false,
                        keyTopics = transcription.keyTopics,
                        sourceFileName = fileName
                    )
                    transcriptRepository.saveTranscript(transcript)
                },
                onFailure = { error ->
                    _fileUiState.update {
                        it.copy(
                            isTranscribing = false,
                            errorMessage = error.localizedMessage ?: "Transcription failed"
                        )
                    }
                }
            )
        }
    }

    fun summarizeCurrentTranscript() {
        val text = _fileUiState.value.result?.fullText ?: return
        val apiKey = secureKeyStorage.getApiKey()

        viewModelScope.launch {
            _fileUiState.update { it.copy(isSummarizing = true) }
            val res = apiClient.summarizeTranscript(apiKey, text)
            res.fold(
                onSuccess = { summary ->
                    _fileUiState.update { it.copy(isSummarizing = false, activeSummary = summary) }
                },
                onFailure = { err ->
                    _fileUiState.update { it.copy(isSummarizing = false, errorMessage = err.message) }
                }
            )
        }
    }

    fun translateCurrentTranscript(targetLanguage: String) {
        val text = _fileUiState.value.result?.fullText ?: return
        val apiKey = secureKeyStorage.getApiKey()

        viewModelScope.launch {
            _fileUiState.update { it.copy(isTranslating = true, targetTranslationLanguage = targetLanguage) }
            val res = apiClient.translateTranscript(apiKey, text, targetLanguage)
            res.fold(
                onSuccess = { translation ->
                    _fileUiState.update { it.copy(isTranslating = false, activeTranslation = translation) }
                },
                onFailure = { err ->
                    _fileUiState.update { it.copy(isTranslating = false, errorMessage = err.message) }
                }
            )
        }
    }

    // --- Web Search Grounding & Fact-Checking ---

    fun performWebSearchGroundingForCurrentFile() {
        val text = _fileUiState.value.result?.fullText ?: return
        val title = _fileUiState.value.result?.title ?: "Transcript"
        val apiKey = secureKeyStorage.getApiKey()

        viewModelScope.launch {
            _webSearchState.value = WebSearchUiState(isSearching = true, activeTitle = title)
            val res = apiClient.performWebSearchGrounding(apiKey, text)
            res.fold(
                onSuccess = { result ->
                    _webSearchState.value = WebSearchUiState(isSearching = false, result = result, activeTitle = title)
                },
                onFailure = { err ->
                    _webSearchState.value = WebSearchUiState(isSearching = false, errorMessage = err.message, activeTitle = title)
                }
            )
        }
    }

    fun performWebSearchGroundingForTranscript(transcript: Transcript) {
        val apiKey = secureKeyStorage.getApiKey()
        viewModelScope.launch {
            _webSearchState.value = WebSearchUiState(isSearching = true, activeTitle = transcript.title)
            val res = apiClient.performWebSearchGrounding(apiKey, transcript.fullText)
            res.fold(
                onSuccess = { result ->
                    _webSearchState.value = WebSearchUiState(isSearching = false, result = result, activeTitle = transcript.title)
                },
                onFailure = { err ->
                    _webSearchState.value = WebSearchUiState(isSearching = false, errorMessage = err.message, activeTitle = transcript.title)
                }
            )
        }
    }

    fun dismissWebSearchDialog() {
        _webSearchState.value = WebSearchUiState()
    }

    fun setFileSearchQuery(query: String) {
        _fileUiState.update { it.copy(searchQuery = query) }
    }

    fun selectKeyword(keyword: String) {
        _fileUiState.update {
            it.copy(selectedKeyword = if (it.selectedKeyword == keyword) null else keyword)
        }
    }

    fun deleteTranscript(transcript: Transcript) {
        viewModelScope.launch {
            transcriptRepository.deleteTranscriptById(transcript.id)
        }
    }

    // --- Export & Share Helpers ---

    fun copyToClipboard(text: String) {
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Audio Transcriber Transcript", text)
        clipboard.setPrimaryClip(clip)
    }

    fun shareTranscript(text: String, title: String = "Transcript") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        getApplication<Application>().startActivity(Intent.createChooser(intent, "Share Transcript via").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    fun generateSrtContent(transcript: Transcript): String {
        val sb = StringBuilder()
        transcript.segments.forEachIndexed { index, seg ->
            sb.append("${index + 1}\n")
            sb.append("${formatSrtTimestamp(seg.startTimeMs)} --> ${formatSrtTimestamp(seg.endTimeMs)}\n")
            sb.append("[${seg.speaker}] ${seg.text}\n\n")
        }
        if (transcript.segments.isEmpty()) {
            sb.append("1\n00:00:00,000 --> 00:00:10,000\n${transcript.fullText}\n")
        }
        return sb.toString()
    }

    private fun formatSrtTimestamp(ms: Long): String {
        val totalSec = ms / 1000
        val millis = ms % 1000
        val hours = totalSec / 3600
        val mins = (totalSec % 3600) / 60
        val secs = totalSec % 60
        return String.format("%02d:%02d:%02d,%03d", hours, mins, secs, millis)
    }
}
