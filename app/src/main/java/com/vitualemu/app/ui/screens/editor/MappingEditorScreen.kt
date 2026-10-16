package com.vitualemu.app.ui.screens.editor

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitualemu.app.domain.model.Mapping
import com.vitualemu.app.domain.model.MappingType
import com.vitualemu.app.domain.validation.ValidationRules
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
import java.util.UUID
import kotlin.math.roundToInt

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

    val mappings = activeProfile?.mappings ?: emptyList()
    val selectedMapping = mappings.find { it.id == selectedMappingId }
    val conflicts = remember(mappings) { ValidationRules.findConflicts(mappings) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MidnightBlue)
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "Canvas Editor: ${activeProfile?.name ?: "No Profile"}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Game: ${activeGame?.displayName ?: "Unassigned"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8)
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            },
            actions = {
                // Snap-to-grid toggle
                IconButton(onClick = { isSnapToGrid = !isSnapToGrid }) {
                    Icon(
                        imageVector = Icons.Default.GridOn,
                        contentDescription = "Snap to grid",
                        tint = if (isSnapToGrid) ElectricBlue else Color(0xFF64748B)
                    )
                }
                // Add new mapping
                IconButton(onClick = { showAddControlDialog = true }) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Control", tint = ElectricBlue)
                }
                // Save Profile
                IconButton(onClick = {
                    activeProfile?.let { viewModel.updateActiveProfile(it) }
                    viewModel.showFeedback("Canvas mappings saved")
                }) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = "Save", tint = StatusEmerald)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = SlateNavy)
        )

        // Conflict warning banner
        if (conflicts.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StatusAmber.copy(alpha = 0.2f))
                    .border(1.dp, StatusAmber.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = StatusAmber, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${conflicts.size} duplicate key conflict(s): ${conflicts.first().description}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFDE68A)
                    )
                }
            }
        }

        // Scrollable container for Canvas + Inspector
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 16:9 Game Screen Canvas Container
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .border(1.dp, SlateBorder, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF090D16))
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures {
                                // Tapping empty canvas deselects
                                selectedMappingId = null
                            }
                        }
                ) {
                    val canvasWidth = maxWidth
                    val canvasHeight = maxHeight

                    // Grid lines (if snap enabled)
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
                        val markerX = (mapping.normalizedX * (canvasWidth.value)).dp
                        val markerY = (mapping.normalizedY * (canvasHeight.value)).dp

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
                                        onTap = { selectedMappingId = mapping.id }
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
                                    // WASD Directional Pad Visual
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .offset(x = (-36).dp, y = (-36).dp)
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
                                            Text("W", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            Row {
                                                Text("A ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                Text("S ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                Text("D", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                                MappingType.AIM_LOOK -> {
                                    // Aim Region Visual
                                    Box(
                                        modifier = Modifier
                                            .size(90.dp)
                                            .offset(x = (-45).dp, y = (-45).dp)
                                            .background(
                                                if (isSelected) Color(0x44F59E0B) else Color(0x22F59E0B),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) StatusAmber else Color(0x66F59E0B),
                                                RoundedCornerShape(8.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(imageVector = Icons.Default.Mouse, contentDescription = null, tint = StatusAmber, modifier = Modifier.size(20.dp))
                                            Text("Aim Look", color = StatusAmber, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                        }
                                    }
                                }
                                else -> {
                                    // Button / Tap / Hold Control Visual
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .offset(x = (-22).dp, y = (-22).dp)
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
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Real-Time Coordinate Inspector & Controls Panel
            selectedMapping?.let { sm ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SlateBorder, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateNavy)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
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

                        // Physical Binding Action
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
                                    Icon(imageVector = Icons.Default.Keyboard, contentDescription = null, modifier = Modifier.size(16.dp), tint = MidnightBlue)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Bind Input", color = MidnightBlue, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Fine-tuning Nudges (±0.01)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Normalized Position: X=%.3f, Y=%.3f".format(sm.normalizedX, sm.normalizedY),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFCBD5E1)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.updateMapping(
                                            sm.copy(normalizedX = ValidationRules.clampCoordinate(sm.normalizedX - 0.01f))
                                        )
                                    },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
                                    modifier = Modifier.size(32.dp)
                                ) { Text("←", fontSize = 12.sp) }

                                OutlinedButton(
                                    onClick = {
                                        viewModel.updateMapping(
                                            sm.copy(normalizedX = ValidationRules.clampCoordinate(sm.normalizedX + 0.01f))
                                        )
                                    },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
                                    modifier = Modifier.size(32.dp)
                                ) { Text("→", fontSize = 12.sp) }

                                OutlinedButton(
                                    onClick = {
                                        viewModel.updateMapping(
                                            sm.copy(normalizedY = ValidationRules.clampCoordinate(sm.normalizedY - 0.01f))
                                        )
                                    },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
                                    modifier = Modifier.size(32.dp)
                                ) { Text("↑", fontSize = 12.sp) }

                                OutlinedButton(
                                    onClick = {
                                        viewModel.updateMapping(
                                            sm.copy(normalizedY = ValidationRules.clampCoordinate(sm.normalizedY + 0.01f))
                                        )
                                    },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
                                    modifier = Modifier.size(32.dp)
                                ) { Text("↓", fontSize = 12.sp) }
                            }
                        }

                        // Sensitivity (for Aim / Mouse Look)
                        if (sm.type == MappingType.AIM_LOOK) {
                            Text("Sensitivity: ${"%.1f".format(sm.sensitivity)}x", style = MaterialTheme.typography.labelSmall, color = Color(0xFFCBD5E1))
                            Slider(
                                value = sm.sensitivity,
                                onValueChange = { s -> viewModel.updateMapping(sm.copy(sensitivity = s)) },
                                valueRange = 0.2f..4.0f,
                                colors = SliderDefaults.colors(thumbColor = ElectricBlue, activeTrackColor = ElectricBlue)
                            )
                        }
                    }
                }
            } ?: run {
                // Hint when nothing is selected
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = "Tap any control on the canvas to inspect coordinates, fine-tune position, or bind a physical keyboard/mouse input.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
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
                        .height(180.dp)
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
                                // Mouse tap capture
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
                        Icon(imageVector = Icons.Default.Keyboard, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (capturedKeyLabel != null) "Detected: $capturedKeyLabel" else "Press any key on physical keyboard\nor click mouse button...",
                            fontWeight = FontWeight.Bold,
                            color = if (capturedKeyLabel != null) StatusEmerald else Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
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

    // Add Control Modal Dialog
    if (showAddControlDialog) {
        var newName by remember { mutableStateOf("") }
        var selectedType by remember { mutableStateOf(MappingType.TAP) }

        AlertDialog(
            onDismissRequest = { showAddControlDialog = false },
            title = { Text("Add Virtual Control", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Control Name (e.g. Fire, Jump, Ability)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Control Type:", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MappingType.values().take(4).forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { selectedType = type },
                                label = { Text(type.name, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricBlue,
                                    selectedLabelColor = MidnightBlue
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            val newM = Mapping(
                                profileId = activeProfile?.profileId ?: UUID.randomUUID().toString(),
                                name = newName.trim(),
                                type = selectedType,
                                normalizedX = 0.5f,
                                normalizedY = 0.5f
                            )
                            viewModel.addMapping(newM)
                            selectedMappingId = newM.id
                            showAddControlDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Text("Add", color = MidnightBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddControlDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = SlateNavy
        )
    }
}
