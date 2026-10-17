package com.dsc.virtualemu.ui.screens.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsc.virtualemu.domain.mapping.ParsedCfgData
import com.dsc.virtualemu.domain.model.Game
import com.dsc.virtualemu.domain.model.Profile
import com.dsc.virtualemu.ui.components.StatusBadge
import com.dsc.virtualemu.ui.theme.ElectricBlue
import com.dsc.virtualemu.ui.theme.MidnightBlue
import com.dsc.virtualemu.ui.theme.SlateBorder
import com.dsc.virtualemu.ui.theme.SlateNavy
import com.dsc.virtualemu.ui.theme.StatusAmber
import com.dsc.virtualemu.ui.theme.StatusEmerald

@Composable
fun ImportPreviewDialog(
    parsedData: ParsedCfgData,
    activeGame: Game?,
    onConfirmImport: (List<Profile>) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedSchemeMap = remember(parsedData) {
        mutableStateMapOf<String, Boolean>().apply {
            parsedData.profiles.forEach { p ->
                put(p.profileId, true)
            }
        }
    }

    val selectedCount = selectedSchemeMap.values.count { it }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Import Keymapping Schemes",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Target Game: ${activeGame?.displayName ?: "Selected Game"}",
                    color = ElectricBlue,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Summary pill
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                        .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Found ${parsedData.profiles.size} scheme(s)",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Total Controls: ${parsedData.totalControlCount} (v${parsedData.parserVersion})",
                                color = Color(0xFF94A3B8),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        StatusBadge("${selectedCount} Selected", color = StatusEmerald, showDot = false)
                    }
                }

                Text(
                    text = "Select Schemes to Import:",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Bold
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(parsedData.profiles, key = { it.profileId }) { profile ->
                        val isChecked = selectedSchemeMap[profile.profileId] ?: false
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                .border(1.dp, if (isChecked) ElectricBlue else SlateBorder, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedSchemeMap[profile.profileId] = checked
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = ElectricBlue,
                                        uncheckedColor = Color(0xFF64748B),
                                        checkmarkColor = MidnightBlue
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = profile.name,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        if (profile.isFavorite) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            StatusBadge("Default", color = ElectricBlue, showDot = false)
                                        }
                                    }
                                    Text(
                                        text = "${profile.mappings.size} mappings • ${profile.macros.size} macros",
                                        color = Color(0xFF94A3B8),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }

                if (parsedData.warnings.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(StatusAmber.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .border(1.dp, StatusAmber.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = StatusAmber, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Parsing Notices (${parsedData.warnings.size}):", color = StatusAmber, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            }
                            for (warn in parsedData.warnings.take(3)) {
                                Text("• $warn", color = Color(0xFFFDE68A), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val toImport = parsedData.profiles.filter { selectedSchemeMap[it.profileId] == true }
                    if (toImport.isNotEmpty()) {
                        onConfirmImport(toImport)
                    }
                },
                enabled = selectedCount > 0,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = MidnightBlue),
                modifier = Modifier.testTag("confirm_import_schemes_button")
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Import ($selectedCount)", fontWeight = FontWeight.Bold)
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
