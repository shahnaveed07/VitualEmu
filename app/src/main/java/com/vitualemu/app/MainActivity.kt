package com.vitualemu.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.vitualemu.app.poc.ui.BridgeTestScreen
import com.vitualemu.app.poc.ui.FeasibilityDashboardScreen
import com.vitualemu.app.poc.ui.FeasibilityViewModel
import com.vitualemu.app.poc.ui.TestResultsScreen
import com.vitualemu.app.ui.theme.VitualEmuTheme

enum class PocScreen {
    DASHBOARD,
    BRIDGE_TEST,
    RESULTS
}

class MainActivity : ComponentActivity() {
    private val viewModel: FeasibilityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VitualEmuTheme {
                PocApp(viewModel = viewModel)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Safety Requirement: On focus loss or backgrounding, neutralize all active touches
        viewModel.onFocusLoss()
    }
}

@Composable
fun PocApp(viewModel: FeasibilityViewModel) {
    var currentScreen by remember { mutableStateOf(PocScreen.DASHBOARD) }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        when (currentScreen) {
            PocScreen.DASHBOARD -> {
                FeasibilityDashboardScreen(
                    viewModel = viewModel,
                    onNavigateToBridgeTest = { currentScreen = PocScreen.BRIDGE_TEST },
                    onNavigateToResults = { currentScreen = PocScreen.RESULTS }
                )
            }
            PocScreen.BRIDGE_TEST -> {
                BackHandler { currentScreen = PocScreen.DASHBOARD }
                BridgeTestScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = PocScreen.DASHBOARD }
                )
            }
            PocScreen.RESULTS -> {
                BackHandler { currentScreen = PocScreen.DASHBOARD }
                TestResultsScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = PocScreen.DASHBOARD }
                )
            }
        }
    }
}
