package com.dsc.virtualemu.poc.model

enum class CandidateBridgeId(val displayName: String, val shortName: String) {
    ROOTLESS_WIRELESS_DEBUGGING("Rootless Wireless Debugging (ADB)", "Candidate A (ADB)"),
    SHIZUKU("Shizuku (Privileged Binder)", "Candidate B (Shizuku)")
}

enum class BridgeConnectionState(val label: String) {
    DISCONNECTED("Disconnected"),
    CONNECTING("Connecting..."),
    AUTHORIZED("Authorized & Connected"),
    PERMISSION_REVOKED("Permission Revoked"),
    FAILED("Connection Failed")
}

enum class FeasibilityExperimentId(val code: String, val title: String, val category: String) {
    ACTIVATION_AUTHORIZATION("EXP-01", "Activation & Authorization", "Bridge Setup"),
    KEYBOARD_KEY_EVENTS("EXP-02", "Keyboard Key Down/Up Events", "Input Detection"),
    MOUSE_MOVE_AND_BUTTONS("EXP-03", "Mouse Movement & Buttons", "Input Detection"),
    SCROLL_WHEEL_EVENTS("EXP-04", "Scroll-Wheel Events", "Input Detection"),
    FOREGROUND_TOUCH_INJECTION("EXP-05", "Foreground Game Touch Injection", "Execution"),
    GAME_MOVEMENT_ACTION("EXP-06", "Game Movement & Action (WASD + Tap)", "Execution"),
    RELATIVE_MOUSE_LOOK("EXP-07", "Relative Mouse-Look Feasibility", "Aim & Camera"),
    TOUCH_REGION_FALLBACK("EXP-08", "Touch-Region Aim Fallback", "Aim & Camera"),
    FOCUS_LOSS_AND_RETURN("EXP-09", "Focus Loss & Return", "Safety & Lifecycle"),
    BRIDGE_DISCONNECT_REVOCATION("EXP-10", "Bridge Disconnect & Permission Loss", "Safety & Lifecycle"),
    PERIPHERAL_HOTPLUG("EXP-11", "Keyboard/Mouse Unplug & Reconnect", "Peripherals"),
    SAFE_STOP_HELD_RELEASE("EXP-12", "Stop Behavior & Held-Input Release", "Safety & Lifecycle"),
    RECOVERY_EXPLICIT_RESTART("EXP-13", "Recovery & Explicit Restart", "Safety & Lifecycle"),
    REPEATABILITY_TRIALS("EXP-14", "Repeatability Over 20 Controlled Trials", "Reliability"),
    LATENCY_AND_JITTER("EXP-15", "Latency (p50/p95) & Jitter Benchmark", "Performance")
}

enum class ExperimentStatus(val label: String) {
    NOT_TESTED("Not Tested"),
    RUNNING("Running..."),
    PASS("PASS"),
    FAIL("FAIL"),
    BLOCKED("BLOCKED")
}

data class ExperimentRecord(
    val experimentId: FeasibilityExperimentId,
    val status: ExperimentStatus = ExperimentStatus.NOT_TESTED,
    val latencyMs: Long? = null,
    val p50Ms: Long? = null,
    val p95Ms: Long? = null,
    val jitterMs: Long? = null,
    val evidenceNotes: String = "No physical-device test run executed yet.",
    val timestamp: Long = System.currentTimeMillis()
)

data class DeviceEnvironment(
    val deviceModel: String = "Xiaomi Redmi Note 13 5G (Primary)",
    val androidVersion: String = "Android 15 (Target API 36+)",
    val osBuild: String = "Xiaomi HyperOS 2.0 (Redmi Note 13 5G)",
    val securityPatch: String = "2026-09-01",
    val usbHubModel: String = "USB-C Multiport OTG Hub (Wired Kbd + Mouse)",
    val isKeyboardConnected: Boolean = true,
    val keyboardName: String = "USB Gaming Keyboard (VID: 0x046d)",
    val isMouseConnected: Boolean = true,
    val mouseName: String = "USB Optical Mouse (VID: 0x1532)",
    val selectedTestGame: String = "Tactical FPS Target (Foreground)"
)

enum class Gate0Verdict(val label: String, val description: String) {
    BLOCKED("BLOCKED", "Physical-device evidence pending. Testing must be completed on target hardware before Phase 1."),
    PASS("PASS", "At least one bridge candidate proved reliable multi-touch injection and safe-stop."),
    FAIL("FAIL", "Evaluated bridges failed to perform foreground injection or release held inputs safely.")
}

data class RedactedFeasibilityReport(
    val generatedAt: String,
    val targetDevice: String,
    val osBuild: String,
    val bridgeCandidate: String,
    val gate0Verdict: Gate0Verdict,
    val latencyMetrics: String,
    val passedCount: Int,
    val failedCount: Int,
    val blockedCount: Int,
    val experiments: List<ExperimentRecord>,
    val safetyObservations: String,
    val recommendation: String
)
