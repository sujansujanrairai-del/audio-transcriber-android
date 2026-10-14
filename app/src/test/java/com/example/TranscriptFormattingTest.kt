package com.example

import com.example.data.repository.TranscriptRepository
import com.example.domain.model.Transcript
import com.example.domain.model.TranscriptSegment
import org.junit.Assert.*
import org.junit.Test

class TranscriptFormattingTest {

    @Test
    fun testTxtAndSrtFormatting() {
        val repo = TranscriptRepository(object : com.example.data.local.dao.TranscriptDao {
            override fun getAllTranscripts() = kotlinx.coroutines.flow.emptyFlow<List<com.example.data.local.entity.TranscriptEntity>>()
            override fun getTranscriptById(id: String) = kotlinx.coroutines.flow.emptyFlow<com.example.data.local.entity.TranscriptEntity?>()
            override fun searchTranscripts(query: String) = kotlinx.coroutines.flow.emptyFlow<List<com.example.data.local.entity.TranscriptEntity>>()
            override fun getTranscriptsByType(isLive: Boolean) = kotlinx.coroutines.flow.emptyFlow<List<com.example.data.local.entity.TranscriptEntity>>()
            override suspend fun insertTranscript(transcript: com.example.data.local.entity.TranscriptEntity) {}
            override suspend fun updateTranscript(transcript: com.example.data.local.entity.TranscriptEntity) {}
            override suspend fun deleteTranscriptById(id: String) {}
            override suspend fun deleteAllTranscripts() {}
        })

        val transcript = Transcript(
            id = "test-1",
            title = "Project Sync",
            fullText = "Good morning everyone. Let's begin the review.",
            segments = listOf(
                TranscriptSegment(id = "1", speaker = "Speaker 1", text = "Good morning everyone.", startTimeMs = 1000L, endTimeMs = 3000L),
                TranscriptSegment(id = "2", speaker = "Speaker 2", text = "Let's begin the review.", startTimeMs = 3500L, endTimeMs = 6000L)
            ),
            durationSeconds = 6L
        )

        val txt = repo.formatAsTxt(transcript)
        assertTrue(txt.contains("Title: Project Sync"))
        assertTrue(txt.contains("Speaker 1: Good morning everyone."))

        val srt = repo.formatAsSrt(transcript)
        assertTrue(srt.contains("00:00:01,000 --> 00:00:03,000"))
        assertTrue(srt.contains("Speaker 1: Good morning everyone."))

        val vtt = repo.formatAsVtt(transcript)
        assertTrue(vtt.startsWith("WEBVTT"))
        assertTrue(vtt.contains("<v Speaker 1>Good morning everyone."))
    }
}
