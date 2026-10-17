package com.dsc.virtualemu.ui.screens.games

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.dsc.virtualemu.domain.model.Game
import com.dsc.virtualemu.domain.usecase.InstalledApp
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
fun GamesScreen(
    viewModel: VitualEmuViewModel,
    onGameSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val games by viewModel.games.collectAsState()
    val activeGame by viewModel.activeGame.collectAsState()
    var showAppPicker by remember { mutableStateOf(false) }
    var gameToRemove by remember { mutableStateOf<Game?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MidnightBlue)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Notice Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                    .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = ElectricBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Installed Game Discovery: Select a launchable application installed on your device to create a custom profile.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCBD5E1),
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CONFIGURED GAMES (${games.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { viewModel.showFeedback("Game status refreshed") }) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (games.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SlateBorder, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SlateNavy)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Gamepad,
                                contentDescription = null,
                                tint = ElectricBlue,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No game added yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Select an installed application from your device to configure your custom control profile.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Button(
                                onClick = { showAppPicker = true },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = MidnightBlue),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("add_first_game_button")
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Game", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(games, key = { it.gameId }) { game ->
                        val isSelected = activeGame?.gameId == game.gameId
                        val isInstalled = viewModel.isPackageInstalledOnDevice(game.packageName)
                        GameItemCard(
                            game = game,
                            isSelected = isSelected,
                            isInstalled = isInstalled,
                            onSelect = {
                                viewModel.selectGame(game)
                                onGameSelected()
                            },
                            onLaunchGame = {
                                viewModel.launchGame(game.packageName)
                            },
                            onDelete = {
                                gameToRemove = game
                            }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAppPicker = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_game_fab"),
            containerColor = ElectricBlue,
            contentColor = MidnightBlue
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Game")
        }
    }

    if (showAppPicker) {
        InstalledAppPickerDialog(
            viewModel = viewModel,
            onDismiss = { showAppPicker = false },
            onAppsApplied = { apps ->
                viewModel.addMultipleInstalledGames(apps)
                showAppPicker = false
                onGameSelected()
            }
        )
    }

    gameToRemove?.let { game ->
        AlertDialog(
            onDismissRequest = { gameToRemove = null },
            title = { Text("Remove Game", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to remove '${game.displayName}'? Any associated key mapping profiles will also be deleted.",
                    color = Color(0xFFCBD5E1),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeGame(game.gameId)
                        gameToRemove = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusCrimson, contentColor = Color.White)
                ) {
                    Text("Remove", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { gameToRemove = null }) {
                    Text("Cancel")
                }
            },
            containerColor = SlateNavy
        )
    }
}

@Composable
fun InstalledAppPickerDialog(
    viewModel: VitualEmuViewModel,
    onDismiss: () -> Unit,
    onAppsApplied: (List<InstalledApp>) -> Unit
) {
    val installedApps by viewModel.installedApps.collectAsState()
    val isDiscovering by viewModel.isDiscoveringApps.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showManualInput by remember { mutableStateOf(false) }
    var manualPkg by remember { mutableStateOf("") }
    var manualName by remember { mutableStateOf("") }

    var selectedApps by remember { mutableStateOf(setOf<InstalledApp>()) }

    LaunchedEffect(Unit) {
        viewModel.discoverInstalledApps()
    }

    val filteredApps = remember(installedApps, searchQuery) {
        if (searchQuery.isBlank()) installedApps
        else installedApps.filter {
            it.displayName.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Select Installed Application(s)", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { viewModel.discoverInstalledApps() }) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh Apps", tint = ElectricBlue)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (showManualInput) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Enter Package Details", color = Color.White, style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = manualPkg,
                            onValueChange = { manualPkg = it },
                            label = { Text("Package Name") },
                            placeholder = { Text("com.example.game") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("manual_pkg_input")
                        )
                        OutlinedTextField(
                            value = manualName,
                            onValueChange = { manualName = it },
                            label = { Text("Friendly Name (Optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search installed apps...") },
                        leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .testTag("app_picker_search_input")
                    )

                    if (isDiscovering) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = ElectricBlue)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Scanning installed applications...", color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else if (filteredApps.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No matching applications found on device", color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredApps, key = { it.packageName }) { app ->
                                val isChecked = app in selectedApps
                                InstalledAppRow(
                                    app = app,
                                    isChecked = isChecked,
                                    onToggleSelect = {
                                        selectedApps = if (isChecked) selectedApps - app else selectedApps + app
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (showManualInput) {
                Button(
                    onClick = {
                        if (manualPkg.isNotBlank()) {
                            viewModel.addManualGame(manualPkg, manualName)
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = MidnightBlue)
                ) {
                    Text("Add Package")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showManualInput = true },
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                    ) {
                        Text("Manual Entry", fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            if (selectedApps.isNotEmpty()) {
                                onAppsApplied(selectedApps.toList())
                            }
                        },
                        enabled = selectedApps.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = MidnightBlue)
                    ) {
                        Text("Apply (${selectedApps.size})", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = SlateNavy
    )
}

@Composable
fun InstalledAppRow(
    app: InstalledApp,
    isChecked: Boolean,
    onToggleSelect: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isChecked) Color(0xFF1E293B) else Color(0xFF0F172A), RoundedCornerShape(10.dp))
            .border(
                1.dp,
                if (isChecked) ElectricBlue else SlateBorder,
                RoundedCornerShape(10.dp)
            )
            .clickable(enabled = !app.isAlreadyAdded, onClick = onToggleSelect)
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconDrawable = app.icon
            if (iconDrawable != null) {
                val bitmap = remember(iconDrawable) {
                    try { iconDrawable.toBitmap(48, 48).asImageBitmap() } catch (_: Throwable) { null }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = app.displayName,
                        modifier = Modifier.size(36.dp)
                    )
                } else {
                    DefaultAppIcon()
                }
            } else {
                DefaultAppIcon()
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = app.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (app.isGame) {
                        Spacer(modifier = Modifier.width(6.dp))
                        StatusBadge("GAME", color = StatusEmerald, showDot = false)
                    }
                }
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (app.isAlreadyAdded) {
                Text(
                    text = "Added",
                    color = Color(0xFF64748B),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { onToggleSelect() },
                    colors = CheckboxDefaults.colors(checkedColor = ElectricBlue, checkmarkColor = MidnightBlue)
                )
            }
        }
    }
}

@Composable
private fun DefaultAppIcon() {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(Color(0xFF1E293B), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Gamepad,
            contentDescription = null,
            tint = ElectricBlue,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun GameItemCard(
    game: Game,
    isSelected: Boolean,
    isInstalled: Boolean,
    onSelect: () -> Unit,
    onLaunchGame: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("game_card_${game.gameId}"),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFF1E293B) else SlateNavy),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) ElectricBlue else SlateBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(Color(0xFF0F172A), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Gamepad,
                    contentDescription = null,
                    tint = if (isSelected) ElectricBlue else Color(0xFF94A3B8),
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = game.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (!isInstalled) {
                        Spacer(modifier = Modifier.width(6.dp))
                        StatusBadge("Not Installed", color = StatusAmber, showDot = false)
                    }
                }
                Text(
                    text = game.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )
            }

            if (isInstalled) {
                Button(
                    onClick = onLaunchGame,
                    colors = ButtonDefaults.buttonColors(containerColor = StatusEmerald, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(36.dp).testTag("launch_game_button_${game.gameId}")
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Start Game", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            IconButton(onClick = onDelete) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Remove Game", tint = Color(0xFF94A3B8))
            }

            Spacer(modifier = Modifier.width(4.dp))

            if (isSelected) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(StatusEmerald.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = StatusEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Active",
                        color = StatusEmerald,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onSelect,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricBlue),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                ) {
                    Text("Select")
                }
            }
        }
    }
}
