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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsc.virtualemu.domain.model.BridgeHealthState
import com.dsc.virtualemu.domain.model.DeviceType
import com.dsc.virtualemu.domain.model.Game
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
    modifier: Modifier = Modifier,
    onNavigateToGames: () -> Unit = {}
) {
    val games by viewModel.games.collectAsState()
    val activeGame by viewModel.activeGame.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val bridgeStatus by viewModel.bridgeStatus.collectAsState()
    val connectedDevices by viewModel.connectedDevices.collectAsState()
    val sessionState by viewModel.sessionState.collectAsState()

    val hasKeyboard = connectedDevices.any { it.type == DeviceType.KEYBOARD && it.isConnected }
    val hasMouse = connectedDevices.any { it.type == DeviceType.MOUSE && it.isConnected }

    // Outer Fixed-Height Container
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MidnightBlue)
    ) {
        // Faint background accents
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridColor = Color(0xFF1E293B).copy(alpha = 0.25f)
            val strokeWidth = 1f
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

        // Fixed vertical layout (No overall vertical scroll)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Top-Left: Logo & Name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
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
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)) {
                                append("Virtual")
                            }
                            withStyle(SpanStyle(color = ElectricBlue, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)) {
                                append("Emu")
                            }
                        }
                    )
                }

                // Top-Center: Permission Required Banner
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
                        .padding(horizontal = 12.dp, vertical = 6.dp)
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
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (isAuthorized) "Bridge Active" else "Permission required",
                            color = if (isAuthorized) StatusEmerald else Color(0xFFFCA5A5),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (!isAuthorized) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = Color(0xFFFCA5A5),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                // Top-Right: Mouse and Keyboard Status Cards
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Mouse Card
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                            .border(1.dp, SlateBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mouse,
                                contentDescription = null,
                                tint = if (hasMouse) StatusEmerald else Color(0xFF94A3B8),
                                modifier = Modifier.size(14.dp)
                            )
                            Column {
                                Text("Mouse", fontSize = 9.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (hasMouse) "Connected" else "Disconnected",
                                    fontSize = 10.sp,
                                    color = if (hasMouse) StatusEmerald else StatusCrimson,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(if (hasMouse) StatusEmerald else StatusCrimson, CircleShape)
                            )
                        }
                    }

                    // Keyboard Card
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                            .border(1.dp, SlateBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Keyboard,
                                contentDescription = null,
                                tint = if (hasKeyboard) StatusEmerald else Color(0xFF94A3B8),
                                modifier = Modifier.size(14.dp)
                            )
                            Column {
                                Text("Keyboard", fontSize = 9.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (hasKeyboard) "Connected" else "Disconnected",
                                    fontSize = 10.sp,
                                    color = if (hasKeyboard) StatusEmerald else StatusCrimson,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(if (hasKeyboard) StatusEmerald else StatusCrimson, CircleShape)
                            )
                        }
                    }
                }
            }

            // 2. Sub-Header Bar: + Add Game Button
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
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("add_game_upper_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MidnightBlue
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Add Game", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            // 3. Main Content Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (games.isEmpty()) {
                    // Empty State
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
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
                                    .size(56.dp)
                                    .background(Color(0xFF1E293B), CircleShape)
                                    .border(1.5.dp, ElectricBlue.copy(alpha = 0.6f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Box {
                                    Icon(
                                        imageVector = Icons.Default.SportsEsports,
                                        contentDescription = null,
                                        tint = ElectricBlue,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(16.dp)
                                            .background(ElectricBlue, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            tint = MidnightBlue,
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "No games added yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Add your games to start using VitualEmu with custom controls.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 420.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = onNavigateToGames,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricBlue,
                                contentColor = MidnightBlue
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("add_game_empty_state_button")
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
                } else {
                    // Populated State: Horizontal Games Carousel + Control Panel
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // A. Horizontal Games Carousel (LazyRow)
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(games, key = { it.gameId }) { game ->
                                val isSelected = (activeGame?.gameId == game.gameId) || (activeGame == null && games.firstOrNull()?.gameId == game.gameId)
                                HorizontalGameCard(
                                    game = game,
                                    isSelected = isSelected,
                                    onSelect = { viewModel.selectGame(game) }
                                )
                            }
                        }

                        // B. Active Game Control Panel
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, SlateBorder, RoundedCornerShape(14.dp)),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = SlateNavy)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "SELECTED TARGET GAME",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF94A3B8),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                        Text(
                                            text = activeGame?.displayName ?: games.firstOrNull()?.displayName ?: "",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 18.sp,
                                            color = Color.White,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    StatusBadge(
                                        text = if (sessionState == SessionState.ACTIVE) "PLAY MODE ACTIVE" else "STANDBY",
                                        color = if (sessionState == SessionState.ACTIVE) StatusEmerald else Color(0xFF94A3B8)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Profile Info Box
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                            .padding(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("Mapped Profile", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B), fontSize = 9.sp)
                                                Text(activeProfile?.name ?: "Default Profile", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = ElectricBlue)
                                            }
                                            IconButton(onClick = onNavigateToEditor, modifier = Modifier.size(28.dp)) {
                                                Icon(imageVector = Icons.Default.Tune, contentDescription = "Edit Canvas", tint = ElectricBlue, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }

                                    // Launch App Button
                                    (activeGame?.packageName ?: games.firstOrNull()?.packageName)?.let { pkg ->
                                        OutlinedButton(
                                            onClick = { viewModel.launchGame(pkg) },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricBlue),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            modifier = Modifier.testTag("home_launch_game_button")
                                        ) {
                                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Start Game", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    // Play Mode Toggle Button
                                    Button(
                                        onClick = { viewModel.startPlayMode() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (sessionState == SessionState.ACTIVE) StatusAmber else StatusEmerald
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("start_play_mode_button")
                                    ) {
                                        Icon(
                                            imageVector = if (sessionState == SessionState.ACTIVE) Icons.Default.Stop else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (sessionState == SessionState.ACTIVE) "PAUSE" else "START PLAY MODE",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HorizontalGameCard(
    game: Game,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(if (isSelected) 200.dp else 150.dp)
            .height(if (isSelected) 95.dp else 80.dp)
            .clickable(onClick = onSelect)
            .testTag("game_card_${game.gameId}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF1E293B) else SlateNavy
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) ElectricBlue else SlateBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 30.dp else 24.dp)
                        .background(
                            if (isSelected) ElectricBlue.copy(alpha = 0.2f) else Color(0xFF0F172A),
                            RoundedCornerShape(6.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Gamepad,
                        contentDescription = null,
                        tint = if (isSelected) ElectricBlue else Color(0xFF94A3B8),
                        modifier = Modifier.size(if (isSelected) 18.dp else 14.dp)
                    )
                }

                if (isSelected) {
                    StatusBadge("ACTIVE", color = StatusEmerald, showDot = true)
                }
            }

            Column {
                Text(
                    text = game.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                    fontSize = if (isSelected) 16.sp else 13.sp,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (isSelected) {
                    Text(
                        text = game.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
