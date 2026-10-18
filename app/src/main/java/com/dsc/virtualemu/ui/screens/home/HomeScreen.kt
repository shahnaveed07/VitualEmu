package com.dsc.virtualemu.ui.screens.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.dsc.virtualemu.domain.model.BridgeHealthState
import com.dsc.virtualemu.domain.model.DeviceType
import com.dsc.virtualemu.domain.model.Game
import com.dsc.virtualemu.ui.VitualEmuViewModel
import com.dsc.virtualemu.ui.theme.ElectricBlue
import com.dsc.virtualemu.ui.theme.MidnightBlue
import com.dsc.virtualemu.ui.theme.SlateBorder
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
    val bridgeStatus by viewModel.bridgeStatus.collectAsState()
    val connectedDevices by viewModel.connectedDevices.collectAsState()

    val hasKeyboard = connectedDevices.any { it.type == DeviceType.KEYBOARD && it.isConnected }
    val hasMouse = connectedDevices.any { it.type == DeviceType.MOUSE && it.isConnected }

    val purpleBg = Color(0xFFC7CBD9)
    val cardIndigo = Color(0xFF6875C4)
    val buttonIndigo = Color(0xFF5B61B9)

    // Outer Fixed-Height Container
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(purpleBg)
    ) {
        // Background Subtle Gradient
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridColor = Color(0xFF535BA3).copy(alpha = 0.15f)
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

        // Fixed Vertical Layout (No overall vertical scroll)
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
                // Top-Left: Logo & Games Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = Color(0xFF1E1B4B), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)) {
                                append("Games")
                            }
                        }
                    )
                }

                // Top-Center: Permission Required Banner
                val isAuthorized = bridgeStatus.state == BridgeHealthState.CONNECTED
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isAuthorized) StatusEmerald.copy(alpha = 0.2f) else Color(0xFFDC2626).copy(alpha = 0.7f))
                        .clickable(enabled = !isAuthorized, onClick = onNavigateToActivation)
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                        .testTag("permission_banner")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isAuthorized) Icons.Default.CheckCircle else Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (isAuthorized) "Bridge Active" else "Permission required",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (!isAuthorized) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                // Top-Right: Mouse and Keyboard Status Badges
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Mouse Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (hasMouse) Color(0xFF10B981) else Color(0xFF6366F1))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mouse,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (hasMouse) "Connected" else "Disconnected",
                                fontSize = 11.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Keyboard Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (hasKeyboard) Color(0xFF10B981) else Color(0xFF6366F1))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Keyboard,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (hasKeyboard) "Connected" else "Disconnected",
                                fontSize = 11.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
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
                        containerColor = buttonIndigo,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("add_game_upper_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Add Game", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            // 3. Main Game Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (games.isEmpty()) {
                    // Empty State: Matches Reference Screenshot 1 exactly
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Large vertical card placeholder with + in center
                        Box(
                            modifier = Modifier
                                .size(width = 190.dp, height = 240.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(cardIndigo)
                                .border(1.5.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .clickable(onClick = onNavigateToGames)
                                .testTag("empty_state_add_placeholder"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Game",
                                tint = Color.White,
                                modifier = Modifier.size(72.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Start Game Button directly beneath placeholder
                        Button(
                            onClick = onNavigateToGames,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = buttonIndigo,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
                            modifier = Modifier.testTag("add_game_empty_state_button")
                        ) {
                            Text("Start Game", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                } else {
                    // Games Added State: Matches Reference Screenshot 2 exactly
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Horizontal Games Carousel
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(games, key = { it.gameId }) { game ->
                                val isSelected = (activeGame?.gameId == game.gameId) || (activeGame == null && games.firstOrNull()?.gameId == game.gameId)
                                ReferenceVerticalGameCard(
                                    game = game,
                                    isSelected = isSelected,
                                    onSelect = { viewModel.selectGame(game) },
                                    cardIndigo = cardIndigo
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Start Game Button directly beneath carousel
                        Button(
                            onClick = {
                                val targetPkg = activeGame?.packageName ?: games.firstOrNull()?.packageName ?: ""
                                if (targetPkg.isNotBlank()) {
                                    viewModel.launchGame(targetPkg)
                                    viewModel.startPlayMode()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = buttonIndigo,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 10.dp),
                            modifier = Modifier.testTag("start_game_carousel_button")
                        ) {
                            Text("Start Game", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReferenceVerticalGameCard(
    game: Game,
    isSelected: Boolean,
    onSelect: () -> Unit,
    cardIndigo: Color
) {
    val width = if (isSelected) 175.dp else 125.dp
    val height = if (isSelected) 220.dp else 165.dp
    val iconSize = if (isSelected) 90.dp else 60.dp

    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) cardIndigo else Color(0xFF535BA3))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onSelect)
            .padding(8.dp)
            .testTag("game_card_${game.gameId}"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // App Launcher Icon
            GameIconImage(
                packageName = game.packageName,
                modifier = Modifier
                    .size(iconSize)
                    .clip(RoundedCornerShape(16.dp))
            )

            // Semi-Transparent Game Title Label Box at Bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1438).copy(alpha = 0.5f))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = game.displayName,
                    color = Color.White,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                    fontSize = if (isSelected) 17.sp else 12.sp,
                    maxLines = if (isSelected) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun GameIconImage(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            drawable.toBitmap(128, 128).asImageBitmap()
        } catch (_: Throwable) {
            null
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(Color(0xFF1E1438), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Gamepad,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
