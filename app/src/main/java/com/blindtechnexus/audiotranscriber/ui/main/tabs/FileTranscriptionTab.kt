package com.blindtechnexus.audiotranscriber.ui.main.tabs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blindtechnexus.audiotranscriber.R
import com.blindtechnexus.audiotranscriber.domain.model.TranscriptSegment
import com.blindtechnexus.audiotranscriber.ui.accessibility.accessibleTouchTarget
import com.blindtechnexus.audiotranscriber.ui.components.KeywordChipsRow
import com.blindtechnexus.audiotranscriber.ui.components.ShimmerTranscriptItem
import com.blindtechnexus.audiotranscriber.ui.components.SpeakerTimelineChart
import com.blindtechnexus.audiotranscriber.ui.main.MainViewModel
import com.blindtechnexus.audiotranscriber.ui.theme.getSpeakerColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileTranscriptionTab(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val fileState by viewModel.fileUiState.collectAsState()
    val preferences by viewModel.userPreferences.transcriptionConfig.collectAsState()
    val context = LocalContext.current
    var showLanguageDropdown by remember { mutableStateOf(false) }
    var showFullPreviewDialog by remember { mutableStateOf(false) }
    var resultViewMode by remember { mutableStateOf("full") }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: ByteArray(0)
                inputStream?.close()

                val cursor = context.contentResolver.query(uri, null, null, null, null)
                val fileName = cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) it.getString(nameIndex) else "audio_file.mp3"
                    } else "audio_file.mp3"
                } ?: "audio_file.mp3"

                val mimeType = context.contentResolver.getType(uri) ?: "audio/mp3"
                viewModel.onAudioFileSelected(fileName, mimeType, bytes)
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("tab_files_content"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // File Selection / Upload Area
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { audioPickerLauncher.launch("audio/*") }
                .testTag("file_picker_area"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (fileState.selectedFileName != null) Icons.Default.AudioFile else Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileState.selectedFileName ?: stringResource(R.string.file_pick_audio),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (fileState.selectedFileBytes != null)
                            "${fileState.selectedFileBytes!!.size / 1024} KB • Ready for Gemini"
                        else
                            stringResource(R.string.file_supported_formats),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalButton(
                    onClick = { audioPickerLauncher.launch("audio/*") },
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text(if (fileState.selectedFileName != null) "Change" else "Browse")
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Audio Language Selector Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                OutlinedButton(
                    onClick = { showLanguageDropdown = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("file_audio_language_button")
                ) {
                    Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Audio language (${getLanguageDisplayName(preferences.languageCode)})",
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
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Diarization & Word-Timestamps Toggles
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.file_speaker_diarization),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = preferences.enableSpeakerDiarization,
                        onCheckedChange = { checked ->
                            viewModel.userPreferences.updateTranscriptionConfig {
                                it.copy(enableSpeakerDiarization = checked)
                            }
                        }
                    )
                }

                if (preferences.enableSpeakerDiarization) {
                    Text(
                        text = stringResource(R.string.file_diarization_warning),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Transcribe Action Button
        Button(
            onClick = { viewModel.transcribeSelectedFile() },
            modifier = Modifier
                .fillMaxWidth()
                .accessibleTouchTarget()
                .testTag("start_file_transcription_btn"),
            enabled = fileState.selectedFileBytes != null && !fileState.isTranscribing,
            shape = RoundedCornerShape(12.dp)
        ) {
            if (fileState.isTranscribing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.file_transcribing))
            } else {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.file_upload_btn))
            }
        }

        // Error message banner
        if (fileState.errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = fileState.errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    if (fileState.errorMessage!!.contains("401") || fileState.errorMessage!!.contains("API key", ignoreCase = true)) {
                        TextButton(onClick = onOpenSettings) {
                            Text("Settings", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Transcription Result Viewer
        if (fileState.isTranscribing) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(8.dp)
            ) {
                Text(
                    text = "Analyzing audio with Gemini 3.5 Transcribe...",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(modifier = Modifier.height(12.dp))
                repeat(4) {
                    ShimmerTranscriptItem()
                }
            }
        } else if (fileState.result != null) {
            val result = fileState.result!!
            val modelLabel = if (preferences.selectedFileModel == "gemini-3.5-transcribe") "Gemini 3.5 Transcribe" else preferences.selectedFileModel

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Top result header with Model Badge and Preview Full Transcription button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = modelLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Button: Preview Full Transcription
                    FilledTonalButton(
                        onClick = { showFullPreviewDialog = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("btn_preview_full_transcript")
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Preview Full", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // View Mode Tabs: Full Transcript vs Speaker Segments
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = resultViewMode == "full",
                        onClick = { resultViewMode = "full" },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Icon(Icons.Default.Notes, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Full Transcript")
                    }
                    SegmentedButton(
                        selected = resultViewMode == "segments",
                        onClick = { resultViewMode = "segments" },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Speaker Segments (${result.segments.size})")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // AI Action Row: Summarize & Translate (Redundant Search button removed)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { viewModel.summarizeCurrentTranscript() },
                        enabled = !fileState.isSummarizing,
                        modifier = Modifier.weight(1f).accessibleTouchTarget(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Summarize, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (fileState.isSummarizing) "Summarizing..." else "Summarize", style = MaterialTheme.typography.labelSmall)
                    }

                    FilledTonalButton(
                        onClick = { viewModel.translateCurrentTranscript(fileState.targetTranslationLanguage) },
                        enabled = !fileState.isTranslating,
                        modifier = Modifier.weight(1f).accessibleTouchTarget(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (fileState.isTranslating) "Translating..." else "Translate", style = MaterialTheme.typography.labelSmall)
                    }
                }

                // Active AI Summary banner
                AnimatedVisibility(visible = fileState.activeSummary != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Gemini Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                IconButton(onClick = { viewModel.copyToClipboard(fileState.activeSummary.orEmpty()) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy summary", modifier = Modifier.size(14.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(fileState.activeSummary.orEmpty(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Active AI Translation banner
                AnimatedVisibility(visible = fileState.activeTranslation != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Translation (${fileState.targetTranslationLanguage})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                IconButton(onClick = { viewModel.copyToClipboard(fileState.activeTranslation.orEmpty()) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy translation", modifier = Modifier.size(14.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(fileState.activeTranslation.orEmpty(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Content View depending on resultViewMode ("full" vs "segments")
                if (resultViewMode == "full") {
                    // Full Transcription Direct Display
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("full_transcript_card"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                            // Header stats
                            val wordCount = result.fullText.split("\\s+".toRegex()).count { it.isNotBlank() }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Full Continuous Transcription",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "$wordCount words • ${result.detectedLanguage}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                            // Scrollable full text
                            Box(modifier = Modifier.weight(1f)) {
                                val scrollState = rememberScrollState()
                                Text(
                                    text = result.fullText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 22.sp,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(scrollState)
                                )
                            }
                        }
                    }
                } else {
                    // Speaker Segments View
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        // Interactive Speaker Timeline Chart
                        SpeakerTimelineChart(
                            segments = result.segments,
                            onSelectSegment = { /* scroll or highlight */ }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Keyword chips row
                        if (result.keyTopics.isNotEmpty()) {
                            KeywordChipsRow(
                                keywords = result.keyTopics,
                                selectedKeyword = fileState.selectedKeyword,
                                onSelectKeyword = { viewModel.selectKeyword(it) }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        // Segments List
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(result.segments) { segment ->
                                FileSegmentCard(
                                    segment = segment,
                                    isHighlighted = fileState.selectedKeyword != null && segment.text.contains(fileState.selectedKeyword!!, ignoreCase = true),
                                    onCopy = { viewModel.copyToClipboard(segment.text) }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Export bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = { viewModel.copyToClipboard(result.fullText) },
                        modifier = Modifier.testTag("btn_copy_all_transcript")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy Full Text")
                    }

                    Button(
                        onClick = { viewModel.shareTranscript(result.fullText, result.title) },
                        modifier = Modifier.testTag("btn_share_transcript")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share Transcript")
                    }
                }
            }
        } else {
            // Empty state for files tab
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Audiotrack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Select an audio file to transcribe with Gemini 3.5",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showFullPreviewDialog && fileState.result != null) {
        val result = fileState.result!!
        val modelLabel = if (preferences.selectedFileModel == "gemini-3.5-transcribe") "Gemini 3.5 Transcribe" else preferences.selectedFileModel
        val wordCount = result.fullText.split("\\s+".toRegex()).count { it.isNotBlank() }
        val charCount = result.fullText.length

        AlertDialog(
            onDismissRequest = { showFullPreviewDialog = false },
            title = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = result.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.weight(1f).semantics { heading() }
                        )
                        IconButton(onClick = { showFullPreviewDialog = false }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close preview")
                        }
                    }
                    Text(
                        text = "$modelLabel • $wordCount words • $charCount characters",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val previewScroll = rememberScrollState()
                        Text(
                            text = result.fullText,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 24.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                                .verticalScroll(previewScroll)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.copyToClipboard(result.fullText)
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Full Text")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    viewModel.shareTranscript(result.fullText, result.title)
                }) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share")
                }
            }
        )
    }
}

@Composable
private fun FileSegmentCard(
    segment: TranscriptSegment,
    isHighlighted: Boolean,
    onCopy: () -> Unit
) {
    val speakerNum = segment.speaker.filter { it.isDigit() }.toIntOrNull() ?: 1
    val speakerColor = getSpeakerColor(speakerNum)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = speakerColor.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = segment.speaker,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = speakerColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    val sec = segment.startTimeMs / 1000
                    Text(
                        text = String.format("%02d:%02d", sec / 60, sec % 60),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy segment", modifier = Modifier.size(14.dp))
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
}
