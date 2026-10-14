package com.blindtechnexus.audiotranscriber.domain.model

data class TranscriptSegment(
    val id: String,
    val speaker: String = "Speaker 1",
    val text: String,
    val startTimeMs: Long = 0L,
    val endTimeMs: Long = 0L,
    val isFillerWordCleaned: Boolean = false,
    val fillerWordsRemoved: List<String> = emptyList(),
    val confidence: Float = 0.98f
)

data class Transcript(
    val id: String,
    val title: String,
    val fullText: String,
    val segments: List<TranscriptSegment> = emptyList(),
    val language: String = "Auto-detect",
    val durationSeconds: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val isLive: Boolean = true,
    val summary: String? = null,
    val keyTopics: List<String> = emptyList(),
    val sourceFileName: String? = null,
    val wordCount: Int = fullText.split("\\s+".toRegex()).count { it.isNotBlank() }
)
