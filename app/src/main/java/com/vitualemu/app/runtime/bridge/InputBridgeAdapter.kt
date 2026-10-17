package com.vitualemu.app.runtime.bridge

import com.vitualemu.app.domain.model.BridgeStatus
import com.vitualemu.app.domain.model.BridgeType
import com.vitualemu.app.platform.activation.rootless.RootlessWirelessAdbBridgeAdapter
import com.vitualemu.app.platform.activation.shizuku.ShizukuBridgeAdapter
import com.vitualemu.app.runtime.input.InputBridge
import kotlinx.coroutines.flow.StateFlow

interface InputBridgeAdapter : InputBridge {
    val bridgeType: BridgeType
    override val statusFlow: StateFlow<BridgeStatus>

    suspend fun checkHealth(): BridgeStatus
    suspend fun initialize(): Boolean
    suspend fun injectTouch(action: String, x: Float, y: Float, pointerId: Int = 0): Boolean
    suspend fun injectKey(keyCode: Int, isDown: Boolean): Boolean
    fun shutdown()
}

typealias ShizukuBridge = ShizukuBridgeAdapter
typealias RootlessAdbBridge = RootlessWirelessAdbBridgeAdapter
