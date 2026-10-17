package com.vitualemu.app.runtime.input

import com.vitualemu.app.domain.model.BridgeStatus
import com.vitualemu.app.domain.model.InputBridgeCapabilities
import com.vitualemu.app.domain.model.MacroStep
import com.vitualemu.app.domain.model.TouchAction
import kotlinx.coroutines.flow.StateFlow

interface InputBridge {
    val statusFlow: StateFlow<BridgeStatus>

    fun getStatus(): BridgeStatus
    fun getCapabilities(): InputBridgeCapabilities
    suspend fun startSession(sessionId: String): Boolean
    suspend fun injectTouch(
        pointerId: Int,
        action: TouchAction,
        normX: Float,
        normY: Float,
        pressure: Float = 1.0f
    ): Boolean

    /**
     * Dispatches a unified multi-pointer [CombinedTouchFrame] containing all currently active
     * pointers (`PointerProperties[]`, `PointerCoords[]`, and Linux evdev Protocol B commands).
     */
    suspend fun injectCombinedTouchFrame(frame: CombinedTouchFrame): Boolean {
        val activeEntry = frame.pointers.find { it.pointerId == frame.actionPointerId }
            ?: frame.pointers.firstOrNull()
        val normX = activeEntry?.normX ?: 0.5f
        val normY = activeEntry?.normY ?: 0.5f
        val pressure = activeEntry?.pressure ?: 1.0f
        return injectTouch(frame.actionPointerId, frame.resolvedTouchAction, normX, normY, pressure)
    }

    suspend fun executeMacroStep(step: MacroStep, sessionId: String): Boolean
    suspend fun cancelAll(sessionId: String)
    suspend fun releaseHeldInputs(sessionId: String)
    suspend fun stopSession(sessionId: String)
}
