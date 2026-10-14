package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.main.MainScreen
import com.example.ui.main.MainViewModel
import com.example.ui.navigation.Screen
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.TranscendTheme
import com.example.ui.welcome.WelcomeScreen
import com.example.ui.welcome.WelcomeViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as TranscendApplication

        setContent {
            val themeSettings by app.userPreferencesManager.themeSettings.collectAsState()
            val accessibilitySettings by app.userPreferencesManager.accessibilitySettings.collectAsState()

            TranscendTheme(
                settings = themeSettings,
                highContrast = accessibilitySettings.highContrast
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val hasKey = remember { app.secureKeyStorage.hasValidApiKey() }
                    val startDestination = if (hasKey) Screen.Main.route else Screen.Welcome.route

                    // Runtime Permission Request for Microphone and Notifications
                    var hasMicPermission by remember {
                        mutableStateOf(
                            ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                        )
                    }

                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions()
                    ) { permissions ->
                        hasMicPermission = permissions[Manifest.permission.RECORD_AUDIO] ?: hasMicPermission
                    }

                    LaunchedEffect(Unit) {
                        val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        permissionLauncher.launch(permissionsToRequest.toTypedArray())
                    }

                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
                        composable(Screen.Welcome.route) {
                            val welcomeVm: WelcomeViewModel = viewModel {
                                WelcomeViewModel(app.secureKeyStorage)
                            }
                            WelcomeScreen(
                                viewModel = welcomeVm,
                                onNavigateToMain = {
                                    navController.navigate(Screen.Main.route) {
                                        popUpTo(Screen.Welcome.route) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(Screen.Main.route) {
                            val mainVm: MainViewModel = viewModel {
                                MainViewModel(app)
                            }
                            MainScreen(
                                viewModel = mainVm,
                                onNavigateToSettings = {
                                    navController.navigate(Screen.Settings.route)
                                }
                            )
                        }

                        composable(Screen.Settings.route) {
                            val settingsVm: SettingsViewModel = viewModel {
                                SettingsViewModel(app)
                            }
                            SettingsScreen(
                                viewModel = settingsVm,
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
