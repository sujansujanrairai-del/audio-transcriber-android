package com.blindtechnexus.audiotranscriber.data.remote.model

import org.json.JSONArray
import org.json.JSONObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object GeminiLiveProtocol {

    const val DEFAULT_MODEL = "models/gemini-3.5-transcribe-live"
    const val FALLBACK_MODEL = "models/gemini-2.5-flash-native-audio-preview-12-2025"
    const val WS_URL_BASE = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"

    /**
     * Builds setup JSON payload for Gemini Live API
     */
    fun createSetupMessage(
        model: String = DEFAULT_MODEL,
        languageCode: String = "",
        customVocabulary: List<String> = emptyList(),
        smartTranscription: Boolean = true
    ): String {
        val formattedModel = if (model.startsWith("models/")) model else "models/$model"
        val setupObj = JSONObject()
        setupObj.put("model", formattedModel)

        val genConfig = JSONObject()
        val modalities = JSONArray().apply { put("TEXT") }
        genConfig.put("responseModalities", modalities)
        setupObj.put("generationConfig", genConfig)

        // Gemini Live Audio Transcription Configuration
        val inputAudioTranscription = JSONObject()
        inputAudioTranscription.put("mode", if (smartTranscription) "SMART" else "VERBATIM")
        if (languageCode.isNotBlank()) {
            val langCodes = JSONArray().apply { put(languageCode) }
            inputAudioTranscription.put("languageCodes", langCodes)
        }
        if (customVocabulary.isNotEmpty()) {
            val vocabArray = JSONArray()
            customVocabulary.take(100).forEach { vocabArray.put(it) }
            inputAudioTranscription.put("customVocabulary", vocabArray)
        }
        setupObj.put("inputAudioTranscription", inputAudioTranscription)

        // Only for fallback conversational models (e.g. gemini-2.5-flash-native-audio), provide transcriber systemInstruction
        if (model.contains("gemini-2.5-flash-native-audio") || model.contains("flash")) {
            val systemInstruction = JSONObject()
            val parts = JSONArray()
            val vocabText = if (customVocabulary.isNotEmpty()) " Custom terms: ${customVocabulary.take(50).joinToString(", ")}." else ""
            parts.put(JSONObject().put("text", "You are a real-time speech transcriber. Accurately transcribe all audio directly into text without conversational commentary.$vocabText"))
            systemInstruction.put("parts", parts)
            setupObj.put("systemInstruction", systemInstruction)
        }

        val root = JSONObject()
        root.put("setup", setupObj)
        return root.toString()
    }

    /**
     * Packages raw 16-bit PCM bytes into realtimeInput mediaChunks
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun createAudioChunkMessage(pcmData: ByteArray): String {
        val base64Data = Base64.encode(pcmData)

        val mediaChunk = JSONObject().apply {
            put("mimeType", "audio/pcm;rate=16000")
            put("data", base64Data)
        }

        val chunks = JSONArray().apply { put(mediaChunk) }
        val realtimeInput = JSONObject().apply {
            put("mediaChunks", chunks)
        }

        val root = JSONObject().apply {
            put("realtimeInput", realtimeInput)
        }
        return root.toString()
    }

    /**
     * Extracts events from server responses.
     * Handles setupComplete, interim_input_transcription, input_audio_transcription,
     * input_transcription, and modelTurn streaming text.
     */
    fun parseServerContent(jsonString: String): LiveTranscriptionEvent? {
        return try {
            val root = JSONObject(jsonString)

            if (root.has("error")) {
                val errObj = root.optJSONObject("error")
                val message = errObj?.optString("message") ?: root.optString("error")
                return LiveTranscriptionEvent.ErrorEvent(message.ifBlank { "Server error" })
            }

            // Check setupComplete / setup_complete at root
            if (root.has("setupComplete") || root.has("setup_complete")) {
                return LiveTranscriptionEvent.SetupComplete
            }

            // Check container: either in "serverContent" or top-level "root"
            val serverContent = root.optJSONObject("serverContent") ?: root

            // 1. Check interim transcription (speculative hypothesis while speaking)
            val interimObj = serverContent.opt("interim_input_transcription")
                ?: serverContent.opt("interimInputTranscription")
                ?: serverContent.opt("interimTranscription")
                ?: serverContent.opt("interim_transcription")

            if (interimObj != null) {
                val text = extractText(interimObj)
                if (text.isNotBlank()) {
                    val speaker = extractSpeaker(interimObj)
                    return LiveTranscriptionEvent.TranscriptionChunk(
                        text = text,
                        isFinal = false,
                        speaker = speaker,
                        isInterim = true
                    )
                }
            }

            // 2. Check finalized input audio transcription
            val finalObj = serverContent.opt("inputAudioTranscription")
                ?: serverContent.opt("inputTranscription")
                ?: serverContent.opt("input_audio_transcription")
                ?: serverContent.opt("input_transcription")

            if (finalObj != null) {
                val text = extractText(finalObj)
                if (text.isNotBlank()) {
                    val isFinal = when {
                        finalObj is JSONObject && finalObj.has("isFinal") -> finalObj.optBoolean("isFinal")
                        finalObj is JSONObject && finalObj.has("is_final") -> finalObj.optBoolean("is_final")
                        else -> true
                    }
                    val speaker = extractSpeaker(finalObj)
                    return LiveTranscriptionEvent.TranscriptionChunk(
                        text = text,
                        isFinal = isFinal,
                        speaker = speaker,
                        isInterim = !isFinal
                    )
                }
            }

            // 3. Check modelTurn text (for fallback conversational model output)
            if (serverContent.has("modelTurn")) {
                val modelTurn = serverContent.optJSONObject("modelTurn")
                val parts = modelTurn?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val textBuilder = StringBuilder()
                    for (i in 0 until parts.length()) {
                        val part = parts.optJSONObject(i)
                        val partText = part?.optString("text", "").orEmpty()
                        textBuilder.append(partText)
                    }
                    val text = textBuilder.toString()
                    if (text.isNotBlank()) {
                        return LiveTranscriptionEvent.TranscriptionChunk(
                            text = text,
                            isFinal = false,
                            speaker = "Speaker 1",
                            isInterim = false
                        )
                    }
                }
            }

            if (serverContent.optBoolean("turnComplete", false) ||
                serverContent.optBoolean("turn_complete", false) ||
                root.optBoolean("turnComplete", false)
            ) {
                return LiveTranscriptionEvent.TurnComplete
            }

            if (serverContent.optBoolean("interrupted", false) ||
                root.optBoolean("interrupted", false)
            ) {
                return LiveTranscriptionEvent.Interrupted
            }

            null
        } catch (_: Exception) {
            null
        }
    }

    private fun extractSpeaker(obj: Any?): String {
        return if (obj is JSONObject) {
            obj.optString("speaker", "Speaker 1").ifBlank { "Speaker 1" }
        } else {
            "Speaker 1"
        }
    }

    private fun extractText(data: Any?): String {
        return when (data) {
            is String -> data
            is JSONObject -> {
                var text = data.optString("text", "")
                if (text.isBlank()) {
                    text = data.optString("transcript", "")
                }
                if (text.isBlank()) {
                    val parts = data.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val sb = StringBuilder()
                        for (i in 0 until parts.length()) {
                            val part = parts.optJSONObject(i)
                            sb.append(part?.optString("text", "") ?: "")
                        }
                        text = sb.toString()
                    }
                }
                text
            }
            is JSONArray -> {
                val sb = StringBuilder()
                for (i in 0 until data.length()) {
                    val item = data.opt(i)
                    sb.append(extractText(item)).append(" ")
                }
                sb.toString().trim()
            }
            else -> ""
        }
    }
}

sealed class LiveTranscriptionEvent {
    data class TranscriptionChunk(
        val text: String,
        val isFinal: Boolean = false,
        val speaker: String = "Speaker 1",
        val isInterim: Boolean = false
    ) : LiveTranscriptionEvent()

    object SetupComplete : LiveTranscriptionEvent()
    object TurnComplete : LiveTranscriptionEvent()
    object Interrupted : LiveTranscriptionEvent()
    data class ErrorEvent(val message: String) : LiveTranscriptionEvent()
}
