package com.dsc.virtualemu.runtime.bridge

import com.dsc.virtualemu.domain.model.BridgeStatus
import com.dsc.virtualemu.domain.model.BridgeType
import com.dsc.virtualemu.platform.activation.rootless.RootlessWirelessAdbBridgeAdapter
import com.dsc.virtualemu.platform.activation.shizuku.ShizukuBridgeAdapter
import com.dsc.virtualemu.runtime.input.InputBridge
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
