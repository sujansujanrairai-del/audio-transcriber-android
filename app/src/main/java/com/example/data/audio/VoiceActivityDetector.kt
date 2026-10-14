package com.example.data.audio

import com.example.domain.model.VadSensitivity
import kotlin.math.sqrt

class VoiceActivityDetector(
    var sensitivity: VadSensitivity = VadSensitivity.MEDIUM
) {
    private var hangoverCounter = 0
    private val hangoverFrames = 5 // Keep speech flag for ~150ms after speech drops to prevent chopping words
    private var adaptiveNoiseFloor = 200.0

    /**
     * Analyzes 16-bit PCM buffer and determines if active speech is present.
     * Returns a Pair of (isSpeech: Boolean, rmsEnergy: Double)
     */
    fun processFrame(pcm16Data: ByteArray, length: Int): Pair<Boolean, Double> {
        if (length < 2) return Pair(false, 0.0)

        var sumSquare = 0.0
        val sampleCount = length / 2

        for (i in 0 until length - 1 step 2) {
            val sample = (pcm16Data[i].toInt() and 0xFF) or (pcm16Data[i + 1].toInt() shl 8)
            val sampleShort = sample.toShort()
            sumSquare += sampleShort * sampleShort
        }

        val rms = sqrt(sumSquare / sampleCount)

        // Adaptive noise floor tracking
        if (rms < adaptiveNoiseFloor * 1.5) {
            adaptiveNoiseFloor = (adaptiveNoiseFloor * 0.95) + (rms * 0.05)
        }

        val threshold = when (sensitivity) {
            VadSensitivity.LOW -> VadSensitivity.LOW.thresholdRms
            VadSensitivity.MEDIUM -> VadSensitivity.MEDIUM.thresholdRms
            VadSensitivity.HIGH -> VadSensitivity.HIGH.thresholdRms
            VadSensitivity.AUTO -> (adaptiveNoiseFloor * 2.5).coerceIn(300.0, 1500.0)
        }

        val isInstantSpeech = rms >= threshold

        if (isInstantSpeech) {
            hangoverCounter = hangoverFrames
        } else if (hangoverCounter > 0) {
            hangoverCounter--
        }

        val isSpeech = isInstantSpeech || hangoverCounter > 0
        return Pair(isSpeech, rms)
    }

    fun reset() {
        hangoverCounter = 0
        adaptiveNoiseFloor = 200.0
    }
}
