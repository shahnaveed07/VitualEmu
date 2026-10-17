package com.dsc.virtualemu.ui.screens.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsEsports
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsc.virtualemu.domain.model.BridgeHealthState
import com.dsc.virtualemu.domain.model.DeviceType
import com.dsc.virtualemu.domain.model.SessionState
import com.dsc.virtualemu.ui.VitualEmuViewModel
import com.dsc.virtualemu.ui.components.StatusBadge
import com.dsc.virtualemu.ui.screens.games.InstalledAppPickerDialog
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
    modifier: Modifier = Modifier,
    onNavigateToGames: () -> Unit = {}
) {
    val games by viewModel.games.collectAsState()
    val activeGame by viewModel.activeGame.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val bridgeStatus by viewModel.bridgeStatus.collectAsState()
    val connectedDevices by viewModel.connectedDevices.collectAsState()
    val sessionState by viewModel.sessionState.collectAsState()
    val preflightResult by viewModel.preflightResult.collectAsState()

    val hasKeyboard = connectedDevices.any { it.type == DeviceType.KEYBOARD && it.isConnected }
    val hasMouse = connectedDevices.any { it.type == DeviceType.MOUSE && it.isConnected }

    var showAppPicker by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MidnightBlue)
    ) {
        // Faint low-contrast gaming background accents
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridColor = Color(0xFF1E293B).copy(alpha = 0.25f)
            val strokeWidth = 1f
            // Subtle diagonal background lines
            val step = 120f
            var x = -size.height
            while (x < size.width + size.height) {
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x + size.height, size.height),
                    strokeWidth = strokeWidth
                )
                x += step
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // 1. A. Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Top-Left: VitualEmu gamepad logo & wordmark
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF0EA5E9), Color(0xFF0284C7))),
                                RoundedCornerShape(10.dp)
                            )
                            .border(1.dp, ElectricBlue.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = "VitualEmu Logo",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)) {
                                append("Virtual")
                            }
                            withStyle(SpanStyle(color = ElectricBlue, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)) {
                                append("Emu")
                            }
                        }
                    )
                }

                // Top-Center: Prominent red/pink rounded status banner "Permission required"
                val isAuthorized = bridgeStatus.state == BridgeHealthState.CONNECTED
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isAuthorized) StatusEmerald.copy(alpha = 0.15f) else Color(0xFF450A0A))
                        .border(
                            1.dp,
                            if (isAuthorized) StatusEmerald.copy(alpha = 0.5f) else StatusCrimson.copy(alpha = 0.6f),
                            RoundedCornerShape(20.dp)
                        )
                        .clickable(enabled = !isAuthorized, onClick = onNavigateToActivation)
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                        .testTag("permission_banner")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isAuthorized) Icons.Default.CheckCircle else Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (isAuthorized) StatusEmerald else StatusCrimson,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isAuthorized) "Bridge Active" else "Permission required",
                            color = if (isAuthorized) StatusEmerald else Color(0xFFFCA5A5),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (!isAuthorized) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = Color(0xFFFCA5A5),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Top-Right: Two compact device-status cards (Mouse & Keyboard)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Mouse Card
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                            .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mouse,
                                contentDescription = null,
                                tint = if (hasMouse) StatusEmerald else Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                            Column {
                                Text("Mouse", fontSize = 10.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (hasMouse) "Connected" else "Disconnected",
                                    fontSize = 11.sp,
                                    color = if (hasMouse) StatusEmerald else StatusCrimson,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(if (hasMouse) StatusEmerald else StatusCrimson, CircleShape)
                            )
                        }
                    }

                    // Keyboard Card
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                            .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Keyboard,
                                contentDescription = null,
                                tint = if (hasKeyboard) StatusEmerald else Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                            Column {
                                Text("Keyboard", fontSize = 10.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (hasKeyboard) "Connected" else "Disconnected",
                                    fontSize = 11.sp,
                                    color = if (hasKeyboard) StatusEmerald else StatusCrimson,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(if (hasKeyboard) StatusEmerald else StatusCrimson, CircleShape)
                            )
                        }
                    }
                }
            }

            // 1. B. Upper-Left "+ Add Game" button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Button(
                    onClick = onNavigateToGames,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricBlue,
                        contentColor = MidnightBlue
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("add_game_upper_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MidnightBlue
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("+ Add Game", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            // 1. C. First Launch Empty State vs Populated State
            if (games.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Gamepad-with-plus illustration
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(ElectricBlue.copy(alpha = 0.25f), Color.Transparent)
                                    ),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .background(Color(0xFF1E293B), CircleShape)
                                    .border(1.5.dp, ElectricBlue.copy(alpha = 0.6f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Box {
                                    Icon(
                                        imageVector = Icons.Default.SportsEsports,
                                        contentDescription = null,
                                        tint = ElectricBlue,
                                        modifier = Modifier.size(38.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(20.dp)
                                            .background(ElectricBlue, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            tint = MidnightBlue,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = "No games added yet",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Add your games to start using VitualEmu with custom controls.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 440.dp)
                        )

                        Spacer(modifier = Modifier.height(22.dp))

                        Button(
                            onClick = onNavigateToGames,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricBlue,
                                contentColor = MidnightBlue
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 26.dp, vertical = 12.dp),
                            modifier = Modifier.testTag("add_game_empty_state_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MidnightBlue
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("+ Add Game", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            } else {
                // Populated Game Card & Play Mode Controls
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SlateBorder, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateNavy)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
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

                        Spacer(modifier = Modifier.height(10.dp))

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
                                    text = activeGame?.displayName ?: games.firstOrNull()?.displayName ?: "",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = activeGame?.packageName ?: games.firstOrNull()?.packageName ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF94A3B8)
                                )
                            }

                            (activeGame?.packageName ?: games.firstOrNull()?.packageName)?.let { pkg ->
                                OutlinedButton(
                                    onClick = { viewModel.launchGame(pkg) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricBlue),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                                    modifier = Modifier.testTag("home_launch_game_button")
                                ) {
                                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Start Game", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Profile / Control Scheme info
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

                // Utilities & Preflight Card
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
            }

            Spacer(modifier = Modifier.height(70.dp))
        }
    }

    // Installed App Picker Dialog (Same Add Game workflow)
    if (showAppPicker) {
        InstalledAppPickerDialog(
            viewModel = viewModel,
            onDismiss = { showAppPicker = false },
            onAppsApplied = { apps ->
                viewModel.addMultipleInstalledGames(apps)
                showAppPicker = false
            }
        )
    }
}
