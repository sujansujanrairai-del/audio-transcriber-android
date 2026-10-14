package com.example.data.remote.model

import org.json.JSONArray
import org.json.JSONObject
import android.util.Base64

object GeminiLiveProtocol {

    const val DEFAULT_MODEL = "models/gemini-3.5-transcribe-live"
    const val WS_URL_BASE = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"

    /**
     * Builds setup JSON payload for Gemini Live API
     */
    fun createSetupMessage(
        model: String = DEFAULT_MODEL,
        languageCode: String = "",
        customVocabulary: List<String> = emptyList()
    ): String {
        val setupObj = JSONObject()
        setupObj.put("model", model)

        val genConfig = JSONObject()
        val modalities = JSONArray().apply { put("TEXT") }
        genConfig.put("responseModalities", modalities)
        setupObj.put("generationConfig", genConfig)

        val inputAudioTranscription = JSONObject()
        val langCodes = JSONArray()
        if (languageCode.isNotBlank()) {
            langCodes.put(languageCode)
        }
        inputAudioTranscription.put("languageCodes", langCodes)
        setupObj.put("inputAudioTranscription", inputAudioTranscription)

        if (customVocabulary.isNotEmpty()) {
            val systemInstruction = JSONObject()
            val parts = JSONArray()
            val vocabStr = customVocabulary.take(1000).joinToString(", ")
            parts.put(JSONObject().put("text", "Custom vocabulary terms to recognize accurately: $vocabStr"))
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
    fun createAudioChunkMessage(pcmData: ByteArray): String {
        val base64Data = Base64.encodeToString(pcmData, Base64.NO_WRAP)

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
     * Extracts text from serverContent response.
     * Checks inputTranscription.text as well as modelTurn text.
     */
    fun parseServerContent(jsonString: String): LiveTranscriptionEvent? {
        return try {
            val root = JSONObject(jsonString)

            if (root.has("serverContent")) {
                val serverContent = root.getJSONObject("serverContent")

                // Check inputAudioTranscription
                if (serverContent.has("inputTranscription")) {
                    val inputTrans = serverContent.getJSONObject("inputTranscription")
                    val text = inputTrans.optString("text", "")
                    if (text.isNotBlank()) {
                        return LiveTranscriptionEvent.TranscriptionChunk(
                            text = text,
                            isFinal = inputTrans.optBoolean("isFinal", false),
                            speaker = inputTrans.optString("speaker", "Speaker 1")
                        )
                    }
                }

                // Check modelTurn text
                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val textBuilder = StringBuilder()
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            textBuilder.append(part.optString("text", ""))
                        }
                        val text = textBuilder.toString()
                        if (text.isNotBlank()) {
                            return LiveTranscriptionEvent.TranscriptionChunk(
                                text = text,
                                isFinal = false,
                                speaker = "Speaker 1"
                            )
                        }
                    }
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    return LiveTranscriptionEvent.TurnComplete
                }

                if (serverContent.optBoolean("interrupted", false)) {
                    return LiveTranscriptionEvent.Interrupted
                }
            }

            null
        } catch (_: Exception) {
            null
        }
    }
}

sealed class LiveTranscriptionEvent {
    data class TranscriptionChunk(
        val text: String,
        val isFinal: Boolean = false,
        val speaker: String = "Speaker 1"
    ) : LiveTranscriptionEvent()

    object TurnComplete : LiveTranscriptionEvent()
    object Interrupted : LiveTranscriptionEvent()
}
