package com.blindtechnexus.audiotranscriber.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.blindtechnexus.audiotranscriber.domain.model.ColorPreset
import com.blindtechnexus.audiotranscriber.domain.model.ThemeMode
import com.blindtechnexus.audiotranscriber.domain.model.ThemeSettings

@Composable
fun AudioTranscriberTheme(
    settings: ThemeSettings = ThemeSettings(),
    highContrast: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val context = LocalContext.current
    val (presetPrimary, presetSecondary) = getColorForPreset(settings.colorPreset, darkTheme)

    val colorScheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> {
            darkColorScheme(
                primary = presetPrimary,
                onPrimary = Color.White,
                primaryContainer = presetSecondary,
                secondary = presetSecondary,
                background = if (settings.oledPureBlack) Color.Black else AudioTranscriberSurfaceDark,
                surface = if (settings.oledPureBlack) Color(0xFF0A0A0A) else AudioTranscriberSlate,
                surfaceVariant = if (settings.oledPureBlack) Color(0xFF141414) else Color(0xFF334155),
                onSurface = Color.White,
                onSurfaceVariant = Color(0xFFCBD5E1)
            )
        }
        else -> {
            lightColorScheme(
                primary = presetPrimary,
                onPrimary = Color.White,
                primaryContainer = presetSecondary.copy(alpha = 0.2f),
                secondary = presetSecondary,
                background = AudioTranscriberSurfaceLight,
                surface = Color.White,
                surfaceVariant = Color(0xFFF1F5F9),
                onSurface = AudioTranscriberNavy,
                onSurfaceVariant = Color(0xFF475569)
            )
        }
    }

    val finalColorScheme = if (highContrast) {
        if (darkTheme) {
            colorScheme.copy(
                background = Color.Black,
                surface = Color.Black,
                onSurface = Color.White,
                primary = Color(0xFF38BDF8)
            )
        } else {
            colorScheme.copy(
                background = Color.White,
                surface = Color.White,
                onSurface = Color.Black,
                primary = Color(0xFF0369A1)
            )
        }
    } else {
        colorScheme
    }

    val cornerRadius = settings.cornerRadiusDp.dp
    val shapes = Shapes(
        small = RoundedCornerShape((cornerRadius * 0.5f).coerceAtLeast(4.dp)),
        medium = RoundedCornerShape(cornerRadius),
        large = RoundedCornerShape((cornerRadius * 1.5f).coerceAtLeast(12.dp))
    )

    val typography = createAudioTranscriberTypography(
        scale = settings.typographyScale,
        fontType = settings.fontFamilyType
    )

    MaterialTheme(
        colorScheme = finalColorScheme,
        typography = typography,
        shapes = shapes,
        content = content
    )
}
