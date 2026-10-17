package com.vitualemu.app

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.vitualemu.app.ui.VitualEmuViewModel
import com.vitualemu.app.ui.navigation.MainTab
import com.vitualemu.app.ui.navigation.NavDestinations
import com.vitualemu.app.ui.screens.activation.ActivationWizardScreen
import com.vitualemu.app.ui.screens.editor.MappingEditorScreen
import com.vitualemu.app.ui.screens.games.GamesScreen
import com.vitualemu.app.ui.screens.home.HomeScreen
import com.vitualemu.app.ui.screens.profiles.ProfilesScreen
import com.vitualemu.app.ui.screens.settings.SettingsScreen
import com.vitualemu.app.ui.screens.testmode.TestModeScreen
import com.vitualemu.app.ui.theme.ElectricBlue
import com.vitualemu.app.ui.theme.MidnightBlue
import com.vitualemu.app.ui.theme.SlateNavy
import com.vitualemu.app.ui.theme.VitualEmuTheme

class MainActivity : ComponentActivity() {
    private val viewModel: VitualEmuViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        enableEdgeToEdge()
        setContent {
            VitualEmuTheme {
                VitualEmuApp(viewModel = viewModel)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Safety Requirement: On focus loss or backgrounding, neutralize all active touches immediately
        viewModel.onFocusLoss()
    }

    override fun onStop() {
        super.onStop()
        viewModel.onFocusLoss()
    }
}

@Composable
fun VitualEmuApp(viewModel: VitualEmuViewModel) {
    var currentRoute by remember { mutableStateOf(NavDestinations.HOME) }
    var currentTab by remember { mutableStateOf(MainTab.HOME) }
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    val isTopLevelTab = currentRoute in listOf(
        NavDestinations.HOME,
        NavDestinations.GAMES,
        NavDestinations.PROFILES,
        NavDestinations.SETTINGS
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (isTopLevelTab) {
                NavigationBar(
                    containerColor = SlateNavy,
                    contentColor = Color.White
                ) {
                    MainTab.values().forEach { tab ->
                        val selected = currentTab == tab && currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                currentTab = tab
                                currentRoute = tab.route
                            },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title
                                )
                            },
                            label = { Text(tab.title) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MidnightBlue,
                                selectedTextColor = ElectricBlue,
                                indicatorColor = ElectricBlue,
                                unselectedIconColor = Color(0xFF94A3B8),
                                unselectedTextColor = Color(0xFF94A3B8)
                            ),
                            modifier = Modifier.testTag("nav_tab_${tab.route}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        when (currentRoute) {
            NavDestinations.HOME -> {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToEditor = { currentRoute = NavDestinations.EDITOR },
                    onNavigateToActivation = { currentRoute = NavDestinations.ACTIVATION },
                    onNavigateToTestMode = { currentRoute = NavDestinations.TEST_MODE },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            NavDestinations.GAMES -> {
                BackHandler {
                    currentTab = MainTab.HOME
                    currentRoute = NavDestinations.HOME
                }
                GamesScreen(
                    viewModel = viewModel,
                    onGameSelected = {
                        currentTab = MainTab.PROFILES
                        currentRoute = NavDestinations.PROFILES
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            NavDestinations.PROFILES -> {
                BackHandler {
                    currentTab = MainTab.HOME
                    currentRoute = NavDestinations.HOME
                }
                ProfilesScreen(
                    viewModel = viewModel,
                    onNavigateToEditor = { currentRoute = NavDestinations.EDITOR },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            NavDestinations.SETTINGS -> {
                BackHandler {
                    currentTab = MainTab.HOME
                    currentRoute = NavDestinations.HOME
                }
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToActivation = { currentRoute = NavDestinations.ACTIVATION },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            NavDestinations.EDITOR -> {
                MappingEditorScreen(
                    viewModel = viewModel,
                    onBack = {
                        currentRoute = currentTab.route
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            NavDestinations.ACTIVATION -> {
                ActivationWizardScreen(
                    viewModel = viewModel,
                    onBack = {
                        currentRoute = currentTab.route
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            NavDestinations.TEST_MODE -> {
                TestModeScreen(
                    viewModel = viewModel,
                    onBack = {
                        currentRoute = currentTab.route
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}
