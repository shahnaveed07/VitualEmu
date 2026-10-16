package com.vitualemu.app.domain.model

import java.util.UUID

enum class BridgeType(val displayName: String) {
    SHIZUKU("Shizuku (Privileged Binder)"),
    ROOTLESS_WIRELESS_DEBUGGING("Rootless Wireless Debugging (ADB)")
}

enum class BridgeHealthState {
    NOT_INITIALIZED,
    CONNECTING,
    PERMISSION_REVOKED,
    CONNECTED,
    DISCONNECTED,
    ERROR
}

data class BridgeStatus(
    val type: BridgeType = BridgeType.SHIZUKU,
    val state: BridgeHealthState = BridgeHealthState.NOT_INITIALIZED,
    val isAuthorized: Boolean = false,
    val lastLatencyMs: Long = 0L,
    val detailMessage: String = "Bridge not started"
)

enum class MappingType(val label: String) {
    TAP("Tap Button"),
    HOLD("Hold Button"),
    WASD_STICK("WASD Movement Joystick"),
    AIM_LOOK("Aim / Camera Look Region"),
    SWIPE("Swipe / Flick"),
    MACRO_TRIGGER("Macro Execution Trigger")
}

enum class SwipeDirection {
    UP, DOWN, LEFT, RIGHT
}

data class Mapping(
    val id: String = UUID.randomUUID().toString(),
    val profileId: String,
    val name: String,
    val type: MappingType,
    val keyCode: Int = 0,
    val keyLabel: String = "",
    val normalizedX: Float = 0.5f,
    val normalizedY: Float = 0.5f,
    val normalizedWidth: Float = 0.12f,
    val normalizedHeight: Float = 0.12f,
    val sensitivity: Float = 1.0f,
    val isToggle: Boolean = false,
    val macroId: String? = null
)

data class MacroStep(
    val id: String = UUID.randomUUID().toString(),
    val macroId: String,
    val stepIndex: Int,
    val actionType: String = "TAP",
    val normalizedX: Float,
    val normalizedY: Float,
    val delayMs: Long = 50L,
    val holdDurationMs: Long = 50L
)

data class Macro(
    val id: String = UUID.randomUUID().toString(),
    val profileId: String,
    val name: String,
    val triggerKeyCode: Int = 0,
    val triggerKeyLabel: String = "",
    val steps: List<MacroStep> = emptyList(),
    val isLooping: Boolean = false
)

data class Profile(
    val profileId: String = UUID.randomUUID().toString(),
    val gameId: String,
    val name: String,
    val schemaVersion: Int = 1,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val mappings: List<Mapping> = emptyList(),
    val macros: List<Macro> = emptyList()
)

data class ProfileSnapshot(
    val id: String = UUID.randomUUID().toString(),
    val profileId: String,
    val snapshotName: String,
    val reason: String,
    val checksum: String,
    val serializedJson: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class Game(
    val gameId: String = UUID.randomUUID().toString(),
    val packageName: String,
    val displayName: String,
    val iconUri: String? = null,
    val isTargeted: Boolean = true,
    val addedAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long? = null
)

enum class DeviceType {
    KEYBOARD,
    MOUSE,
    TOUCHPAD,
    GAMEPAD,
    UNKNOWN
}

data class DeviceInfo(
    val id: Int,
    val name: String,
    val type: DeviceType,
    val isConnected: Boolean = true,
    val isPhysical: Boolean = true,
    val vendorId: Int = 0,
    val productId: Int = 0
)

data class PreflightResult(
    val isReady: Boolean,
    val bridgeReady: Boolean,
    val overlayReady: Boolean,
    val devicesConnected: Boolean,
    val profileValid: Boolean,
    val warnings: List<String> = emptyList(),
    val blockingErrors: List<String> = emptyList()
)

enum class SessionState {
    STOPPED,
    PREFLIGHT,
    READY,
    ACTIVE,
    PAUSED_RESET,
    SAFE_STOP
}
