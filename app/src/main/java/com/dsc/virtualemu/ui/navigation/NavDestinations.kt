package com.dsc.virtualemu.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector

enum class MainTab(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    HOME("home", "Home", Icons.Default.SportsEsports),
    GAMES("games", "Games", Icons.Default.Gamepad),
    PROFILES("profiles", "Profiles", Icons.Default.Tune),
    SETTINGS("settings", "Settings", Icons.Default.Settings)
}

object NavDestinations {
    const val HOME = "home"
    const val GAMES = "games"
    const val PROFILES = "profiles"
    const val SETTINGS = "settings"
    const val EDITOR = "editor"
    const val ACTIVATION = "activation"
    const val TEST_MODE = "test_mode"
}
