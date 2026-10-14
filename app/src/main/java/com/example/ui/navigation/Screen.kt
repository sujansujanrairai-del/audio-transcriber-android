package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")
    object Main : Screen("main")
    object Settings : Screen("settings")
}

enum class MainTab(val label: String, val route: String) {
    LIVE("Live", "tab_live"),
    FILES("Files", "tab_files"),
    HISTORY("History", "tab_history"),
    SETTINGS("Settings", "tab_settings")
}
