package com.example.ui.main

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.example.R
import com.example.ui.accessibility.accessibleTouchTarget
import com.example.ui.main.tabs.FileTranscriptionTab
import com.example.ui.main.tabs.HistoryTab
import com.example.ui.main.tabs.LiveTranscriptionTab
import com.example.ui.navigation.MainTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val hasApiKey by viewModel.hasApiKey.collectAsState()

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
                            fontWeight = FontWeight.ExtraBold
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

                    // Settings quick access
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("top_settings_button")
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
                    onClick = { onNavigateToSettings() },
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
            if (!hasApiKey) {
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
                    MainTab.LIVE -> LiveTranscriptionTab(viewModel = viewModel)
                    MainTab.FILES -> FileTranscriptionTab(viewModel = viewModel)
                    MainTab.HISTORY -> HistoryTab(viewModel = viewModel)
                    MainTab.SETTINGS -> {
                        LaunchedEffect(Unit) { onNavigateToSettings() }
                    }
                }
            }
        }
    }
}
