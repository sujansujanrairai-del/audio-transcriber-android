package com.example.data.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.example.domain.model.VadSensitivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioRecordManager(
    private val vadSensitivity: VadSensitivity = VadSensitivity.MEDIUM
) {
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val vad = VoiceActivityDetector(vadSensitivity)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _currentAmplitude = MutableStateFlow(0f)
    val currentAmplitude: StateFlow<Float> = _currentAmplitude.asStateFlow()

    private val _isSpeechDetected = MutableStateFlow(false)
    val isSpeechDetected: StateFlow<Boolean> = _isSpeechDetected.asStateFlow()

    private val _audioChunks = MutableSharedFlow<ByteArray>(replay = 0, extraBufferCapacity = 64)
    val audioChunks: SharedFlow<ByteArray> = _audioChunks.asSharedFlow()

    val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    fun updateVadSensitivity(sensitivity: VadSensitivity) {
        vad.sensitivity = sensitivity
    }

    @SuppressLint("MissingPermission")
    fun startRecording(coroutineScope: CoroutineScope): Boolean {
        if (_isRecording.value) return true

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = (minBufferSize * 2).coerceAtLeast(sampleRate / 10 * 2) // ~100ms buffer chunks

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                // Fallback to standard MIC source if VOICE_RECOGNITION is restricted
                audioRecord?.release()
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("AudioRecordManager", "AudioRecord initialization failed")
                return false
            }

            audioRecord?.startRecording()
            _isRecording.value = true
            _isPaused.value = false
            vad.reset()

            recordingJob = coroutineScope.launch(Dispatchers.IO) {
                val buffer = ByteArray(bufferSize)

                while (isActive && _isRecording.value) {
                    if (_isPaused.value) {
                        delay(50)
                        continue
                    }

                    val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (readBytes > 0) {
                        val pcmChunk = buffer.copyOf(readBytes)
                        val (isSpeech, rms) = vad.processFrame(pcmChunk, readBytes)

                        _isSpeechDetected.value = isSpeech

                        // Normalize amplitude 0.0 .. 1.0 with a soft cap for visualizer
                        val normalizedAmp = (rms / 3000.0).toFloat().coerceIn(0f, 1f)
                        _currentAmplitude.value = normalizedAmp

                        // Emit audio chunk for streaming
                        _audioChunks.emit(pcmChunk)
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.e("AudioRecordManager", "Error starting AudioRecord", e)
            stopRecording()
            return false
        }
    }

    fun pauseRecording() {
        _isPaused.value = true
        _currentAmplitude.value = 0f
        _isSpeechDetected.value = false
    }

    fun resumeRecording() {
        _isPaused.value = false
    }

    fun stopRecording() {
        _isRecording.value = false
        _isPaused.value = false
        _currentAmplitude.value = 0f
        _isSpeechDetected.value = false

        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e("AudioRecordManager", "Error releasing AudioRecord", e)
        } finally {
            audioRecord = null
        }
    }
}
