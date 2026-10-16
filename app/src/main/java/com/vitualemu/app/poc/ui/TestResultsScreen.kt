package com.vitualemu.app.poc.ui

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.vitualemu.app.poc.model.ExperimentRecord
import com.vitualemu.app.poc.model.ExperimentStatus
import com.vitualemu.app.poc.model.Gate0Verdict
import com.vitualemu.app.ui.theme.ElectricBlue
import com.vitualemu.app.ui.theme.MidnightBlue
import com.vitualemu.app.ui.theme.SlateBorder
import com.vitualemu.app.ui.theme.SlateNavy
import com.vitualemu.app.ui.theme.StatusAmber
import com.vitualemu.app.ui.theme.StatusCrimson
import com.vitualemu.app.ui.theme.StatusEmerald
import com.vitualemu.app.ui.theme.StatusSlate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestResultsScreen(
    viewModel: FeasibilityViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val experiments by viewModel.experiments.collectAsState()
    val deviceEnv by viewModel.deviceEnv.collectAsState()
    val selectedCandidate by viewModel.selectedCandidate.collectAsState()
    val overallStatus by viewModel.overallStatus.collectAsState()

    var showReportDialog by remember { mutableStateOf(false) }
    var reportText by remember { mutableStateOf("") }

    val experimentList = experiments.values.sortedBy { it.experimentId.code }
    val passedCount = experimentList.count { it.status == ExperimentStatus.PASS }
    val failedCount = experimentList.count { it.status == ExperimentStatus.FAIL }
    val blockedCount = experimentList.count { it.status == ExperimentStatus.BLOCKED || it.status == ExperimentStatus.NOT_TESTED }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Gate 0 Physical Test Matrix",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${selectedCandidate.displayName} Evidence",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricBlue
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            val report = viewModel.runner.generateRedactedReport()
                            reportText = buildRedactedReportString(report)
                            showReportDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = MidnightBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(end = 8.dp).testTag("export_report_button")
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export Report", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SlateNavy)
            )
        },
        containerColor = MidnightBlue
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))

                // Summary scorecard card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SlateNavy),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "EXPERIMENT SCORECARD (15 TOTAL)",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8),
                                fontWeight = FontWeight.Bold
                            )
                            OverallStatusBadge(status = overallStatus)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("PASSED", style = MaterialTheme.typography.labelSmall, color = StatusEmerald, fontWeight = FontWeight.Bold)
                                Text("$passedCount", style = MaterialTheme.typography.titleLarge, color = StatusEmerald, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("FAILED", style = MaterialTheme.typography.labelSmall, color = StatusCrimson, fontWeight = FontWeight.Bold)
                                Text("$failedCount", style = MaterialTheme.typography.titleLarge, color = StatusCrimson, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("PENDING / BLOCKED", style = MaterialTheme.typography.labelSmall, color = StatusAmber, fontWeight = FontWeight.Bold)
                                Text("$blockedCount", style = MaterialTheme.typography.titleLarge, color = StatusAmber, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Target Hardware: ${deviceEnv.deviceModel} • ${deviceEnv.osBuild}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }

            item {
                Text(
                    text = "RECORDED EVIDENCE MATRIX",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Bold
                )
            }

            items(experimentList, key = { it.experimentId.code }) { item ->
                ExperimentRecordCard(record = item)
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Redacted Feasibility Report", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Technical audit report with zero secrets, pairing codes, or tokens included:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCBD5E1)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reportText,
                        onValueChange = {},
                        readOnly = true,
                        maxLines = 12,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("VitualEmu Phase 0 Report", reportText))
                        showReportDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = MidnightBlue)
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy Report")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showReportDialog = false }) {
                    Text("Close")
                }
            },
            containerColor = SlateNavy
        )
    }
}

@Composable
fun ExperimentRecordCard(record: ExperimentRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SlateNavy),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = record.experimentId.code,
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricBlue,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = record.experimentId.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                ExperimentStatusBadge(status = record.status)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Category: ${record.experimentId.category}",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF94A3B8)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = record.evidenceNotes,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCBD5E1)
                )
            }
        }
    }
}

@Composable
fun ExperimentStatusBadge(status: ExperimentStatus) {
    val (color, text) = when (status) {
        ExperimentStatus.PASS -> StatusEmerald to "PASS"
        ExperimentStatus.FAIL -> StatusCrimson to "FAIL"
        ExperimentStatus.BLOCKED -> StatusAmber to "BLOCKED"
        ExperimentStatus.RUNNING -> StatusAmber to "RUNNING"
        ExperimentStatus.NOT_TESTED -> StatusSlate to "NOT TESTED"
    }

    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

fun buildRedactedReportString(report: com.vitualemu.app.poc.model.RedactedFeasibilityReport): String {
    val sb = StringBuilder()
    sb.appendLine("==================================================")
    sb.appendLine("VITUALEMU PHASE 0 FEASIBILITY AUDIT REPORT")
    sb.appendLine("Generated: ${report.generatedAt}")
    sb.appendLine("Device: ${report.targetDevice}")
    sb.appendLine("OS / Build: ${report.osBuild}")
    sb.appendLine("Evaluated Bridge: ${report.bridgeCandidate}")
    sb.appendLine("Gate 0 Verdict: ${report.gate0Verdict.name}")
    sb.appendLine("Latency Metrics: ${report.latencyMetrics}")
    sb.appendLine("Summary: Passed: ${report.passedCount} | Failed: ${report.failedCount} | Blocked/Pending: ${report.blockedCount}")
    sb.appendLine("Safety: ${report.safetyObservations}")
    sb.appendLine("Recommendation: ${report.recommendation}")
    sb.appendLine("==================================================")
    sb.appendLine("EXPERIMENT EVIDENCE MATRIX:")
    report.experiments.forEach { e ->
        sb.appendLine("[${e.experimentId.code}] ${e.experimentId.title}: ${e.status.name}")
        sb.appendLine("  Evidence: ${e.evidenceNotes}")
    }
    sb.appendLine("==================================================")
    return sb.toString()
}
