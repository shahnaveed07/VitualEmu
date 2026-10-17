package com.dsc.virtualemu.ui.screens.profiles

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Gamepad
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
import com.dsc.virtualemu.domain.model.Profile
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
fun ProfilesScreen(
    viewModel: VitualEmuViewModel,
    onNavigateToEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val profiles by viewModel.profiles.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val activeGame by viewModel.activeGame.collectAsState()
    val parsedImportData by viewModel.parsedImportData.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    var profileToDelete by remember { mutableStateOf<Profile?>(null) }

    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.parseCfgFileFromUri(context, it) }
    }

    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { viewModel.exportAllSchemesToCfg(context, it) }
    }

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

            // Action toolbar: SAF Import & Export
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { openDocLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f).testTag("import_profile_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricBlue),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import File (.cfg/.json)")
                }

                OutlinedButton(
                    onClick = {
                        val fileName = "${activeGame?.packageName ?: "VitualEmu"}_keymap.cfg"
                        createDocLauncher.launch(fileName)
                    },
                    modifier = Modifier.weight(1f).testTag("export_profile_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                ) {
                    Icon(imageVector = Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export Schemes")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Scheme Selector Bar
            if (activeGame != null && profiles.isNotEmpty()) {
                Text(
                    text = "CONTROL SCHEMES FOR ${activeGame?.displayName?.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(profiles, key = { it.profileId }) { profile ->
                        val isSelected = activeProfile?.profileId == profile.profileId
                        Box(
                            modifier = Modifier
                                .background(if (isSelected) ElectricBlue else SlateNavy, RoundedCornerShape(10.dp))
                                .border(1.dp, if (isSelected) ElectricBlue else SlateBorder, RoundedCornerShape(10.dp))
                                .clickable { viewModel.selectProfile(profile) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = profile.name,
                                color = if (isSelected) MidnightBlue else Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            Text(
                text = "SAVED SCHEMES (${profiles.size})",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF94A3B8),
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (profiles.isEmpty()) {
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
                            Icon(imageVector = Icons.Default.Gamepad, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("No schemes created yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Import a .cfg / .json configuration or create a new scheme for your selected game.", color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { showCreateDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = MidnightBlue)
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("New Control Scheme", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(profiles, key = { it.profileId }) { profile ->
                        val isActive = activeProfile?.profileId == profile.profileId
                        ProfileCardItem(
                            profile = profile,
                            isActive = isActive,
                            onSelect = { viewModel.selectProfile(profile) },
                            onEdit = {
                                viewModel.selectProfile(profile)
                                onNavigateToEditor()
                            },
                            onDuplicate = {
                                viewModel.selectProfile(profile)
                                viewModel.duplicateActiveProfile()
                            },
                            onDelete = {
                                profileToDelete = profile
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
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("create_profile_fab"),
            containerColor = ElectricBlue,
            contentColor = MidnightBlue
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "New Scheme")
        }
    }

    // Import Preview Dialog
    parsedImportData?.let { parsed ->
        ImportPreviewDialog(
            parsedData = parsed,
            activeGame = activeGame,
            onConfirmImport = { selectedProfiles ->
                val gameId = activeGame?.gameId ?: ""
                if (gameId.isNotBlank()) {
                    viewModel.confirmImportSelectedSchemes(gameId, selectedProfiles)
                } else {
                    viewModel.showFeedback("Please select a target game first")
                }
            },
            onDismiss = { viewModel.clearImportPreview() }
        )
    }

    // Create Scheme Dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New Control Scheme", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter a friendly name for this scheme (e.g., 'CUSTOM', 'Casual', 'Vehicle').", color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = { newProfileName = it },
                        label = { Text("Scheme Name") },
                        placeholder = { Text("CUSTOM") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_scheme_name_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newProfileName.isNotBlank()) {
                            viewModel.createNewProfile(newProfileName)
                            newProfileName = ""
                            showCreateDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = MidnightBlue)
                ) {
                    Text("Create Scheme", fontWeight = FontWeight.Bold)
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

    // Delete Scheme Dialog
    profileToDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            title = { Text("Delete Scheme", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete scheme '${profile.name}'?", color = Color(0xFFCBD5E1)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProfile(profile.profileId)
                        profileToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusCrimson, contentColor = Color.White)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { profileToDelete = null }) {
                    Text("Cancel")
                }
            },
            containerColor = SlateNavy
        )
    }
}

@Composable
fun ProfileCardItem(
    profile: Profile,
    isActive: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_card_${profile.profileId}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (isActive) Color(0xFF1E293B) else SlateNavy),
        border = androidx.compose.foundation.BorderStroke(
            if (isActive) 2.dp else 1.dp,
            if (isActive) ElectricBlue else SlateBorder
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (isActive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusBadge("ACTIVE SCHEME", color = StatusEmerald, showDot = true)
                    }
                }

                Row {
                    IconButton(onClick = onDuplicate) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Duplicate", tint = Color(0xFF94A3B8))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFF94A3B8))
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${profile.mappings.size} Key Mappings • ${profile.macros.size} Macro Combos",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isActive) {
                    OutlinedButton(
                        onClick = onSelect,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricBlue),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                    ) {
                        Text("Select Scheme")
                    }
                }

                Button(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = MidnightBlue)
                ) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Edit Canvas", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
