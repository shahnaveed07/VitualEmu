package com.dsc.virtualemu.runtime.input

import com.dsc.virtualemu.runtime.overlay.PlayModeOverlayRuntimeBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
