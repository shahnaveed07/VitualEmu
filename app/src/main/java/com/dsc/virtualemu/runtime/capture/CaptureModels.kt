package com.dsc.virtualemu.runtime.capture

/**
 * Identifies which system-level mechanism produced a captured physical input event.
 */
enum class CaptureSource {
    /** Android AccessibilityService onKeyEvent / onMotionEvent */
    ACCESSIBILITY_SERVICE,
    /** Shizuku UserService reading /dev/input via getevent */
    SHIZUKU_GETEVENT,
    /** No capture source active */
    NONE
}

/**
 * Discriminates the kind of raw hardware event captured from a physical peripheral.
 */
enum class PhysicalInputType {
    KEY_DOWN,
    KEY_UP,
    KEY_REPEAT,
    MOUSE_BUTTON_DOWN,
    MOUSE_BUTTON_UP,
    MOUSE_MOVE,
    MOUSE_WHEEL,
    DEVICE_CONNECTED,
    DEVICE_DISCONNECTED
}

/**
 * Unified event emitted by every capture source after normalization.
 *
 * Keyboard events populate [keyCode] and [isDown].
 * Mouse movement populates [deltaX] / [deltaY] (relative, in raw device units before
 * sensitivity scaling — the [MouseLookEngine] handles normalization and scaling).
 * Mouse wheel populates [wheelDelta].
 * Mouse buttons use [keyCode] with Linux BTN_LEFT (0x110), BTN_RIGHT (0x111), BTN_MIDDLE (0x112)
 * mapped to Android KEYCODE_BUTTON_L1 / KEYCODE_BUTTON_R1 equivalents.
 */
data class PhysicalInputEvent(
    val type: PhysicalInputType,
    val keyCode: Int = 0,
    val isDown: Boolean = false,
    val repeatCount: Int = 0,
    val deltaX: Float = 0f,
    val deltaY: Float = 0f,
    val wheelDelta: Float = 0f,
    val deviceId: Int = -1,
    val deviceName: String = "",
    val source: CaptureSource = CaptureSource.NONE,
    val timestampNs: Long = System.nanoTime(),
    val isSynthetic: Boolean = false
)

/**
 * Lifecycle state of the physical input capture subsystem.
 *
 * The UI must display the actual state — never show "Active" or "Ready" unless capture
 * is genuinely operational and has been verified by receiving at least one real event.
 */
enum class CaptureState {
    /** No capture source configured */
    NOT_CONFIGURED,
    /** A source is selected but the required permission has not been granted */
    PERMISSION_REQUIRED,
    /** The capture service / process is starting up */
    INITIALIZING,
    /** Capture is configured and ready but not actively forwarding events (Play Mode not started) */
    READY,
    /** Actively capturing hardware events and forwarding them to the mapping engine */
    CAPTURING,
    /** Play Mode paused — capture suspended, all held inputs released */
    PAUSED,
    /** The input device was physically disconnected during an active session */
    DEVICE_DISCONNECTED,
    /** The capture permission was revoked while capture was active */
    PERMISSION_REVOKED,
    /** SELinux or OS restriction prevents capture on this device */
    CAPTURE_UNAVAILABLE,
    /** Unexpected failure */
    ERROR
}

/**
 * Snapshot of what capture sources are currently available and active.
 */
data class CaptureSourceStatus(
    val keyboardSource: CaptureSource = CaptureSource.NONE,
    val mouseSource: CaptureSource = CaptureSource.NONE,
    val accessibilityServiceEnabled: Boolean = false,
    val shizukuGeteventAvailable: Boolean = false,
    val detailMessage: String = "Not configured"
)
