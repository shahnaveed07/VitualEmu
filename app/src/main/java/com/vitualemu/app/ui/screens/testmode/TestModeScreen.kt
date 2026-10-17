package com.vitualemu.app.ui.screens.testmode

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitualemu.app.domain.usecase.HeldInputRegistry
import com.vitualemu.app.ui.VitualEmuViewModel
import com.vitualemu.app.ui.components.KeyCapChip
import com.vitualemu.app.ui.theme.ElectricBlue
import com.vitualemu.app.ui.theme.MidnightBlue
import com.vitualemu.app.ui.theme.SlateBorder
import com.vitualemu.app.ui.theme.SlateNavy
import com.vitualemu.app.ui.theme.StatusAmber
import com.vitualemu.app.ui.theme.StatusCrimson
import com.vitualemu.app.ui.theme.StatusEmerald
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestModeScreen(
    viewModel: VitualEmuViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val heldKeys by HeldInputRegistry.heldKeysFlow.collectAsState()
    val activeSlots by viewModel.activePointerSlots.collectAsState()
    val mouseLookState by viewModel.mouseLookState.collectAsState()

    val eventLogs = remember { mutableStateListOf<String>() }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MidnightBlue)
    ) {
        // Compact Landscape Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SlateNavy)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text("Input Mapping & Multi-Touch Sandbox", fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Pointers: ${activeSlots.size}/10 • 360° Resets: ${mouseLookState.edgeResetCount}",
                        color = Color(0xFF94A3B8),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { viewModel.toggleShootingMode() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (mouseLookState.isShootingMode) StatusEmerald else ElectricBlue,
                        contentColor = MidnightBlue
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(34.dp).testTag("sandbox_toggle_shooting_mode")
                ) {
                    Icon(
                        imageVector = if (mouseLookState.isShootingMode) Icons.Default.GpsFixed else Icons.Default.Mouse,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (mouseLookState.isShootingMode) "SHOOTING MODE (ON)" else "CURSOR MODE",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                Button(
                    onClick = {
                        viewModel.emergencySafeStop()
                        eventLogs.add(0, "SAFE_STOP: All 10 pointer slots & keys neutralized")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusCrimson),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(34.dp).testTag("sandbox_release_all_button")
                ) {
                    Text("Neutralize All", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                }

                IconButton(onClick = {
                    viewModel.emergencySafeStop()
                    eventLogs.clear()
                }) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset", tint = Color.White)
                }
            }
        }

        // Landscape Split-Pane: Left = 16:9 Interactive Touch & Mouse-Look Simulator, Right = Telemetry & Log
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1.6f)
                    .fillMaxHeight()
                    .aspectRatio(16f / 9f, matchHeightConstraintsFirst = true)
                    .background(Color(0xFF090D16), RoundedCornerShape(12.dp))
                    .border(1.dp, ElectricBlue.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .focusRequester(focusRequester)
                    .onKeyEvent { keyEvent ->
                        val code = keyEvent.nativeKeyEvent.keyCode
                        val isDown = keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN
                        val isUp = keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_UP
                        if (isDown || isUp) {
                            viewModel.dispatchSandboxKeyEvent(code, isDown) { summary ->
                                eventLogs.add(0, summary)
                            }
                        }
                        true
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val dx = dragAmount.x / size.width.coerceAtLeast(1)
                            val dy = dragAmount.y / size.height.coerceAtLeast(1)
                            viewModel.dispatchSandboxMouseDelta(dx, dy)
                        }
                    }
            ) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (mouseLookState.isShootingMode) "360° Shooting Mode Active (Drag to test Aim + Auto Edge Reset)" else "Cursor Mode Active (Press WASD / Keys or Drag Mouse)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Active Held Keys: ${heldKeys.size} • Active Multi-Touch Pointers: ${activeSlots.size}/10",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (heldKeys.isEmpty() && activeSlots.isEmpty()) StatusEmerald else StatusAmber
                    )
                }

                // Render Virtual Gaming Cursor when in Cursor Mode
                if (!mouseLookState.isShootingMode) {
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = (mouseLookState.cursorNormX * constraints.maxWidth).roundToInt() - 12,
                                    y = (mouseLookState.cursorNormY * constraints.maxHeight).roundToInt() - 12
                                )
                            }
                            .size(24.dp)
                            .background(ElectricBlue.copy(alpha = 0.35f), CircleShape)
                            .border(2.dp, ElectricBlue, CircleShape)
                    )
                }

                // Render all live active Multi-Touch Pointers (Slots 0..9)
                for (slot in activeSlots) {
                    val slotColor = when (slot.pointerId) {
                        0 -> ElectricBlue
                        1 -> StatusAmber
                        else -> StatusEmerald
                    }
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = (slot.normX * constraints.maxWidth).roundToInt() - 20,
                                    y = (slot.normY * constraints.maxHeight).roundToInt() - 20
                                )
                            }
                            .size(40.dp)
                            .background(slotColor.copy(alpha = 0.35f), CircleShape)
                            .border(2.dp, slotColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "P${slot.pointerId}",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                if (heldKeys.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (key in heldKeys) {
                            val label = KeyEvent.keyCodeToString(key).removePrefix("KEYCODE_")
                            KeyCapChip(label = label, highlighted = true)
                        }
                    }
                }
            }

            // Right Pane: Live Slot Table & Event Dispatch Log
            Card(
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxHeight()
                    .border(1.dp, SlateBorder, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SlateNavy)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "ACTIVE 10-POINTER SLOTS (${activeSlots.size}/10)",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricBlue,
                        fontWeight = FontWeight.Bold
                    )
                    if (activeSlots.isEmpty()) {
                        Text(
                            text = "Zero stuck inputs (Clean state)",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusEmerald
                        )
                    } else {
                        for (s in activeSlots) {
                            Text(
                                text = "Slot #${s.pointerId} [${s.ownerId}] -> (%.2f, %.2f)".format(s.normX, s.normY),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "EVENT DISPATCH LOG",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Bold
                    )

                    if (eventLogs.isEmpty()) {
                        Text(
                            text = "No events recorded yet. Press keys or drag mouse inside the 16:9 simulator.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B)
                        )
                    } else {
                        for (log in eventLogs.take(12)) {
                            Text(
                                text = log,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (log.contains("DOWN") || log.contains("ON")) StatusEmerald else Color(0xFFCBD5E1),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}
