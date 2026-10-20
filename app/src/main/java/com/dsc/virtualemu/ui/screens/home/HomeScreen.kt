package com.dsc.virtualemu.ui.screens.home

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import com.dsc.virtualemu.ui.theme.StatusAmber
import com.dsc.virtualemu.ui.theme.StatusEmerald
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dsc.virtualemu.domain.model.BridgeHealthState
import com.dsc.virtualemu.domain.model.DeviceType
import com.dsc.virtualemu.domain.model.Game
import com.dsc.virtualemu.ui.VitualEmuViewModel
import com.dsc.virtualemu.ui.theme.StatusEmerald
import kotlinx.coroutines.launch

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun HomeScreen(
    viewModel: VitualEmuViewModel,
    onNavigateToEditor: () -> Unit,
    onNavigateToActivation: () -> Unit,
    onNavigateToPermissions: () -> Unit = {},
    onNavigateToTestMode: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToGames: () -> Unit = {}
) {
    val games by viewModel.games.collectAsState()
    val activeGame by viewModel.activeGame.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val bridgeStatus by viewModel.bridgeStatus.collectAsState()
    val connectedDevices by viewModel.connectedDevices.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    val pendingStartGamePkg by viewModel.pendingStartGamePkg.collectAsState()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAndResumePendingLaunch()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val hasKeyboard = connectedDevices.any { it.type == DeviceType.KEYBOARD && it.isConnected }
    val hasMouse = connectedDevices.any { it.type == DeviceType.MOUSE && it.isConnected }

    val purpleBg = Color(0xFFC7CBD9)
    val cardIndigo = Color(0xFF6875C4)
    val buttonIndigo = Color(0xFF5B61B9)

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Outer Fixed-Height Container
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(purpleBg)
    ) {
        // Background Subtle Grid
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

        // Fixed Vertical Layout
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

                // Top-Center: Permission Required Banner (Hides automatically when all prerequisites are satisfied)
                val hasBridge = bridgeStatus.state == BridgeHealthState.CONNECTED && bridgeStatus.isAuthorized
                val hasOverlay = viewModel.checkOverlayPermission()
                val isFullyAuthorized = hasBridge && hasOverlay

                if (!isFullyAuthorized) {
                    val bannerText = if (!hasBridge) "Activation Required" else "Overlay Permission Required"
                    val onBannerClick = if (!hasBridge) onNavigateToActivation else onNavigateToPermissions
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFDC2626).copy(alpha = 0.85f))
                            .clickable(onClick = onBannerClick)
                            .padding(horizontal = 16.dp, vertical = 7.dp)
                            .testTag("permission_banner")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = bannerText,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
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

            // 3. Responsive Main Game Area & Centered Carousel
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                val viewportWidth = maxWidth
                val viewportHeight = maxHeight
                val scaleFactor = (viewportHeight / 260.dp).coerceIn(0.7f, 1.0f)

                if (games.isEmpty()) {
                    // Empty State: Zero Games
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 190.dp * scaleFactor, height = 230.dp * scaleFactor)
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
                                modifier = Modifier.size(64.dp * scaleFactor)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = onNavigateToGames,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = buttonIndigo,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("add_game_empty_state_button")
                        ) {
                            Text("Start Game", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                } else {
                    val selectedCardWidth = 205.dp * scaleFactor
                    val centerPadding = ((viewportWidth - selectedCardWidth) / 2).coerceAtLeast(16.dp)

                    val selectedIndex = remember(games, activeGame) {
                        val idx = games.indexOfFirst { it.gameId == activeGame?.gameId }
                        if (idx >= 0) idx else 0
                    }

                    val density = LocalDensity.current
                    val centerPaddingPx = remember(centerPadding, density) {
                        with(density) { centerPadding.toPx() }
                    }
                    val maxRadiusPx = remember(scaleFactor, density) {
                        with(density) { (200.dp * scaleFactor).toPx() }
                    }

                    // Programmatic scroll animation executes only when user is NOT actively dragging
                    LaunchedEffect(selectedIndex, games.size) {
                        if (!lazyListState.isScrollInProgress && games.isNotEmpty() && selectedIndex in games.indices) {
                            lazyListState.animateScrollToItem(selectedIndex, 0)
                        }
                    }

                    // Snap-to-center settling when user finishes manual dragging/swiping
                    LaunchedEffect(lazyListState.isScrollInProgress) {
                        if (!lazyListState.isScrollInProgress && games.isNotEmpty()) {
                            val layoutInfo = lazyListState.layoutInfo
                            val visibleItems = layoutInfo.visibleItemsInfo
                            if (visibleItems.isNotEmpty()) {
                                val closestItem = visibleItems.minByOrNull { item ->
                                    kotlin.math.abs(item.offset - centerPaddingPx)
                                }
                                if (closestItem != null && closestItem.index in games.indices) {
                                    val targetGame = games[closestItem.index]
                                    if (activeGame?.gameId != targetGame.gameId) {
                                        viewModel.selectGame(targetGame)
                                    }
                                    lazyListState.animateScrollToItem(closestItem.index, 0)
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Horizontal Centered Games Carousel with currentGamePoint
                        LazyRow(
                            state = lazyListState,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = centerPadding, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            itemsIndexed(games, key = { _, game -> game.gameId }) { index, game ->
                                val isSelectedTarget = (activeGame?.gameId == game.gameId) || (activeGame == null && index == 0)

                                // Continuous distance-based fraction calculation from center alignment offset
                                val fraction by remember(centerPaddingPx, maxRadiusPx, isSelectedTarget) {
                                    derivedStateOf {
                                        val itemInfo = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                                        if (itemInfo != null) {
                                            val distancePx = kotlin.math.abs(itemInfo.offset - centerPaddingPx)
                                            (1f - (distancePx / maxRadiusPx)).coerceIn(0f, 1f)
                                        } else if (isSelectedTarget) {
                                            1.0f
                                        } else {
                                            0.0f
                                        }
                                    }
                                }

                                ReferenceVerticalGameCard(
                                    game = game,
                                    proximityFraction = fraction,
                                    onSelect = {
                                        viewModel.selectGame(game)
                                        if (!lazyListState.isScrollInProgress) {
                                            coroutineScope.launch {
                                                lazyListState.animateScrollToItem(index, 0)
                                            }
                                        }
                                    },
                                    scaleFactor = scaleFactor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Start Game Button directly beneath carousel targeting activeGame
                        Button(
                            onClick = {
                                val targetPkg = activeGame?.packageName ?: ""
                                if (targetPkg.isNotBlank()) {
                                    val started = viewModel.requestStartGameGated(targetPkg)
                                    if (!started) {
                                        viewModel.showFeedback("Activation requirement pending. Complete activation steps.")
                                    }
                                } else {
                                    viewModel.showFeedback("No game selected")
                                }
                            },
                            enabled = games.isNotEmpty() && activeGame != null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = buttonIndigo,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("start_game_carousel_button")
                        ) {
                            Text("Start Game", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        // Pending Activation Guided Checklist Dialog
        if (pendingStartGamePkg != null) {
            val hasBridge = bridgeStatus.state == BridgeHealthState.CONNECTED && bridgeStatus.isAuthorized
            val hasOverlay = viewModel.checkOverlayPermission()

            AlertDialog(
                onDismissRequest = { viewModel.cancelPendingStartGameLaunch() },
                title = {
                    Text("Activation Prerequisites Required", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Complete the required activation steps below to launch ${activeGame?.displayName ?: "game"}:",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp
                        )

                        // Step 1: Input Bridge
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E1438))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("1. Input Bridge Activation", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                Text(if (hasBridge) "Active & Authorized" else "Activation required", color = if (hasBridge) StatusEmerald else StatusAmber, fontSize = 11.sp)
                            }
                            if (!hasBridge) {
                                Button(
                                    onClick = onNavigateToActivation,
                                    colors = ButtonDefaults.buttonColors(containerColor = buttonIndigo)
                                ) {
                                    Text("Activate", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Step 2: Overlay Permission
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E1438))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("2. System Overlay Window", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                Text(if (hasOverlay) "Permission Granted" else "Permission required", color = if (hasOverlay) StatusEmerald else StatusAmber, fontSize = 11.sp)
                            }
                            if (!hasOverlay) {
                                Button(
                                    onClick = onNavigateToPermissions,
                                    colors = ButtonDefaults.buttonColors(containerColor = buttonIndigo)
                                ) {
                                    Text("Grant", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Step 3: Game Profile & Keymap Scheme
                        val hasProfile = activeProfile != null
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E1438))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("3. Game Profile & Keymap Scheme", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                Text(if (hasProfile) "Active Scheme: ${activeProfile?.name}" else "No profile configured for game", color = if (hasProfile) StatusEmerald else StatusAmber, fontSize = 11.sp)
                            }
                            if (!hasProfile) {
                                Button(
                                    onClick = onNavigateToEditor,
                                    colors = ButtonDefaults.buttonColors(containerColor = buttonIndigo)
                                ) {
                                    Text("Configure", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.refreshAndResumePendingLaunch()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusEmerald)
                    ) {
                        Text("Check & Resume", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { viewModel.cancelPendingStartGameLaunch() }
                    ) {
                        Text("Cancel", color = Color.White)
                    }
                },
                containerColor = Color(0xFF281845)
            )
        }
    }
}

@Composable
private fun ReferenceVerticalGameCard(
    game: Game,
    proximityFraction: Float,
    onSelect: () -> Unit,
    scaleFactor: Float = 1.0f
) {
    // Interpolate card dimensions smoothly based on proximity fraction (0.0 to 1.0)
    val width = (125.dp + (80.dp * proximityFraction)) * scaleFactor
    val height = (160.dp + (90.dp * proximityFraction)) * scaleFactor
    val iconSize = (54.dp + (42.dp * proximityFraction)) * scaleFactor
    val fontSize = (11f + (7f * proximityFraction)).sp
    val borderWidth = (1f + (1.5f * proximityFraction)).dp
    val borderAlpha = 0.3f + (0.7f * proximityFraction)
    val isPrimary = proximityFraction > 0.6f

    // Two-tone diagonal slash gradient background matching GG Mouse Pro reference
    val cardBrush = remember(isPrimary) {
        if (isPrimary) {
            Brush.linearGradient(
                colors = listOf(Color(0xFF6875C4), Color(0xFF485192)),
                start = Offset(0f, 0f),
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            )
        } else {
            Brush.linearGradient(
                colors = listOf(Color(0xFF535BA3), Color(0xFF3B4178)),
                start = Offset(0f, 0f),
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            )
        }
    }

    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(12.dp))
            .background(cardBrush)
            .border(
                width = borderWidth,
                color = if (isPrimary) Color.White.copy(alpha = 0.95f) else Color.White.copy(alpha = borderAlpha),
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
            Spacer(modifier = Modifier.height(2.dp))

            // App Launcher Icon
            GameIconImage(
                packageName = game.packageName,
                modifier = Modifier
                    .size(iconSize)
                    .clip(RoundedCornerShape(18.dp))
            )

            // Semi-Transparent Game Title Label Box at Bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1438).copy(alpha = 0.55f))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = game.displayName,
                    color = Color.White,
                    fontWeight = if (isPrimary) FontWeight.ExtraBold else FontWeight.Bold,
                    fontSize = fontSize,
                    maxLines = if (isPrimary) 2 else 1,
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
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
