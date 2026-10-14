package com.example

import com.example.data.remote.model.GeminiLiveProtocol
import com.example.data.remote.model.LiveTranscriptionEvent
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
                        "text": "Hello world from Transcend",
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
        assertEquals("Hello world from Transcend", chunk.text)
        assertTrue(chunk.isFinal)
    }
}
