package com.blindtechnexus.audiotranscriber

import com.blindtechnexus.audiotranscriber.data.remote.model.GeminiLiveProtocol
import com.blindtechnexus.audiotranscriber.data.remote.model.LiveTranscriptionEvent
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GeminiLiveProtocolTest {

    @Test
    fun testSetupMessageStructure() {
        val message = GeminiLiveProtocol.createSetupMessage(
            languageCode = "ne-NP",
            customVocabulary = listOf("Sujan", "Kathmandu")
        )

        val json = JSONObject(message)
        assertTrue(json.has("setup"))

        val setup = json.getJSONObject("setup")
        assertEquals(GeminiLiveProtocol.DEFAULT_MODEL, setup.getString("model"))
        assertTrue(setup.has("generationConfig"))
        assertTrue(setup.has("inputAudioTranscription"))
    }

    @Test
    fun testAudioChunkSerialization() {
        val pcm = byteArrayOf(0, 1, 2, 3, 4, 5)
        val message = GeminiLiveProtocol.createAudioChunkMessage(pcm)

        val json = JSONObject(message)
        assertTrue(json.has("realtimeInput"))
        val realtimeInput = json.getJSONObject("realtimeInput")
        val chunks = realtimeInput.getJSONArray("mediaChunks")
        assertEquals(1, chunks.length())
        assertEquals("audio/pcm;rate=16000", chunks.getJSONObject(0).getString("mimeType"))
    }

    @Test
    fun testServerContentParsing() {
        val serverJson = """
            {
                "serverContent": {
                    "inputTranscription": {
                        "text": "Hello world from Audio Transcriber",
                        "isFinal": true,
                        "speaker": "Speaker 1"
                    }
                }
            }
        """.trimIndent()

        val event = GeminiLiveProtocol.parseServerContent(serverJson)
        assertNotNull(event)
        assertTrue(event is LiveTranscriptionEvent.TranscriptionChunk)

        val chunk = event as LiveTranscriptionEvent.TranscriptionChunk
        assertEquals("Hello world from Audio Transcriber", chunk.text)
        assertTrue(chunk.isFinal)
    }

    @Test
    fun testSetupCompleteParsing() {
        val setupCompleteJson = """{"setupComplete":{}}"""
        val event = GeminiLiveProtocol.parseServerContent(setupCompleteJson)
        assertNotNull(event)
        assertTrue(event is LiveTranscriptionEvent.SetupComplete)
    }

    @Test
    fun testInterimTranscriptionParsing() {
        val serverJson = """
            {
                "serverContent": {
                    "interim_input_transcription": {
                        "text": "Hello world in progress",
                        "speaker": "Speaker 1"
                    }
                }
            }
        """.trimIndent()

        val event = GeminiLiveProtocol.parseServerContent(serverJson)
        assertNotNull(event)
        assertTrue(event is LiveTranscriptionEvent.TranscriptionChunk)

        val chunk = event as LiveTranscriptionEvent.TranscriptionChunk
        assertEquals("Hello world in progress", chunk.text)
        assertFalse(chunk.isFinal)
        assertTrue(chunk.isInterim)
    }

    @Test
    fun testSetupMessageVerbatimMode() {
        val message = GeminiLiveProtocol.createSetupMessage(
            model = "gemini-3.5-transcribe-live",
            smartTranscription = false
        )
        val json = JSONObject(message)
        val setup = json.getJSONObject("setup")
        val inputTrans = setup.getJSONObject("inputAudioTranscription")
        assertEquals("VERBATIM", inputTrans.getString("mode"))
    }

    @Test
    fun testRootLevelTranscriptionParsing() {
        val rootJson = """
            {
                "input_transcription": {
                    "text": "Root level finalized text",
                    "speaker": "Speaker 2",
                    "isFinal": true
                }
            }
        """.trimIndent()
        val event = GeminiLiveProtocol.parseServerContent(rootJson)
        assertNotNull(event)
        assertTrue(event is LiveTranscriptionEvent.TranscriptionChunk)
        val chunk = event as LiveTranscriptionEvent.TranscriptionChunk
        assertEquals("Root level finalized text", chunk.text)
        assertEquals("Speaker 2", chunk.speaker)
        assertTrue(chunk.isFinal)
    }
}
