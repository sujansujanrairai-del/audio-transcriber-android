package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomVocabularyBottomSheet(
    currentVocabulary: List<String>,
    onSaveVocabulary: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var terms by remember { mutableStateOf(currentVocabulary) }
    var singleInput by remember { mutableStateOf("") }
    var bulkInput by remember { mutableStateOf("") }
    var isBulkMode by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Custom Vocabulary",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${terms.size} / 1000 terms registered",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(onClick = { isBulkMode = !isBulkMode }) {
                    Text(if (isBulkMode) "Single Add" else "Bulk Paste")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!isBulkMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = singleInput,
                        onValueChange = { singleInput = it },
                        label = { Text("Add specialized term (e.g., medical, tech)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            val trimmed = singleInput.trim()
                            if (trimmed.isNotBlank() && !terms.contains(trimmed) && terms.size < 1000) {
                                terms = terms + trimmed
                                singleInput = ""
                            }
                        },
                        enabled = singleInput.isNotBlank() && terms.size < 1000
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add term")
                    }
                }
            } else {
                OutlinedTextField(
                    value = bulkInput,
                    onValueChange = { bulkInput = it },
                    label = { Text("Paste comma-separated or newline-separated terms") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    placeholder = { Text("Kathmandu, Pokhara, PyTorch, Kubernetes, CRISPR...") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val newTerms = bulkInput.split("[,\\n]+".toRegex())
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                        terms = (terms + newTerms).distinct().take(1000)
                        bulkInput = ""
                        isBulkMode = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = bulkInput.isNotBlank()
                ) {
                    Text("Import Terms")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Current terms display
            Text(
                text = "Active Session Vocabulary",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (terms.isEmpty()) {
                Text(
                    text = "No custom vocabulary set yet. Add terms to improve Gemini 3.5 Transcribe's accuracy on unique names, jargon, and acronyms.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    terms.chunked(3).forEach { chunk ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            chunk.forEach { term ->
                                InputChip(
                                    selected = true,
                                    onClick = { },
                                    label = { Text(term) },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { terms = terms - term },
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Remove $term",
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (terms.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { terms = emptyList() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear All")
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(
                    onClick = {
                        onSaveVocabulary(terms)
                        onDismiss()
                    }
                ) {
                    Text("Apply to Transcription")
                }
            }
        }
    }
}
