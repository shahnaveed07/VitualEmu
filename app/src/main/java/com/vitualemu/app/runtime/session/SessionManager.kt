package com.vitualemu.app.runtime.session

import com.vitualemu.app.domain.model.BridgeStatus
import com.vitualemu.app.domain.model.Game
import com.vitualemu.app.domain.model.PreflightResult
import com.vitualemu.app.domain.model.Profile
import com.vitualemu.app.domain.model.SessionState
import com.vitualemu.app.domain.usecase.HeldInputRegistry
import com.vitualemu.app.domain.usecase.PreflightCheckUseCase
import com.vitualemu.app.platform.devices.DeviceManager
import com.vitualemu.app.runtime.bridge.InputBridgeAdapter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionManager(
    private val preflightUseCase: PreflightCheckUseCase = PreflightCheckUseCase()
) {
    private val _sessionState = MutableStateFlow(SessionState.STOPPED)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _preflightResult = MutableStateFlow<PreflightResult?>(null)
    val preflightResult: StateFlow<PreflightResult?> = _preflightResult.asStateFlow()

    fun runPreflight(
        bridgeStatus: BridgeStatus,
        hasOverlayPermission: Boolean,
        deviceManager: DeviceManager,
        game: Game?,
        profile: Profile?
    ): PreflightResult {
        _sessionState.value = SessionState.PREFLIGHT
        val result = preflightUseCase(
            bridgeStatus = bridgeStatus,
            hasOverlayPermission = hasOverlayPermission,
            connectedDevices = deviceManager.devicesFlow.value,
            selectedGame = game,
            activeProfile = profile
        )
        _preflightResult.value = result
        _sessionState.value = if (result.isReady) SessionState.READY else SessionState.STOPPED
        return result
    }

    fun startSession(bridgeAdapter: InputBridgeAdapter): Boolean {
        if (_sessionState.value != SessionState.READY && _sessionState.value != SessionState.PAUSED_RESET) {
            return false
        }
        _sessionState.value = SessionState.ACTIVE
        return true
    }

    fun pauseSession() {
        if (_sessionState.value == SessionState.ACTIVE) {
            HeldInputRegistry.releaseAll()
            _sessionState.value = SessionState.PAUSED_RESET
        }
    }

    /**
     * Emergency Safe Stop: Immediately clears all touches and resets state to STOPPED
     */
    fun emergencySafeStop() {
        HeldInputRegistry.releaseAll()
        _sessionState.value = SessionState.SAFE_STOP
        _sessionState.value = SessionState.STOPPED
    }

    fun onFocusLost() {
        // Critical lifecycle safety: when another window or system dialog appears
        HeldInputRegistry.releaseAll()
        if (_sessionState.value == SessionState.ACTIVE) {
            _sessionState.value = SessionState.PAUSED_RESET
        }
    }
}
