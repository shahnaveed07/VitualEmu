package com.dsc.virtualemu.ui.screens.settings

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsc.virtualemu.ui.VitualEmuViewModel
import com.dsc.virtualemu.ui.screens.activation.ActivationModeDialog
import com.dsc.virtualemu.ui.theme.StatusCrimson
import com.dsc.virtualemu.ui.theme.StatusEmerald

@Composable
fun SettingsScreen(
    viewModel: VitualEmuViewModel,
    onNavigateToAccount: () -> Unit = {},
    onNavigateToActivation: () -> Unit = {},
    onNavigateToPermissions: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val density = LocalDensity.current

    val bridgeStatus by viewModel.bridgeStatus.collectAsState()
    val isUiTestModeEnabled by viewModel.isUiTestModeEnabled.collectAsState()

    var showResetDialog by remember { mutableStateOf(false) }
    var showActivationModeDialog by remember { mutableStateOf(false) }
    var useOldConfigs by remember { mutableStateOf(false) }
    var touchCompatMode by remember { mutableStateOf(false) }

    // Dynamic Runtime Device Metadata
    val deviceModel = remember {
        val vendor = Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        "$vendor ${Build.MODEL}"
    }

    val androidVersion = remember {
        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }

    val screenRes = remember(config) {
        val widthPx = (config.screenWidthDp * density.density).toInt()
        val heightPx = (config.screenHeightDp * density.density).toInt()
        "${maxOf(widthPx, heightPx)} x ${minOf(widthPx, heightPx)} px"
    }

    val (totalRam, availRam) = remember(context) {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(memInfo)
            if (memInfo != null) {
                val totalGb = "%.1f GB".format(memInfo.totalMem / (1024.0 * 1024.0 * 1024.0))
                val availGb = "%.1f GB".format(memInfo.availMem / (1024.0 * 1024.0 * 1024.0))
                Pair(totalGb, availGb)
            } else Pair("N/A", "N/A")
        } catch (_: Exception) {
            Pair("N/A", "N/A")
        }
    }

    val appVersion = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    // Outer Background: Light Lavender
    val outerLavender = Color(0xFFE9D5FF)
    val panelIndigo = Color(0xFF281845)
    val rowPeriwinkle = Color(0xFF4C3A7A)
    val sectionDarkBadge = Color(0xFF1E1438)
    val peachText = Color(0xFFFB923C)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(outerLavender)
    ) {
        // A. Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 10.dp)
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                fontStyle = FontStyle.Italic,
                color = Color(0xFF1E1B4B),
                fontSize = 24.sp
            )
        }

        // B. Main Settings Panel
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(panelIndigo)
                .border(1.dp, Color(0xFF3B2A68), RoundedCornerShape(18.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Row 1: Account Info
                SettingsClickableRow(
                    title = "Account Info",
                    onClick = onNavigateToAccount,
                    rowColor = rowPeriwinkle
                )

                // Row 2: Activation Mode
                SettingsValueRow(
                    title = "Activation Mode",
                    value = bridgeStatus.type.displayName,
                    onClick = { showActivationModeDialog = true },
                    rowColor = rowPeriwinkle
                )

                // Row 3: Activation Assistant
                SettingsClickableRow(
                    title = "Activation Assistant",
                    onClick = { showActivationModeDialog = true },
                    rowColor = rowPeriwinkle
                )

                // Row 4: Permissions Management
                SettingsClickableRow(
                    title = "Permissions Management",
                    onClick = onNavigateToPermissions,
                    rowColor = rowPeriwinkle
                )

                // Row 5: Use Old Configs
                SettingsSwitchRow(
                    title = "Use Old Configs",
                    checked = useOldConfigs,
                    onCheckedChange = {
                        useOldConfigs = it
                        viewModel.showFeedback(if (it) "Legacy config compatibility enabled" else "Standard config active")
                    },
                    rowColor = rowPeriwinkle
                )

                // Row 6: Touch Compatibility Mode
                SettingsSwitchRow(
                    title = "Touch Compatibility Mode",
                    checked = touchCompatMode,
                    onCheckedChange = {
                        touchCompatMode = it
                        viewModel.showFeedback(if (it) "Touch compatibility mode enabled" else "Standard touch dispatch active")
                    },
                    rowColor = rowPeriwinkle
                )

                // Row 6.5: Debug Mode
                SettingsSwitchRow(
                    title = "Debug Mode (UI Overlay Preview)",
                    checked = isUiTestModeEnabled,
                    onCheckedChange = {
                        viewModel.setUiTestModeEnabled(it)
                    },
                    rowColor = rowPeriwinkle
                )

                // Row 7: App Version
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(rowPeriwinkle)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "App Version",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "v$appVersion",
                            color = Color(0xFFD8B4FE),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }

                // Row 8: Reset All Settings
                SettingsClickableRow(
                    title = "Reset All Settings",
                    onClick = { showResetDialog = true },
                    rowColor = rowPeriwinkle,
                    titleColor = Color(0xFFFCA5A5)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // C. Device Information Section
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(sectionDarkBadge)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Device Information",
                        color = peachText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(rowPeriwinkle)
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DeviceInfoRow("Device Model", deviceModel)
                        DeviceInfoRow("Android Version", androidVersion)
                        DeviceInfoRow("Screen Resolution", screenRes)
                        DeviceInfoRow("Memory (Total / Free)", "$totalRam / $availRam")
                    }
                }
            }
        }
    }

    // Confirmation Dialog for Reset All Settings
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text("Reset All Settings", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Are you sure you want to reset all local settings and diagnostics logs? Saved games and key mapping profiles will remain intact.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearDiagnosticsCache()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusCrimson,
                        contentColor = Color.White
                    )
                ) {
                    Text("Reset", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = panelIndigo
        )
    }

    // Modal Activation Mode Selection Dialog
    if (showActivationModeDialog) {
        ActivationModeDialog(
            viewModel = viewModel,
            onDismiss = { showActivationModeDialog = false },
            onOpenWizard = {
                showActivationModeDialog = false
                onNavigateToActivation()
            }
        )
    }
}

@Composable
private fun SettingsClickableRow(
    title: String,
    onClick: () -> Unit,
    rowColor: Color,
    titleColor: Color = Color.White
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(rowColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("settings_row_$title")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = titleColor,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFFD8B4FE),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingsValueRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    rowColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(rowColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value,
                    color = Color(0xFF38BDF8),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFFD8B4FE),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    rowColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(rowColor)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF281845),
                    checkedTrackColor = Color(0xFF38BDF8),
                    uncheckedThumbColor = Color(0xFF94A3B8),
                    uncheckedTrackColor = Color(0xFF1E1438)
                )
            )
        }
    }
}

@Composable
private fun DeviceInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFFCBD5E1),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
