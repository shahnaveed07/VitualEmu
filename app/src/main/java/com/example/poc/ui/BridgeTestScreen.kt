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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.poc.model.BridgeConnectionState
import com.example.poc.model.CandidateBridgeId
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.MidnightBlue
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateNavy
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusCrimson
import com.example.ui.theme.StatusEmerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BridgeTestScreen(
    viewModel: FeasibilityViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedCandidate by viewModel.selectedCandidate.collectAsState()
    val bridgeState by viewModel.bridgeState.collectAsState()
    val heldCount by viewModel.heldCount.collectAsState()
    val lastActionFeedback by viewModel.lastActionFeedback.collectAsState()
    val rawInputLog by viewModel.rawInputLog.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Bridge Test Harness",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = selectedCandidate.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricBlue
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    Button(
                        onClick = { viewModel.triggerSafeStop() },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusCrimson, contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(end = 8.dp).testTag("safe_stop_button")
                    ) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Safe Stop")
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

            // Action feedback banner
            if (lastActionFeedback != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                        .border(1.dp, ElectricBlue.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Telemetry: $lastActionFeedback",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                }
            }

            // 1. Connection & Authorization
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateNavy),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("1. CONNECTION & AUTHORIZATION", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(selectedCandidate.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("State: ${bridgeState.label}", style = MaterialTheme.typography.bodySmall, color = if (bridgeState == BridgeConnectionState.AUTHORIZED) StatusEmerald else StatusAmber)
                        }
                        Button(
                            onClick = { viewModel.connectAndAuthorize() },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = MidnightBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("connect_bridge_button")
                        ) {
                            Text("Connect / Authorize", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 2. Physical Input Detection Monitor
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateNavy),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("2. PHYSICAL INPUT EVENT LISTENER", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                        Text("Active Held Inputs: $heldCount", color = if (heldCount > 0) StatusAmber else StatusEmerald, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.recordRawInput("KeyDown: KEYCODE_W (19) [USB Kbd]")
                            },
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A), contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Simulate 'W'")
                        }
                        Button(
                            onClick = {
                                viewModel.recordRawInput("MotionEvent: BUTTON_PRIMARY [USB Mouse]")
                            },
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A), contentColor = ElectricBlue),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Simulate 'LMB'")
                        }
                        Button(
                            onClick = {
                                viewModel.recordRawInput("Scroll: AXIS_VSCROLL +1.0 [USB Mouse]")
                            },
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A), contentColor = StatusEmerald),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Scroll")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF090D16), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        if (rawInputLog.isEmpty()) {
                            Text("Listening for raw KeyEvents and MotionEvents...", color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                rawInputLog.take(4).forEach { eventLine ->
                                    Text(eventLine, color = Color(0xFFE2E8F0), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            // 3. Controlled Touch Action Experiments
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateNavy),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("3. TOUCH INJECTION & MOVEMENT EXPERIMENTS", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.executeSingleTap() },
                            modifier = Modifier.weight(1f).testTag("test_single_tap_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B), contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Single Tap")
                        }

                        Button(
                            onClick = { viewModel.executeMovementAction() },
                            modifier = Modifier.weight(1f).testTag("test_movement_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B), contentColor = ElectricBlue),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("WASD + Tap")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { viewModel.executeMouseLookTest() },
                        modifier = Modifier.fillMaxWidth().testTag("test_mouse_look_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B), contentColor = StatusEmerald),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Test Relative Mouse-Look & Touch-Region Fallback")
                    }
                }
            }

            // 4. Benchmarking & Repeatability
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateNavy),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("4. REPEATABILITY & JITTER BENCHMARK", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Runs 20 consecutive controlled trials to calculate p50 latency, p95 latency, and jitter variance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCBD5E1)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.runBenchmark() },
                        modifier = Modifier.fillMaxWidth().testTag("run_benchmark_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusEmerald, contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Execute 20-Trial Benchmark (Gate 0 Proof)")
                    }
                }
            }

            // 5. Safety Failure Simulation
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateNavy),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("5. SAFETY & FAILURE RECOVERY STRESS TESTS", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Simulate unexpected interruptions to verify conservative safe-stop behavior:", style = MaterialTheme.typography.bodySmall, color = Color(0xFFCBD5E1))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.onFocusLoss() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Focus Loss")
                        }
                        OutlinedButton(
                            onClick = { viewModel.onPeripheralDisconnect("USB Gaming Keyboard") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Unplug Keyboard")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
