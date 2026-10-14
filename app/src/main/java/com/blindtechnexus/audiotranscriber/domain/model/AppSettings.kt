package com.blindtechnexus.audiotranscriber.domain.model

data class TranscriptionConfig(
    val languageCode: String = "", // empty means auto-detect
    val smartTranscription: Boolean = true, // filler word removal, self-corrections, auto-punctuation
    val enableSpeakerDiarization: Boolean = true,
    val maxSpeakers: Int = 8,
    val wordLevelTimestamps: Boolean = true,
    val highlightFillerWords: Boolean = false,
    val customVocabulary: List<String> = emptyList()
)

data class AudioSettings(
    val sampleRate: Int = 16000, // 16 kHz PCM 16-bit mono
    val channels: Int = 1,
    val bitDepth: Int = 16,
    val inputSource: AudioInputSource = AudioInputSource.MICROPHONE,
    val noiseSuppression: Boolean = true,
    val vadSensitivity: VadSensitivity = VadSensitivity.MEDIUM
)

enum class AudioInputSource(val label: String) {
    MICROPHONE("Microphone"),
    BLUETOOTH("Bluetooth Headset"),
    INTERNAL("Internal Device Audio")
}

enum class VadSensitivity(val label: String, val thresholdRms: Double) {
    LOW("Low Sensitivity (Loud speech only)", 1200.0),
    MEDIUM("Medium (Balanced)", 650.0),
    HIGH("High (Captures quiet speech)", 350.0),
    AUTO("Auto-adaptive", 500.0)
}

data class ThemeSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val colorPreset: ColorPreset = ColorPreset.INDIGO,
    val customAccentColor: Long = 0xFF6366F1, // Default indigo
    val typographyScale: TypographyScale = TypographyScale.DEFAULT,
    val cornerRadiusDp: Int = 16,
    val fontFamilyType: FontFamilyType = FontFamilyType.SYSTEM,
    val transcriptTextSizeSp: Int = 16,
    val animationIntensity: AnimationIntensity = AnimationIntensity.FULL,
    val oledPureBlack: Boolean = false
)

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

enum class ColorPreset(val label: String, val primaryHex: Long) {
    INDIGO("Royal Indigo", 0xFF6366F1),
    OCEAN("Deep Ocean", 0xFF0284C7),
    SUNSET("Sunset Coral", 0xFFF97316),
    FOREST("Nordic Forest", 0xFF059669),
    EMERALD("Luminous Emerald", 0xFF10B981),
    ROSE("Velvet Rose", 0xFFE11D48),
    AMBER("Golden Amber", 0xFFD97706),
    PURPLE("Electric Purple", 0xFF9333EA),
    MONOCHROME("Slate Monochrome", 0xFF475569),
    CYBERPUNK("Neon Cyberpunk", 0xFF06B6D4)
}

enum class TypographyScale(val label: String, val scaleFactor: Float) {
    COMPACT("Compact", 0.9f),
    DEFAULT("Default", 1.0f),
    LARGE("Large", 1.15f),
    EXTRA_LARGE("Extra Large", 1.3f)
}

enum class FontFamilyType(val label: String) {
    SYSTEM("System Default"),
    SERIF("Classic Serif"),
    MONOSPACE("Developer Monospace"),
    DYSLEXIA_FRIENDLY("Dyslexia Friendly")
}

enum class AnimationIntensity(val label: String) {
    FULL("Full Animations"),
    REDUCED("Reduced Motion"),
    NONE("No Animations")
}

data class AccessibilitySettings(
    val talkBackOptimized: Boolean = true,
    val highContrast: Boolean = false,
    val largeTouchTargets: Boolean = false, // 48dp vs 56dp
    val hapticFeedback: HapticIntensity = HapticIntensity.MEDIUM,
    val liveRegionAnnouncements: Boolean = true,
    val screenReaderOnlyMode: Boolean = false,
    val hearingVisualAlerts: Boolean = true,
    val colorBlindMode: ColorBlindType = ColorBlindType.NONE
)

enum class HapticIntensity(val label: String) {
    OFF("Off"),
    LIGHT("Light"),
    MEDIUM("Medium"),
    STRONG("Strong")
}

enum class ColorBlindType(val label: String) {
    NONE("Standard"),
    PROTANOPIA("Protanopia (Red-blind)"),
    DEUTERANOPIA("Deuteranopia (Green-blind)"),
    TRITANOPIA("Tritanopia (Blue-blind)")
}
