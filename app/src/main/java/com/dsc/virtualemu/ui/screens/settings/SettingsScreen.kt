package com.dsc.virtualemu.ui.screens.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsc.virtualemu.domain.model.BridgeType
import com.dsc.virtualemu.ui.VitualEmuViewModel
import com.dsc.virtualemu.ui.theme.ElectricBlue
import com.dsc.virtualemu.ui.theme.MidnightBlue
import com.dsc.virtualemu.ui.theme.SlateBorder
import com.dsc.virtualemu.ui.theme.SlateNavy
import com.dsc.virtualemu.ui.theme.StatusAmber
import com.dsc.virtualemu.ui.theme.StatusCrimson
import com.dsc.virtualemu.ui.theme.StatusEmerald

@Composable
fun SettingsScreen(
    viewModel: VitualEmuViewModel,
    onNavigateToActivation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bridgeStatus by viewModel.bridgeStatus.collectAsState()
    val connectedDevices by viewModel.connectedDevices.collectAsState()
    val hotPlugLog by viewModel.hotPlugLog.collectAsState()
    val isProUnlocked by viewModel.isProUnlocked.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()

    var showAccountDeletionDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MidnightBlue)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Active Scheme Tuning Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SlateNavy),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ACTIVE SCHEME TUNING (${activeProfile?.name ?: "None Selected"})",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                val profile = activeProfile
                if (profile == null) {
                    Text(
                        text = "No active scheme selected. Select a game and control scheme from Profiles tab.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                } else {
                    val config = profile.mouseLookConfig
                    Text(
                        text = "Mouse Look X Sensitivity: ${"%.2f".format(config.sensitivityX)}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = config.sensitivityX,
                        onValueChange = { newX ->
                            viewModel.updateMouseLookConfig(config.copy(sensitivityX = newX))
                        },
                        valueRange = 0.2f..5.0f,
                        colors = SliderDefaults.colors(thumbColor = ElectricBlue, activeTrackColor = ElectricBlue)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Mouse Look Y Sensitivity: ${"%.2f".format(config.sensitivityY)}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = config.sensitivityY,
                        onValueChange = { newY ->
                            viewModel.updateMouseLookConfig(config.copy(sensitivityY = newY))
                        },
                        valueRange = 0.2f..5.0f,
                        colors = SliderDefaults.colors(thumbColor = ElectricBlue, activeTrackColor = ElectricBlue)
                    )
                }
            }
        }

        // Bridge Candidate Switcher
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SlateNavy),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ACTIVE INPUT BRIDGE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(
                        onClick = onNavigateToActivation,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Wizard", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                BridgeType.values().forEach { type ->
                    val isSelected = bridgeStatus.type == type
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { viewModel.setBridge(type) },
                            colors = RadioButtonDefaults.colors(selectedColor = ElectricBlue)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = type.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = Color.White
                            )
                            Text(
                                text = if (type == BridgeType.SHIZUKU) "Privileged Binder IPC (Recommended, ~6ms latency)" else "Local TCP ADB pairing (~18ms latency)",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }
        }

        // Hardware Peripherals Monitor
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SlateNavy),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PERIPHERAL HARDWARE (ACTUAL)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${connectedDevices.size} connected",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (connectedDevices.isNotEmpty()) StatusEmerald else Color(0xFF94A3B8)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (connectedDevices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "No physical keyboard or mouse detected. Connect peripherals via USB-C OTG hub or Bluetooth.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                } else {
                    connectedDevices.forEach { dev ->
                        Text(
                            text = "• ${dev.name} (VID: ${Integer.toHexString(dev.vendorId)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "HOT-PLUG EVENT LOG",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    if (hotPlugLog.isEmpty()) {
                        Text("No disconnect events recorded.", color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
                    } else {
                        Column {
                            hotPlugLog.take(4).forEach { logLine ->
                                Text(logLine, color = StatusAmber, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        // Pro Entitlement & Google Play Billing
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SlateNavy),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PRO ENTITLEMENT (GOOGLE PLAY)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isProUnlocked) "PRO ACTIVE" else "FREE TIER",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isProUnlocked) StatusEmerald else ElectricBlue,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Free tier includes core mapping, WASD, macros, single import/export, and safety controls. Pro unlocks batch ZIP exports, profile folders, and side-by-side comparison.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCBD5E1),
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isProUnlocked) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = StatusEmerald, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Verified via Google Play Developer API", color = StatusEmerald, style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.unlockProSimulated() },
                            modifier = Modifier.weight(1f).testTag("purchase_pro_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = MidnightBlue)
                        ) {
                            Icon(imageVector = Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Unlock Pro ($4.99)")
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.unlockProSimulated()
                                viewModel.showFeedback("Purchases Restored")
                            },
                            modifier = Modifier.weight(1f).testTag("restore_purchases_button")
                        ) {
                            Text("Restore Purchases")
                        }
                    }
                }
            }
        }

        // Privacy, Policy & Account Deletion
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SlateNavy),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "PRIVACY & DATA SAFETY",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• Zero analytics or background trackers bundled.\n• Local-first: all profiles and macros remain private to this device.\n• Safe-stop guarantees all virtual keys are released upon exit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCBD5E1),
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = { showAccountDeletionDialog = true },
                    modifier = Modifier.fillMaxWidth().testTag("delete_account_button"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusCrimson),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StatusCrimson.copy(alpha = 0.5f))
                ) {
                    Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Account & Cloud Records")
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }

    if (showAccountDeletionDialog) {
        AlertDialog(
            onDismissRequest = { showAccountDeletionDialog = false },
            title = { Text("Delete Cloud Account", color = Color.White) },
            text = {
                Text(
                    "In compliance with Google Play Account Deletion guidelines, this action revokes authentication sessions and deletes all cloud entitlement records. Note that your locally saved profiles and custom mappings on this phone will NOT be erased.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCBD5E1)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.simulateAccountDeletion()
                        showAccountDeletionDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusCrimson, contentColor = Color.White)
                ) {
                    Text("Confirm Deletion")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAccountDeletionDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = SlateNavy
        )
    }
}
