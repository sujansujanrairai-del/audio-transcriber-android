package com.blindtechnexus.audiotranscriber.ui.main.tabs

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blindtechnexus.audiotranscriber.domain.model.Transcript
import com.blindtechnexus.audiotranscriber.ui.accessibility.accessibleTouchTarget
import com.blindtechnexus.audiotranscriber.ui.main.HistoryFilter
import com.blindtechnexus.audiotranscriber.ui.main.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryTab(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val transcripts by viewModel.allTranscripts.collectAsState()
    val searchQuery by viewModel.historySearchQuery.collectAsState()
    val currentFilter by viewModel.historyFilter.collectAsState()
    var selectedTranscriptForDetail by remember { mutableStateOf<Transcript?>(null) }
    var transcriptToDelete by remember { mutableStateOf<Transcript?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("tab_history_content")
    ) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setHistorySearchQuery(it) },
            placeholder = { Text("Search past transcripts...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.setHistorySearchQuery("") },
                        modifier = Modifier.accessibleTouchTarget()
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("history_search_field"),
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter chips row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = currentFilter == HistoryFilter.ALL,
                onClick = { viewModel.setHistoryFilter(HistoryFilter.ALL) },
                label = { Text("All") },
                leadingIcon = if (currentFilter == HistoryFilter.ALL) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                modifier = Modifier.accessibleTouchTarget()
            )

            FilterChip(
                selected = currentFilter == HistoryFilter.LIVE,
                onClick = { viewModel.setHistoryFilter(HistoryFilter.LIVE) },
                label = { Text("Live Sessions") },
                leadingIcon = { Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.accessibleTouchTarget()
            )

            FilterChip(
                selected = currentFilter == HistoryFilter.FILES,
                onClick = { viewModel.setHistoryFilter(HistoryFilter.FILES) },
                label = { Text("Audio Files") },
                leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.accessibleTouchTarget()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (transcripts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "No transcripts matching \"$searchQuery\""
                        else "No saved transcripts found for this filter. Start live transcription or transcribe an audio file.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            // Stats bar
            val totalSeconds = transcripts.sumOf { it.durationSeconds }
            val totalWords = transcripts.sumOf { it.wordCount }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${transcripts.size} saved • ${totalSeconds / 60}m recorded • $totalWords words",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(transcripts, key = { it.id }) { transcript ->
                    TranscriptHistoryCard(
                        transcript = transcript,
                        onClick = { selectedTranscriptForDetail = transcript },
                        onCopy = { viewModel.copyToClipboard(transcript.fullText) },
                        onShare = { viewModel.shareTranscript(transcript.fullText, transcript.title) },
                        onWebSearch = { viewModel.performWebSearchGroundingForTranscript(transcript) },
                        onDelete = { transcriptToDelete = transcript }
                    )
                }
            }
        }
    }

    // Detail dialog when tapping a transcript
    if (selectedTranscriptForDetail != null) {
        val transcript = selectedTranscriptForDetail!!
        AlertDialog(
            onDismissRequest = { selectedTranscriptForDetail = null },
            title = {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (transcript.isLive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (transcript.isLive) Icons.Default.Mic else Icons.Default.AudioFile,
                                contentDescription = null,
                                tint = if (transcript.isLive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = transcript.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { heading() }
                        )
                    }
                    Text(
                        text = "${transcript.language} • ${transcript.wordCount} words • ${formatDuration(transcript.durationSeconds)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (transcript.summary != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "Gemini Summary:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = transcript.summary!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }

                    // Key topics tags if available
                    if (transcript.keyTopics.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            transcript.keyTopics.take(4).forEach { topic ->
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(topic, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.height(24.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = transcript.fullText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Secondary actions inside detail
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.performWebSearchGroundingForTranscript(transcript)
                                selectedTranscriptForDetail = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .accessibleTouchTarget(),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(Icons.Default.TravelExplore, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Web Verify", style = MaterialTheme.typography.labelSmall)
                        }

                        OutlinedButton(
                            onClick = {
                                val srt = viewModel.generateSrtContent(transcript)
                                viewModel.shareTranscript(srt, "${transcript.title}.srt")
                            },
                            modifier = Modifier
                                .weight(1f)
                                .accessibleTouchTarget(),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(Icons.Default.Subtitles, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share SRT", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.copyToClipboard(transcript.fullText)
                        selectedTranscriptForDetail = null
                    },
                    modifier = Modifier.accessibleTouchTarget()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy Text")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { selectedTranscriptForDetail = null },
                    modifier = Modifier.accessibleTouchTarget()
                ) {
                    Text("Close")
                }
            }
        )
    }

    // Delete confirmation dialog
    if (transcriptToDelete != null) {
        val toDelete = transcriptToDelete!!
        AlertDialog(
            onDismissRequest = { transcriptToDelete = null },
            title = {
                Text(
                    text = "Delete Transcript?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
            },
            text = {
                Text("Are you sure you want to delete \"${toDelete.title}\"? This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTranscript(toDelete)
                        transcriptToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.accessibleTouchTarget()
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { transcriptToDelete = null },
                    modifier = Modifier.accessibleTouchTarget()
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun TranscriptHistoryCard(
    transcript: Transcript,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onWebSearch: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(transcript.createdAt))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("history_item_${transcript.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (transcript.isLive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (transcript.isLive) Icons.Default.Mic else Icons.Default.AudioFile,
                            contentDescription = null,
                            tint = if (transcript.isLive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = transcript.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = dateStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier
                            .size(32.dp)
                            .accessibleTouchTarget()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy transcript text", modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier
                            .size(32.dp)
                            .accessibleTouchTarget()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share transcript", modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = onWebSearch,
                        modifier = Modifier
                            .size(32.dp)
                            .accessibleTouchTarget()
                    ) {
                        Icon(Icons.Default.TravelExplore, contentDescription = "Web Search and verify", modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(32.dp)
                            .accessibleTouchTarget()
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete transcript", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = transcript.fullText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = { },
                    label = { Text(transcript.language, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(26.dp)
                )
                AssistChip(
                    onClick = { },
                    label = { Text("${transcript.wordCount} words", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(26.dp)
                )
                AssistChip(
                    onClick = { },
                    label = { Text(formatDuration(transcript.durationSeconds), style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(26.dp)
                )
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}
