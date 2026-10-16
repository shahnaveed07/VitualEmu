package com.vitualemu.app.ui.screens.profiles

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitualemu.app.domain.model.Profile
import com.vitualemu.app.ui.VitualEmuViewModel
import com.vitualemu.app.ui.theme.ElectricBlue
import com.vitualemu.app.ui.theme.MidnightBlue
import com.vitualemu.app.ui.theme.SlateBorder
import com.vitualemu.app.ui.theme.SlateNavy
import com.vitualemu.app.ui.theme.StatusAmber
import com.vitualemu.app.ui.theme.StatusCrimson
import com.vitualemu.app.ui.theme.StatusEmerald

@Composable
fun ProfilesScreen(
    viewModel: VitualEmuViewModel,
    onNavigateToEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val profiles by viewModel.profiles.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val activeGame by viewModel.activeGame.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }

    var showExportDialog by remember { mutableStateOf<String?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }

    var showHistoryForProfile by remember { mutableStateOf<Profile?>(null) }

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

            // Action toolbar: Import & Export
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showImportDialog = true },
                    modifier = Modifier.weight(1f).testTag("import_profile_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricBlue),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import JSON")
                }

                OutlinedButton(
                    onClick = {
                        val json = viewModel.exportActiveProfileJson()
                        if (json != null) {
                            showExportDialog = json
                        } else {
                            viewModel.showFeedback("No profile selected to export")
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("export_profile_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                ) {
                    Icon(imageVector = Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export JSON")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "SAVED PROFILES (${profiles.size})",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF94A3B8),
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(profiles, key = { it.profileId }) { profile ->
                    val isActive = activeProfile?.profileId == profile.profileId
                    ProfileCard(
                        profile = profile,
                        isActive = isActive,
                        onSelect = {
                            viewModel.selectProfile(profile)
                        },
                        onEdit = {
                            viewModel.selectProfile(profile)
                            onNavigateToEditor()
                        },
                        onDuplicate = {
                            viewModel.selectProfile(profile)
                            viewModel.duplicateActiveProfile()
                        },
                        onHistory = {
                            showHistoryForProfile = profile
                        },
                        onDelete = {
                            viewModel.deleteProfile(profile.profileId)
                        }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("create_profile_fab"),
            containerColor = ElectricBlue,
            contentColor = MidnightBlue
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "New Profile")
        }
    }

    // Create Profile Dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New Profile", color = Color.White) },
            text = {
                Column {
                    Text(
                        "Enter a name for this control profile for ${activeGame?.displayName ?: "current game"}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = { newProfileName = it },
                        label = { Text("Profile Name") },
                        placeholder = { Text("e.g. Tactical Sniper") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_profile_name_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createNewProfile(newProfileName)
                        newProfileName = ""
                        showCreateDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = MidnightBlue)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = SlateNavy
        )
    }

    // Export Dialog
    if (showExportDialog != null) {
        AlertDialog(
            onDismissRequest = { showExportDialog = null },
            title = { Text("Exported Profile JSON", color = Color.White) },
            text = {
                Column {
                    Text(
                        "Safe versioned interchange JSON. Contains normalized coordinates and macro bounds. No sensitive credentials are included.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = showExportDialog!!,
                        onValueChange = {},
                        readOnly = true,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("VitualEmu Profile", showExportDialog))
                        viewModel.showFeedback("Profile JSON copied to clipboard")
                        showExportDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = MidnightBlue)
                ) {
                    Text("Copy to Clipboard")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showExportDialog = null }) {
                    Text("Close")
                }
            },
            containerColor = SlateNavy
        )
    }

    // Import Dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Profile JSON", color = Color.White) },
            text = {
                Column {
                    Text(
                        "Paste a valid VitualEmu profile JSON below. Schema version and boundary constraints will be strictly validated.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importJsonText,
                        onValueChange = { importJsonText = it },
                        label = { Text("Profile JSON Payload") },
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth().testTag("import_json_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importJsonText.isNotBlank()) {
                            viewModel.importProfileJson(importJsonText)
                            importJsonText = ""
                            showImportDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = MidnightBlue)
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showImportDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = SlateNavy
        )
    }

    // Snapshot History Dialog
    if (showHistoryForProfile != null) {
        val targetProfile = showHistoryForProfile!!
        val snapshots by viewModel.getSnapshots(targetProfile.profileId).collectAsState(initial = emptyList())

        AlertDialog(
            onDismissRequest = { showHistoryForProfile = null },
            title = {
                Text("Snapshot History & Rollback", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Bounded recovery snapshots for '${targetProfile.name}'. Restore any prior state without losing other profiles.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    if (snapshots.isEmpty()) {
                        Text("No snapshots recorded yet. Snapshots are created automatically on each save.", color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
                    } else {
                        snapshots.forEach { snap ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                    .border(1.dp, SlateBorder, RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = snap.reason,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", snap.createdAt).toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                                Button(
                                    onClick = {
                                        viewModel.rollbackToSnapshot(snap)
                                        showHistoryForProfile = null
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = StatusAmber, contentColor = MidnightBlue),
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Text("Rollback", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = { showHistoryForProfile = null }) {
                    Text("Close")
                }
            },
            containerColor = SlateNavy
        )
    }
}

@Composable
fun ProfileCard(
    profile: Profile,
    isActive: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onHistory: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_card_${profile.profileId}"),
        colors = CardDefaults.cardColors(containerColor = if (isActive) Color(0xFF1E293B) else SlateNavy),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            if (isActive) 2.dp else 1.dp,
            if (isActive) ElectricBlue else SlateBorder
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (profile.isFavorite) {
                        Icon(imageVector = Icons.Default.Star, contentDescription = "Favorite", tint = StatusAmber, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                if (isActive) {
                    Text(
                        text = "ACTIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusEmerald,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(StatusEmerald.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${profile.mappings.size} controls mapped • Schema v${profile.schemaVersion}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isActive) {
                    Button(
                        onClick = onSelect,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = MidnightBlue),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Select", fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = onEdit,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isActive) ElectricBlue else SlateBorder, contentColor = if (isActive) MidnightBlue else Color.White),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Editor")
                }

                IconButton(onClick = onDuplicate, modifier = Modifier.size(36.dp)) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Save Copy", tint = Color.White)
                }

                IconButton(onClick = onHistory, modifier = Modifier.size(36.dp)) {
                    Icon(imageVector = Icons.Default.History, contentDescription = "Snapshot History", tint = ElectricBlue)
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = StatusCrimson)
                }
            }
        }
    }
}
