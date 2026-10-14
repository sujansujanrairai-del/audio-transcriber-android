package com.blindtechnexus.audiotranscriber.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import com.blindtechnexus.audiotranscriber.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

class UserPreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("audio_transcriber_user_settings", Context.MODE_PRIVATE)

    private val _themeSettings = MutableStateFlow(loadThemeSettings())
    val themeSettings: StateFlow<ThemeSettings> = _themeSettings.asStateFlow()

    private val _transcriptionConfig = MutableStateFlow(loadTranscriptionConfig())
    val transcriptionConfig: StateFlow<TranscriptionConfig> = _transcriptionConfig.asStateFlow()

    private val _audioSettings = MutableStateFlow(loadAudioSettings())
    val audioSettings: StateFlow<AudioSettings> = _audioSettings.asStateFlow()

    private val _accessibilitySettings = MutableStateFlow(loadAccessibilitySettings())
    val accessibilitySettings: StateFlow<AccessibilitySettings> = _accessibilitySettings.asStateFlow()

    private fun loadThemeSettings(): ThemeSettings {
        val modeStr = prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        val presetStr = prefs.getString("color_preset", ColorPreset.INDIGO.name) ?: ColorPreset.INDIGO.name
        val scaleStr = prefs.getString("typography_scale", TypographyScale.DEFAULT.name) ?: TypographyScale.DEFAULT.name
        val fontStr = prefs.getString("font_family", FontFamilyType.SYSTEM.name) ?: FontFamilyType.SYSTEM.name
        val animStr = prefs.getString("animation_intensity", AnimationIntensity.FULL.name) ?: AnimationIntensity.FULL.name

        return ThemeSettings(
            themeMode = try { ThemeMode.valueOf(modeStr) } catch (_: Exception) { ThemeMode.SYSTEM },
            dynamicColor = prefs.getBoolean("dynamic_color", true),
            colorPreset = try { ColorPreset.valueOf(presetStr) } catch (_: Exception) { ColorPreset.INDIGO },
            customAccentColor = prefs.getLong("custom_accent_color", 0xFF6366F1),
            typographyScale = try { TypographyScale.valueOf(scaleStr) } catch (_: Exception) { TypographyScale.DEFAULT },
            cornerRadiusDp = prefs.getInt("corner_radius_dp", 16),
            fontFamilyType = try { FontFamilyType.valueOf(fontStr) } catch (_: Exception) { FontFamilyType.SYSTEM },
            transcriptTextSizeSp = prefs.getInt("transcript_text_size_sp", 16),
            animationIntensity = try { AnimationIntensity.valueOf(animStr) } catch (_: Exception) { AnimationIntensity.FULL },
            oledPureBlack = prefs.getBoolean("oled_pure_black", false)
        )
    }

    fun updateThemeSettings(update: (ThemeSettings) -> ThemeSettings) {
        val updated = update(_themeSettings.value)
        _themeSettings.value = updated
        prefs.edit()
            .putString("theme_mode", updated.themeMode.name)
            .putBoolean("dynamic_color", updated.dynamicColor)
            .putString("color_preset", updated.colorPreset.name)
            .putLong("custom_accent_color", updated.customAccentColor)
            .putString("typography_scale", updated.typographyScale.name)
            .putInt("corner_radius_dp", updated.cornerRadiusDp)
            .putString("font_family", updated.fontFamilyType.name)
            .putInt("transcript_text_size_sp", updated.transcriptTextSizeSp)
            .putString("animation_intensity", updated.animationIntensity.name)
            .putBoolean("oled_pure_black", updated.oledPureBlack)
            .apply()
    }

    private fun loadTranscriptionConfig(): TranscriptionConfig {
        val vocabJson = prefs.getString("custom_vocab_json", "[]") ?: "[]"
        val vocabList = mutableListOf<String>()
        try {
            val arr = JSONArray(vocabJson)
            for (i in 0 until arr.length()) {
                vocabList.add(arr.getString(i))
            }
        } catch (_: Exception) {}

        return TranscriptionConfig(
            languageCode = prefs.getString("language_code", "") ?: "",
            smartTranscription = prefs.getBoolean("smart_transcription", true),
            enableSpeakerDiarization = prefs.getBoolean("speaker_diarization", true),
            maxSpeakers = prefs.getInt("max_speakers", 8),
            wordLevelTimestamps = prefs.getBoolean("word_level_timestamps", true),
            highlightFillerWords = prefs.getBoolean("highlight_filler_words", false),
            customVocabulary = vocabList
        )
    }

    fun updateTranscriptionConfig(update: (TranscriptionConfig) -> TranscriptionConfig) {
        val updated = update(_transcriptionConfig.value)
        _transcriptionConfig.value = updated
        val vocabArr = JSONArray()
        updated.customVocabulary.forEach { vocabArr.put(it) }

        prefs.edit()
            .putString("language_code", updated.languageCode)
            .putBoolean("smart_transcription", updated.smartTranscription)
            .putBoolean("speaker_diarization", updated.enableSpeakerDiarization)
            .putInt("max_speakers", updated.maxSpeakers)
            .putBoolean("word_level_timestamps", updated.wordLevelTimestamps)
            .putBoolean("highlight_filler_words", updated.highlightFillerWords)
            .putString("custom_vocab_json", vocabArr.toString())
            .apply()
    }

    private fun loadAudioSettings(): AudioSettings {
        val vadStr = prefs.getString("vad_sensitivity", VadSensitivity.MEDIUM.name) ?: VadSensitivity.MEDIUM.name
        val inputStr = prefs.getString("input_source", AudioInputSource.MICROPHONE.name) ?: AudioInputSource.MICROPHONE.name

        return AudioSettings(
            sampleRate = 16000,
            channels = 1,
            bitDepth = 16,
            inputSource = try { AudioInputSource.valueOf(inputStr) } catch (_: Exception) { AudioInputSource.MICROPHONE },
            noiseSuppression = prefs.getBoolean("noise_suppression", true),
            vadSensitivity = try { VadSensitivity.valueOf(vadStr) } catch (_: Exception) { VadSensitivity.MEDIUM }
        )
    }

    fun updateAudioSettings(update: (AudioSettings) -> AudioSettings) {
        val updated = update(_audioSettings.value)
        _audioSettings.value = updated
        prefs.edit()
            .putString("vad_sensitivity", updated.vadSensitivity.name)
            .putString("input_source", updated.inputSource.name)
            .putBoolean("noise_suppression", updated.noiseSuppression)
            .apply()
    }

    private fun loadAccessibilitySettings(): AccessibilitySettings {
        val hapticStr = prefs.getString("haptic_intensity", HapticIntensity.MEDIUM.name) ?: HapticIntensity.MEDIUM.name
        val colorBlindStr = prefs.getString("color_blind_mode", ColorBlindType.NONE.name) ?: ColorBlindType.NONE.name

        return AccessibilitySettings(
            talkBackOptimized = prefs.getBoolean("talkback_optimized", true),
            highContrast = prefs.getBoolean("high_contrast", false),
            largeTouchTargets = prefs.getBoolean("large_touch_targets", false),
            hapticFeedback = try { HapticIntensity.valueOf(hapticStr) } catch (_: Exception) { HapticIntensity.MEDIUM },
            liveRegionAnnouncements = prefs.getBoolean("live_region_announcements", true),
            screenReaderOnlyMode = prefs.getBoolean("screen_reader_only_mode", false),
            hearingVisualAlerts = prefs.getBoolean("hearing_visual_alerts", true),
            colorBlindMode = try { ColorBlindType.valueOf(colorBlindStr) } catch (_: Exception) { ColorBlindType.NONE }
        )
    }

    fun updateAccessibilitySettings(update: (AccessibilitySettings) -> AccessibilitySettings) {
        val updated = update(_accessibilitySettings.value)
        _accessibilitySettings.value = updated
        prefs.edit()
            .putBoolean("talkback_optimized", updated.talkBackOptimized)
            .putBoolean("high_contrast", updated.highContrast)
            .putBoolean("large_touch_targets", updated.largeTouchTargets)
            .putString("haptic_intensity", updated.hapticFeedback.name)
            .putBoolean("live_region_announcements", updated.liveRegionAnnouncements)
            .putBoolean("screen_reader_only_mode", updated.screenReaderOnlyMode)
            .putBoolean("hearing_visual_alerts", updated.hearingVisualAlerts)
            .putString("color_blind_mode", updated.colorBlindMode.name)
            .apply()
    }
}
