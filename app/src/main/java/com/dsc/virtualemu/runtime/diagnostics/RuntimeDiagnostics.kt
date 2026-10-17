package com.dsc.virtualemu.runtime.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DiagnosticsSnapshot(
    val totalInjectedTouches: Long = 0L,
    val totalMacroExecutions: Long = 0L,
    val totalSafeStopEvents: Int = 0,
    val lastDispatchLatencyMs: Long = 0L,
    val recentEvents: List<String> = emptyList()
)

class RuntimeDiagnostics {
    private val _snapshot = MutableStateFlow(DiagnosticsSnapshot())
    val snapshot: StateFlow<DiagnosticsSnapshot> = _snapshot.asStateFlow()

    fun recordTouchDispatch(latencyMs: Long, description: String) {
        val current = _snapshot.value
        val updatedEvents = (listOf(description) + current.recentEvents).take(25)
        _snapshot.value = current.copy(
            totalInjectedTouches = current.totalInjectedTouches + 1L,
            lastDispatchLatencyMs = latencyMs,
            recentEvents = updatedEvents
        )
    }

    fun recordMacroExecution(macroName: String) {
        val current = _snapshot.value
        val updatedEvents = (listOf("MACRO: $macroName") + current.recentEvents).take(25)
        _snapshot.value = current.copy(
            totalMacroExecutions = current.totalMacroExecutions + 1L,
            recentEvents = updatedEvents
        )
    }

    fun recordSafeStop(reason: String) {
        val current = _snapshot.value
        val updatedEvents = (listOf("SAFE_STOP: $reason") + current.recentEvents).take(25)
        _snapshot.value = current.copy(
            totalSafeStopEvents = current.totalSafeStopEvents + 1,
            recentEvents = updatedEvents
        )
    }

    fun clear() {
        _snapshot.value = DiagnosticsSnapshot()
    }
}
