package com.dsc.virtualemu.domain.model

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

data class InputBridgeCapabilities(
    val maxPointers: Int = 10,
    val supportsMultiTouch: Boolean = true,
    val supportsRelativeMouseLook: Boolean = true,
    val supportsKeyInjection: Boolean = true,
    val minDispatchIntervalMs: Long = 4L
)

data class BridgeStatus(
    val type: BridgeType = BridgeType.SHIZUKU,
    val state: BridgeHealthState = BridgeHealthState.NOT_INITIALIZED,
    val isAuthorized: Boolean = false,
    val lastLatencyMs: Long = 0L,
    val detailMessage: String = "Bridge not started",
    val activeDaemonPort: Int? = null,
    val capabilities: InputBridgeCapabilities = InputBridgeCapabilities()
)

enum class TouchAction {
    DOWN,
    MOVE,
    UP,
    CANCEL,
    POINTER_DOWN,
    POINTER_UP
}

enum class MappingType(val label: String) {
    TAP("Tap Button"),
    HOLD("Hold Button"),
    WASD_STICK("WASD Movement Joystick"),
    AIM_LOOK("Aim / Camera Look Region"),
    FREE_LOOK_EYE("Free Look (Eye Button)"),
    FIRE_WEAPON("Primary Fire / Smart Drag"),
    SWIPE("Swipe / Flick"),
    TAP_REPEAT("Rapid Tap Repeat"),
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
    val altKeyCode: Int = 0,
    val altKeyLabel: String = "",
    val normalizedX: Float = 0.5f,
    val normalizedY: Float = 0.5f,
    val endNormalizedX: Float = 0.5f,
    val endNormalizedY: Float = 0.35f,
    val normalizedWidth: Float = 0.12f,
    val normalizedHeight: Float = 0.12f,
    val sensitivity: Float = 1.0f,
    val sensitivityRatioY: Float = 1.0f,
    val isToggle: Boolean = false,
    val repeatDelayMs: Long = 50L,
    val repeatUntilKeyUp: Boolean = true,
    val guidanceCategory: String = "",
    val guidanceText: String = "",
    val macroId: String? = null
)

enum class MacroTriggerMode(val label: String) {
    ON_PRESS("On Press (Key Down)"),
    ON_RELEASE("On Release (Key Up)"),
    WHILE_HELD("While Held (Bounded Loop)"),
    PRESS_AND_RELEASE("Press & Release Dual"),
    CHORD("Key Chord (Modifier + Key)")
}

enum class MacroStepType(val label: String) {
    TAP("Tap Coordinate"),
    PRESS_HOLD("Press & Hold"),
    RELEASE("Release Pointer"),
    SWIPE("Swipe / Drag"),
    WAIT("Wait Delay")
}

data class MacroStep(
    val id: String = UUID.randomUUID().toString(),
    val macroId: String,
    val stepIndex: Int,
    val actionType: String = "TAP",
    val normalizedX: Float,
    val normalizedY: Float,
    val endNormalizedX: Float = normalizedX,
    val endNormalizedY: Float = normalizedY,
    val delayMs: Long = 50L,
    val holdDurationMs: Long = 50L
)

data class Macro(
    val id: String = UUID.randomUUID().toString(),
    val profileId: String,
    val name: String,
    val triggerKeyCode: Int = 0,
    val triggerKeyLabel: String = "",
    val chordModifierKeyCode: Int = 0,
    val triggerMode: MacroTriggerMode = MacroTriggerMode.ON_PRESS,
    val steps: List<MacroStep> = emptyList(),
    val isLooping: Boolean = false,
    val maxLoopIterations: Int = 10
)

data class MouseLookConfig(
    val toggleKeyCode: Int = 68, // KEYCODE_GRAVE (~) or Right Click
    val toggleKeyLabel: String = "GRAVE (~) / RClick",
    val sensitivityX: Float = 1.2f,
    val sensitivityY: Float = 1.0f,
    val invertX: Boolean = false,
    val invertY: Boolean = false,
    val smoothing: Float = 0.25f,
    val acceleration: Float = 0.10f,
    val autoEdgeReset: Boolean = true,
    val edgeThreshold: Float = 0.08f,
    val centerNormalizedX: Float = 0.72f,
    val centerNormalizedY: Float = 0.48f,
    val freeLookEnabled: Boolean = true,
    val freeLookKeyCode: Int = 57, // KEYCODE_ALT_LEFT
    val freeLookKeyLabel: String = "ALT_LEFT",
    val freeLookNormalizedX: Float = 0.82f,
    val freeLookNormalizedY: Float = 0.26f,
    val smartDragEnabled: Boolean = false,
    val smartDragVerticalSpeed: Float = 0.04f,
    val smartDragHorizontalSpeed: Float = 0.0f
)

data class Profile(
    val profileId: String = UUID.randomUUID().toString(),
    val gameId: String,
    val name: String,
    val schemaVersion: Int = 2,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val mappings: List<Mapping> = emptyList(),
    val macros: List<Macro> = emptyList(),
    val mouseLookConfig: MouseLookConfig = MouseLookConfig()
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
