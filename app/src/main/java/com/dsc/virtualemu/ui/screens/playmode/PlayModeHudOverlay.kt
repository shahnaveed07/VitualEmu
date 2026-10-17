package com.dsc.virtualemu.ui.screens.playmode

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun PlayModeHudOverlay(
    viewModel: VitualEmuViewModel,
    modifier: Modifier = Modifier
) {
    val sessionState by viewModel.sessionState.collectAsState()
    val activeGame by viewModel.activeGame.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val mouseLookState by viewModel.mouseLookState.collectAsState()
    val activeSlots by viewModel.activePointerSlots.collectAsState()
    val runningMacros by viewModel.runningMacros.collectAsState()

    var isMinimized by remember { mutableStateOf(false) }
    var hudOpacity by remember { mutableFloatStateOf(0.94f) }

    if (sessionState != SessionState.ACTIVE && sessionState != SessionState.PAUSED_RESET) {
        return
    }

    Card(
        modifier = modifier
            .alpha(hudOpacity)
            .border(1.5.dp, if (mouseLookState.isShootingMode) StatusEmerald else ElectricBlue, RoundedCornerShape(14.dp))
            .testTag("play_mode_hud_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xF00F172A))
    ) {
        if (isMinimized) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (mouseLookState.isShootingMode) StatusEmerald else ElectricBlue, CircleShape)
                )
                Text(
                    text = if (mouseLookState.isShootingMode) "AIM LOCK" else "CURSOR",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                Text(
                    text = "Ptrs: ${activeSlots.size}/10",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
                Button(
                    onClick = { viewModel.emergencySafeStop() },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusCrimson),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text("STOP", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
                IconButton(onClick = { isMinimized = false }, modifier = Modifier.size(24.dp)) {
                    Icon(imageVector = Icons.Default.Visibility, contentDescription = "Expand HUD", tint = ElectricBlue, modifier = Modifier.size(16.dp))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .width(420.dp)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusBadge(
                            text = if (sessionState == SessionState.ACTIVE) "PLAY HUD ACTIVE" else "PAUSED (SAFE)",
                            color = if (sessionState == SessionState.ACTIVE) StatusEmerald else StatusAmber
                        )
                        Text(
                            text = "${activeGame?.displayName ?: "Game"} • ${activeProfile?.name ?: "Profile"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFCBD5E1),
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { isMinimized = true }, modifier = Modifier.size(24.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Minimize HUD", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Shooting Mode vs Cursor Mode + Free-Look + Smart Drag Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { viewModel.toggleShootingMode() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (mouseLookState.isShootingMode) StatusEmerald else ElectricBlue,
                            contentColor = MidnightBlue
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(34.dp).testTag("hud_toggle_shooting_mode")
                    ) {
                        Icon(
                            imageVector = if (mouseLookState.isShootingMode) Icons.Default.GpsFixed else Icons.Default.Mouse,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (mouseLookState.isShootingMode) "AIM LOCK (360°)" else "CURSOR MODE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    OutlinedButton(
                        onClick = { viewModel.toggleFreeLookPreview() },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (mouseLookState.isFreeLookActive) StatusAmber else Color(0xFFCBD5E1)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (mouseLookState.isFreeLookActive) StatusAmber else SlateBorder
                        ),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(
                            text = if (mouseLookState.isFreeLookActive) "EYE: ON" else "EYE (ALT)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = { viewModel.emergencySafeStop() },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusCrimson),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp).testTag("hud_safe_stop_button")
                    ) {
                        Icon(imageVector = Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("SAFE STOP", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                }

                // Telemetry bar: 10-Pointer slots, 360 Edge Resets, Running Macros, Opacity
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SlateNavy, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pointers: ${activeSlots.size}/10",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (activeSlots.isNotEmpty()) ElectricBlue else Color(0xFF94A3B8),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "360° Resets: ${mouseLookState.edgeResetCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFCBD5E1)
                    )
                    Text(
                        text = "Macros: ${runningMacros.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (runningMacros.isNotEmpty()) StatusAmber else Color(0xFF94A3B8)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(90.dp)) {
                        Slider(
                            value = hudOpacity,
                            onValueChange = { hudOpacity = it.coerceIn(0.35f, 1.0f) },
                            valueRange = 0.35f..1.0f,
                            colors = SliderDefaults.colors(thumbColor = ElectricBlue, activeTrackColor = ElectricBlue)
                        )
                    }
                }
            }
        }
    }
}
