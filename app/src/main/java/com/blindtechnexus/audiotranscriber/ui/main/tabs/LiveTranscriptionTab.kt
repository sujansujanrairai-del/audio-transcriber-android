package com.blindtechnexus.audiotranscriber.ui.main.tabs

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blindtechnexus.audiotranscriber.R
import com.blindtechnexus.audiotranscriber.data.remote.LiveConnectionState
import com.blindtechnexus.audiotranscriber.domain.model.TranscriptSegment
import com.blindtechnexus.audiotranscriber.ui.accessibility.accessibleTouchTarget
import com.blindtechnexus.audiotranscriber.ui.accessibility.transcribeLiveRegion
import com.blindtechnexus.audiotranscriber.ui.components.AudioWaveformVisualizer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.blindtechnexus.audiotranscriber.ui.components.CustomVocabularyBottomSheet
import com.blindtechnexus.audiotranscriber.ui.main.LiveTranscriptionUiState
import com.blindtechnexus.audiotranscriber.ui.main.MainViewModel
import com.blindtechnexus.audiotranscriber.ui.theme.getSpeakerColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTranscriptionTab(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val liveState by viewModel.liveUiState.collectAsState()
    val preferences by viewModel.userPreferences.transcriptionConfig.collectAsState()
    var showVocabSheet by remember { mutableStateOf(false) }
    var showLanguageDropdown by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Auto scroll to bottom when new segments or streaming text arrive
    LaunchedEffect(liveState.liveSegments.size, liveState.currentStreamingText) {
        if (liveState.liveSegments.isNotEmpty()) {
            listState.animateScrollToItem(liveState.liveSegments.size)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("tab_live_content"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Live Control Bar: Language, Vocab & Limit Warning
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Language selector button
            Box {
                OutlinedButton(
                    onClick = { showLanguageDropdown = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (preferences.languageCode.isBlank()) "Auto-detect" else preferences.languageCode,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                }

                DropdownMenu(
                    expanded = showLanguageDropdown,
                    onDismissRequest = { showLanguageDropdown = false }
                ) {
                    val languages = listOf(
                        "" to "Auto-detect (All 85+ Languages)",
                        "en-US" to "English (US)",
                        "es-ES" to "Spanish (Español)",
                        "ne-NP" to "Nepali (नेपाली)",
                        "hi-IN" to "Hindi (हिन्दी)",
                        "fr-FR" to "French (Français)",
                        "de-DE" to "German (Deutsch)",
                        "ja-JP" to "Japanese (日本語)",
                        "zh-CN" to "Chinese (Mandarin)",
                        "ar-SA" to "Arabic (العربية)",
                        "pt-BR" to "Portuguese (Português)"
                    )
                    languages.forEach { (code, name) ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                viewModel.userPreferences.updateTranscriptionConfig {
                                    it.copy(languageCode = code)
                                }
                                showLanguageDropdown = false
                            }
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Custom Vocabulary Button
                FilledTonalIconButton(
                    onClick = { showVocabSheet = true },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Spellcheck,
                        contentDescription = "Custom Vocabulary",
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Timestamps Toggle Button
                FilledTonalIconToggleButton(
                    checked = liveState.showTimestamps,
                    onCheckedChange = { viewModel.toggleTimestamps() },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = "Toggle Timestamps",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Connection Error Alert Banner
        AnimatedVisibility(visible = liveState.connectionState is LiveConnectionState.Error) {
            val errorMsg = (liveState.connectionState as? LiveConnectionState.Error)?.message ?: "Connection error"
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onOpenSettings) {
                        Text("Settings", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Audio Waveform Canvas Visualizer
        AudioWaveformVisualizer(
            amplitude = liveState.currentAmplitude,
            isRecording = liveState.isRecording && !liveState.isPaused,
            isSpeechDetected = liveState.isSpeechDetected,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        )

        // Real-Time Transcript Feed Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("transcript_feed"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
        ) {
            if (liveState.liveSegments.isEmpty() && liveState.currentStreamingText.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (liveState.isRecording) "Listening for speech..." else stringResource(R.string.live_recording_idle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                        .transcribeLiveRegion(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(liveState.liveSegments, key = { it.id }) { segment ->
                        LiveSegmentRow(
                            segment = segment,
                            showTimestamps = liveState.showTimestamps,
                            highlightFillers = preferences.highlightFillerWords
                        )
                    }

                    // Incremental Streaming Chunk with Blinking Cursor
                    if (liveState.currentStreamingText.isNotEmpty()) {
                        item {
                            StreamingChunkRow(
                                speaker = liveState.activeSpeaker,
                                text = liveState.currentStreamingText,
                                elapsedSeconds = liveState.elapsedSeconds,
                                showTimestamps = liveState.showTimestamps
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Session Timer and Elapsed Duration
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Timer Display with color shift approaching 10 mins
            val mins = liveState.elapsedSeconds / 60
            val secs = liveState.elapsedSeconds % 60
            val timerColor = when {
                liveState.isLimitWarning -> MaterialTheme.colorScheme.error
                liveState.isRecording -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(timerColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = String.format("%02d:%02d", mins, secs),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = timerColor
                )
                Text(
                    text = " / 10:00",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Connection / VAD State Pill
            val vadText = when {
                liveState.isPaused -> "Paused"
                liveState.isRecording && liveState.isSpeechDetected -> "Speech Active"
                liveState.isRecording -> "Silence / Standby"
                else -> "Standby"
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (liveState.isSpeechDetected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = vadText,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = if (liveState.isSpeechDetected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        val hasTranscriptions = liveState.liveSegments.isNotEmpty() || liveState.currentStreamingText.isNotEmpty()

        // Session Controls: Large Animated Pulsing Record Button + Control Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Clear Button (only when recording or segments present)
            if (hasTranscriptions || liveState.isRecording) {
                IconButton(
                    onClick = { viewModel.clearLiveSession() },
                    modifier = Modifier
                        .size(48.dp)
                        .accessibleTouchTarget()
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Clear session")
                }
            }

            // Pause / Resume Button
            if (liveState.isRecording) {
                IconButton(
                    onClick = {
                        if (liveState.isPaused) viewModel.resumeLiveRecording()
                        else viewModel.pauseLiveRecording()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .accessibleTouchTarget()
                ) {
                    Icon(
                        imageVector = if (liveState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (liveState.isPaused) "Resume" else "Pause"
                    )
                }
            }

            // Large Animated Microphone Button with Pulsing Ring
            PulsingRecordButton(
                isRecording = liveState.isRecording,
                isPaused = liveState.isPaused,
                onClick = { viewModel.toggleLiveRecording() }
            )

            // Copy, Save & Share Buttons - ONLY visible when transcriptions are present!
            if (hasTranscriptions) {
                // Copy Button
                IconButton(
                    onClick = {
                        val full = liveState.liveSegments.joinToString("\n") { "${it.speaker}: ${it.text}" }
                            .ifBlank { liveState.currentStreamingText }
                        viewModel.copyToClipboard(full)
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .accessibleTouchTarget()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Transcript")
                }

                // Save Transcript Button
                IconButton(
                    onClick = { viewModel.saveLiveTranscript() },
                    modifier = Modifier
                        .size(48.dp)
                        .accessibleTouchTarget()
                ) {
                    Icon(Icons.Default.BookmarkBorder, contentDescription = "Save Transcript")
                }

                // Export / Share Button
                IconButton(
                    onClick = {
                        val full = liveState.liveSegments.joinToString("\n") { "${it.speaker}: ${it.text}" }
                            .ifBlank { liveState.currentStreamingText }
                        viewModel.shareTranscript(full, "Audio Transcriber Live Transcript")
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .accessibleTouchTarget()
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share Transcript")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }

    if (showVocabSheet) {
        CustomVocabularyBottomSheet(
            currentVocabulary = preferences.customVocabulary,
            onSaveVocabulary = { updated ->
                viewModel.userPreferences.updateTranscriptionConfig {
                    it.copy(customVocabulary = updated)
                }
            },
            onDismiss = { showVocabSheet = false }
        )
    }
}

@Composable
private fun PulsingRecordButton(
    isRecording: Boolean,
    isPaused: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_trans")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording && !isPaused) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(80.dp)
    ) {
        if (isRecording && !isPaused) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            )
        }

        FilledIconButton(
            onClick = onClick,
            modifier = Modifier
                .size(64.dp)
                .testTag("live_mic_button"),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (isRecording) "Stop live recording" else "Start live recording",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun LiveSegmentRow(
    segment: TranscriptSegment,
    showTimestamps: Boolean,
    highlightFillers: Boolean
) {
    val speakerNum = segment.speaker.filter { it.isDigit() }.toIntOrNull() ?: 1
    val speakerColor = getSpeakerColor(speakerNum)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Speaker badge
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = speakerColor.copy(alpha = 0.18f),
                border = androidx.compose.foundation.BorderStroke(1.dp, speakerColor.copy(alpha = 0.4f))
            ) {
                Text(
                    text = segment.speaker,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = speakerColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            if (showTimestamps) {
                val totalSec = segment.startTimeMs / 1000
                Text(
                    text = String.format("%02d:%02d", totalSec / 60, totalSec % 60),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = segment.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun StreamingChunkRow(
    speaker: String,
    text: String,
    elapsedSeconds: Long,
    showTimestamps: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_blink"
    )

    val speakerNum = speaker.filter { it.isDigit() }.toIntOrNull() ?: 1
    val speakerColor = getSpeakerColor(speakerNum)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = speakerColor.copy(alpha = 0.18f)
            ) {
                Text(
                    text = speaker,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = speakerColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            if (showTimestamps) {
                Text(
                    text = String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            // Blinking cursor at end
            Box(
                modifier = Modifier
                    .padding(start = 2.dp)
                    .width(2.dp)
                    .height(16.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = cursorAlpha))
            )
        }
    }
}
