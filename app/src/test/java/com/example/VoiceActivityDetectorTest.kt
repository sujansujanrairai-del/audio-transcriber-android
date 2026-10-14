package com.example

import com.example.data.audio.VoiceActivityDetector
import com.example.domain.model.VadSensitivity
import org.junit.Assert.*
import org.junit.Test

class VoiceActivityDetectorTest {

    @Test
    fun testSilenceDetection() {
        val vad = VoiceActivityDetector(VadSensitivity.MEDIUM)
        // 1000 zero samples (silence)
        val silencePcm = ByteArray(2000)

        val (isSpeech, rms) = vad.processFrame(silencePcm, silencePcm.size)

        assertFalse("Silence buffer should not be detected as speech", isSpeech)
        assertEquals(0.0, rms, 0.001)
    }

    @Test
    fun testActiveSpeechDetection() {
        val vad = VoiceActivityDetector(VadSensitivity.MEDIUM)
        // Simulated loud audio frame: alternating between high values
        val speechPcm = ByteArray(2000)
        for (i in speechPcm.indices step 2) {
            val sampleShort: Short = 3000
            speechPcm[i] = (sampleShort.toInt() and 0xFF).toByte()
            speechPcm[i + 1] = ((sampleShort.toInt() shr 8) and 0xFF).toByte()
        }

        val (isSpeech, rms) = vad.processFrame(speechPcm, speechPcm.size)

        assertTrue("High amplitude frame should trigger speech flag", isSpeech)
        assertTrue("RMS should exceed 2000", rms > 2000.0)
    }

    @Test
    fun testHangoverFrameHold() {
        val vad = VoiceActivityDetector(VadSensitivity.MEDIUM)

        // Trigger speech
        val speechPcm = ByteArray(2000)
        for (i in speechPcm.indices step 2) {
            val sampleShort: Short = 4000
            speechPcm[i] = (sampleShort.toInt() and 0xFF).toByte()
            speechPcm[i + 1] = ((sampleShort.toInt() shr 8) and 0xFF).toByte()
        }
        vad.processFrame(speechPcm, speechPcm.size)

        // Immediate silence frame: should still hold speech flag due to hangover smoothing
        val silencePcm = ByteArray(2000)
        val (isSpeechAfterSilence, _) = vad.processFrame(silencePcm, silencePcm.size)

        assertTrue("Hangover counter should keep speech active across momentary silence", isSpeechAfterSilence)
    }
}
