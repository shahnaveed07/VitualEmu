package com.dsc.virtualemu.runtime.capture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * System-level AccessibilityService that captures physical keyboard key events globally,
 * including while a third-party game is in the foreground.
 *
 * This service:
 * - Receives all physical key-down and key-up events via [onKeyEvent]
 * - Consumes mapped keys (returns true) to prevent double-input in the game
 * - Passes through unmapped keys (returns false) so the game or system can use them
 * - Does NOT set `isAccessibilityTool=true` — VitualEmu is not an accessibility tool
 * - Does NOT read or inspect screen content
 *
 * Events are forwarded to the app via the [eventBridge] singleton which the ViewModel observes.
 */
class VitualEmuAccessibilityService : AccessibilityService() {

    companion object {
        /**
         * Static bridge for communication between the AccessibilityService (runs in app process)
         * and the ViewModel. The service writes events here; the ViewModel reads them.
         */
        val eventBridge = AccessibilityEventBridge()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Dynamically configure at runtime to ensure flags are set correctly
        serviceInfo = serviceInfo?.apply {
            eventTypes = 0 // We don't need accessibility events, only key events
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            notificationTimeout = 0
        } ?: return

        eventBridge.markServiceConnected(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op — we don't process accessibility events.
        // We only use this service for onKeyEvent() global keyboard capture.
    }

    override fun onInterrupt() {
        // Required override — nothing to interrupt
    }

    /**
     * Called for every physical key event system-wide when FLAG_REQUEST_FILTER_KEY_EVENTS is set.
     *
     * @return true to consume the event (mapped keys), false to pass through (unmapped keys)
     */
    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false

        val keyCode = event.keyCode
        val isDown = event.action == KeyEvent.ACTION_DOWN
        val isUp = event.action == KeyEvent.ACTION_UP
        val repeatCount = event.repeatCount
        val deviceId = event.deviceId

        // Only process down and up actions
        if (!isDown && !isUp) return false

        val type = when {
            isDown && repeatCount > 0 -> PhysicalInputType.KEY_REPEAT
            isDown -> PhysicalInputType.KEY_DOWN
            else -> PhysicalInputType.KEY_UP
        }

        val physicalEvent = PhysicalInputEvent(
            type = type,
            keyCode = keyCode,
            isDown = isDown,
            repeatCount = repeatCount,
            deviceId = deviceId,
            deviceName = event.device?.name ?: "",
            source = CaptureSource.ACCESSIBILITY_SERVICE,
            timestampNs = System.nanoTime()
        )

        // Forward to the bridge — it returns whether the key should be consumed
        return eventBridge.onKeyEventCaptured(physicalEvent)
    }

    override fun onDestroy() {
        eventBridge.markServiceConnected(false)
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        eventBridge.markServiceConnected(false)
        return super.onUnbind(intent)
    }
}

/**
 * Thread-safe bridge between [VitualEmuAccessibilityService] and the app's ViewModel.
 *
 * The ViewModel registers a [keyEventConsumer] which receives every captured key event
 * and returns whether the key should be consumed (true) or passed through (false).
 */
class AccessibilityEventBridge {

    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

    /**
     * Set by the ViewModel. Receives a captured physical key event and returns
     * true to consume the key (preventing double-input), false to pass through.
     */
    @Volatile
    var keyEventConsumer: ((PhysicalInputEvent) -> Boolean)? = null

    fun markServiceConnected(connected: Boolean) {
        _isServiceConnected.value = connected
    }

    /**
     * Called by the AccessibilityService for each captured key event.
     */
    fun onKeyEventCaptured(event: PhysicalInputEvent): Boolean {
        return keyEventConsumer?.invoke(event) ?: false
    }
}
