package com.blindtechnexus.audiotranscriber.ui.main

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.blindtechnexus.audiotranscriber.ui.accessibility.accessibleTouchTarget
import com.blindtechnexus.audiotranscriber.ui.main.tabs.FileTranscriptionTab
import com.blindtechnexus.audiotranscriber.ui.main.tabs.HistoryTab
import com.blindtechnexus.audiotranscriber.ui.main.tabs.LiveTranscriptionTab
import com.blindtechnexus.audiotranscriber.ui.navigation.MainTab

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blindtechnexus.audiotranscriber.AudioTranscriberApplication
import com.blindtechnexus.audiotranscriber.ui.settings.SettingsContent
import com.blindtechnexus.audiotranscriber.ui.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as AudioTranscriberApplication
    val selectedTab by viewModel.selectedTab.collectAsState()
    val hasApiKey by viewModel.hasApiKey.collectAsState()
    val showMissingKeyDialog by viewModel.showApiKeyMissingDialog.collectAsState()
    val webSearchState by viewModel.webSearchState.collectAsState()

    val settingsVm: SettingsViewModel = viewModel {
        SettingsViewModel(app)
    }

    LaunchedEffect(Unit) {
        viewModel.refreshApiKeyStatus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.semantics { heading() }
                        )
                    }
                },
                actions = {
                    // API Key Status Indicator Dot
                    Surface(
                        shape = CircleShape,
                        color = if (hasApiKey) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .accessibleTouchTarget()
                            .clickable { onNavigateToSettings() }
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
                                    .background(if (hasApiKey) Color(0xFF10B981) else MaterialTheme.colorScheme.error)
                            )
                            Text(
                                text = if (hasApiKey) "Active" else "No Key",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (hasApiKey) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Settings quick access (opens full screen settings without tab bar)
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .accessibleTouchTarget()
                            .testTag("top_settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_settings_btn))
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("main_bottom_nav")
            ) {
                NavigationBarItem(
                    selected = selectedTab == MainTab.LIVE,
                    onClick = { viewModel.selectTab(MainTab.LIVE) },
                    icon = { Icon(Icons.Default.Mic, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_live)) },
                    modifier = Modifier.testTag("nav_item_live")
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.FILES,
                    onClick = { viewModel.selectTab(MainTab.FILES) },
                    icon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_files)) },
                    modifier = Modifier.testTag("nav_item_files")
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.HISTORY,
                    onClick = { viewModel.selectTab(MainTab.HISTORY) },
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_history)) },
                    modifier = Modifier.testTag("nav_item_history")
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.SETTINGS,
                    onClick = { viewModel.selectTab(MainTab.SETTINGS) },
                    icon = { Icon(Icons.Default.Tune, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_settings)) },
                    modifier = Modifier.testTag("nav_item_settings")
                )
            }
        },
        modifier = modifier.testTag("main_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Demo mode warning banner if no API key is provided
            if (!hasApiKey && selectedTab != MainTab.SETTINGS) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.banner_api_key_required),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onNavigateToSettings) {
                            Text("Setup Key", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Tab Content
            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    MainTab.LIVE -> LiveTranscriptionTab(viewModel = viewModel, onOpenSettings = onNavigateToSettings)
                    MainTab.FILES -> FileTranscriptionTab(viewModel = viewModel, onOpenSettings = onNavigateToSettings)
                    MainTab.HISTORY -> HistoryTab(viewModel = viewModel)
                    MainTab.SETTINGS -> {
                        SettingsContent(viewModel = settingsVm)
                    }
                }
            }
        }
    }

    // Missing API Key Dialog
    if (showMissingKeyDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissApiKeyMissingDialog() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(
                        stringResource(R.string.api_key_required_dialog_title),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() }
                    )
                }
            },
            text = {
                Text(
                    stringResource(R.string.api_key_required_dialog_desc),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissApiKeyMissingDialog()
                        onNavigateToSettings()
                    },
                    modifier = Modifier.accessibleTouchTarget()
                ) {
                    Text(stringResource(R.string.api_key_required_dialog_open_settings))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissApiKeyMissingDialog() },
                    modifier = Modifier.accessibleTouchTarget()
                ) {
                    Text(stringResource(R.string.api_key_required_dialog_dismiss))
                }
            }
        )
    }

    // Web Search Grounding / Fact-Checking Modal Dialog
    if (webSearchState.isSearching || webSearchState.result != null || webSearchState.errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissWebSearchDialog() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.TravelExplore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Web Search & Fact Check",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() }
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    if (webSearchState.isSearching) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Searching web & verifying facts with Google Search...",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else if (webSearchState.errorMessage != null) {
                        Text(
                            text = webSearchState.errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else if (webSearchState.result != null) {
                        val result = webSearchState.result!!
                        Column(
                            modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = result.content,
                                style = MaterialTheme.typography.bodyMedium
                            )

                            if (result.sources.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                Text(
                                    text = "Sources & References:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                result.sources.forEach { source ->
                                    Text(
                                        text = "• $source",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (webSearchState.result != null) {
                    Button(
                        onClick = {
                            viewModel.copyToClipboard(webSearchState.result!!.content)
                            viewModel.dismissWebSearchDialog()
                        },
                        modifier = Modifier.accessibleTouchTarget()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy Info")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissWebSearchDialog() },
                    modifier = Modifier.accessibleTouchTarget()
                ) {
                    Text("Close")
                }
            }
        )
    }
}
