package com.vitualemu.app.runtime.input

import com.vitualemu.app.domain.model.TouchAction
import com.vitualemu.app.domain.policy.SafetyPolicy
import com.vitualemu.app.domain.usecase.HeldInputRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

data class ActivePointerSlot(
    val pointerId: Int,
    val ownerId: String,
    val normX: Float,
    val normY: Float,
    val pressure: Float = 1.0f,
    val updatedAt: Long = System.currentTimeMillis()
)

class MultiTouchDispatcher(
    private var bridgeProvider: () -> InputBridge? = { null }
) {
    private val lock = Any()

    // Active pointer state indexed by pointerId (0..9)
    private val activeSlots = mutableMapOf<Int, ActivePointerSlot>()
    // Owner ID -> allocated dynamic pointer slot (2..9)
    private val ownerToSlot = mutableMapOf<String, Int>()

    private val _slotsFlow = MutableStateFlow<List<ActivePointerSlot>>(emptyList())
    val slotsFlow: StateFlow<List<ActivePointerSlot>> = _slotsFlow.asStateFlow()

    fun setBridgeProvider(provider: () -> InputBridge?) {
        bridgeProvider = provider
    }

    /**
     * Pointer ID 0: Dedicated WASD Virtual Thumbstick movement.
     * Supports 8-direction vector synthesis with 1/sqrt(2) diagonal normalization
     * without disturbing any other active pointer (1..9).
     */
    suspend fun updateWasdStick(
        up: Boolean,
        down: Boolean,
        left: Boolean,
        right: Boolean,
        centerNormX: Float,
        centerNormY: Float,
        radius: Float = 0.08f
    ): Boolean {
        val rawDx = (if (right) 1f else 0f) - (if (left) 1f else 0f)
        val rawDy = (if (down) 1f else 0f) - (if (up) 1f else 0f)

        val isMoving = rawDx != 0f || rawDy != 0f
        val slotId = SafetyPolicy.WASD_POINTER_SLOT

        if (!isMoving) {
            return releaseFixedSlot(slotId, "WASD_STICK")
        }

        val magnitude = sqrt(rawDx * rawDx + rawDy * rawDy)
        val normDx = if (magnitude > 0f) rawDx / magnitude else 0f
        val normDy = if (magnitude > 0f) rawDy / magnitude else 0f

        val targetX = (centerNormX + normDx * radius).coerceIn(0.01f, 0.99f)
        val targetY = (centerNormY + normDy * radius).coerceIn(0.01f, 0.99f)

        val wasActive: Boolean
        synchronized(lock) {
            wasActive = activeSlots.containsKey(slotId)
        }

        if (!wasActive) {
            // Touch down at joystick center first, then drag to target direction
            dispatchSlotTouch(slotId, "WASD_STICK", TouchAction.DOWN, centerNormX, centerNormY)
        }
        return dispatchSlotTouch(slotId, "WASD_STICK", TouchAction.MOVE, targetX, targetY)
    }

    /**
     * Pointer ID 1: Dedicated FPS Mouse-Look / Free-Look Camera Pointer.
     */
    suspend fun updateMouseLookPointer(
        action: TouchAction,
        normX: Float,
        normY: Float,
        ownerTag: String = "MOUSE_LOOK"
    ): Boolean {
        val slotId = SafetyPolicy.MOUSE_LOOK_POINTER_SLOT
        return when (action) {
            TouchAction.UP, TouchAction.CANCEL, TouchAction.POINTER_UP ->
                releaseFixedSlot(slotId, ownerTag)
            TouchAction.DOWN, TouchAction.POINTER_DOWN ->
                dispatchSlotTouch(slotId, ownerTag, TouchAction.DOWN, normX, normY)
            TouchAction.MOVE ->
                dispatchSlotTouch(slotId, ownerTag, TouchAction.MOVE, normX, normY)
        }
    }

    /**
     * Pointer IDs 2..9: Dynamic pool for simultaneous Firing, Scope, Jump, Crouch, Gloo Wall, and Macros.
     */
    fun acquireDynamicSlot(ownerId: String): Int? {
        synchronized(lock) {
            ownerToSlot[ownerId]?.let { return it }
            for (slot in SafetyPolicy.FIRST_DYNAMIC_POINTER_SLOT..SafetyPolicy.LAST_DYNAMIC_POINTER_SLOT) {
                if (!activeSlots.containsKey(slot) && !ownerToSlot.containsValue(slot)) {
                    ownerToSlot[ownerId] = slot
                    return slot
                }
            }
            return null
        }
    }

    suspend fun touchDownDynamic(ownerId: String, normX: Float, normY: Float, pressure: Float = 1.0f): Int? {
        val slot = acquireDynamicSlot(ownerId) ?: return null
        dispatchSlotTouch(slot, ownerId, TouchAction.DOWN, normX, normY, pressure)
        return slot
    }

    suspend fun touchMoveDynamic(ownerId: String, normX: Float, normY: Float, pressure: Float = 1.0f): Boolean {
        val slot = synchronized(lock) { ownerToSlot[ownerId] } ?: return false
        return dispatchSlotTouch(slot, ownerId, TouchAction.MOVE, normX, normY, pressure)
    }

    suspend fun touchUpDynamic(ownerId: String): Boolean {
        val slot: Int
        val lastSlotState: ActivePointerSlot?
        synchronized(lock) {
            slot = ownerToSlot.remove(ownerId) ?: return false
            lastSlotState = activeSlots.remove(slot)
            publishSlotsLocked()
        }
        HeldInputRegistry.recordTouchUp(slot)
        val normX = lastSlotState?.normX ?: 0.5f
        val normY = lastSlotState?.normY ?: 0.5f
        val effectiveAction = if (getActiveSlotCount() > 0) TouchAction.POINTER_UP else TouchAction.UP
        return bridgeProvider()?.injectTouch(slot, effectiveAction, normX, normY, 0f) ?: true
    }

    suspend fun releaseFixedSlot(slotId: Int, ownerTag: String): Boolean {
        val lastState: ActivePointerSlot?
        synchronized(lock) {
            lastState = activeSlots.remove(slotId)
            ownerToSlot.remove(ownerTag)
            publishSlotsLocked()
        }
        if (lastState == null) return true
        HeldInputRegistry.recordTouchUp(slotId)
        val effectiveAction = if (getActiveSlotCount() > 0) TouchAction.POINTER_UP else TouchAction.UP
        return bridgeProvider()?.injectTouch(slotId, effectiveAction, lastState.normX, lastState.normY, 0f) ?: true
    }

    private suspend fun dispatchSlotTouch(
        slotId: Int,
        ownerId: String,
        requestedAction: TouchAction,
        normX: Float,
        normY: Float,
        pressure: Float = 1.0f
    ): Boolean {
        val clampedX = normX.coerceIn(0.0f, 1.0f)
        val clampedY = normY.coerceIn(0.0f, 1.0f)
        val otherPointersActive: Boolean

        synchronized(lock) {
            otherPointersActive = activeSlots.keys.any { it != slotId }
            activeSlots[slotId] = ActivePointerSlot(
                pointerId = slotId,
                ownerId = ownerId,
                normX = clampedX,
                normY = clampedY,
                pressure = pressure
            )
            publishSlotsLocked()
        }

        HeldInputRegistry.recordTouchDown(slotId, clampedX, clampedY, ownerId)

        val effectiveAction = when (requestedAction) {
            TouchAction.DOWN -> if (otherPointersActive) TouchAction.POINTER_DOWN else TouchAction.DOWN
            TouchAction.UP -> if (otherPointersActive) TouchAction.POINTER_UP else TouchAction.UP
            else -> requestedAction
        }

        return bridgeProvider()?.injectTouch(slotId, effectiveAction, clampedX, clampedY, pressure) ?: true
    }

    suspend fun releaseAllSlots(sessionId: String = "default") {
        val snapshot: List<ActivePointerSlot>
        synchronized(lock) {
            snapshot = activeSlots.values.toList()
            activeSlots.clear()
            ownerToSlot.clear()
            publishSlotsLocked()
        }
        HeldInputRegistry.releaseAll()
        val bridge = bridgeProvider()
        for (slot in snapshot) {
            bridge?.injectTouch(slot.pointerId, TouchAction.UP, slot.normX, slot.normY, 0f)
        }
        bridge?.releaseHeldInputs(sessionId)
    }

    fun getActiveSlotCount(): Int = synchronized(lock) { activeSlots.size }

    fun isSlotActive(pointerId: Int): Boolean = synchronized(lock) { activeSlots.containsKey(pointerId) }

    fun getSlotForOwner(ownerId: String): Int? = synchronized(lock) { ownerToSlot[ownerId] }

    private fun publishSlotsLocked() {
        _slotsFlow.value = activeSlots.values.sortedBy { it.pointerId }
    }
}
