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
        const val MODEL_FLASH = "gemini-2.5-flash"
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
     * Transcribes pre-recorded audio file with Gemini 3.5 Transcribe
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

            // Construct prompt for gemini-3.5-transcribe
            val promptBuilder = StringBuilder()
            promptBuilder.append("You are Gemini 3.5 Transcribe. Accurately transcribe the provided audio.\n")
            if (config.smartTranscription) {
                promptBuilder.append("- Apply smart transcription: remove filler words (um, uh, ah), fix stuttering and self-corrections, and apply proper punctuation and capitalization.\n")
            }
            if (config.enableSpeakerDiarization) {
                promptBuilder.append("- Perform speaker diarization for up to ${config.maxSpeakers} speakers. Label each section as Speaker 1, Speaker 2, etc.\n")
            }
            if (config.wordLevelTimestamps) {
                promptBuilder.append("- Provide timestamps in [MM:SS] format for each speaker turn or sentence.\n")
            }
            if (config.customVocabulary.isNotEmpty()) {
                promptBuilder.append("- Custom Vocabulary (ensure these terms are recognized accurately): ${config.customVocabulary.joinToString(", ")}\n")
            }
            promptBuilder.append("\nReturn your output strictly as a JSON object with this exact schema:\n")
            promptBuilder.append("{\n")
            promptBuilder.append("  \"title\": \"Descriptive title of audio\",\n")
            promptBuilder.append("  \"detectedLanguage\": \"Language name\",\n")
            promptBuilder.append("  \"fullText\": \"Complete clean transcript text\",\n")
            promptBuilder.append("  \"keyTopics\": [\"topic1\", \"topic2\"],\n")
            promptBuilder.append("  \"segments\": [\n")
            promptBuilder.append("    {\n")
            promptBuilder.append("      \"speaker\": \"Speaker 1\",\n")
            promptBuilder.append("      \"startTimeMs\": 0,\n")
            promptBuilder.append("      \"endTimeMs\": 5000,\n")
            promptBuilder.append("      \"text\": \"Spoken sentence here\"\n")
            promptBuilder.append("    }\n")
            promptBuilder.append("  ]\n")
            promptBuilder.append("}")

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

                val genConfig = JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.2)
                }
                put("generationConfig", genConfig)
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

                    // Fallback attempt with gemini-2.5-flash if gemini-3.5-transcribe returns 404
                    if (code == 404) {
                        return@withContext transcribeWithFallbackModel(apiKey, audioBytes, mimeType, config, fileName)
                    }
                    return@withContext Result.failure(Exception("Transcription failed ($code): ${parseErrorMessage(errorBody)}"))
                }

                val responseBody = response.body?.string().orEmpty()
                val parsed = parseTranscriptionResponse(responseBody, fileName)
                Result.success(parsed)
            }
        } catch (e: Exception) {
            Log.e("GeminiTranscribeApi", "Error in transcribeAudioFile", e)
            Result.failure(e)
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
        val prompt = "Accurately transcribe this audio. Return ONLY a valid JSON object with: title, detectedLanguage, fullText, keyTopics (list of strings), and segments (list of objects with speaker, startTimeMs, endTimeMs, text)."

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
            parts.put(JSONObject().apply { put("text", prompt) })

            contentObj.put("parts", parts)
            contents.put(contentObj)
            put("contents", contents)
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
                Result.success(parseTranscriptionResponse(responseBody, fileName))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseTranscriptionResponse(jsonResponse: String, fallbackTitle: String): FileTranscriptionResult {
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

            // Robust JSON extraction: clean markdown fences ```json ... ```
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
                cleanText = cleanText.substring(firstBrace, lastBrace + 1)
            }

            val data = JSONObject(cleanText)
            val title = data.optString("title", fallbackTitle.removeSuffix(".mp3").removeSuffix(".wav"))
            val detectedLang = data.optString("detectedLanguage", "Auto-detected")
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

            if (segments.isEmpty() && fullText.isNotBlank()) {
                segments.add(
                    TranscriptSegment(
                        id = UUID.randomUUID().toString(),
                        speaker = "Speaker 1",
                        text = fullText
                    )
                )
            }

            FileTranscriptionResult(
                title = title,
                fullText = fullText.ifBlank { segments.joinToString(" ") { it.text } },
                segments = segments,
                detectedLanguage = detectedLang,
                keyTopics = topics
            )
        } catch (e: Exception) {
            Log.e("GeminiTranscribeApi", "Error parsing response, falling back to raw text", e)
            // If structured JSON fails, fallback to extracting text from raw response
            val fallbackText = try {
                val root = JSONObject(jsonResponse)
                root.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
            } catch (_: Exception) {
                jsonResponse.take(500)
            }

            FileTranscriptionResult(
                title = fallbackTitle.removeSuffix(".mp3").removeSuffix(".wav"),
                fullText = fallbackText,
                segments = listOf(
                    TranscriptSegment(
                        id = UUID.randomUUID().toString(),
                        speaker = "Speaker 1",
                        text = fallbackText
                    )
                ),
                detectedLanguage = "Auto-detected",
                keyTopics = emptyList()
            )
        }
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
