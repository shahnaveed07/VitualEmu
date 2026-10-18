package com.dsc.virtualemu.ui.screens.editor

import android.annotation.SuppressLint
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsc.virtualemu.domain.model.Macro
import com.dsc.virtualemu.domain.model.MacroStep
import com.dsc.virtualemu.domain.model.MacroTriggerMode
import com.dsc.virtualemu.domain.model.Mapping
import com.dsc.virtualemu.domain.model.MappingType
import com.dsc.virtualemu.domain.model.MouseLookConfig
import com.dsc.virtualemu.domain.validation.ValidationRules
import com.dsc.virtualemu.ui.VitualEmuViewModel
import com.dsc.virtualemu.ui.components.KeyCapChip
import com.dsc.virtualemu.ui.theme.ElectricBlue
import com.dsc.virtualemu.ui.theme.MidnightBlue
import com.dsc.virtualemu.ui.theme.SlateBorder
import com.dsc.virtualemu.ui.theme.SlateNavy
import com.dsc.virtualemu.ui.theme.StatusAmber
import com.dsc.virtualemu.ui.theme.StatusCrimson
import com.dsc.virtualemu.ui.theme.StatusEmerald
import java.util.UUID
import kotlin.math.roundToInt

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MappingEditorScreen(
    viewModel: VitualEmuViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val activeProfile by viewModel.activeProfile.collectAsState()
    val activeGame by viewModel.activeGame.collectAsState()

    var selectedMappingId by remember { mutableStateOf<String?>(null) }
    var isSnapToGrid by remember { mutableStateOf(false) }
    var showKeyCaptureDialog by remember { mutableStateOf(false) }
    var showAddControlDialog by remember { mutableStateOf(false) }
    var showShareCodeDialog by remember { mutableStateOf(false) }
    var showImportCodePrompt by remember { mutableStateOf(false) }
    var inspectorTab by remember { mutableIntStateOf(0) }

    val mappings = activeProfile?.mappings ?: emptyList()
    val macros = activeProfile?.macros ?: emptyList()
    val mouseLookConfig = activeProfile?.mouseLookConfig ?: MouseLookConfig()
    val selectedMapping = mappings.find { it.id == selectedMappingId }
    val conflicts = remember(mappings) { ValidationRules.findConflicts(mappings) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MidnightBlue)
    ) {
        // Compact Landscape Header Bar
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
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "Canvas Editor: ${activeProfile?.name ?: "No Profile"}",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${activeGame?.displayName ?: "Unassigned"} • ${mappings.size} controls • 10-Pointer Engine",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (conflicts.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .background(StatusAmber.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                            .border(1.dp, StatusAmber.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = StatusAmber, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${conflicts.size} key conflict(s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFDE68A)
                        )
                    }
                }

                IconButton(onClick = { isSnapToGrid = !isSnapToGrid }) {
                    Icon(
                        imageVector = Icons.Default.GridOn,
                        contentDescription = "Snap to grid",
                        tint = if (isSnapToGrid) ElectricBlue else Color(0xFF64748B)
                    )
                }
                IconButton(onClick = { showAddControlDialog = true }) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Control", tint = ElectricBlue)
                }
                IconButton(onClick = {
                    activeProfile?.let { viewModel.updateActiveProfile(it) }
                    viewModel.showFeedback("Canvas mappings & Mouse-Look settings saved")
                }) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = "Save", tint = StatusEmerald)
                }
            }
        }

        // Landscape Split-Pane Body: Left = Largest 16:9 Canvas, Right = Tabbed Inspector
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Left Pane: Interactive 16:9 Game Overlay Canvas
            Card(
                modifier = Modifier
                    .weight(1.65f)
                    .fillMaxHeight()
                    .border(1.dp, SlateBorder, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF090D16))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .aspectRatio(16f / 9f, matchHeightConstraintsFirst = true)
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    selectedMappingId = null
                                }
                            }
                    ) {
                        // Top Center GG Mouse Pro Action Bar Overlay (Image 3)
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xE63B82F6))
                                .border(1.dp, Color(0xFF93C5FD), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Exit",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { onBack() }
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                            .testTag("editor_action_exit")
                                    )
                                    Text("|", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                                    Text(
                                        text = "Add component",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { showAddControlDialog = true }
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                            .testTag("editor_action_add_component")
                                    )
                                    Text("|", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                                    Text(
                                        text = "Save",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                activeProfile?.let { viewModel.updateActiveProfile(it) }
                                                viewModel.showFeedback("Keymap configuration saved")
                                            }
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                            .testTag("editor_action_save")
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Share",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { showShareCodeDialog = true }
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                            .testTag("editor_action_share")
                                    )
                                    Text("|", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                                    Text(
                                        text = "Import",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { showImportCodePrompt = true }
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                            .testTag("editor_action_import")
                                    )
                                }
                            }
                        }
                        if (isSnapToGrid) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val stepX = size.width / 10f
                                val stepY = size.height / 10f
                                for (i in 1..9) {
                                    drawLine(
                                        color = Color(0xFF1E293B),
                                        start = Offset(stepX * i, 0f),
                                        end = Offset(stepX * i, size.height),
                                        strokeWidth = 1f
                                    )
                                    drawLine(
                                        color = Color(0xFF1E293B),
                                        start = Offset(0f, stepY * i),
                                        end = Offset(size.width, stepY * i),
                                        strokeWidth = 1f
                                    )
                                }
                            }
                        }

                        // Render Draggable Control Markers
                        for (mapping in mappings) {
                            val isSelected = mapping.id == selectedMappingId

                            Box(
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            x = (mapping.normalizedX * constraints.maxWidth).roundToInt(),
                                            y = (mapping.normalizedY * constraints.maxHeight).roundToInt()
                                        )
                                    }
                                    .pointerInput(mapping.id) {
                                        detectTapGestures(
                                            onTap = {
                                                selectedMappingId = mapping.id
                                                inspectorTab = 0
                                            }
                                        )
                                    }
                                    .pointerInput(mapping.id) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            var newNormX = mapping.normalizedX + (dragAmount.x / constraints.maxWidth)
                                            var newNormY = mapping.normalizedY + (dragAmount.y / constraints.maxHeight)

                                            if (isSnapToGrid) {
                                                newNormX = (newNormX * 20f).roundToInt() / 20f
                                                newNormY = (newNormY * 20f).roundToInt() / 20f
                                            }

                                            newNormX = ValidationRules.clampCoordinate(newNormX)
                                            newNormY = ValidationRules.clampCoordinate(newNormY)

                                            viewModel.updateMapping(
                                                mapping.copy(normalizedX = newNormX, normalizedY = newNormY)
                                            )
                                        }
                                    }
                            ) {
                                when (mapping.type) {
                                    MappingType.WASD_STICK -> {
                                        Box(
                                            modifier = Modifier
                                                .size(68.dp)
                                                .offset(x = (-34).dp, y = (-34).dp)
                                                .background(
                                                    if (isSelected) ElectricBlue.copy(alpha = 0.35f) else Color(0x3338BDF8),
                                                    CircleShape
                                                )
                                                .border(
                                                    2.dp,
                                                    if (isSelected) ElectricBlue else Color(0x8838BDF8),
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("W", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                                Row {
                                                    Text("A ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                                    Text("S ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                                    Text("D", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                                }
                                            }
                                        }
                                    }
                                    MappingType.AIM_LOOK -> {
                                        Box(
                                            modifier = Modifier
                                                .size(84.dp)
                                                .offset(x = (-42).dp, y = (-42).dp)
                                                .background(
                                                    if (isSelected) Color(0x44F59E0B) else Color(0x22F59E0B),
                                                    RoundedCornerShape(10.dp)
                                                )
                                                .border(
                                                    1.5.dp,
                                                    if (isSelected) StatusAmber else Color(0x66F59E0B),
                                                    RoundedCornerShape(10.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(imageVector = Icons.Default.Mouse, contentDescription = null, tint = StatusAmber, modifier = Modifier.size(18.dp))
                                                Text("360° Aim", color = StatusAmber, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                    MappingType.FREE_LOOK_EYE -> {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .offset(x = (-23).dp, y = (-23).dp)
                                                .background(
                                                    if (isSelected) StatusAmber.copy(alpha = 0.4f) else Color(0x33F59E0B),
                                                    CircleShape
                                                )
                                                .border(1.5.dp, StatusAmber, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(imageVector = Icons.Default.Visibility, contentDescription = null, tint = StatusAmber, modifier = Modifier.size(14.dp))
                                                Text(mapping.keyLabel.ifBlank { "ALT" }, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    MappingType.FIRE_WEAPON -> {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .offset(x = (-24).dp, y = (-24).dp)
                                                .background(
                                                    if (isSelected) StatusCrimson else StatusCrimson.copy(alpha = 0.35f),
                                                    CircleShape
                                                )
                                                .border(1.5.dp, Color.White, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(imageVector = Icons.Default.GpsFixed, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                Text(mapping.keyLabel.ifBlank { "Fire" }, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    else -> {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .offset(x = (-21).dp, y = (-21).dp)
                                                .background(
                                                    if (isSelected) ElectricBlue else SlateNavy,
                                                    CircleShape
                                                )
                                                .border(
                                                    1.5.dp,
                                                    if (isSelected) Color.White else SlateBorder,
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = mapping.keyLabel.ifBlank { "?" },
                                                color = if (isSelected) MidnightBlue else Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Right Pane: Tabbed Inspector (Controls | Mouse 360° | Macros)
            Card(
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxHeight()
                    .border(1.dp, SlateBorder, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SlateNavy)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    TabRow(
                        selectedTabIndex = inspectorTab,
                        containerColor = Color(0xFF0F172A),
                        contentColor = ElectricBlue
                    ) {
                        Tab(
                            selected = inspectorTab == 0,
                            onClick = { inspectorTab = 0 },
                            text = { Text("Control", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = inspectorTab == 1,
                            onClick = { inspectorTab = 1 },
                            text = { Text("Mouse 360°", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = inspectorTab == 2,
                            onClick = { inspectorTab = 2 },
                            text = { Text("Macros (${macros.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        when (inspectorTab) {
                            0 -> {
                                selectedMapping?.let { sm ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "SELECTED: ${sm.name}",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "Type: ${sm.type.label}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF94A3B8)
                                            )
                                        }
                                        IconButton(onClick = {
                                            viewModel.removeMapping(sm.id)
                                            selectedMappingId = null
                                        }) {
                                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = StatusCrimson)
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                            .padding(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("BOUND PHYSICAL INPUT", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                                                Spacer(modifier = Modifier.height(2.dp))
                                                KeyCapChip(label = sm.keyLabel.ifBlank { "Unbound (Key: ${sm.keyCode})" }, highlighted = true)
                                            }

                                            Button(
                                                onClick = { showKeyCaptureDialog = true },
                                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.testTag("bind_physical_key_button")
                                            ) {
                                                Icon(imageVector = Icons.Default.Keyboard, contentDescription = null, modifier = Modifier.size(14.dp), tint = MidnightBlue)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Bind Input", color = MidnightBlue, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                        }
                                    }

                                    Text(
                                        text = "Normalized Position: X=%.3f, Y=%.3f".format(sm.normalizedX, sm.normalizedY),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFCBD5E1)
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.updateMapping(
                                                    sm.copy(normalizedX = ValidationRules.clampCoordinate(sm.normalizedX - 0.01f))
                                                )
                                            },
                                            contentPadding = PaddingValues(4.dp),
                                            modifier = Modifier.size(34.dp)
                                        ) { Text("←", fontSize = 12.sp) }

                                        OutlinedButton(
                                            onClick = {
                                                viewModel.updateMapping(
                                                    sm.copy(normalizedX = ValidationRules.clampCoordinate(sm.normalizedX + 0.01f))
                                                )
                                            },
                                            contentPadding = PaddingValues(4.dp),
                                            modifier = Modifier.size(34.dp)
                                        ) { Text("→", fontSize = 12.sp) }

                                        OutlinedButton(
                                            onClick = {
                                                viewModel.updateMapping(
                                                    sm.copy(normalizedY = ValidationRules.clampCoordinate(sm.normalizedY - 0.01f))
                                                )
                                            },
                                            contentPadding = PaddingValues(4.dp),
                                            modifier = Modifier.size(34.dp)
                                        ) { Text("↑", fontSize = 12.sp) }

                                        OutlinedButton(
                                            onClick = {
                                                viewModel.updateMapping(
                                                    sm.copy(normalizedY = ValidationRules.clampCoordinate(sm.normalizedY + 0.01f))
                                                )
                                            },
                                            contentPadding = PaddingValues(4.dp),
                                            modifier = Modifier.size(34.dp)
                                        ) { Text("↓", fontSize = 12.sp) }
                                    }

                                    if (sm.type == MappingType.AIM_LOOK || sm.type == MappingType.FREE_LOOK_EYE) {
                                        Text("Sensitivity: ${"%.1f".format(sm.sensitivity)}x", style = MaterialTheme.typography.labelSmall, color = Color(0xFFCBD5E1))
                                        Slider(
                                            value = sm.sensitivity,
                                            onValueChange = { s -> viewModel.updateMapping(sm.copy(sensitivity = s)) },
                                            valueRange = 0.2f..4.0f,
                                            colors = SliderDefaults.colors(thumbColor = ElectricBlue, activeTrackColor = ElectricBlue)
                                        )
                                    }
                                } ?: run {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            text = "Tap any control on the 16:9 canvas to inspect coordinates, fine-tune position, or bind a physical keyboard/mouse input.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            }

                            1 -> {
                                // Mouse-Look 360°, Free-Look Eye, & Smart Drag Inspector
                                Text(
                                    text = "FPS SHOOTING MODE & 360° RESET",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricBlue,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Toggle Key: ${mouseLookConfig.toggleKeyLabel}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White
                                )

                                Text("X Sensitivity: ${"%.2f".format(mouseLookConfig.sensitivityX)}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFCBD5E1))
                                Slider(
                                    value = mouseLookConfig.sensitivityX,
                                    onValueChange = { viewModel.updateMouseLookConfig(mouseLookConfig.copy(sensitivityX = it)) },
                                    valueRange = 0.2f..3.5f,
                                    colors = SliderDefaults.colors(thumbColor = ElectricBlue, activeTrackColor = ElectricBlue)
                                )

                                Text("Y Sensitivity: ${"%.2f".format(mouseLookConfig.sensitivityY)}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFCBD5E1))
                                Slider(
                                    value = mouseLookConfig.sensitivityY,
                                    onValueChange = { viewModel.updateMouseLookConfig(mouseLookConfig.copy(sensitivityY = it)) },
                                    valueRange = 0.2f..3.5f,
                                    colors = SliderDefaults.colors(thumbColor = ElectricBlue, activeTrackColor = ElectricBlue)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Infinite 360° Edge Reset", color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                        Text("Auto recenter Pointer 1 at screen bounds", color = Color(0xFF94A3B8), style = MaterialTheme.typography.labelSmall)
                                    }
                                    Switch(
                                        checked = mouseLookConfig.autoEdgeReset,
                                        onCheckedChange = { viewModel.updateMouseLookConfig(mouseLookConfig.copy(autoEdgeReset = it)) },
                                        colors = SwitchDefaults.colors(checkedThumbColor = StatusEmerald)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Free Look (Eye Button)", color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                        Text("Hold ${mouseLookConfig.freeLookKeyLabel} to shift camera to Eye", color = Color(0xFF94A3B8), style = MaterialTheme.typography.labelSmall)
                                    }
                                    Switch(
                                        checked = mouseLookConfig.freeLookEnabled,
                                        onCheckedChange = { viewModel.updateMouseLookConfig(mouseLookConfig.copy(freeLookEnabled = it)) },
                                        colors = SwitchDefaults.colors(checkedThumbColor = StatusAmber)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Smart Drag (Aim Comp)", color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                        Text("Vertical micro-drag while Primary Fire held", color = Color(0xFF94A3B8), style = MaterialTheme.typography.labelSmall)
                                    }
                                    Switch(
                                        checked = mouseLookConfig.smartDragEnabled,
                                        onCheckedChange = { viewModel.updateMouseLookConfig(mouseLookConfig.copy(smartDragEnabled = it)) },
                                        colors = SwitchDefaults.colors(checkedThumbColor = ElectricBlue)
                                    )
                                }
                            }

                            2 -> {
                                // Bounded Macro Builder Tab
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "BOUNDED MACROS",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricBlue,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Button(
                                        onClick = {
                                            val pid = activeProfile?.profileId ?: return@Button
                                            val newMacroId = UUID.randomUUID().toString()
                                            val quickGloo = Macro(
                                                id = newMacroId,
                                                profileId = pid,
                                                name = "Gloo Wall + Crouch (#${macros.size + 1})",
                                                triggerKeyCode = KeyEvent.KEYCODE_G,
                                                triggerKeyLabel = "G",
                                                triggerMode = MacroTriggerMode.ON_PRESS,
                                                steps = listOf(
                                                    MacroStep(
                                                        macroId = newMacroId,
                                                        stepIndex = 0,
                                                        actionType = "TAP",
                                                        normalizedX = 0.92f,
                                                        normalizedY = 0.86f,
                                                        delayMs = 24L,
                                                        holdDurationMs = 40L
                                                    ),
                                                    MacroStep(
                                                        macroId = newMacroId,
                                                        stepIndex = 1,
                                                        actionType = "TAP",
                                                        normalizedX = 0.85f,
                                                        normalizedY = 0.65f,
                                                        delayMs = 32L,
                                                        holdDurationMs = 45L
                                                    )
                                                )
                                            )
                                            viewModel.addMacro(quickGloo)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = MidnightBlue),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("+ Quick Macro", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Text(
                                    text = "Safety Bounds: Max 32 steps • Min 16ms delay • Instant Safe-Stop cancel",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF94A3B8)
                                )

                                if (macros.isEmpty()) {
                                    Text(
                                        text = "No macros configured in this profile.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF64748B)
                                    )
                                } else {
                                    macros.forEach { macro ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                                .border(1.dp, SlateBorder, RoundedCornerShape(8.dp))
                                                .padding(8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(macro.name, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                                Text(
                                                    "Trigger: ${macro.triggerKeyLabel} (${macro.triggerMode.name}) • ${macro.steps.size} steps",
                                                    color = Color(0xFF94A3B8),
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                            IconButton(onClick = { viewModel.removeMacro(macro.id) }, modifier = Modifier.size(28.dp)) {
                                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Macro", tint = StatusCrimson, modifier = Modifier.size(16.dp))
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
    }

    // Physical Input Capture Dialog
    if (showKeyCaptureDialog && selectedMapping != null) {
        val sm = selectedMapping
        val focusRequester = remember { FocusRequester() }
        var capturedKeyLabel by remember { mutableStateOf<String?>(null) }
        var capturedKeyCode by remember { mutableStateOf<Int?>(null) }

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        AlertDialog(
            onDismissRequest = { showKeyCaptureDialog = false },
            title = {
                Text("Capture Physical Input", fontWeight = FontWeight.Bold, color = Color.White)
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                        .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
                        .focusRequester(focusRequester)
                        .onKeyEvent { keyEvent ->
                            val nativeCode = keyEvent.nativeKeyEvent.keyCode
                            val label = KeyEvent.keyCodeToString(nativeCode).removePrefix("KEYCODE_")
                            capturedKeyCode = nativeCode
                            capturedKeyLabel = label
                            true
                        }
                        .pointerInput(Unit) {
                            detectTapGestures {
                                capturedKeyCode = KeyEvent.KEYCODE_BUTTON_L1
                                capturedKeyLabel = "Mouse LClick"
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(imageVector = Icons.Default.Keyboard, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (capturedKeyLabel != null) "Detected: $capturedKeyLabel" else "Press any key on physical keyboard\nor click mouse button...",
                            fontWeight = FontWeight.Bold,
                            color = if (capturedKeyLabel != null) StatusEmerald else Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = if (capturedKeyCode != null) "Native KeyCode: $capturedKeyCode" else "Listening for hardware events",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val code = capturedKeyCode
                        val label = capturedKeyLabel
                        if (code != null && label != null) {
                            viewModel.updateMapping(
                                sm.copy(keyCode = code, keyLabel = label)
                            )
                        }
                        showKeyCaptureDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                    enabled = capturedKeyCode != null
                ) {
                    Text("Apply Binding", color = MidnightBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showKeyCaptureDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = SlateNavy
        )
    }

    // Reference GGMouse Pro "Add component" Modal Dialog (Screenshots 5 & 6)
    if (showAddControlDialog) {
        AddComponentDialog(
            onDismiss = { showAddControlDialog = false },
            onSelectComponent = { type, label ->
                val newM = Mapping(
                    profileId = activeProfile?.profileId ?: UUID.randomUUID().toString(),
                    name = label,
                    type = type,
                    normalizedX = 0.5f,
                    normalizedY = 0.5f
                )
                viewModel.addMapping(newM)
                selectedMappingId = newM.id
                showAddControlDialog = false
                viewModel.showFeedback("Added component: $label")
            }
        )
    }

    // Share Code Prompt Dialog (Image 6)
    if (showShareCodeDialog) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val code = remember { viewModel.generateShareCode() }
        AlertDialog(
            onDismissRequest = { showShareCodeDialog = false },
            title = {
                Text("Prompt", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Here is the share code of your keymap. You can send the code to others and they can click the import button on the panel to import your keymap using the share code",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    OutlinedTextField(
                        value = code,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Keymap Share Code", code)
                        clipboard?.setPrimaryClip(clip)
                        viewModel.showFeedback("Copied share code to clipboard!")
                        showShareCodeDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Text("Copy to clipboard", color = MidnightBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showShareCodeDialog = false }) {
                    Text("Close", color = Color.White)
                }
            },
            containerColor = SlateNavy
        )
    }

    // Import Code Prompt Dialog (Image 5)
    if (showImportCodePrompt) {
        var importInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImportCodePrompt = false },
            title = {
                Text("Prompt", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Please enter the share code", color = Color(0xFFCBD5E1), fontSize = 13.sp)
                    OutlinedTextField(
                        value = importInput,
                        onValueChange = { importInput = it },
                        singleLine = true,
                        placeholder = { Text("Enter code...", color = Color(0xFF64748B)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importInput.isNotBlank()) {
                            viewModel.importShareCode(importInput)
                            showImportCodePrompt = false
                        } else {
                            viewModel.showFeedback("Please enter a valid share code")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Text("Import", color = MidnightBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showImportCodePrompt = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = SlateNavy
        )
    }
}

@Composable
fun AddComponentDialog(
    onDismiss: () -> Unit,
    onSelectComponent: (type: MappingType, name: String) -> Unit
) {
    val dimBg = Color.Black.copy(alpha = 0.72f)
    val panelNavy = Color(0xFF0F172A)
    val cardBg = Color(0xFF1E293B)
    val electricBlue = Color(0xFF38BDF8)

    val components = listOf(
        Triple("Touch", MappingType.TAP, Icons.Default.TouchApp),
        Triple("Swipe", MappingType.SWIPE, Icons.Default.Swipe),
        Triple("Mouse Scroll", MappingType.AIM_LOOK, Icons.Default.Mouse),
        Triple("Macro", MappingType.MACRO_TRIGGER, Icons.Default.AccountTree),
        Triple("Joystick", MappingType.WASD_STICK, Icons.Default.OpenWith),
        Triple("FPS View", MappingType.AIM_LOOK, Icons.Default.GpsFixed),
        Triple("Free Look", MappingType.FREE_LOOK_EYE, Icons.Default.Visibility),
        Triple("Mouse Pick", MappingType.FIRE_WEAPON, Icons.Default.CenterFocusWeak),
        Triple("Long press group", MappingType.HOLD, Icons.Default.Schedule),
        Triple("Floating ball display", MappingType.TAP_REPEAT, Icons.Default.RadioButtonUnchecked)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(dimBg)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(16.dp))
                .background(panelNavy)
                .border(2.dp, electricBlue, RoundedCornerShape(16.dp))
                .clickable(enabled = false, onClick = {})
                .padding(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Add component",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontSize = 22.sp
                )

                Text(
                    text = "Select a component to place on keymap",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(2.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                ) {
                    items(components) { (label, type, icon) ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(cardBg)
                                .border(1.dp, electricBlue.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .clickable {
                                    onSelectComponent(type, label)
                                }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = electricBlue,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = label,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
