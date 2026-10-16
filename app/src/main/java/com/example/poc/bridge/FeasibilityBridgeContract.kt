package com.example.poc.bridge

import com.example.poc.model.BridgeConnectionState
import com.example.poc.model.CandidateBridgeId
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

data class VerifiedBridgeCapabilities(
    val canInjectSingleTap: Boolean = false,
    val canInjectMultiTouchJoystick: Boolean = false,
    val canCaptureRelativeMouseLook: Boolean = false,
    val canExecuteTouchRegionAim: Boolean = false,
    val canReleaseHeldInputsReliably: Boolean = false,
    val requiresActivePairingOnReboot: Boolean = true
)

interface FeasibilityBridgeContract {
    val bridgeId: CandidateBridgeId
    suspend fun checkConnectionAndHealth(): BridgeConnectionState
    suspend fun getVerifiedCapabilities(): VerifiedBridgeCapabilities
    suspend fun startTestSession(sessionId: String): Boolean
    suspend fun executeTestAction(actionType: String, normX: Float, normY: Float): Boolean
    suspend fun cancelActiveActions()
    suspend fun releaseHeldInputs(): Int
    suspend fun stopTestSession(): Boolean
    fun getFailureReason(): String?
}

class SafeStopHeldRegistry {
    private val heldKeys = ConcurrentHashMap.newKeySet<Int>()
    private val activePointers = ConcurrentHashMap<Int, Pair<Float, Float>>()

    fun recordKeyDown(keyCode: Int) {
        heldKeys.add(keyCode)
    }

    fun recordKeyUp(keyCode: Int) {
        heldKeys.remove(keyCode)
    }

    fun recordPointerDown(pointerId: Int, x: Float, y: Float) {
        activePointers[pointerId] = Pair(x, y)
    }

    fun recordPointerUp(pointerId: Int) {
        activePointers.remove(pointerId)
    }

    fun getHeldCount(): Int = heldKeys.size + activePointers.size

    fun releaseAll(): Int {
        val totalReleased = heldKeys.size + activePointers.size
        heldKeys.clear()
        activePointers.clear()
        return totalReleased
    }
}

class CandidateA_WirelessAdbBridge(
    private val heldRegistry: SafeStopHeldRegistry
) : FeasibilityBridgeContract {
    override val bridgeId = CandidateBridgeId.ROOTLESS_WIRELESS_DEBUGGING
    private var isConnected = false
    private var isSessionActive = false
    private var lastFailureReason: String? = null

    override suspend fun checkConnectionAndHealth(): BridgeConnectionState {
        delay(35) // Simulated local loopback TCP latency
        isConnected = true
        return BridgeConnectionState.AUTHORIZED
    }

    override suspend fun getVerifiedCapabilities(): VerifiedBridgeCapabilities {
        return VerifiedBridgeCapabilities(
            canInjectSingleTap = true,
            canInjectMultiTouchJoystick = false, // Rootless adb input tap lacks concurrent multitouch
            canCaptureRelativeMouseLook = false, // Pointer capture restricted when 3rd-party game owns focus
            canExecuteTouchRegionAim = true, // Sequential touch delta swipe fallback
            canReleaseHeldInputsReliably = true,
            requiresActivePairingOnReboot = true
        )
    }

    override suspend fun startTestSession(sessionId: String): Boolean {
        if (!isConnected) {
            lastFailureReason = "Wireless Debugging port not paired or unreachable."
            return false
        }
        isSessionActive = true
        return true
    }

    override suspend fun executeTestAction(actionType: String, normX: Float, normY: Float): Boolean {
        if (!isSessionActive) return false
        heldRegistry.recordPointerDown(1, normX, normY)
        delay(18) // Process invocation overhead
        heldRegistry.recordPointerUp(1)
        return true
    }

    override suspend fun cancelActiveActions() {
        heldRegistry.releaseAll()
    }

    override suspend fun releaseHeldInputs(): Int {
        return heldRegistry.releaseAll()
    }

    override suspend fun stopTestSession(): Boolean {
        heldRegistry.releaseAll()
        isSessionActive = false
        return true
    }

    override fun getFailureReason(): String? = lastFailureReason
}

class CandidateB_ShizukuBridge(
    private val heldRegistry: SafeStopHeldRegistry
) : FeasibilityBridgeContract {
    override val bridgeId = CandidateBridgeId.SHIZUKU
    private var isConnected = false
    private var isSessionActive = false
    private var lastFailureReason: String? = null

    override suspend fun checkConnectionAndHealth(): BridgeConnectionState {
        delay(12) // Binder IPC latency
        isConnected = true
        return BridgeConnectionState.AUTHORIZED
    }

    override suspend fun getVerifiedCapabilities(): VerifiedBridgeCapabilities {
        return VerifiedBridgeCapabilities(
            canInjectSingleTap = true,
            canInjectMultiTouchJoystick = true, // Privileged binder supports multi-pointer MotionEvent
            canCaptureRelativeMouseLook = false, // Note: Android OS delivers pointer capture only to focused window
            canExecuteTouchRegionAim = true, // Verified fallback
            canReleaseHeldInputsReliably = true,
            requiresActivePairingOnReboot = true
        )
    }

    override suspend fun startTestSession(sessionId: String): Boolean {
        if (!isConnected) {
            lastFailureReason = "Shizuku service not running or permission denied in Developer Options."
            return false
        }
        isSessionActive = true
        return true
    }

    override suspend fun executeTestAction(actionType: String, normX: Float, normY: Float): Boolean {
        if (!isSessionActive) return false
        heldRegistry.recordPointerDown(1, normX, normY)
        delay(6) // Low-latency binder transaction
        heldRegistry.recordPointerUp(1)
        return true
    }

    override suspend fun cancelActiveActions() {
        heldRegistry.releaseAll()
    }

    override suspend fun releaseHeldInputs(): Int {
        return heldRegistry.releaseAll()
    }

    override suspend fun stopTestSession(): Boolean {
        heldRegistry.releaseAll()
        isSessionActive = false
        return true
    }

    override fun getFailureReason(): String? = lastFailureReason
}
