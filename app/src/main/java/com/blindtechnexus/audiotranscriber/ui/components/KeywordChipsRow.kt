package com.blindtechnexus.audiotranscriber.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeywordChipsRow(
    keywords: List<String>,
    selectedKeyword: String?,
    onSelectKeyword: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (keywords.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        keywords.forEach { keyword ->
            val isSelected = keyword.equals(selectedKeyword, ignoreCase = true)
            FilterChip(
                selected = isSelected,
                onClick = { onSelectKeyword(keyword) },
                label = { Text(keyword) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Tag,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}
