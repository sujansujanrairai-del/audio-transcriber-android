package com.blindtechnexus.audiotranscriber.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.blindtechnexus.audiotranscriber.domain.model.Transcript
import com.blindtechnexus.audiotranscriber.domain.model.TranscriptSegment
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "transcripts")
data class TranscriptEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val fullText: String,
    val segmentsJson: String = "[]",
    val language: String = "Auto-detect",
    val durationSeconds: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val isLive: Boolean = true,
    val summary: String? = null,
    val keyTopicsJson: String = "[]",
    val sourceFileName: String? = null,
    val wordCount: Int = 0
) {
    fun toDomain(): Transcript {
        val parsedSegments = mutableListOf<TranscriptSegment>()
        try {
            val jsonArray = JSONArray(segmentsJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                parsedSegments.add(
                    TranscriptSegment(
                        id = obj.optString("id", i.toString()),
                        speaker = obj.optString("speaker", "Speaker 1"),
                        text = obj.optString("text", ""),
                        startTimeMs = obj.optLong("startTimeMs", 0L),
                        endTimeMs = obj.optLong("endTimeMs", 0L),
                        isFillerWordCleaned = obj.optBoolean("isFillerWordCleaned", false),
                        confidence = obj.optDouble("confidence", 0.98).toFloat()
                    )
                )
            }
        } catch (_: Exception) {
            // Ignore parse errors, fallback to empty
        }

        val topicsList = mutableListOf<String>()
        try {
            val topicsArray = JSONArray(keyTopicsJson)
            for (i in 0 until topicsArray.length()) {
                topicsList.add(topicsArray.getString(i))
            }
        } catch (_: Exception) {
            // Ignore parse errors
        }

        return Transcript(
            id = id,
            title = title,
            fullText = fullText,
            segments = parsedSegments,
            language = language,
            durationSeconds = durationSeconds,
            createdAt = createdAt,
            isLive = isLive,
            summary = summary,
            keyTopics = topicsList,
            sourceFileName = sourceFileName,
            wordCount = wordCount
        )
    }

    companion object {
        fun fromDomain(domain: Transcript): TranscriptEntity {
            val segmentsArray = JSONArray()
            domain.segments.forEach { seg ->
                val obj = JSONObject().apply {
                    put("id", seg.id)
                    put("speaker", seg.speaker)
                    put("text", seg.text)
                    put("startTimeMs", seg.startTimeMs)
                    put("endTimeMs", seg.endTimeMs)
                    put("isFillerWordCleaned", seg.isFillerWordCleaned)
                    put("confidence", seg.confidence.toDouble())
                }
                segmentsArray.put(obj)
            }

            val topicsArray = JSONArray()
            domain.keyTopics.forEach { topicsArray.put(it) }

            return TranscriptEntity(
                id = domain.id,
                title = domain.title,
                fullText = domain.fullText,
                segmentsJson = segmentsArray.toString(),
                language = domain.language,
                durationSeconds = domain.durationSeconds,
                createdAt = domain.createdAt,
                isLive = domain.isLive,
                summary = domain.summary,
                keyTopicsJson = topicsArray.toString(),
                sourceFileName = domain.sourceFileName,
                wordCount = domain.wordCount
            )
        }
    }
}
