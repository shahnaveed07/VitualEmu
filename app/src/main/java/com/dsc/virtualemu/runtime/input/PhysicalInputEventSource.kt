package com.dsc.virtualemu.runtime.input

import com.dsc.virtualemu.runtime.capture.CaptureState
import com.dsc.virtualemu.runtime.capture.CaptureStateManager
import com.dsc.virtualemu.runtime.capture.PhysicalInputEvent
import com.dsc.virtualemu.runtime.capture.PhysicalInputRouter
import com.dsc.virtualemu.runtime.capture.PhysicalInputType
import com.dsc.virtualemu.runtime.overlay.PlayModeOverlayRuntimeBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PhysicalKeyEvent(
    val keyCode: Int,
    val isDown: Boolean,
    val keyLabel: String = "",
    val timestampMs: Long = System.currentTimeMillis()
)

interface PhysicalInputEventSource {
    val isListening: StateFlow<Boolean>
    val lastEvent: StateFlow<PhysicalKeyEvent?>
    fun startListening(onKeyEvent: (keyCode: Int, isDown: Boolean) -> Unit)
    fun stopListening()
}

/**
 * Production implementation of [PhysicalInputEventSource] that subscribes to
 * [PhysicalInputRouter] and forwards real hardware key events.
 */
class SystemPhysicalInputEventSource(
    private val router: PhysicalInputRouter,
    private val stateManager: CaptureStateManager
) : PhysicalInputEventSource {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var collectJob: Job? = null

    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _lastEvent = MutableStateFlow<PhysicalKeyEvent?>(null)
    override val lastEvent: StateFlow<PhysicalKeyEvent?> = _lastEvent.asStateFlow()

    private val _lastUnifiedEvent = MutableStateFlow<PhysicalInputEvent?>(null)
    val lastUnifiedEvent: StateFlow<PhysicalInputEvent?> = _lastUnifiedEvent.asStateFlow()

    override fun startListening(onKeyEvent: (keyCode: Int, isDown: Boolean) -> Unit) {
        _isListening.value = true
        collectJob?.cancel()
        collectJob = scope.launch {
            router.events.collect { event ->
                _lastUnifiedEvent.value = event
                if (event.type == PhysicalInputType.KEY_DOWN ||
                    event.type == PhysicalInputType.KEY_UP ||
                    event.type == PhysicalInputType.MOUSE_BUTTON_DOWN ||
                    event.type == PhysicalInputType.MOUSE_BUTTON_UP
                ) {
                    val keyEvent = PhysicalKeyEvent(event.keyCode, event.isDown)
                    _lastEvent.value = keyEvent
                    onKeyEvent(event.keyCode, event.isDown)
                }
            }
        }
    }

    override fun stopListening() {
        _isListening.value = false
        collectJob?.cancel()
        collectJob = null
    }

    fun shutdown() {
        stopListening()
        scope.cancel()
    }
}

/**
 * Fallback callback-based event source using [PlayModeOverlayRuntimeBridge].
 */
class OverlayInputEventSource : PhysicalInputEventSource {
    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _lastEvent = MutableStateFlow<PhysicalKeyEvent?>(null)
    override val lastEvent: StateFlow<PhysicalKeyEvent?> = _lastEvent.asStateFlow()

    override fun startListening(onKeyEvent: (keyCode: Int, isDown: Boolean) -> Unit) {
        _isListening.value = true
        PlayModeOverlayRuntimeBridge.onPhysicalKeyCaptured = { keyCode, isDown ->
            val event = PhysicalKeyEvent(keyCode, isDown)
            _lastEvent.value = event
            onKeyEvent(keyCode, isDown)
        }
    }

    override fun stopListening() {
        _isListening.value = false
        PlayModeOverlayRuntimeBridge.onPhysicalKeyCaptured = null
    }
}
