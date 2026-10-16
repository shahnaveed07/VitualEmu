package com.example.poc.ui

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
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import com.example.poc.model.CandidateBridgeId
import com.example.poc.model.ExperimentStatus
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.MidnightBlue
import com.example.ui.theme.SkyBlueContainer
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateNavy
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusCrimson
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.StatusSlate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeasibilityDashboardScreen(
    viewModel: FeasibilityViewModel,
    onNavigateToBridgeTest: () -> Unit,
    onNavigateToResults: () -> Unit,
    modifier: Modifier = Modifier
) {
    val deviceEnv by viewModel.deviceEnv.collectAsState()
    val selectedCandidate by viewModel.selectedCandidate.collectAsState()
    val overallStatus by viewModel.overallStatus.collectAsState()
    val bridgeState by viewModel.bridgeState.collectAsState()
    val capabilities by viewModel.capabilities.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "VitualEmu — Phase 0 PoC",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Technical Feasibility & Gate 0 Evaluation",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricBlue
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SlateNavy)
            )
        },
        containerColor = MidnightBlue
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            // Overall Gate 0 State Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("overall_status_card"),
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
                            text = "GATE 0 OVERALL STATE",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold
                        )
                        OverallStatusBadge(status = overallStatus)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = when (overallStatus) {
                            ExperimentStatus.NOT_TESTED -> "Testing not executed yet. Full application implementation remains blocked pending physical-device proof."
                            ExperimentStatus.RUNNING -> "Test session active. Controlled input actions in progress."
                            ExperimentStatus.PASS -> "Gate 0 criteria satisfied: Multi-touch injection and safe stop proven with recorded evidence."
                            ExperimentStatus.FAIL -> "Feasibility failure: Touch injection or safe-stop release failed on target hardware."
                            ExperimentStatus.BLOCKED -> "Implementation blocked: Waiting for required physical-device verification."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCBD5E1),
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onNavigateToBridgeTest,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_bridge_test_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = MidnightBlue),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Bridge Test", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onNavigateToResults,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_results_button"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Assessment, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Detailed Results")
                        }
                    }
                }
            }

            // Target Device & Environment Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateNavy),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.PhoneAndroid, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PRIMARY PHYSICAL TEST DEVICE",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = deviceEnv.deviceModel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${deviceEnv.osBuild} • Security: ${deviceEnv.securityPatch}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("• OS Target: ${deviceEnv.androidVersion}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFCBD5E1))
                            Text("• Hardware Hub: ${deviceEnv.usbHubModel}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFCBD5E1))
                            Text("• Test FPS Target: ${deviceEnv.selectedTestGame}", style = MaterialTheme.typography.bodySmall, color = ElectricBlue)
                        }
                    }
                }
            }

            // Candidate Bridge Comparison Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateNavy),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "EVALUATED BRIDGE CANDIDATES",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Candidate A: Rootless ADB
                    CandidateStatusRow(
                        name = "Candidate A: Rootless ADB",
                        desc = "Local TCP pairing • Shell touch command • ~18-35ms latency",
                        isSelected = selectedCandidate == CandidateBridgeId.ROOTLESS_WIRELESS_DEBUGGING,
                        onSelect = { viewModel.selectCandidate(CandidateBridgeId.ROOTLESS_WIRELESS_DEBUGGING) }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Candidate B: Shizuku
                    CandidateStatusRow(
                        name = "Candidate B: Shizuku Binder",
                        desc = "Privileged Binder IPC • Multi-touch supported • ~5-8ms latency",
                        isSelected = selectedCandidate == CandidateBridgeId.SHIZUKU,
                        onSelect = { viewModel.selectCandidate(CandidateBridgeId.SHIZUKU) }
                    )
                }
            }

            // Physical Peripherals Detection Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateNavy),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "PHYSICAL INPUT DETECTION STATUS",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Keyboard, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(deviceEnv.keyboardName, style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("Raw KeyEvents exposed to focused view", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                        }
                        Text("DETECTED", color = StatusEmerald, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Mouse, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(deviceEnv.mouseName, style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("MotionEvents (Buttons & Scroll exposed)", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                        }
                        Text("DETECTED", color = StatusEmerald, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Verified Action Capabilities
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateNavy),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "VERIFIED ACTION CAPABILITIES",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    CapabilityItem("Single Tap Touch Injection", capabilities.canInjectSingleTap)
                    CapabilityItem("Multi-Touch Joystick (Simultaneous Touches)", capabilities.canInjectMultiTouchJoystick)
                    CapabilityItem("Relative Mouse-Look (Pointer Capture in 3rd-Party)", capabilities.canCaptureRelativeMouseLook, note = "OS focus boundary")
                    CapabilityItem("Touch-Region Aim Fallback", capabilities.canExecuteTouchRegionAim)
                    CapabilityItem("Safe Stop & Held Input Release", capabilities.canReleaseHeldInputsReliably)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun OverallStatusBadge(status: ExperimentStatus) {
    val (color, text) = when (status) {
        ExperimentStatus.NOT_TESTED -> StatusSlate to "NOT TESTED"
        ExperimentStatus.RUNNING -> StatusAmber to "RUNNING TEST"
        ExperimentStatus.PASS -> StatusEmerald to "PASS"
        ExperimentStatus.FAIL -> StatusCrimson to "FAIL"
        ExperimentStatus.BLOCKED -> StatusAmber to "BLOCKED"
    }

    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CandidateStatusRow(
    name: String,
    desc: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) Color(0xFF1E293B) else Color(0xFF0F172A), RoundedCornerShape(10.dp))
            .border(
                if (isSelected) 1.5.dp else 1.dp,
                if (isSelected) ElectricBlue else SlateBorder,
                RoundedCornerShape(10.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(desc, color = Color(0xFF94A3B8), style = MaterialTheme.typography.labelSmall)
        }
        if (isSelected) {
            Text("ACTIVE", color = ElectricBlue, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        } else {
            OutlinedButton(
                onClick = onSelect,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("Select", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun CapabilityItem(label: String, isSupported: Boolean, note: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isSupported) Icons.Default.Check else Icons.Default.Close,
            contentDescription = null,
            tint = if (isSupported) StatusEmerald else StatusCrimson,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
        if (note != null) {
            Text(
                text = "($note)",
                style = MaterialTheme.typography.labelSmall,
                color = StatusAmber,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = if (isSupported) "SUPPORTED" else "UNSUPPORTED",
            style = MaterialTheme.typography.labelSmall,
            color = if (isSupported) StatusEmerald else StatusCrimson,
            fontWeight = FontWeight.Bold
        )
    }
}
