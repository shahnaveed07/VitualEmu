package com.dsc.virtualemu.ui.screens.games

import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
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

@Composable
fun AddGameScreen(
    viewModel: VitualEmuViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val installedApps by viewModel.installedApps.collectAsState()
    val isDiscovering by viewModel.isDiscoveringApps.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
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

    // Outer Background: Light Lavender
    val outerLavender = Color(0xFFE9D5FF)
    val panelIndigo = Color(0xFF281845)
    val cardPeriwinkle = Color(0xFF4C3A7A)
    val cardSelectedIndigo = Color(0xFF281845)
    val darkButtonIndigo = Color(0xFF281845)
    val greyButtonBg = Color(0xFF64748B)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(outerLavender)
    ) {
        // A. Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 8.dp)
        ) {
            Text(
                text = "Add Game",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                fontStyle = FontStyle.Italic,
                color = Color(0xFF1E1B4B),
                fontSize = 24.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Click the game you wish to add, then click Apply to confirm.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF334155),
                fontSize = 13.sp
            )
        }

        // Search Bar (Optional / Helpful for quickly filtering installed apps)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search installed applications...", color = Color(0xFF64748B), fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF475569),
                        modifier = Modifier.size(18.dp)
                    )
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.7f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.5f),
                    focusedBorderColor = Color(0xFF4C3A7A),
                    unfocusedBorderColor = Color(0xFFCBD5E1),
                    focusedTextColor = Color(0xFF1E1B4B),
                    unfocusedTextColor = Color(0xFF1E1B4B)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("add_game_search_input")
            )
        }

        // B. Installed Applications Grid Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp, vertical = 6.dp)
        ) {
            if (isDiscovering) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = panelIndigo)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Scanning installed applications...",
                            color = Color(0xFF334155),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
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
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 130.dp),
                    contentPadding = PaddingValues(4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isSelected = app in selectedApps
                        AppGridCardItem(
                            app = app,
                            isSelected = isSelected,
                            onToggle = {
                                selectedApps = if (isSelected) selectedApps - app else selectedApps + app
                            },
                            unselectedColor = cardPeriwinkle,
                            selectedColor = cardSelectedIndigo
                        )
                    }
                }
            }
        }

        // C. Bottom Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = greyButtonBg,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 10.dp),
                modifier = Modifier.testTag("add_game_back_button")
            ) {
                Text("Back", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Button(
                onClick = {
                    if (selectedApps.isNotEmpty()) {
                        viewModel.addMultipleInstalledGames(selectedApps.toList())
                        onBack()
                    } else {
                        viewModel.showFeedback("Please select at least one game to add")
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = darkButtonIndigo,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 10.dp),
                modifier = Modifier.testTag("add_game_apply_button")
            ) {
                Text(
                    text = if (selectedApps.isNotEmpty()) "Apply (${selectedApps.size})" else "Apply",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun AppGridCardItem(
    app: InstalledApp,
    isSelected: Boolean,
    onToggle: () -> Unit,
    unselectedColor: Color,
    selectedColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) selectedColor else unselectedColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF5B4BB7),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(enabled = !app.isAlreadyAdded, onClick = onToggle)
            .padding(10.dp)
            .testTag("app_card_${app.packageName}")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AppIconGraphic(iconDrawable = app.icon, modifier = Modifier.size(44.dp))

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = app.displayName,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }

        // Selection Indicator / Added Indicator Badge
        if (app.isAlreadyAdded) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1E293B))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("Added", color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        } else if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF38BDF8)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color(0xFF1E1B4B),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun AppIconGraphic(iconDrawable: Drawable?, modifier: Modifier = Modifier) {
    if (iconDrawable != null) {
        val bitmap = remember(iconDrawable) {
            try {
                iconDrawable.toBitmap(64, 64).asImageBitmap()
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
            .background(Color(0xFF1E293B), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Gamepad,
            contentDescription = null,
            tint = Color(0xFF38BDF8),
            modifier = Modifier.size(24.dp)
        )
    }
}
