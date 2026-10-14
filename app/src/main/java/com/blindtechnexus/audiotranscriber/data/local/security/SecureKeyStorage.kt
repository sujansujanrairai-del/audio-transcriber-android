package com.blindtechnexus.audiotranscriber.data.local.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.blindtechnexus.audiotranscriber.BuildConfig

class SecureKeyStorage(context: Context) {

    private val sharedPreferences: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e("SecureKeyStorage", "Failed to initialize EncryptedSharedPreferences, fallback to standard", e)
        context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveApiKey(apiKey: String) {
        sharedPreferences.edit()
            .putString(KEY_GEMINI_API_KEY, apiKey.trim())
            .putBoolean(KEY_KEY_VALIDATED, true)
            .apply()
    }

    fun getApiKey(): String {
        val stored = sharedPreferences.getString(KEY_GEMINI_API_KEY, null)?.trim()
        if (!stored.isNullOrEmpty()) {
            return stored
        }
        // Fallback to BuildConfig if configured in environment, but only if not empty placeholder
        return try {
            val buildKey = BuildConfig.GEMINI_API_KEY
            if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY") buildKey else ""
        } catch (_: Exception) {
            ""
        }
    }

    fun hasValidApiKey(): Boolean {
        val key = getApiKey()
        return key.isNotBlank()
    }

    fun getMaskedApiKey(): String {
        val key = getApiKey()
        return maskKey(key)
    }

    fun maskKey(key: String): String {
        if (key.length <= 8) return if (key.isEmpty()) "Not Set" else "••••••••"
        val prefix = key.take(4)
        val suffix = key.takeLast(4)
        return "$prefix...$suffix"
    }

    fun clearApiKey() {
        sharedPreferences.edit()
            .remove(KEY_GEMINI_API_KEY)
            .remove(KEY_KEY_VALIDATED)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "audio_transcriber_secure_prefs"
        private const val FALLBACK_PREFS_NAME = "audio_transcriber_secure_fallback"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key_encrypted"
        private const val KEY_KEY_VALIDATED = "gemini_api_key_validated"
    }
}
