package com.dsc.virtualemu.ui.screens.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dsc.virtualemu.ui.VitualEmuViewModel
import com.dsc.virtualemu.ui.theme.StatusAmber
import com.dsc.virtualemu.ui.theme.StatusEmerald

@Composable
fun PermissionsScreen(
    viewModel: VitualEmuViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isIgnoringBattery by remember {
        mutableStateOf(
            (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                ?.isIgnoringBatteryOptimizations(context.packageName) == true
        )
    }
    var isPowerSaveMode by remember {
        mutableStateOf(
            (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                ?.isPowerSaveMode == true
        )
    }

    var showOverlayHelp by remember { mutableStateOf(false) }
    var showBackgroundHelp by remember { mutableStateOf(false) }
    var showPowerSaverHelp by remember { mutableStateOf(false) }

    fun refreshState() {
        hasOverlayPermission = Settings.canDrawOverlays(context)
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        isIgnoringBattery = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        isPowerSaveMode = pm?.isPowerSaveMode == true
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Outer container in Light Lavender
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
        // A. Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Permissions Management",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                fontStyle = FontStyle.Italic,
                color = Color(0xFF1E1B4B),
                fontSize = 22.sp
            )

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(panelIndigo)
                    .clickable(onClick = onBack)
                    .testTag("permissions_close_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // B. Main Content Panel
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, bottom = 20.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(panelIndigo)
                .border(1.dp, Color(0xFF3B2A68), RoundedCornerShape(18.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // C. Required Permission Section
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(sectionDarkBadge)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Required Permission",
                        color = peachText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                // Row 1 — Overlay Window Permission
                PermissionRowItem(
                    title = "Overlay Window Permission",
                    isGranted = hasOverlayPermission,
                    onHelpClick = { showOverlayHelp = true },
                    onGrantClick = {
                        try {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                viewModel.showFeedback("Unable to open Overlay settings page")
                            }
                        }
                    },
                    rowColor = rowPeriwinkle,
                    badgeColor = sectionDarkBadge
                )

                // Row 2 — Run in Background Permission
                PermissionRowItem(
                    title = "Run in Background Permission",
                    isGranted = isIgnoringBattery,
                    onHelpClick = { showBackgroundHelp = true },
                    onGrantClick = {
                        try {
                            val intent = Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            try {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                try {
                                    val intent = Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    viewModel.showFeedback("Unable to open Battery settings page")
                                }
                            }
                        }
                    },
                    rowColor = rowPeriwinkle,
                    badgeColor = sectionDarkBadge
                )

                Spacer(modifier = Modifier.height(4.dp))

                // D. Recommended Settings Section
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(sectionDarkBadge)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Recommended Settings",
                        color = peachText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                // Row 3 — Turn Off Power Saver Mode
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(rowPeriwinkle)
                        .clickable {
                            try {
                                val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                try {
                                    val intent = Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    viewModel.showFeedback("Unable to open Power Saver settings page")
                                }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Turn Off Power Saver Mode",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { showPowerSaverHelp = true },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HelpOutline,
                                    contentDescription = "Help",
                                    tint = Color(0xFFD8B4FE),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (!isPowerSaveMode) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(StatusEmerald),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Satisfactory",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(StatusAmber.copy(alpha = 0.2f))
                                    .border(1.dp, StatusAmber, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "POWER SAVER ON",
                                    color = StatusAmber,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // E. Help Explanation Dialogs
    if (showOverlayHelp) {
        PermissionHelpDialog(
            title = "Overlay Window Permission",
            description = "VitualEmu requires the Display Over Other Apps (Overlay) permission to show floating keymapping HUD controls over your game while playing.",
            onDismiss = { showOverlayHelp = false }
        )
    }

    if (showBackgroundHelp) {
        PermissionHelpDialog(
            title = "Run in Background Permission",
            description = "VitualEmu requires background operation access to ensure the input mapping bridge remains active without being interrupted by Android memory management during gameplay.",
            onDismiss = { showBackgroundHelp = false }
        )
    }

    if (showPowerSaverHelp) {
        PermissionHelpDialog(
            title = "Turn Off Power Saver Mode",
            description = "Android Power Saver mode restricts background CPU and touch event processing. Turning off Power Saver mode ensures zero input latency and precise response times.",
            onDismiss = { showPowerSaverHelp = false }
        )
    }
}

@Composable
private fun PermissionRowItem(
    title: String,
    isGranted: Boolean,
    onHelpClick: () -> Unit,
    onGrantClick: () -> Unit,
    rowColor: Color,
    badgeColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(rowColor)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onHelpClick,
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = "Help",
                        tint = Color(0xFFD8B4FE),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (isGranted) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(StatusEmerald.copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = StatusEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "GRANTED",
                        color = StatusEmerald,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            } else {
                Button(
                    onClick = onGrantClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = badgeColor,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("grant_permission_button_$title")
                ) {
                    Text("GRANT", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun PermissionHelpDialog(
    title: String,
    description: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Text(
                text = description,
                color = Color(0xFFE2E8F0),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFB923C),
                    contentColor = Color(0xFF1E1438)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Got it", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color(0xFF281845)
    )
}
