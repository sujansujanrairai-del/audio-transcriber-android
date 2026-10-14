package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.domain.model.ColorPreset
import com.example.domain.model.ThemeMode
import com.example.domain.model.ThemeSettings

@Composable
fun TranscendTheme(
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
                background = if (settings.oledPureBlack) Color.Black else TranscendSurfaceDark,
                surface = if (settings.oledPureBlack) Color(0xFF0A0A0A) else TranscendSlate,
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
                background = TranscendSurfaceLight,
                surface = Color.White,
                surfaceVariant = Color(0xFFF1F5F9),
                onSurface = TranscendNavy,
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

    val typography = createTranscendTypography(
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
