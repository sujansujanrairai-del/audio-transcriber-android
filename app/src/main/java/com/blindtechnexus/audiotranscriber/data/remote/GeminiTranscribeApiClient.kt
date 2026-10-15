package com.blindtechnexus.audiotranscriber.data.remote

import android.util.Log
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import com.blindtechnexus.audiotranscriber.domain.model.TranscriptSegment
import com.blindtechnexus.audiotranscriber.domain.model.TranscriptionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed class ApiValidationResult {
    object Success : ApiValidationResult()
    data class Error(val message: String) : ApiValidationResult()
}

data class FileTranscriptionResult(
    val title: String,
    val fullText: String,
    val segments: List<TranscriptSegment>,
    val detectedLanguage: String,
    val keyTopics: List<String> = emptyList(),
    val summary: String? = null
)

data class WebSearchGroundingResult(
    val content: String,
    val sources: List<String> = emptyList()
)

@OptIn(ExperimentalEncodingApi::class)
class GeminiTranscribeApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com"
        const val MODEL_TRANSCRIBE = "gemini-3.5-transcribe"
        const val MODEL_FLASH = "gemini-3.5-flash"
        const val MODEL_TEXT = "gemini-3.5-flash"
    }

    /**
     * Validates Gemini API Key by listing models
     */
    suspend fun validateApiKey(apiKey: String): ApiValidationResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext ApiValidationResult.Error("API key cannot be empty")
        }

        val request = Request.Builder()
            .url("$BASE_URL/v1beta/models?key=${apiKey.trim()}")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    ApiValidationResult.Success
                } else {
                    val code = response.code
                    val body = response.body?.string().orEmpty()
                    val errorMessage = when (code) {
                        401 -> "Authentication error (401): Invalid or unauthorized Gemini API key. Please verify your API key from Google AI Studio."
                        400, 403 -> "Invalid API key ($code): ${parseErrorMessage(body)}"
                        429 -> "Quota exceeded (429). Please check your Gemini API rate limits."
                        else -> "API verification failed (HTTP $code): ${parseErrorMessage(body)}"
                    }
                    ApiValidationResult.Error(errorMessage)
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiTranscribeApi", "Validation error", e)
            ApiValidationResult.Error("Network error: ${e.localizedMessage ?: "Unable to connect to Google AI"}")
        }
    }

    /**
     * Transcribes pre-recorded audio file with Gemini 3.5 Transcribe.
     * Note: gemini-3.5-transcribe does NOT support JSON mode (responseMimeType="application/json").
     * Clean audio + text instructions are sent, and responses are parsed cleanly.
     * If gemini-3.5-transcribe is unavailable or returns an error, it seamlessly falls back to gemini-3.5-flash.
     */
    suspend fun transcribeAudioFile(
        apiKey: String,
        audioBytes: ByteArray,
        mimeType: String,
        config: TranscriptionConfig,
        fileName: String
    ): Result<FileTranscriptionResult> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(Exception("Gemini API key is required. Please configure it in Settings."))
        }

        try {
            val base64Audio = Base64.encode(audioBytes)

            // Construct prompt for gemini-3.5-transcribe (STT format without JSON mode constraints)
            val promptBuilder = StringBuilder()
            promptBuilder.append("Accurately transcribe the provided audio.\n")
            if (config.languageCode.isNotBlank()) {
                promptBuilder.append("- Spoken audio language: ${config.languageCode}\n")
            }
            if (config.smartTranscription) {
                promptBuilder.append("- Smart transcription: remove filler words (um, uh, er, ah, like), fix stuttering and self-corrections, and format proper punctuation.\n")
            } else {
                promptBuilder.append("- Verbatim mode: transcribe word-for-word exactly as spoken.\n")
            }
            if (config.enableSpeakerDiarization) {
                promptBuilder.append("- Distinguish up to ${config.maxSpeakers} speakers. Label each speaker turn clearly as Speaker 1, Speaker 2, etc.\n")
            }
            if (config.wordLevelTimestamps) {
                promptBuilder.append("- Include timestamps in [MM:SS] format at the beginning of each speaker turn.\n")
            }
            if (config.customVocabulary.isNotEmpty()) {
                promptBuilder.append("- Custom Vocabulary (ensure accurate spelling): ${config.customVocabulary.joinToString(", ")}\n")
            }
            promptBuilder.append("\nFormat output as speaker turns with timestamps:\n")
            promptBuilder.append("[00:00] Speaker 1: <transcription>\n")
            promptBuilder.append("[00:15] Speaker 2: <transcription>\n")

            val requestBodyJson = JSONObject().apply {
                val contents = JSONArray()
                val contentObj = JSONObject()
                val parts = JSONArray()

                // Audio part
                val audioPart = JSONObject().apply {
                    val inlineData = JSONObject().apply {
                        put("mimeType", mimeType)
                        put("data", base64Audio)
                    }
                    put("inlineData", inlineData)
                }
                parts.put(audioPart)

                // Instruction part
                val textPart = JSONObject().apply {
                    put("text", promptBuilder.toString())
                }
                parts.put(textPart)

                contentObj.put("parts", parts)
                contents.put(contentObj)
                put("contents", contents)

                // CRITICAL: gemini-3.5-transcribe rejects responseMimeType="application/json" with
                // 400: JSON mode is not enabled for this model.
                // Do NOT pass responseMimeType in generationConfig!
            }

            val url = "$BASE_URL/v1beta/models/$MODEL_TRANSCRIBE:generateContent?key=${apiKey.trim()}"
            val request = Request.Builder()
                .url(url)
                .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    val errorBody = response.body?.string().orEmpty()

                    if (code == 401) {
                        return@withContext Result.failure(Exception("Authentication error (401): Invalid or unauthorized Gemini API key. Please check your key in Settings."))
                    }

                    Log.w("GeminiTranscribeApi", "gemini-3.5-transcribe returned HTTP $code: $errorBody. Falling back to $MODEL_FLASH")
                    // Fallback attempt with gemini-3.5-flash if gemini-3.5-transcribe returns 400, 404, or any error
                    return@withContext transcribeWithFallbackModel(apiKey, audioBytes, mimeType, config, fileName)
                }

                val responseBody = response.body?.string().orEmpty()
                val parsed = parseTranscriptionResponse(responseBody, fileName, config.languageCode)
                Result.success(parsed)
            }
        } catch (e: Exception) {
            Log.e("GeminiTranscribeApi", "Error in transcribeAudioFile, trying fallback model", e)
            try {
                transcribeWithFallbackModel(apiKey, audioBytes, mimeType, config, fileName)
            } catch (fallbackEx: Exception) {
                Result.failure(e)
            }
        }
    }

    private suspend fun transcribeWithFallbackModel(
        apiKey: String,
        audioBytes: ByteArray,
        mimeType: String,
        config: TranscriptionConfig,
        fileName: String
    ): Result<FileTranscriptionResult> = withContext(Dispatchers.IO) {
        val base64Audio = Base64.encode(audioBytes)
        val promptBuilder = StringBuilder()
        promptBuilder.append("Accurately transcribe this audio.\n")
        if (config.languageCode.isNotBlank()) {
            promptBuilder.append("- Audio language: ${config.languageCode}\n")
        }
        if (config.smartTranscription) {
            promptBuilder.append("- Remove filler words (um, uh, ah), fix false starts, and apply clean punctuation.\n")
        }
        if (config.enableSpeakerDiarization) {
            promptBuilder.append("- Identify speakers up to ${config.maxSpeakers} (Speaker 1, Speaker 2).\n")
        }
        if (config.customVocabulary.isNotEmpty()) {
            promptBuilder.append("- Custom Vocabulary: ${config.customVocabulary.joinToString(", ")}\n")
        }
        promptBuilder.append("Return ONLY a valid JSON object matching this schema:\n")
        promptBuilder.append("{\"title\":\"string\",\"detectedLanguage\":\"string\",\"fullText\":\"string\",\"keyTopics\":[\"topic1\",\"topic2\"],\"segments\":[{\"speaker\":\"Speaker 1\",\"startTimeMs\":0,\"endTimeMs\":5000,\"text\":\"sentence\"}]}")

        val requestBodyJson = JSONObject().apply {
            val contents = JSONArray()
            val contentObj = JSONObject()
            val parts = JSONArray()

            parts.put(JSONObject().apply {
                put("inlineData", JSONObject().apply {
                    put("mimeType", mimeType)
                    put("data", base64Audio)
                })
            })
            parts.put(JSONObject().apply { put("text", promptBuilder.toString()) })

            contentObj.put("parts", parts)
            contents.put(contentObj)
            put("contents", contents)
            // gemini-3.5-flash fully supports JSON mode
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.2)
            })
        }

        val url = "$BASE_URL/v1beta/models/$MODEL_FLASH:generateContent?key=${apiKey.trim()}"
        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    val errorBody = response.body?.string().orEmpty()
                    if (code == 401) {
                        return@withContext Result.failure(Exception("Authentication error (401): Invalid or unauthorized Gemini API key."))
                    }
                    return@withContext Result.failure(Exception("Transcription failed ($code): ${parseErrorMessage(errorBody)}"))
                }
                val responseBody = response.body?.string().orEmpty()
                Result.success(parseTranscriptionResponse(responseBody, fileName, config.languageCode))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseTranscriptionResponse(
        jsonResponse: String,
        fallbackTitle: String,
        hintLanguage: String = ""
    ): FileTranscriptionResult {
        return try {
            val root = JSONObject(jsonResponse)
            val candidates = root.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                val errorMsg = root.optJSONObject("error")?.optString("message") ?: "No candidates returned from Gemini"
                throw Exception(errorMsg)
            }
            val candidate = candidates.getJSONObject(0)
            val content = candidate.getJSONObject("content")
            val rawText = content.getJSONArray("parts").getJSONObject(0).getString("text")

            // Check if response contains JSON object
            var cleanText = rawText.trim()
            if (cleanText.startsWith("```json")) {
                cleanText = cleanText.removePrefix("```json")
            } else if (cleanText.startsWith("```")) {
                cleanText = cleanText.removePrefix("```")
            }
            if (cleanText.endsWith("```")) {
                cleanText = cleanText.removeSuffix("```")
            }
            cleanText = cleanText.trim()

            val firstBrace = cleanText.indexOf('{')
            val lastBrace = cleanText.lastIndexOf('}')

            if (firstBrace >= 0 && lastBrace > firstBrace) {
                // Try JSON parsing
                try {
                    val jsonCandidate = cleanText.substring(firstBrace, lastBrace + 1)
                    val data = JSONObject(jsonCandidate)
                    val title = data.optString("title", fallbackTitle.removeSuffix(".mp3").removeSuffix(".wav"))
                    val detectedLang = data.optString("detectedLanguage", if (hintLanguage.isNotBlank()) hintLanguage else "Auto-detected")
                    val fullText = data.optString("fullText", "")

                    val topics = mutableListOf<String>()
                    val topicsArr = data.optJSONArray("keyTopics")
                    if (topicsArr != null) {
                        for (i in 0 until topicsArr.length()) {
                            topics.add(topicsArr.getString(i))
                        }
                    }

                    val segments = mutableListOf<TranscriptSegment>()
                    val segmentsArr = data.optJSONArray("segments")
                    if (segmentsArr != null) {
                        for (i in 0 until segmentsArr.length()) {
                            val segObj = segmentsArr.getJSONObject(i)
                            segments.add(
                                TranscriptSegment(
                                    id = UUID.randomUUID().toString(),
                                    speaker = segObj.optString("speaker", "Speaker 1"),
                                    startTimeMs = segObj.optLong("startTimeMs", 0L),
                                    endTimeMs = segObj.optLong("endTimeMs", 0L),
                                    text = segObj.optString("text", "")
                                )
                            )
                        }
                    }

                    if (segments.isNotEmpty() || fullText.isNotBlank()) {
                        return FileTranscriptionResult(
                            title = title,
                            fullText = fullText.ifBlank { segments.joinToString(" ") { it.text } },
                            segments = segments.ifEmpty {
                                listOf(TranscriptSegment(id = UUID.randomUUID().toString(), speaker = "Speaker 1", text = fullText))
                            },
                            detectedLanguage = detectedLang,
                            keyTopics = topics
                        )
                    }
                } catch (jsonEx: Exception) {
                    Log.d("GeminiTranscribeApi", "Candidate JSON parse failed, falling back to line parser", jsonEx)
                }
            }

            // Parse formatted transcript lines (e.g. "[00:05] Speaker 1: Hello world")
            parseLineBasedTranscript(rawText, fallbackTitle, hintLanguage)
        } catch (e: Exception) {
            Log.e("GeminiTranscribeApi", "Error parsing response, falling back to raw text", e)
            val fallbackText = try {
                val root = JSONObject(jsonResponse)
                root.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
            } catch (_: Exception) {
                jsonResponse.take(500)
            }
            parseLineBasedTranscript(fallbackText, fallbackTitle, hintLanguage)
        }
    }

    private fun parseLineBasedTranscript(
        rawText: String,
        fallbackTitle: String,
        hintLanguage: String
    ): FileTranscriptionResult {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val segments = mutableListOf<TranscriptSegment>()

        // Regex to match timestamps like [01:23] or [00:01:23], speaker labels like Speaker 1:, Person 1:
        val timestampRegex = Regex("""\[(\d{1,2}):(\d{2})(?::(\d{2}))?\]""")
        val speakerRegex = Regex("""(Speaker\s*\d+|Person\s*\d+|Host|Guest|[A-Za-z0-9_-]+):""", RegexOption.IGNORE_CASE)

        var currentSpeaker = "Speaker 1"
        var lastTimeMs = 0L

        for (line in lines) {
            val tsMatch = timestampRegex.find(line)
            var timeMs = lastTimeMs
            if (tsMatch != null) {
                val p1 = tsMatch.groupValues[1].toLongOrNull() ?: 0L
                val p2 = tsMatch.groupValues[2].toLongOrNull() ?: 0L
                val p3 = tsMatch.groupValues.getOrNull(3)?.toLongOrNull()
                timeMs = if (p3 != null) {
                    // HH:MM:SS
                    (p1 * 3600 + p2 * 60 + p3) * 1000L
                } else {
                    // MM:SS
                    (p1 * 60 + p2) * 1000L
                }
                lastTimeMs = timeMs
            }

            val lineWithoutTs = line.replace(timestampRegex, "").trim()
            val spMatch = speakerRegex.find(lineWithoutTs)
            if (spMatch != null) {
                currentSpeaker = spMatch.groupValues[1].trim()
            }

            val textContent = lineWithoutTs.replace(speakerRegex, "").trim()
            if (textContent.isNotBlank()) {
                segments.add(
                    TranscriptSegment(
                        id = UUID.randomUUID().toString(),
                        speaker = currentSpeaker,
                        startTimeMs = timeMs,
                        endTimeMs = timeMs + 3000L,
                        text = textContent
                    )
                )
            }
        }

        // Adjust endTimeMs of each segment to the startTimeMs of the next segment
        for (i in 0 until segments.size - 1) {
            val nextStart = segments[i + 1].startTimeMs
            if (nextStart > segments[i].startTimeMs) {
                segments[i] = segments[i].copy(endTimeMs = nextStart)
            }
        }

        val fullText = if (segments.isNotEmpty()) {
            segments.joinToString("\n") { "${it.speaker}: ${it.text}" }
        } else {
            rawText.trim()
        }

        // Extract key topics (frequent notable words / capitalized terms)
        val words = fullText.split("\\s+".toRegex())
            .map { it.trim('.', ',', '!', '?', ':', ';', '"', '\'').lowercase() }
            .filter { it.length > 4 && it !in listOf("speaker", "about", "there", "their", "which", "would", "could", "should", "transcription") }
        val topics = words.groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key.replaceFirstChar { char -> char.uppercase() } }

        val cleanTitle = fallbackTitle.removeSuffix(".mp3").removeSuffix(".wav").removeSuffix(".m4a").removeSuffix(".ogg").replace('_', ' ')

        return FileTranscriptionResult(
            title = cleanTitle.ifBlank { "Audio Transcript" },
            fullText = fullText,
            segments = segments.ifEmpty {
                listOf(TranscriptSegment(id = UUID.randomUUID().toString(), speaker = "Speaker 1", text = rawText))
            },
            detectedLanguage = if (hintLanguage.isNotBlank()) hintLanguage else "Auto-detected",
            keyTopics = topics
        )
    }

    /**
     * Performs Google Search Grounded fact checking and web verification on transcript
     */
    suspend fun performWebSearchGrounding(apiKey: String, transcriptText: String): Result<WebSearchGroundingResult> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(Exception("Gemini API key is required for Web Search."))
        }

        try {
            val prompt = """
                You are a research and fact-checking assistant. Analyze this transcript, verify facts and key entities using Google Search, and provide:
                1. Fact Verification & Context (verify dates, names, facts or claims made in the transcript)
                2. Related Information & Knowledge Summary
                3. Key Sources & References

                Transcript:
                ${transcriptText.take(3500)}
            """.trimIndent()

            val requestBodyJson = JSONObject().apply {
                val contents = JSONArray()
                val contentObj = JSONObject()
                val parts = JSONArray().apply {
                    put(JSONObject().apply { put("text", prompt) })
                }
                contentObj.put("parts", parts)
                contents.put(contentObj)
                put("contents", contents)

                // Add Google Search Tool
                val tools = JSONArray().apply {
                    put(JSONObject().apply {
                        put("googleSearch", JSONObject())
                    })
                }
                put("tools", tools)
            }

            val url = "$BASE_URL/v1beta/models/$MODEL_TEXT:generateContent?key=${apiKey.trim()}"
            val request = Request.Builder()
                .url(url)
                .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    val errorBody = response.body?.string().orEmpty()
                    // Fallback without tools if googleSearch tool is restricted
                    return@use fallbackSearchWithoutTool(apiKey, prompt)
                }

                val body = response.body?.string().orEmpty()
                val root = JSONObject(body)
                val candidate = root.getJSONArray("candidates").getJSONObject(0)
                val text = candidate.getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")

                val sources = mutableListOf<String>()
                val groundingMetadata = candidate.optJSONObject("groundingMetadata")
                if (groundingMetadata != null) {
                    val chunks = groundingMetadata.optJSONArray("groundingChunks")
                    if (chunks != null) {
                        for (i in 0 until chunks.length()) {
                            val chunk = chunks.getJSONObject(i)
                            val web = chunk.optJSONObject("web")
                            val title = web?.optString("title")
                            val uri = web?.optString("uri")
                            if (!uri.isNullOrBlank()) {
                                sources.add("${title ?: "Web Source"}: $uri")
                            }
                        }
                    }
                }

                Result.success(WebSearchGroundingResult(content = text, sources = sources))
            }
        } catch (e: Exception) {
            Log.e("GeminiTranscribeApi", "Error in performWebSearchGrounding", e)
            Result.failure(e)
        }
    }

    private suspend fun fallbackSearchWithoutTool(apiKey: String, prompt: String): Result<WebSearchGroundingResult> = withContext(Dispatchers.IO) {
        try {
            val result = generateSimpleText(apiKey, prompt)
            result.map { WebSearchGroundingResult(content = it, sources = listOf("Grounded via Google Gemini")) }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generates a concise summary with action items and key decisions
     */
    suspend fun summarizeTranscript(apiKey: String, fullText: String): Result<String> = withContext(Dispatchers.IO) {
        val prompt = "Provide a high-quality summary of this transcript including: 1. Executive Summary, 2. Key Takeaways & Decisions, 3. Action Items. Transcript:\n\n$fullText"
        generateSimpleText(apiKey, prompt)
    }

    /**
     * Translates transcript to target language
     */
    suspend fun translateTranscript(apiKey: String, fullText: String, targetLanguage: String): Result<String> = withContext(Dispatchers.IO) {
        val prompt = "Translate the following transcript accurately and naturally into $targetLanguage. Preserve speaker flow and context:\n\n$fullText"
        generateSimpleText(apiKey, prompt)
    }

    private suspend fun generateSimpleText(apiKey: String, prompt: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(Exception("Gemini API key is required. Please set your key in Settings."))
        }

        try {
            val requestBodyJson = JSONObject().apply {
                val contents = JSONArray()
                val contentObj = JSONObject()
                val parts = JSONArray().apply {
                    put(JSONObject().apply { put("text", prompt) })
                }
                contentObj.put("parts", parts)
                contents.put(contentObj)
                put("contents", contents)
            }

            val url = "$BASE_URL/v1beta/models/$MODEL_TEXT:generateContent?key=${apiKey.trim()}"
            val request = Request.Builder()
                .url(url)
                .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    val errorBody = response.body?.string().orEmpty()
                    if (code == 401) {
                        return@withContext Result.failure(Exception("Authentication error (401): Invalid or unauthorized Gemini API key."))
                    }
                    return@withContext Result.failure(Exception("Gemini request failed ($code): ${parseErrorMessage(errorBody)}"))
                }
                val body = response.body?.string().orEmpty()
                val root = JSONObject(body)
                val text = root.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseErrorMessage(errorBody: String): String {
        return try {
            val json = JSONObject(errorBody)
            val errObj = json.optJSONObject("error")
            if (errObj != null) {
                val msg = errObj.optString("message")
                val status = errObj.optString("status")
                if (msg.isNotBlank()) {
                    return if (status.isNotBlank()) "$msg ($status)" else msg
                }
            }
            errorBody.take(150)
        } catch (_: Exception) {
            errorBody.take(150)
        }
    }
}
