package com.example.data.repository

import com.example.data.local.dao.TranscriptDao
import com.example.data.local.entity.TranscriptEntity
import com.example.domain.model.Transcript
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TranscriptRepository(
    private val transcriptDao: TranscriptDao
) {

    val allTranscripts: Flow<List<Transcript>> = transcriptDao.getAllTranscripts().map { list ->
        list.map { it.toDomain() }
    }

    fun getTranscriptById(id: String): Flow<Transcript?> = transcriptDao.getTranscriptById(id).map { entity ->
        entity?.toDomain()
    }

    fun searchTranscripts(query: String): Flow<List<Transcript>> = transcriptDao.searchTranscripts(query).map { list ->
        list.map { it.toDomain() }
    }

    suspend fun saveTranscript(transcript: Transcript) {
        val entity = TranscriptEntity.fromDomain(transcript)
        transcriptDao.insertTranscript(entity)
    }

    suspend fun updateTranscript(transcript: Transcript) {
        val entity = TranscriptEntity.fromDomain(transcript)
        transcriptDao.updateTranscript(entity)
    }

    suspend fun deleteTranscriptById(id: String) {
        transcriptDao.deleteTranscriptById(id)
    }

    suspend fun deleteAll() {
        transcriptDao.deleteAllTranscripts()
    }

    // Export utilities
    fun formatAsTxt(transcript: Transcript): String {
        val sb = StringBuilder()
        sb.append("Title: ${transcript.title}\n")
        sb.append("Date: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(transcript.createdAt))}\n")
        sb.append("Language: ${transcript.language}\n")
        sb.append("Duration: ${formatDuration(transcript.durationSeconds)}\n")
        sb.append("--------------------------------------------------\n\n")

        if (transcript.segments.isNotEmpty()) {
            transcript.segments.forEach { seg ->
                val timeStr = formatMillis(seg.startTimeMs)
                sb.append("[$timeStr] ${seg.speaker}: ${seg.text}\n\n")
            }
        } else {
            sb.append(transcript.fullText)
        }

        if (!transcript.summary.isNullOrBlank()) {
            sb.append("\n--------------------------------------------------\n")
            sb.append("SUMMARY:\n")
            sb.append(transcript.summary)
        }

        return sb.toString()
    }

    fun formatAsSrt(transcript: Transcript): String {
        val sb = StringBuilder()
        transcript.segments.forEachIndexed { index, seg ->
            sb.append("${index + 1}\n")
            val start = formatSrtTime(seg.startTimeMs)
            val end = formatSrtTime(seg.endTimeMs.coerceAtLeast(seg.startTimeMs + 2000L))
            sb.append("$start --> $end\n")
            sb.append("${seg.speaker}: ${seg.text}\n\n")
        }
        return sb.toString()
    }

    fun formatAsVtt(transcript: Transcript): String {
        val sb = StringBuilder()
        sb.append("WEBVTT\n\n")
        transcript.segments.forEachIndexed { index, seg ->
            val start = formatVttTime(seg.startTimeMs)
            val end = formatVttTime(seg.endTimeMs.coerceAtLeast(seg.startTimeMs + 2000L))
            sb.append("${index + 1}\n")
            sb.append("$start --> $end\n")
            sb.append("<v ${seg.speaker}>${seg.text}\n\n")
        }
        return sb.toString()
    }

    private fun formatDuration(seconds: Long): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", mins, secs)
    }

    private fun formatMillis(ms: Long): String {
        val totalSec = ms / 1000
        val mins = totalSec / 60
        val secs = totalSec % 60
        return String.format("%02d:%02d", mins, secs)
    }

    private fun formatSrtTime(ms: Long): String {
        val hours = ms / 3600000
        val mins = (ms % 3600000) / 60000
        val secs = (ms % 60000) / 1000
        val millis = ms % 1000
        return String.format("%02d:%02d:%02d,%03d", hours, mins, secs, millis)
    }

    private fun formatVttTime(ms: Long): String {
        val hours = ms / 3600000
        val mins = (ms % 3600000) / 60000
        val secs = (ms % 60000) / 1000
        val millis = ms % 1000
        return String.format("%02d:%02d:%02d.%03d", hours, mins, secs, millis)
    }
}
