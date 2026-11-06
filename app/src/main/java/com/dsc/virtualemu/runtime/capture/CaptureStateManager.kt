package com.dsc.virtualemu.runtime.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the lifecycle state of the physical input capture subsystem.
 *
 * All state transitions that exit an active capture state trigger the
 * [onNeutralizeRequired] callback so the caller can release all held inputs.
 */
class CaptureStateManager(
    private val onNeutralizeRequired: (reason: String) -> Unit = {}
) {
    private val _state = MutableStateFlow(CaptureState.NOT_CONFIGURED)
    val state: StateFlow<CaptureState> = _state.asStateFlow()

    private val _sourceStatus = MutableStateFlow(CaptureSourceStatus())
    val sourceStatus: StateFlow<CaptureSourceStatus> = _sourceStatus.asStateFlow()

    private val activeStates = setOf(
        CaptureState.CAPTURING,
        CaptureState.PAUSED,
        CaptureState.READY
    )

    fun transitionTo(newState: CaptureState, reason: String = "") {
        val old = _state.value
        if (old == newState) return

        // Neutralize held inputs when leaving any active capture state
        if (old == CaptureState.CAPTURING || old == CaptureState.PAUSED) {
            onNeutralizeRequired(reason.ifBlank { "Capture state: $old → $newState" })
        }

        _state.value = newState
    }

    fun updateSourceStatus(status: CaptureSourceStatus) {
        _sourceStatus.value = status
    }

    /**
     * Evaluate available sources and set the appropriate initial state.
     */
    fun evaluateReadiness(
        accessibilityEnabled: Boolean,
        shizukuAvailable: Boolean
    ) {
        val keyboardSource = when {
            accessibilityEnabled -> CaptureSource.ACCESSIBILITY_SERVICE
            shizukuAvailable -> CaptureSource.SHIZUKU_GETEVENT
            else -> CaptureSource.NONE
        }
        val mouseSource = if (shizukuAvailable) CaptureSource.SHIZUKU_GETEVENT else CaptureSource.NONE

        val status = CaptureSourceStatus(
            keyboardSource = keyboardSource,
            mouseSource = mouseSource,
            accessibilityServiceEnabled = accessibilityEnabled,
            shizukuGeteventAvailable = shizukuAvailable,
            detailMessage = buildStatusMessage(keyboardSource, mouseSource)
        )
        _sourceStatus.value = status

        val currentState = _state.value
        if (currentState == CaptureState.NOT_CONFIGURED ||
            currentState == CaptureState.PERMISSION_REQUIRED
        ) {
            if (keyboardSource != CaptureSource.NONE || mouseSource != CaptureSource.NONE) {
                _state.value = CaptureState.READY
            } else {
                _state.value = CaptureState.NOT_CONFIGURED
            }
        }
    }

    fun startCapturing() {
        if (_state.value == CaptureState.READY || _state.value == CaptureState.PAUSED) {
            _state.value = CaptureState.CAPTURING
        }
    }

    fun pauseCapturing(reason: String = "Paused") {
        if (_state.value == CaptureState.CAPTURING) {
            onNeutralizeRequired(reason)
            _state.value = CaptureState.PAUSED
        }
    }

    fun stopCapturing(reason: String = "Stopped") {
        val old = _state.value
        if (old == CaptureState.CAPTURING || old == CaptureState.PAUSED) {
            onNeutralizeRequired(reason)
        }
        _state.value = CaptureState.READY
    }

    fun reportDeviceDisconnected(reason: String) {
        if (_state.value == CaptureState.CAPTURING) {
            onNeutralizeRequired(reason)
            _state.value = CaptureState.DEVICE_DISCONNECTED
        }
    }

    fun reportPermissionRevoked(reason: String) {
        val old = _state.value
        if (old in activeStates || old == CaptureState.CAPTURING) {
            onNeutralizeRequired(reason)
        }
        _state.value = CaptureState.PERMISSION_REVOKED
    }

    fun reportError(reason: String) {
        val old = _state.value
        if (old == CaptureState.CAPTURING || old == CaptureState.PAUSED) {
            onNeutralizeRequired(reason)
        }
        _state.value = CaptureState.ERROR
    }

    fun isCapturing(): Boolean = _state.value == CaptureState.CAPTURING

    private fun buildStatusMessage(keyboard: CaptureSource, mouse: CaptureSource): String {
        val kbLabel = when (keyboard) {
            CaptureSource.ACCESSIBILITY_SERVICE -> "Accessibility"
            CaptureSource.SHIZUKU_GETEVENT -> "Shizuku"
            CaptureSource.NONE -> "None"
        }
        val mouseLabel = when (mouse) {
            CaptureSource.SHIZUKU_GETEVENT -> "Shizuku"
            CaptureSource.ACCESSIBILITY_SERVICE -> "Accessibility (no relative)"
            CaptureSource.NONE -> "None"
        }
        return "Keyboard: $kbLabel • Mouse: $mouseLabel"
    }
}
