package com.dsc.virtualemu.ui.screens.activation

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsc.virtualemu.domain.model.BridgeType
import com.dsc.virtualemu.ui.VitualEmuViewModel

@Composable
fun ActivationModeDialog(
    viewModel: VitualEmuViewModel,
    onDismiss: () -> Unit,
    onOpenWizard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimBg = Color.Black.copy(alpha = 0.65f)
    val panelIndigo = Color(0xFF281845)
    val rowPeriwinkle = Color(0xFF4C3A7A)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(dimBg)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        // Modal Container (Consumes clicks so clicking inside dialog won't dismiss)
        Box(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(18.dp))
                .background(panelIndigo)
                .border(1.dp, Color(0xFF3B2A68), RoundedCornerShape(18.dp))
                .clickable(enabled = false, onClick = {})
                .padding(22.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Bar: Title & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Activation Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        fontStyle = FontStyle.Italic,
                        color = Color.White,
                        fontSize = 20.sp
                    )

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E1438))
                            .clickable(onClick = onDismiss)
                            .testTag("activation_mode_close_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Explanatory Subtitle
                Text(
                    text = "Select your preferred activation method according to your device environment.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Scrollable Options List
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Option 1: Wired / PC Activation
                    ActivationOptionRow(
                        title = "1. Wired / PC Activation (Port 5555)",
                        rowColor = rowPeriwinkle,
                        onClick = {
                            viewModel.setBridge(BridgeType.ROOTLESS_WIRELESS_DEBUGGING)
                            viewModel.showFeedback("Wired / PC ADB Activation selected (Port 5555)")
                            onOpenWizard()
                        }
                    )

                    // Option 2: Wireless Debugging Activation
                    ActivationOptionRow(
                        title = "2. Wireless Debugging Activation (ADB)",
                        rowColor = rowPeriwinkle,
                        onClick = {
                            viewModel.setBridge(BridgeType.ROOTLESS_WIRELESS_DEBUGGING)
                            viewModel.showFeedback("Wireless ADB Activation selected")
                            onOpenWizard()
                        }
                    )

                    // Option 3: Root Activation
                    ActivationOptionRow(
                        title = "3. Root / SU Activation (via Shizuku / SU)",
                        rowColor = rowPeriwinkle,
                        onClick = {
                            viewModel.setBridge(BridgeType.SHIZUKU)
                            viewModel.showFeedback("Root / Shizuku Activation selected")
                            onOpenWizard()
                        }
                    )

                    // Option 4: Shizuku Manager
                    ActivationOptionRow(
                        title = "4. Shizuku Manager",
                        rowColor = rowPeriwinkle,
                        onClick = {
                            viewModel.setBridge(BridgeType.SHIZUKU)
                            viewModel.requestShizukuPermission()
                            onOpenWizard()
                        }
                    )

                    // Option 5: Shizuku (Legacy Mode)
                    ActivationOptionRow(
                        title = "5. Shizuku (Legacy Mode)",
                        rowColor = rowPeriwinkle,
                        onClick = {
                            viewModel.setBridge(BridgeType.SHIZUKU)
                            viewModel.showFeedback("Shizuku Legacy Mode selected")
                            onOpenWizard()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivationOptionRow(
    title: String,
    rowColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(rowColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("activation_option_${title.take(15)}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFFD8B4FE),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
