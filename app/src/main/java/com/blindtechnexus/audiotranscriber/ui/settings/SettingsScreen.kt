package com.blindtechnexus.audiotranscriber.ui.settings

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blindtechnexus.audiotranscriber.R
import com.blindtechnexus.audiotranscriber.domain.model.*
import com.blindtechnexus.audiotranscriber.ui.accessibility.accessibleTouchTarget
import com.blindtechnexus.audiotranscriber.ui.components.CustomVocabularyBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeSettings by viewModel.themeSettings.collectAsState()
    val transcriptionConfig by viewModel.transcriptionConfig.collectAsState()
    val audioSettings by viewModel.audioSettings.collectAsState()
    val accessibilitySettings by viewModel.accessibilitySettings.collectAsState()
    val maskedKey by viewModel.maskedApiKey.collectAsState()
    val keyUpdateInput by viewModel.apiKeyUpdateInput.collectAsState()
    val validationStatus by viewModel.keyValidationStatus.collectAsState()

    var showVocabSheet by remember { mutableStateOf(false) }
    var showKeyUpdateDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    // Section collapse states
    var isTranscriptionExpanded by remember { mutableStateOf(true) }
    var isAudioExpanded by remember { mutableStateOf(false) }
    var isAppearanceExpanded by remember { mutableStateOf(false) }
    var isAccessibilityExpanded by remember { mutableStateOf(false) }
    var isDataExpanded by remember { mutableStateOf(false) }
    var isAboutExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier.testTag("settings_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Section 1: Transcription Configuration
            CollapsibleSectionCard(
                title = stringResource(R.string.settings_section_transcription),
                icon = Icons.Default.Subtitles,
                isExpanded = isTranscriptionExpanded,
                onToggle = { isTranscriptionExpanded = !isTranscriptionExpanded }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Smart transcription toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Smart Transcription", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Removes 'uh'/'um', fixes self-corrections and auto-punctuates", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = transcriptionConfig.smartTranscription,
                            onCheckedChange = { chk ->
                                viewModel.updateTranscriptionConfig { it.copy(smartTranscription = chk) }
                            }
                        )
                    }

                    Divider()

                    // Highlight removed filler words
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Highlight Removed Filler Words", style = MaterialTheme.typography.bodyMedium)
                            Text("Highlights cleaned words in transcript feed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = transcriptionConfig.highlightFillerWords,
                            onCheckedChange = { chk ->
                                viewModel.updateTranscriptionConfig { it.copy(highlightFillerWords = chk) }
                            }
                        )
                    }

                    Divider()

                    // Custom Vocabulary Manager Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showVocabSheet = true }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Custom Vocabulary", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("${transcriptionConfig.customVocabulary.size} terms configured", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        FilledTonalButton(onClick = { showVocabSheet = true }) {
                            Text("Manage")
                        }
                    }
                }
            }

            // Section 2: Audio & VAD Settings
            CollapsibleSectionCard(
                title = stringResource(R.string.settings_section_audio),
                icon = Icons.Default.GraphicEq,
                isExpanded = isAudioExpanded,
                onToggle = { isAudioExpanded = !isAudioExpanded }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Sample rate (Locked to 16 kHz)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Sample Rate", style = MaterialTheme.typography.bodyMedium)
                        Text("16,000 Hz (16-bit Mono PCM)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }

                    Divider()

                    // VAD Sensitivity
                    Text("Voice Activity Detection (VAD) Sensitivity", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        VadSensitivity.values().forEach { sens ->
                            FilterChip(
                                selected = audioSettings.vadSensitivity == sens,
                                onClick = {
                                    viewModel.updateAudioSettings { it.copy(vadSensitivity = sens) }
                                },
                                label = { Text(sens.name, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Divider()

                    // Noise suppression
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Hardware Noise Suppression", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = audioSettings.noiseSuppression,
                            onCheckedChange = { chk ->
                                viewModel.updateAudioSettings { it.copy(noiseSuppression = chk) }
                            }
                        )
                    }
                }
            }

            // Section 3: Appearance & Themes (10+ Color presets, Typography, Corner Radius)
            CollapsibleSectionCard(
                title = stringResource(R.string.settings_section_appearance),
                icon = Icons.Default.Palette,
                isExpanded = isAppearanceExpanded,
                onToggle = { isAppearanceExpanded = !isAppearanceExpanded }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Theme Mode
                    Text("Theme Mode", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeMode.values().forEach { mode ->
                            FilterChip(
                                selected = themeSettings.themeMode == mode,
                                onClick = { viewModel.updateTheme { it.copy(themeMode = mode) } },
                                label = { Text(mode.name) }
                            )
                        }
                    }

                    // OLED True Black
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("OLED Pure Black", style = MaterialTheme.typography.bodyMedium)
                            Text("True #000000 background for battery savings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = themeSettings.oledPureBlack,
                            onCheckedChange = { chk -> viewModel.updateTheme { it.copy(oledPureBlack = chk) } }
                        )
                    }

                    Divider()

                    // 10 Preset Color Schemes with live color dots
                    Text("Color Palette Preset (10 Presets)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ColorPreset.values().forEach { preset ->
                            val isSelected = themeSettings.colorPreset == preset
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(
                                    if (isSelected) 2.dp else 1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .clickable { viewModel.updateTheme { it.copy(colorPreset = preset) } }
                                    .padding(2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(Color(preset.primaryHex))
                                    )
                                    Text(preset.label, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    Divider()

                    // Corner Radius Slider
                    Text("Corner Radius: ${themeSettings.cornerRadiusDp} dp", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = themeSettings.cornerRadiusDp.toFloat(),
                        onValueChange = { viewModel.updateTheme { it.copy(cornerRadiusDp = it.cornerRadiusDp) } },
                        valueRange = 0f..28f,
                        steps = 27
                    )

                    Divider()

                    // Font Family Selector
                    Text("Font Family", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FontFamilyType.values().forEach { font ->
                            FilterChip(
                                selected = themeSettings.fontFamilyType == font,
                                onClick = { viewModel.updateTheme { it.copy(fontFamilyType = font) } },
                                label = { Text(font.label) }
                            )
                        }
                    }
                }
            }

            // Section 4: Deep Accessibility
            CollapsibleSectionCard(
                title = stringResource(R.string.settings_section_accessibility),
                icon = Icons.Default.AccessibilityNew,
                isExpanded = isAccessibilityExpanded,
                onToggle = { isAccessibilityExpanded = !isAccessibilityExpanded }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // High contrast mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("High Contrast Mode (WCAG AAA)", style = MaterialTheme.typography.bodyMedium)
                            Text("Maximized contrast for low vision", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = accessibilitySettings.highContrast,
                            onCheckedChange = { chk -> viewModel.updateAccessibilitySettings { it.copy(highContrast = chk) } }
                        )
                    }

                    Divider()

                    // Large Touch Targets (48dp vs 56dp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Expanded Touch Targets (56dp)", style = MaterialTheme.typography.bodyMedium)
                            Text("Increases minimum touch area for limited dexterity", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = accessibilitySettings.largeTouchTargets,
                            onCheckedChange = { chk -> viewModel.updateAccessibilitySettings { it.copy(largeTouchTargets = chk) } }
                        )
                    }

                    Divider()

                    // Live Region Announcements
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("TalkBack Live Region Announcements", style = MaterialTheme.typography.bodyMedium)
                            Text("Announces new transcript segments politely", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = accessibilitySettings.liveRegionAnnouncements,
                            onCheckedChange = { chk -> viewModel.updateAccessibilitySettings { it.copy(liveRegionAnnouncements = chk) } }
                        )
                    }
                }
            }

            // Section 5: Data & Privacy
            CollapsibleSectionCard(
                title = stringResource(R.string.settings_section_data),
                icon = Icons.Default.Security,
                isExpanded = isDataExpanded,
                onToggle = { isDataExpanded = !isDataExpanded }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Encrypted Gemini API Key", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Current: $maskedKey", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Button(onClick = { showKeyUpdateDialog = true }) {
                            Text("Update Key")
                        }
                    }

                    Divider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Clear All Transcripts", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                            Text("Permanently delete local Room database records", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(
                            onClick = { showClearHistoryDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Clear")
                        }
                    }
                }
            }

            // Section 6: About Audio Transcriber
            CollapsibleSectionCard(
                title = stringResource(R.string.settings_section_about),
                icon = Icons.Default.Info,
                isExpanded = isAboutExpanded,
                onToggle = { isAboutExpanded = !isAboutExpanded }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Audio Transcriber v1.0.0 (Build 1)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Developed by Sujan Rai • Nepal", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Powered by Gemini 3.5 Transcribe with 4.0% live WER and 2.6% pre-recorded WER. Features real-time WebSocket streaming, speaker diarization up to 8 speakers, word-level timestamps, and deep Android accessibility.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Key update dialog
    if (showKeyUpdateDialog) {
        AlertDialog(
            onDismissRequest = { showKeyUpdateDialog = false },
            title = { Text("Update Gemini API Key") },
            text = {
                Column {
                    OutlinedTextField(
                        value = keyUpdateInput,
                        onValueChange = { viewModel.onApiKeyUpdateInputChanged(it) },
                        label = { Text("New Gemini API Key") },
                        placeholder = { Text("AIzaSy...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (validationStatus != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(validationStatus!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.validateAndSaveNewApiKey {
                        showKeyUpdateDialog = false
                    }
                }) {
                    Text("Verify & Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showKeyUpdateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear history confirmation dialog
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Delete All History?") },
            text = { Text("Are you sure you want to permanently delete all saved transcripts from device storage?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllHistory()
                        showClearHistoryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showVocabSheet) {
        CustomVocabularyBottomSheet(
            currentVocabulary = transcriptionConfig.customVocabulary,
            onSaveVocabulary = { updated ->
                viewModel.updateTranscriptionConfig { it.copy(customVocabulary = updated) }
            },
            onDismiss = { showVocabSheet = false }
        )
    }
}

@Composable
private fun CollapsibleSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    content()
                }
            }
        }
    }
}
