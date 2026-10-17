package com.dsc.virtualemu.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.ui.graphics.vector.ImageVector

enum class MainTab(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    HOME("home", "Start Game", Icons.Default.SportsEsports),
    ACCOUNT("profiles", "Account", Icons.Default.Person),
    SETTINGS("settings", "Settings", Icons.Default.Settings)
}

object NavDestinations {
    const val HOME = "home"
    const val GAMES = "games"
    const val PROFILES = "profiles"
    const val SETTINGS = "settings"
    const val EDITOR = "editor"
    const val ACTIVATION = "activation"
    const val PERMISSIONS = "permissions"
    const val TEST_MODE = "test_mode"
}
