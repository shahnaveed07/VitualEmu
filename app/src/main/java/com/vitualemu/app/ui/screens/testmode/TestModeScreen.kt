package com.vitualemu.app.ui.screens.testmode

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitualemu.app.domain.usecase.HeldInputRegistry
import com.vitualemu.app.ui.VitualEmuViewModel
import com.vitualemu.app.ui.components.KeyCapChip
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
fun TestModeScreen(
    viewModel: VitualEmuViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val activeProfile by viewModel.activeProfile.collectAsState()
    val heldKeys by HeldInputRegistry.heldKeysFlow.collectAsState()
    val activePointers by HeldInputRegistry.activePointersFlow.collectAsState()

    val eventLogs = remember { mutableStateListOf<String>() }
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MidnightBlue)
    ) {
        TopAppBar(
            title = {
                Text("Input Mapping Test Sandbox", fontWeight = FontWeight.Bold, color = Color.White)
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            },
            actions = {
                IconButton(onClick = {
                    HeldInputRegistry.releaseAll()
                    eventLogs.clear()
                }) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = SlateNavy)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Live status banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SlateBorder, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SlateNavy)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("ACTIVE HELD INPUTS: ${heldKeys.size}", fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            text = if (heldKeys.isEmpty()) "Zero stuck inputs (Clean state)" else "Inputs currently held down",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (heldKeys.isEmpty()) StatusEmerald else StatusAmber
                        )
                    }

                    Button(
                        onClick = { HeldInputRegistry.releaseAll() },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusCrimson),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("sandbox_release_all_button")
                    ) {
                        Text("Neutralize All", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // Interactive Capture Canvas
            Text(
                text = "INTERACTIVE TOUCH SIMULATOR (Press keyboard/mouse here):",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF94A3B8),
                fontWeight = FontWeight.Bold
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color(0xFF090D16), RoundedCornerShape(12.dp))
                    .border(1.dp, ElectricBlue.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .focusRequester(focusRequester)
                    .onKeyEvent { keyEvent ->
                        val code = keyEvent.nativeKeyEvent.keyCode
                        val label = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
                        if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                            HeldInputRegistry.recordKeyDown(code)
                            eventLogs.add(0, "KEY_DOWN: $label ($code)")
                        } else if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                            HeldInputRegistry.recordKeyUp(code)
                            eventLogs.add(0, "KEY_UP: $label ($code)")
                        }
                        true
                    },
                contentAlignment = Alignment.Center
            ) {
                // Render visual crosshairs and active virtual touches
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Touch Sandbox Active",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Press physical keys or click mouse to observe event dispatch",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B)
                    )
                }

                // Show held keys chips inside sandbox
                if (heldKeys.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (key in heldKeys) {
                            val label = KeyEvent.keyCodeToString(key).removePrefix("KEYCODE_")
                            KeyCapChip(label = label, highlighted = true)
                        }
                    }
                }
            }

            // Event Log Console
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SlateBorder, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SlateNavy)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "EVENT DISPATCH LOG",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (eventLogs.isEmpty()) {
                        Text(
                            text = "No events recorded yet. Focus sandbox and tap keys.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (log in eventLogs.take(8)) {
                                Text(
                                    text = log,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (log.startsWith("KEY_DOWN")) StatusEmerald else Color(0xFF94A3B8),
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
