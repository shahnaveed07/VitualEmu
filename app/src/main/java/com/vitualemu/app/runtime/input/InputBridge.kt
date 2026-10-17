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
    suspend fun executeMacroStep(step: MacroStep, sessionId: String): Boolean
    suspend fun cancelAll(sessionId: String)
    suspend fun releaseHeldInputs(sessionId: String)
    suspend fun stopSession(sessionId: String)
}
