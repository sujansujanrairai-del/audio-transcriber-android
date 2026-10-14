package com.blindtechnexus.audiotranscriber.ui.theme

import androidx.compose.ui.graphics.Color
import com.blindtechnexus.audiotranscriber.domain.model.ColorPreset

// Base brand tones
val AudioTranscriberNavy = Color(0xFF0F172A)
val AudioTranscriberSlate = Color(0xFF1E293B)
val AudioTranscriberSurfaceLight = Color(0xFFF8FAFC)
val AudioTranscriberSurfaceDark = Color(0xFF090D16)

// Speaker Diarization Badges (up to 8 speakers)
val SpeakerColors = listOf(
    Color(0xFF6366F1), // Speaker 1: Indigo
    Color(0xFF10B981), // Speaker 2: Emerald
    Color(0xFFF59E0B), // Speaker 3: Amber
    Color(0xFFEC4899), // Speaker 4: Rose Pink
    Color(0xFF8B5CF6), // Speaker 5: Violet
    Color(0xFF06B6D4), // Speaker 6: Cyan
    Color(0xFF84CC16), // Speaker 7: Lime
    Color(0xFFF97316)  // Speaker 8: Orange
)

fun getSpeakerColor(speakerIndex: Int): Color {
    val index = (speakerIndex - 1).coerceAtLeast(0) % SpeakerColors.size
    return SpeakerColors[index]
}

fun getColorForPreset(preset: ColorPreset, isDark: Boolean): Pair<Color, Color> {
    val primary = Color(preset.primaryHex)
    return when (preset) {
        ColorPreset.INDIGO -> Pair(Color(0xFF6366F1), if (isDark) Color(0xFFC7D2FE) else Color(0xFF4338CA))
        ColorPreset.OCEAN -> Pair(Color(0xFF0284C7), if (isDark) Color(0xFFBAE6FD) else Color(0xFF0369A1))
        ColorPreset.SUNSET -> Pair(Color(0xFFF97316), if (isDark) Color(0xFFFFEDD5) else Color(0xFFC2410C))
        ColorPreset.FOREST -> Pair(Color(0xFF059669), if (isDark) Color(0xFFA7F3D0) else Color(0xFF047857))
        ColorPreset.EMERALD -> Pair(Color(0xFF10B981), if (isDark) Color(0xFFD1FAE5) else Color(0xFF065F46))
        ColorPreset.ROSE -> Pair(Color(0xFFE11D48), if (isDark) Color(0xFFFFE4E6) else Color(0xFFBE123C))
        ColorPreset.AMBER -> Pair(Color(0xFFD97706), if (isDark) Color(0xFFFEF3C7) else Color(0xFFB45309))
        ColorPreset.PURPLE -> Pair(Color(0xFF9333EA), if (isDark) Color(0xFFF3E8FF) else Color(0xFF7E22CE))
        ColorPreset.MONOCHROME -> Pair(Color(0xFF475569), if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155))
        ColorPreset.CYBERPUNK -> Pair(Color(0xFF06B6D4), if (isDark) Color(0xFFCFFAFE) else Color(0xFF0891B2))
    }
}
