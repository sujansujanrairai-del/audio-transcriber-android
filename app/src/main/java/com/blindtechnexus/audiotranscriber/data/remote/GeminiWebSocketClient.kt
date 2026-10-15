package com.blindtechnexus.audiotranscriber.data.remote

import android.util.Log
import com.blindtechnexus.audiotranscriber.data.remote.model.GeminiLiveProtocol
import com.blindtechnexus.audiotranscriber.data.remote.model.LiveTranscriptionEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow

sealed class LiveConnectionState {
    object Disconnected : LiveConnectionState()
    object Connecting : LiveConnectionState()
    object Connected : LiveConnectionState()
    data class Reconnecting(val attempt: Int, val maxAttempts: Int = 5) : LiveConnectionState()
    data class Error(val message: String) : LiveConnectionState()
}

class GeminiWebSocketClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    private var webSocket: WebSocket? = null
    private var clientScope: CoroutineScope? = null

    private val _connectionState = MutableStateFlow<LiveConnectionState>(LiveConnectionState.Disconnected)
    val connectionState: StateFlow<LiveConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<LiveTranscriptionEvent>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<LiveTranscriptionEvent> = _events.asSharedFlow()

    private var currentApiKey: String = ""
    private var currentLanguageCode: String = ""
    private var currentCustomVocab: List<String> = emptyList()

    private var reconnectAttempt = 0
    private val maxReconnectAttempts = 5
    private var shouldReconnect = false

    fun connect(
        apiKey: String,
        languageCode: String = "",
        customVocabulary: List<String> = emptyList(),
        scope: CoroutineScope
    ) {
        if (apiKey.isBlank()) {
            _connectionState.value = LiveConnectionState.Error("API key is missing")
            return
        }

        currentApiKey = apiKey
        currentLanguageCode = languageCode
        currentCustomVocab = customVocabulary
        clientScope = scope
        shouldReconnect = true
        reconnectAttempt = 0

        initiateConnection()
    }

    private var currentModel: String = GeminiLiveProtocol.DEFAULT_MODEL
    private var isSetupComplete: Boolean = false

    private fun initiateConnection() {
        isSetupComplete = false
        val url = "${GeminiLiveProtocol.WS_URL_BASE}?key=$currentApiKey"
        val request = Request.Builder().url(url).build()

        _connectionState.value = if (reconnectAttempt == 0) {
            LiveConnectionState.Connecting
        } else {
            LiveConnectionState.Reconnecting(reconnectAttempt, maxReconnectAttempts)
        }

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("GeminiWebSocketClient", "WebSocket socket opened using $currentModel. Sending setup message...")
                reconnectAttempt = 0
                _connectionState.value = LiveConnectionState.Connecting

                // Send Setup Message immediately upon connection
                val setupJson = GeminiLiveProtocol.createSetupMessage(
                    model = currentModel,
                    languageCode = currentLanguageCode,
                    customVocabulary = currentCustomVocab
                )
                webSocket.send(setupJson)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val event = GeminiLiveProtocol.parseServerContent(text)
                if (event != null) {
                    when (event) {
                        is LiveTranscriptionEvent.SetupComplete -> {
                            Log.d("GeminiWebSocketClient", "Received setupComplete from Gemini Live API")
                            isSetupComplete = true
                            _connectionState.value = LiveConnectionState.Connected
                        }
                        is LiveTranscriptionEvent.ErrorEvent -> {
                            Log.e("GeminiWebSocketClient", "Server error event: ${event.message}")
                            if (currentModel != GeminiLiveProtocol.FALLBACK_MODEL &&
                                (event.message.contains("not found", ignoreCase = true) ||
                                 event.message.contains("invalid", ignoreCase = true) ||
                                 event.message.contains("unsupported", ignoreCase = true))) {
                                Log.w("GeminiWebSocketClient", "Switching to fallback model ${GeminiLiveProtocol.FALLBACK_MODEL}")
                                currentModel = GeminiLiveProtocol.FALLBACK_MODEL
                                webSocket.close(1000, "Switching to fallback model")
                                clientScope?.launch {
                                    delay(500)
                                    if (shouldReconnect) {
                                        initiateConnection()
                                    }
                                }
                                return
                            }
                            _connectionState.value = LiveConnectionState.Error(event.message)
                        }
                        else -> {
                            // Any content message also implies setup is complete
                            if (!isSetupComplete) {
                                isSetupComplete = true
                                _connectionState.value = LiveConnectionState.Connected
                            }
                        }
                    }

                    clientScope?.launch {
                        _events.emit(event)
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isSetupComplete = false
                val statusCode = response?.code ?: 0
                val errorBody = try { response?.body?.string().orEmpty() } catch (_: Exception) { "" }
                Log.e("GeminiWebSocketClient", "WebSocket Failure: code=$statusCode, msg=${t.message}, body=$errorBody", t)

                if (statusCode in listOf(401, 403)) {
                    shouldReconnect = false
                    _connectionState.value = LiveConnectionState.Error(
                        "Authentication error ($statusCode): Invalid or unauthorized Gemini API key. Please check your API key in Settings."
                    )
                    return
                }

                // If 404 or 400 model error, switch to fallback model and retry
                if ((statusCode == 404 || statusCode == 400) && currentModel != GeminiLiveProtocol.FALLBACK_MODEL) {
                    Log.w("GeminiWebSocketClient", "Switching to fallback model ${GeminiLiveProtocol.FALLBACK_MODEL}")
                    currentModel = GeminiLiveProtocol.FALLBACK_MODEL
                    clientScope?.launch {
                        delay(500)
                        if (shouldReconnect) {
                            initiateConnection()
                        }
                    }
                    return
                }

                val failureMessage = when {
                    statusCode != 0 -> "Connection failed (HTTP $statusCode): ${t.message ?: "Server rejected connection"}"
                    else -> t.message ?: "Connection lost"
                }
                handleDisconnection(failureMessage)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("GeminiWebSocketClient", "WebSocket Closed: $reason ($code)")
                isSetupComplete = false
                if (code != 1000) {
                    handleDisconnection(reason)
                } else {
                    _connectionState.value = LiveConnectionState.Disconnected
                }
            }
        })
    }

    private fun handleDisconnection(reason: String) {
        isSetupComplete = false
        if (shouldReconnect && reconnectAttempt < maxReconnectAttempts) {
            reconnectAttempt++
            val backoffMillis = min(1000L * (2.0.pow(reconnectAttempt.toDouble())).toLong(), 16000L)
            _connectionState.value = LiveConnectionState.Reconnecting(reconnectAttempt, maxReconnectAttempts)

            clientScope?.launch {
                delay(backoffMillis)
                if (shouldReconnect) {
                    initiateConnection()
                }
            }
        } else {
            _connectionState.value = LiveConnectionState.Error(
                if (reconnectAttempt >= maxReconnectAttempts) "Reconnection failed after $maxReconnectAttempts attempts: $reason"
                else reason
            )
        }
    }

    fun sendAudioChunk(pcmData: ByteArray): Boolean {
        val ws = webSocket
        if (ws != null && isSetupComplete && _connectionState.value == LiveConnectionState.Connected) {
            val chunkMessage = GeminiLiveProtocol.createAudioChunkMessage(pcmData)
            return ws.send(chunkMessage)
        }
        return false
    }

    fun disconnect() {
        shouldReconnect = false
        reconnectAttempt = 0
        isSetupComplete = false
        try {
            webSocket?.close(1000, "User stopped session")
        } catch (e: Exception) {
            Log.e("GeminiWebSocketClient", "Error closing WebSocket", e)
        } finally {
            webSocket = null
            _connectionState.value = LiveConnectionState.Disconnected
        }
    }
}
