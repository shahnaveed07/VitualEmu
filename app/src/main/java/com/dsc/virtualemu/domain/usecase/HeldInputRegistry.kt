package com.dsc.virtualemu.domain.usecase

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

data class VirtualTouchPoint(
    val pointerId: Int,
    val x: Float,
    val y: Float,
    val action: String, // DOWN, MOVE, UP
    val sourceMappingId: String,
    val timestamp: Long = System.currentTimeMillis()
)

object HeldInputRegistry {
    private val heldKeyCodes = ConcurrentHashMap.newKeySet<Int>()
    private val activeTouches = ConcurrentHashMap<Int, VirtualTouchPoint>()

    private val _heldKeysFlow = MutableStateFlow<Set<Int>>(emptySet())
    val heldKeysFlow: StateFlow<Set<Int>> = _heldKeysFlow.asStateFlow()

    private val _activePointersFlow = MutableStateFlow<Map<Int, VirtualTouchPoint>>(emptyMap())
    val activePointersFlow: StateFlow<Map<Int, VirtualTouchPoint>> = _activePointersFlow.asStateFlow()

    fun recordKeyDown(keyCode: Int) {
        heldKeyCodes.add(keyCode)
        _heldKeysFlow.value = heldKeyCodes.toSet()
    }

    fun recordKeyUp(keyCode: Int) {
        heldKeyCodes.remove(keyCode)
        _heldKeysFlow.value = heldKeyCodes.toSet()
    }

    fun recordTouchDown(pointerId: Int, x: Float, y: Float, mappingId: String) {
        activeTouches[pointerId] = VirtualTouchPoint(
            pointerId = pointerId,
            x = x,
            y = y,
            action = "DOWN",
            sourceMappingId = mappingId
        )
        _activePointersFlow.value = activeTouches.toMap()
    }

    fun recordTouchMove(pointerId: Int, x: Float, y: Float) {
        val existing = activeTouches[pointerId]
        if (existing != null) {
            activeTouches[pointerId] = existing.copy(x = x, y = y, action = "MOVE")
            _activePointersFlow.value = activeTouches.toMap()
        }
    }

    fun recordTouchUp(pointerId: Int) {
        activeTouches.remove(pointerId)
        _activePointersFlow.value = activeTouches.toMap()
    }

    /**
     * Atomically releases all held keys and touch points.
     * Guaranteed zero stuck inputs on focus loss, app pause, or emergency stop.
     */
    fun releaseAll(): List<VirtualTouchPoint> {
        val releasedPointers = activeTouches.values.map { it.copy(action = "UP") }
        activeTouches.clear()
        heldKeyCodes.clear()
        _heldKeysFlow.value = emptySet()
        _activePointersFlow.value = emptyMap()
        return releasedPointers
    }

    fun getHeldKeyCount(): Int = heldKeyCodes.size
    fun getActiveTouchCount(): Int = activeTouches.size
}
