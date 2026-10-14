package com.blindtechnexus.audiotranscriber.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blindtechnexus.audiotranscriber.domain.model.TranscriptSegment
import com.blindtechnexus.audiotranscriber.ui.theme.getSpeakerColor

@Composable
fun SpeakerTimelineChart(
    segments: List<TranscriptSegment>,
    onSelectSegment: (TranscriptSegment) -> Unit,
    modifier: Modifier = Modifier
) {
    if (segments.isEmpty()) return

    val totalDurationMs = segments.maxOfOrNull { it.endTimeMs }?.coerceAtLeast(1000L)
        ?: (segments.size * 3000L)

    // Group segments by speaker for statistics
    val speakerMap = segments.groupBy { it.speaker }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Speaker Timeline Diarization",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${speakerMap.size} Speakers",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Horizontal Timeline Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            segments.forEachIndexed { index, seg ->
                val duration = (seg.endTimeMs - seg.startTimeMs).coerceAtLeast(1000L)
                val weight = (duration.toFloat() / totalDurationMs).coerceIn(0.02f, 1f)
                val speakerNum = seg.speaker.filter { it.isDigit() }.toIntOrNull() ?: (index + 1)
                val color = getSpeakerColor(speakerNum)

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(weight)
                        .background(color)
                        .clickable { onSelectSegment(seg) }
                        .semantics {
                            contentDescription = "${seg.speaker} speaking from ${formatMs(seg.startTimeMs)} to ${formatMs(seg.endTimeMs)}"
                        }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Speaker Badges Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            speakerMap.keys.forEachIndexed { idx, speakerName ->
                val speakerNum = speakerName.filter { it.isDigit() }.toIntOrNull() ?: (idx + 1)
                val color = getSpeakerColor(speakerNum)

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = color.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Text(
                            text = speakerName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val sec = ms / 1000
    val m = sec / 60
    val s = sec % 60
    return String.format("%02d:%02d", m, s)
}
