package com.dsc.virtualemu.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsc.virtualemu.domain.model.BridgeHealthState
import com.dsc.virtualemu.domain.model.DeviceType
import com.dsc.virtualemu.domain.model.SessionState
import com.dsc.virtualemu.ui.VitualEmuViewModel
import com.dsc.virtualemu.ui.components.StatusBadge
import com.dsc.virtualemu.ui.theme.ElectricBlue
import com.dsc.virtualemu.ui.theme.MidnightBlue
import com.dsc.virtualemu.ui.theme.SlateBorder
import com.dsc.virtualemu.ui.theme.SlateNavy
import com.dsc.virtualemu.ui.theme.StatusAmber
import com.dsc.virtualemu.ui.theme.StatusCrimson
import com.dsc.virtualemu.ui.theme.StatusEmerald

@Composable
fun HomeScreen(
    viewModel: VitualEmuViewModel,
    onNavigateToEditor: () -> Unit,
    onNavigateToActivation: () -> Unit,
    onNavigateToTestMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeGame by viewModel.activeGame.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val bridgeStatus by viewModel.bridgeStatus.collectAsState()
    val connectedDevices by viewModel.connectedDevices.collectAsState()
    val sessionState by viewModel.sessionState.collectAsState()
    val preflightResult by viewModel.preflightResult.collectAsState()

    val hasKeyboard = connectedDevices.any { it.type == DeviceType.KEYBOARD && it.isConnected }
    val hasMouse = connectedDevices.any { it.type == DeviceType.MOUSE && it.isConnected }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MidnightBlue)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Hero Header & Brand
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "VitualEmu",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = "Tactical Keyboard & Mouse Input Mapper",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )
            }
            // Emergency Safe-Stop Button
            Button(
                onClick = { viewModel.emergencySafeStop() },
                colors = ButtonDefaults.buttonColors(containerColor = StatusCrimson),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("emergency_stop_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Safe Stop",
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("SAFE STOP", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        // Active Game & Profile Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SlateBorder, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SlateNavy)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ACTIVE TARGET GAME",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Bold
                    )
                    StatusBadge(
                        text = if (sessionState == SessionState.ACTIVE) "PLAY MODE ACTIVE" else "STANDBY",
                        color = if (sessionState == SessionState.ACTIVE) StatusEmerald else Color(0xFF94A3B8)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF0284C7), Color(0xFF0369A1))),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Gamepad,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeGame?.displayName ?: "No Game Selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = activeGame?.packageName ?: "Select a game from Games library",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Profile info
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Mapped Profile",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF64748B)
                            )
                            Text(
                                text = activeProfile?.name ?: "No Profile Attached",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = ElectricBlue
                            )
                        }

                        Button(
                            onClick = onNavigateToEditor,
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("open_editor_button")
                        ) {
                            Icon(imageVector = Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp), tint = MidnightBlue)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit Canvas", color = MidnightBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Launch / Play Mode Start Button
                Button(
                    onClick = { viewModel.startPlayMode() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("start_play_mode_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (sessionState == SessionState.ACTIVE) StatusAmber else StatusEmerald
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (sessionState == SessionState.ACTIVE) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (sessionState == SessionState.ACTIVE) "PAUSE PLAY MODE" else "START PLAY MODE",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }
            }
        }

        // Hardware Peripherals & Bridge Status
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SlateBorder, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SlateNavy)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PERIPHERALS & BRIDGE READINESS",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = {
                        viewModel.refreshBridgeHealth()
                    }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Peripherals row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Keyboard Pill
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                            .border(1.dp, if (hasKeyboard) StatusEmerald else SlateBorder, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Keyboard,
                                    contentDescription = null,
                                    tint = if (hasKeyboard) StatusEmerald else Color(0xFF64748B),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Keyboard", style = MaterialTheme.typography.labelSmall, color = Color(0xFFCBD5E1))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (hasKeyboard) "Connected" else "Not Detected",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (hasKeyboard) StatusEmerald else StatusAmber
                            )
                        }
                    }

                    // Mouse Pill
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                            .border(1.dp, if (hasMouse) StatusEmerald else SlateBorder, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Mouse,
                                    contentDescription = null,
                                    tint = if (hasMouse) StatusEmerald else Color(0xFF64748B),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Mouse", style = MaterialTheme.typography.labelSmall, color = Color(0xFFCBD5E1))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (hasMouse) "Connected" else "Not Detected",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (hasMouse) StatusEmerald else StatusAmber
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bridge Status Line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = bridgeStatus.type.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = bridgeStatus.detailMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        StatusBadge(
                            text = if (bridgeStatus.state == BridgeHealthState.CONNECTED) "AUTHORIZED" else "DISCONNECTED",
                            color = if (bridgeStatus.state == BridgeHealthState.CONNECTED) StatusEmerald else StatusCrimson
                        )
                    }
                }
            }
        }

        // Preflight & Quick Tools Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SlateBorder, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SlateNavy)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "UTILITY ACTIONS & VALIDATION",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.runPreflight() },
                        modifier = Modifier.weight(1f).testTag("run_preflight_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricBlue),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                    ) {
                        Icon(imageVector = Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Preflight")
                    }

                    OutlinedButton(
                        onClick = onNavigateToActivation,
                        modifier = Modifier.weight(1f).testTag("open_activation_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricBlue),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                    ) {
                        Icon(imageVector = Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Bridge Wizard")
                    }

                    OutlinedButton(
                        onClick = onNavigateToTestMode,
                        modifier = Modifier.weight(1f).testTag("open_testmode_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricBlue),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                    ) {
                        Text("Sandbox")
                    }
                }

                // Show preflight warnings or errors if any
                preflightResult?.let { pf ->
                    Spacer(modifier = Modifier.height(10.dp))
                    if (pf.blockingErrors.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(StatusCrimson.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .border(1.dp, StatusCrimson.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Error, contentDescription = null, tint = StatusCrimson, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Blocking Issues (${pf.blockingErrors.size}):", color = StatusCrimson, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                }
                                for (err in pf.blockingErrors) {
                                    Text("• $err", color = Color(0xFFFCA5A5), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    } else if (pf.warnings.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(StatusAmber.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .border(1.dp, StatusAmber.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = StatusAmber, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Notices (${pf.warnings.size}):", color = StatusAmber, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                }
                                for (warn in pf.warnings) {
                                    Text("• $warn", color = Color(0xFFFDE68A), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    } else if (pf.isReady) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(StatusEmerald.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .border(1.dp, StatusEmerald.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = StatusEmerald, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("All preflight checks passed. Ready to play!", color = StatusEmerald, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}
