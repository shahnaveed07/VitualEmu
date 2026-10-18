package com.dsc.virtualemu.ui.screens.games

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.dsc.virtualemu.domain.usecase.InstalledApp
import com.dsc.virtualemu.ui.VitualEmuViewModel

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun AddGameScreen(
    viewModel: VitualEmuViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val allSavedGames by viewModel.allSavedGames.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val isDiscovering by viewModel.isDiscoveringApps.collectAsState()
    val isSyncing by viewModel.isSyncingSelection.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedPackageNames by remember { mutableStateOf<Set<String>?>(null) }

    LaunchedEffect(Unit) {
        viewModel.discoverInstalledApps()
    }

    LaunchedEffect(allSavedGames) {
        if (selectedPackageNames == null) {
            selectedPackageNames = allSavedGames.filter { it.isTargeted }.map { it.packageName }.toSet()
        }
    }

    val currentSelected = selectedPackageNames ?: emptySet()

    val filteredApps = remember(installedApps, searchQuery) {
        if (searchQuery.isBlank()) installedApps
        else installedApps.filter {
            it.displayName.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    val lavenderBg = Color(0xFFC7CBD9)
    val cardIndigo = Color(0xFF535BA3)
    val cardSelectedIndigo = Color(0xFF6875C4)
    val buttonIndigo = Color(0xFF5B61B9)
    val greyButtonBg = Color(0xFF64748B)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(lavenderBg)
    ) {
        // A. Header Row: Title & Subtitle on Left, Compact Search Field on Right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = "Add Game",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    fontStyle = FontStyle.Italic,
                    color = Color(0xFF1E1B4B),
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Click games to select or unselect, then click Apply to confirm.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF334155),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Compact Search Field at Top-Right
            Box(
                modifier = Modifier
                    .width(220.dp)
                    .height(38.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search apps...", color = Color(0xFF64748B), fontSize = 11.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.85f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.65f),
                        focusedBorderColor = cardIndigo,
                        unfocusedBorderColor = Color(0xFFCBD5E1),
                        focusedTextColor = Color(0xFF1E1B4B),
                        unfocusedTextColor = Color(0xFF1E1B4B)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("add_game_search_input")
                )
            }
        }

        // B. Installed Applications Padded Responsive Grid
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp, vertical = 6.dp)
        ) {
            val availableWidth = maxWidth
            val minCardWidth = 120.dp
            val calculatedColumns = maxOf(3, (availableWidth / (minCardWidth + 12.dp)).toInt())

            if (isDiscovering || selectedPackageNames == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = buttonIndigo)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Scanning installed applications...",
                            color = Color(0xFF334155),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            } else if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "No applications matching '$searchQuery'" else "No launchable applications found on device",
                        color = Color(0xFF334155),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(calculatedColumns),
                    contentPadding = PaddingValues(4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isSelected = app.packageName in currentSelected
                        ReferenceAppGridCardItem(
                            app = app,
                            isSelected = isSelected,
                            onToggle = {
                                val next = if (isSelected) {
                                    currentSelected - app.packageName
                                } else {
                                    currentSelected + app.packageName
                                }
                                selectedPackageNames = next
                            },
                            cardColor = if (isSelected) cardSelectedIndigo else cardIndigo
                        )
                    }
                }
            }
        }

        // C. Bottom Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = greyButtonBg,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 8.dp),
                modifier = Modifier
                    .width(140.dp)
                    .testTag("add_game_back_button")
            ) {
                Text("Back", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.width(20.dp))

            Button(
                onClick = {
                    val toCommit = selectedPackageNames ?: emptySet()
                    viewModel.syncSelection(toCommit) {
                        onBack()
                    }
                },
                enabled = !isSyncing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonIndigo,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 8.dp),
                modifier = Modifier
                    .width(150.dp)
                    .testTag("add_game_apply_button")
            ) {
                val count = currentSelected.size
                Text(
                    text = if (count > 0) "Apply ($count)" else "Apply",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun ReferenceAppGridCardItem(
    app: InstalledApp,
    isSelected: Boolean,
    onToggle: () -> Unit,
    cardColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(cardColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.3f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onToggle)
            .padding(8.dp)
            .testTag("app_card_${app.packageName}")
    ) {
        // Upper-Left Corner Checkbox
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (isSelected) Color(0xFF10B981) else Color.Black.copy(alpha = 0.2f))
                .border(
                    width = 1.5.dp,
                    color = if (isSelected) Color(0xFF10B981) else Color.White.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(4.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // App Launcher Icon & Name
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            AppIconDrawableImage(
                iconDrawable = app.icon,
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(12.dp))
            )

            // Lower Semi-Transparent Title Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1E1438).copy(alpha = 0.5f))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = app.displayName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun AppIconDrawableImage(iconDrawable: Drawable?, modifier: Modifier = Modifier) {
    if (iconDrawable != null) {
        val bitmap = remember(iconDrawable) {
            try {
                iconDrawable.toBitmap(128, 128).asImageBitmap()
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
            return
        }
    }

    Box(
        modifier = modifier
            .background(Color(0xFF1E1438), RoundedCornerShape(10.dp)),
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
