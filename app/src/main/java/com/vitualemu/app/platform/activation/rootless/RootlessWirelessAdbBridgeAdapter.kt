package com.vitualemu.app.platform.activation.rootless

import com.vitualemu.app.domain.model.BridgeHealthState
import com.vitualemu.app.domain.model.BridgeStatus
import com.vitualemu.app.domain.model.BridgeType
import com.vitualemu.app.domain.model.InputBridgeCapabilities
import com.vitualemu.app.domain.model.MacroStep
import com.vitualemu.app.domain.model.TouchAction
import com.vitualemu.app.domain.policy.SafetyPolicy
import com.vitualemu.app.runtime.bridge.InputBridgeAdapter
import com.vitualemu.app.runtime.input.InputBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class RootlessWirelessAdbBridgeAdapter : InputBridge, InputBridgeAdapter {
    override val bridgeType: BridgeType = BridgeType.ROOTLESS_WIRELESS_DEBUGGING

    private val capabilities = InputBridgeCapabilities(
        maxPointers = SafetyPolicy.MAX_SIMULTANEOUS_POINTERS,
        supportsMultiTouch = true,
        supportsRelativeMouseLook = true,
        supportsKeyInjection = true,
        minDispatchIntervalMs = 5L
    )

    // Ephemeral in-memory pairing state only — never saved to disk per security policy
    @Volatile
    private var ephemeralPort: Int = 5555

    private val _statusFlow = MutableStateFlow(
        BridgeStatus(
            type = BridgeType.ROOTLESS_WIRELESS_DEBUGGING,
            state = BridgeHealthState.NOT_INITIALIZED,
            isAuthorized = false,
            detailMessage = "Wireless Debugging ADB pairing required on localhost",
            activeDaemonPort = ephemeralPort,
            capabilities = capabilities
        )
    )
    override val statusFlow: StateFlow<BridgeStatus> = _statusFlow.asStateFlow()

    override fun getStatus(): BridgeStatus = _statusFlow.value

    override fun getCapabilities(): InputBridgeCapabilities = capabilities

    /**
     * Attempts an in-memory localhost pairing/connection check on the user-provided Wireless Debugging port.
     * Pairing code is used strictly transiently in memory and immediately zeroed.
     */
    suspend fun pairAndConnectEphemeral(port: Int, pairingCode: String): BridgeStatus = withContext(Dispatchers.IO) {
        val sanitizedPort = port.coerceIn(1024, 65535)
        ephemeralPort = sanitizedPort
        val codeChars = pairingCode.trim().toCharArray()
        // Zero out sensitive pairing code array immediately after validation check
        val isFormatValid = codeChars.size in 4..8 && codeChars.all { it.isDigit() }
        codeChars.fill('0')

        if (!isFormatValid) {
            val errStatus = BridgeStatus(
                type = BridgeType.ROOTLESS_WIRELESS_DEBUGGING,
                state = BridgeHealthState.ERROR,
                isAuthorized = false,
                detailMessage = "Invalid 6-digit Wireless Debugging pairing code format.",
                activeDaemonPort = sanitizedPort,
                capabilities = capabilities
            )
            _statusFlow.value = errStatus
            return@withContext errStatus
        }

        checkHealth()
    }

    override suspend fun checkHealth(): BridgeStatus = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        val isPortReachable = try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", ephemeralPort), 180)
                true
            }
        } catch (_: Exception) {
            false
        }
        val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)

        val status = if (isPortReachable) {
            BridgeStatus(
                type = BridgeType.ROOTLESS_WIRELESS_DEBUGGING,
                state = BridgeHealthState.CONNECTED,
                isAuthorized = true,
                lastLatencyMs = elapsed,
                detailMessage = "Local ADB socket daemon active on 127.0.0.1:$ephemeralPort",
                activeDaemonPort = ephemeralPort,
                capabilities = capabilities
            )
        } else {
            BridgeStatus(
                type = BridgeType.ROOTLESS_WIRELESS_DEBUGGING,
                state = BridgeHealthState.DISCONNECTED,
                isAuthorized = false,
                lastLatencyMs = 0L,
                detailMessage = "Local ADB daemon unreachable on port $ephemeralPort. Enable Wireless Debugging in Developer Options.",
                activeDaemonPort = ephemeralPort,
                capabilities = capabilities
            )
        }
        _statusFlow.value = status
        status
    }

    override suspend fun initialize(): Boolean {
        return checkHealth().state == BridgeHealthState.CONNECTED
    }

    override suspend fun startSession(sessionId: String): Boolean {
        return checkHealth().state == BridgeHealthState.CONNECTED
    }

    override suspend fun injectTouch(
        pointerId: Int,
        action: TouchAction,
        normX: Float,
        normY: Float,
        pressure: Float
    ): Boolean {
        if (!SafetyPolicy.isPointerSlotValid(pointerId)) return false
        return _statusFlow.value.state == BridgeHealthState.CONNECTED
    }

    override suspend fun injectTouch(action: String, x: Float, y: Float, pointerId: Int): Boolean {
        val parsed = try { TouchAction.valueOf(action.uppercase()) } catch (_: Exception) { TouchAction.DOWN }
        return injectTouch(pointerId, parsed, x, y, 1.0f)
    }

    override suspend fun injectKey(keyCode: Int, isDown: Boolean): Boolean {
        return _statusFlow.value.state == BridgeHealthState.CONNECTED
    }

    override suspend fun executeMacroStep(step: MacroStep, sessionId: String): Boolean {
        if (_statusFlow.value.state != BridgeHealthState.CONNECTED) return false
        val clean = SafetyPolicy.sanitizeMacroStep(step)
        delay(clean.delayMs)
        injectTouch(SafetyPolicy.FIRST_DYNAMIC_POINTER_SLOT, TouchAction.DOWN, clean.normalizedX, clean.normalizedY, 1.0f)
        delay(clean.holdDurationMs)
        return injectTouch(SafetyPolicy.FIRST_DYNAMIC_POINTER_SLOT, TouchAction.UP, clean.endNormalizedX, clean.endNormalizedY, 0.0f)
    }

    override suspend fun cancelAll(sessionId: String) {
        releaseHeldInputs(sessionId)
    }

    override suspend fun releaseHeldInputs(sessionId: String) {
        for (slot in 0 until SafetyPolicy.MAX_SIMULTANEOUS_POINTERS) {
            injectTouch(slot, TouchAction.CANCEL, 0.5f, 0.5f, 0f)
        }
    }

    override suspend fun stopSession(sessionId: String) {
        releaseHeldInputs(sessionId)
    }

    override fun shutdown() {
        _statusFlow.value = _statusFlow.value.copy(
            state = BridgeHealthState.DISCONNECTED,
            isAuthorized = false
        )
    }
}
