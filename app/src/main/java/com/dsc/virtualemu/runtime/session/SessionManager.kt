package com.dsc.virtualemu.runtime.session

import com.dsc.virtualemu.domain.model.BridgeStatus
import com.dsc.virtualemu.domain.model.Game
import com.dsc.virtualemu.domain.model.PreflightResult
import com.dsc.virtualemu.domain.model.Profile
import com.dsc.virtualemu.domain.model.SessionState
import com.dsc.virtualemu.domain.usecase.HeldInputRegistry
import com.dsc.virtualemu.domain.usecase.PreflightCheckUseCase
import com.dsc.virtualemu.platform.devices.DeviceManager
import com.dsc.virtualemu.runtime.bridge.InputBridgeAdapter
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

    suspend fun startSession(bridgeAdapter: InputBridgeAdapter, sessionId: String = "default_session"): Boolean {
        if (_sessionState.value != SessionState.READY && _sessionState.value != SessionState.PAUSED_RESET) {
            return false
        }
        val initialized = bridgeAdapter.initialize()
        if (!initialized) {
            _sessionState.value = SessionState.STOPPED
            return false
        }
        val started = bridgeAdapter.startSession(sessionId)
        if (!started) {
            _sessionState.value = SessionState.STOPPED
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
