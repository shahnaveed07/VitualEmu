package com.vitualemu.app.ui.screens.activation

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitualemu.app.domain.model.BridgeHealthState
import com.vitualemu.app.domain.model.BridgeType
import com.vitualemu.app.ui.VitualEmuViewModel
import com.vitualemu.app.ui.components.StatusBadge
import com.vitualemu.app.ui.theme.ElectricBlue
import com.vitualemu.app.ui.theme.MidnightBlue
import com.vitualemu.app.ui.theme.SlateBorder
import com.vitualemu.app.ui.theme.SlateNavy
import com.vitualemu.app.ui.theme.StatusAmber
import com.vitualemu.app.ui.theme.StatusCrimson
import com.vitualemu.app.ui.theme.StatusEmerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivationWizardScreen(
    viewModel: VitualEmuViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val bridgeStatus by viewModel.bridgeStatus.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MidnightBlue)
    ) {
        TopAppBar(
            title = {
                Text("Input Bridge Activation Wizard", fontWeight = FontWeight.Bold, color = Color.White)
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            },
            actions = {
                IconButton(onClick = { viewModel.refreshBridgeHealth() }) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = SlateNavy)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Status Header
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SlateBorder, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SlateNavy)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("CURRENT BRIDGE STATUS", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                        StatusBadge(
                            text = if (bridgeStatus.state == BridgeHealthState.CONNECTED) "READY / AUTHORIZED" else "ACTION REQUIRED",
                            color = if (bridgeStatus.state == BridgeHealthState.CONNECTED) StatusEmerald else StatusAmber
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = bridgeStatus.type.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = bridgeStatus.detailMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCBD5E1)
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.refreshBridgeHealth() },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("test_bridge_health_button")
                    ) {
                        Text("Test Bridge Connection", color = MidnightBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Method 1: Shizuku (Recommended)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (bridgeStatus.type == BridgeType.SHIZUKU) ElectricBlue else SlateBorder, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SlateNavy)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Method 1: Shizuku (Recommended)", fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusBadge("Low Latency (~6ms)", color = StatusEmerald, showDot = false)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Shizuku provides a privileged Android Binder service allowing low-latency virtual touch injection without PC tethering.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    StepItem(number = "1", title = "Install Shizuku", desc = "Install Shizuku from GitHub or Google Play Store.")
                    StepItem(number = "2", title = "Start via Wireless Debugging", desc = "Enable Developer Options > Wireless Debugging. Pair and start Shizuku.")
                    StepItem(number = "3", title = "Authorize VitualEmu", desc = "Open Shizuku app, tap 'Authorized applications', and grant VitualEmu access.")

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { viewModel.setBridge(BridgeType.SHIZUKU) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Select Shizuku as Active Bridge")
                    }
                }
            }

            // Method 2: Wireless Debugging ADB
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (bridgeStatus.type == BridgeType.ROOTLESS_WIRELESS_DEBUGGING) ElectricBlue else SlateBorder, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SlateNavy)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Method 2: Rootless Wireless Debugging (ADB)", fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Direct localhost ADB connection over TCP port. Requires pairing in Developer Options after reboot.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    StepItem(number = "1", title = "Developer Options", desc = "Enable Developer Options and turn on 'Wireless Debugging'.")
                    StepItem(number = "2", title = "Pair Device", desc = "Select 'Pair device with pairing code' and connect to localhost port.")
                    StepItem(number = "3", title = "Verify Port", desc = "Ensure ADB daemon is running in background.")

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { viewModel.setBridge(BridgeType.ROOTLESS_WIRELESS_DEBUGGING) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Select Wireless Debugging ADB")
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun StepItem(number: String, title: String, desc: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
                .border(1.dp, SlateBorder, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = ElectricBlue, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, color = Color.White, style = MaterialTheme.typography.bodySmall)
            Text(desc, color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodySmall)
        }
    }
}
