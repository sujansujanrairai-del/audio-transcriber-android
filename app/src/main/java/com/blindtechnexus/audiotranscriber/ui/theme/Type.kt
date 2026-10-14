package com.blindtechnexus.audiotranscriber.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.blindtechnexus.audiotranscriber.domain.model.FontFamilyType
import com.blindtechnexus.audiotranscriber.domain.model.TypographyScale

fun getFontFamily(type: FontFamilyType): FontFamily {
    return when (type) {
        FontFamilyType.SYSTEM -> FontFamily.Default
        FontFamilyType.SERIF -> FontFamily.Serif
        FontFamilyType.MONOSPACE -> FontFamily.Monospace
        FontFamilyType.DYSLEXIA_FRIENDLY -> FontFamily.SansSerif // High legibility wide tracking
    }
}

fun createAudioTranscriberTypography(
    scale: TypographyScale = TypographyScale.DEFAULT,
    fontType: FontFamilyType = FontFamilyType.SYSTEM
): Typography {
    val factor = scale.scaleFactor
    val family = getFontFamily(fontType)

    return Typography(
        displayLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = (48 * factor).sp,
            lineHeight = (56 * factor).sp,
            letterSpacing = if (fontType == FontFamilyType.DYSLEXIA_FRIENDLY) 1.5.sp else (-0.25).sp
        ),
        headlineLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = (32 * factor).sp,
            lineHeight = (40 * factor).sp,
            letterSpacing = if (fontType == FontFamilyType.DYSLEXIA_FRIENDLY) 1.2.sp else 0.sp
        ),
        headlineMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = (26 * factor).sp,
            lineHeight = (32 * factor).sp
        ),
        titleLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = (20 * factor).sp,
            lineHeight = (26 * factor).sp
        ),
        titleMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = (16 * factor).sp,
            lineHeight = (24 * factor).sp
        ),
        bodyLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = (16 * factor).sp,
            lineHeight = if (fontType == FontFamilyType.DYSLEXIA_FRIENDLY) (28 * factor).sp else (24 * factor).sp,
            letterSpacing = if (fontType == FontFamilyType.DYSLEXIA_FRIENDLY) 0.8.sp else 0.5.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = (14 * factor).sp,
            lineHeight = (20 * factor).sp
        ),
        labelLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = (14 * factor).sp,
            lineHeight = (20 * factor).sp
        ),
        labelSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = (11 * factor).sp,
            lineHeight = (16 * factor).sp
        )
    )
}
