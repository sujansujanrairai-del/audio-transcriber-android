package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun AudioWaveformVisualizer(
    amplitude: Float,
    isRecording: Boolean,
    isSpeechDetected: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 32
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val infiniteTransition = rememberInfiniteTransition(label = "ambient_waveform")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val animatedAmplitude by animateFloatAsState(
        targetValue = if (isRecording) amplitude.coerceIn(0.08f, 1f) else 0.05f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "amp"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val barWidth = (width / barCount) * 0.65f
        val spacing = (width - (barWidth * barCount)) / (barCount - 1).coerceAtLeast(1)

        val gradient = Brush.verticalGradient(
            colors = listOf(
                primaryColor.copy(alpha = 0.9f),
                if (isSpeechDetected) tertiaryColor else secondaryColor.copy(alpha = 0.8f)
            )
        )

        for (i in 0 until barCount) {
            val normX = i.toFloat() / barCount
            val sinOffset = sin((normX * 4 * Math.PI) + phase).toFloat()

            val barHeightMultiplier = if (isRecording) {
                val variance = (sinOffset * 0.35f) + (Random(i).nextFloat() * 0.25f)
                (animatedAmplitude + variance).coerceIn(0.12f, 1f)
            } else {
                (0.1f + sinOffset * 0.04f).coerceAtLeast(0.06f)
            }

            val barHeight = (height * 0.85f) * barHeightMultiplier
            val left = i * (barWidth + spacing)
            val top = centerY - (barHeight / 2f)

            drawRoundRect(
                brush = gradient,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
