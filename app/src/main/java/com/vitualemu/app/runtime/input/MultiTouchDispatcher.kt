package com.vitualemu.app.runtime.input

import com.vitualemu.app.domain.model.TouchAction
import com.vitualemu.app.domain.policy.SafetyPolicy
import com.vitualemu.app.domain.usecase.HeldInputRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ActivePointerSlot(
    val pointerId: Int,
    val ownerId: String,
    val normX: Float,
    val normY: Float,
    val pressure: Float = 1.0f,
    val updatedAt: Long = System.currentTimeMillis()
)

class MultiTouchDispatcher(
    private var bridgeProvider: () -> InputBridge? = { null },
    val pointerCombiner: MultiTouchPointerCombiner = MultiTouchPointerCombiner(),
    val wasdSynthesizer: WasdJoystickSynthesizer = WasdJoystickSynthesizer()
) {
    private val lock = Any()
    private val dispatchMutex = Mutex()

    // Active pointer state indexed by pointerId (0..9)
    private val activeSlots = mutableMapOf<Int, ActivePointerSlot>()
    // Owner ID -> allocated dynamic pointer slot (2..9)
    private val ownerToSlot = mutableMapOf<String, Int>()

    private val _slotsFlow = MutableStateFlow<List<ActivePointerSlot>>(emptyList())
    val slotsFlow: StateFlow<List<ActivePointerSlot>> = _slotsFlow.asStateFlow()

    private val _lastCombinedFrame = MutableStateFlow<CombinedTouchFrame?>(null)
    val lastCombinedFrame: StateFlow<CombinedTouchFrame?> = _lastCombinedFrame.asStateFlow()

    fun setBridgeProvider(provider: () -> InputBridge?) {
        bridgeProvider = provider
    }

    fun updateDisplaySize(widthPx: Int, heightPx: Int) {
        pointerCombiner.updateDisplaySize(widthPx, heightPx)
    }

    /**
     * Pointer ID 0: Dedicated WASD Virtual Thumbstick movement.
     * Uses [WasdJoystickSynthesizer] for 8-direction vector synthesis with 1/sqrt(2) diagonal
     * normalization without disturbing any other active pointer (1..9).
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
        val slotId = SafetyPolicy.WASD_POINTER_SLOT
        val vectorState = wasdSynthesizer.evaluate(
            up = up,
            down = down,
            left = left,
            right = right,
            centerNormX = centerNormX,
            centerNormY = centerNormY,
            radius = radius
        )

        if (!vectorState.isActive) {
            return releaseFixedSlot(slotId, "WASD_STICK")
        }

        if (vectorState.isInitialTouchDown) {
            dispatchSlotTouch(slotId, "WASD_STICK", TouchAction.DOWN, vectorState.centerNormX, vectorState.centerNormY)
        }
        return dispatchSlotTouch(slotId, "WASD_STICK", TouchAction.MOVE, vectorState.targetNormX, vectorState.targetNormY)
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
     * Executes an atomic edge-recenter sequence on Pointer Slot 1 under the dispatch mutex:
     * 1. MOVE to clamped edge coordinate
     * 2. UP at clamped edge coordinate (releasing camera drag before resetting)
     * 3. DOWN at anchor center coordinate
     * 4. MOVE to residual carry coordinate so 360° camera rotation continues seamlessly
     */
    suspend fun performAtomicEdgeRecenter(
        edgeNormX: Float,
        edgeNormY: Float,
        anchorNormX: Float,
        anchorNormY: Float,
        carryNormX: Float,
        carryNormY: Float,
        ownerTag: String = "MOUSE_LOOK"
    ): Boolean = dispatchMutex.withLock {
        val slotId = SafetyPolicy.MOUSE_LOOK_POINTER_SLOT
        dispatchSlotTouchInternal(slotId, ownerTag, TouchAction.MOVE, edgeNormX, edgeNormY, 1.0f)
        releaseFixedSlotInternal(slotId, ownerTag)
        dispatchSlotTouchInternal(slotId, ownerTag, TouchAction.DOWN, anchorNormX, anchorNormY, 1.0f)
        dispatchSlotTouchInternal(slotId, ownerTag, TouchAction.MOVE, carryNormX, carryNormY, 1.0f)
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

    suspend fun touchDownDynamic(ownerId: String, normX: Float, normY: Float, pressure: Float = 1.0f): Int? =
        dispatchMutex.withLock {
            val slot = acquireDynamicSlot(ownerId) ?: return@withLock null
            dispatchSlotTouchInternal(slot, ownerId, TouchAction.DOWN, normX, normY, pressure)
            slot
        }

    suspend fun touchMoveDynamic(ownerId: String, normX: Float, normY: Float, pressure: Float = 1.0f): Boolean =
        dispatchMutex.withLock {
            val slot = synchronized(lock) { ownerToSlot[ownerId] } ?: return@withLock false
            dispatchSlotTouchInternal(slot, ownerId, TouchAction.MOVE, normX, normY, pressure)
        }

    suspend fun touchUpDynamic(ownerId: String): Boolean = dispatchMutex.withLock {
        val slot: Int
        val lastSlotState: ActivePointerSlot?
        synchronized(lock) {
            slot = ownerToSlot.remove(ownerId) ?: return@withLock false
            lastSlotState = activeSlots.remove(slot)
            publishSlotsLocked()
        }
        HeldInputRegistry.recordTouchUp(slot)
        val normX = lastSlotState?.normX ?: 0.5f
        val normY = lastSlotState?.normY ?: 0.5f
        val combinedFrame = pointerCombiner.onPointerTransition(
            pointerId = slot,
            requestedAction = TouchAction.UP,
            normX = normX,
            normY = normY,
            pressure = 0f
        )
        _lastCombinedFrame.value = combinedFrame
        val bridge = bridgeProvider() ?: return@withLock true
        if (combinedFrame != null) {
            bridge.injectCombinedTouchFrame(combinedFrame)
        } else {
            val effectiveAction = if (getActiveSlotCount() > 0) TouchAction.POINTER_UP else TouchAction.UP
            bridge.injectTouch(slot, effectiveAction, normX, normY, 0f)
        }
    }

    suspend fun releaseFixedSlot(slotId: Int, ownerTag: String): Boolean = dispatchMutex.withLock {
        releaseFixedSlotInternal(slotId, ownerTag)
    }

    private suspend fun releaseFixedSlotInternal(slotId: Int, ownerTag: String): Boolean {
        val lastState: ActivePointerSlot?
        synchronized(lock) {
            lastState = activeSlots.remove(slotId)
            ownerToSlot.remove(ownerTag)
            if (slotId == SafetyPolicy.WASD_POINTER_SLOT) {
                wasdSynthesizer.reset()
            }
            publishSlotsLocked()
        }
        if (lastState == null) return true
        HeldInputRegistry.recordTouchUp(slotId)
        val combinedFrame = pointerCombiner.onPointerTransition(
            pointerId = slotId,
            requestedAction = TouchAction.UP,
            normX = lastState.normX,
            normY = lastState.normY,
            pressure = 0f
        )
        _lastCombinedFrame.value = combinedFrame
        val bridge = bridgeProvider() ?: return true
        return if (combinedFrame != null) {
            bridge.injectCombinedTouchFrame(combinedFrame)
        } else {
            val effectiveAction = if (getActiveSlotCount() > 0) TouchAction.POINTER_UP else TouchAction.UP
            bridge.injectTouch(slotId, effectiveAction, lastState.normX, lastState.normY, 0f)
        }
    }

    private suspend fun dispatchSlotTouch(
        slotId: Int,
        ownerId: String,
        requestedAction: TouchAction,
        normX: Float,
        normY: Float,
        pressure: Float = 1.0f
    ): Boolean = dispatchMutex.withLock {
        dispatchSlotTouchInternal(slotId, ownerId, requestedAction, normX, normY, pressure)
    }

    private suspend fun dispatchSlotTouchInternal(
        slotId: Int,
        ownerId: String,
        requestedAction: TouchAction,
        normX: Float,
        normY: Float,
        pressure: Float = 1.0f
    ): Boolean {
        val clampedX = normX.coerceIn(0.0f, 1.0f)
        val clampedY = normY.coerceIn(0.0f, 1.0f)

        synchronized(lock) {
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

        val combinedFrame = pointerCombiner.onPointerTransition(
            pointerId = slotId,
            requestedAction = requestedAction,
            normX = clampedX,
            normY = clampedY,
            pressure = pressure
        )
        _lastCombinedFrame.value = combinedFrame

        val bridge = bridgeProvider() ?: return true
        return if (combinedFrame != null) {
            bridge.injectCombinedTouchFrame(combinedFrame)
        } else {
            bridge.injectTouch(slotId, requestedAction, clampedX, clampedY, pressure)
        }
    }

    suspend fun releaseAllSlots(sessionId: String = "default") = dispatchMutex.withLock {
        val snapshot: List<ActivePointerSlot>
        synchronized(lock) {
            snapshot = activeSlots.values.toList()
            activeSlots.clear()
            ownerToSlot.clear()
            wasdSynthesizer.reset()
            publishSlotsLocked()
        }
        HeldInputRegistry.releaseAll()
        val cancelFrame = pointerCombiner.cancelAll()
        _lastCombinedFrame.value = cancelFrame
        val bridge = bridgeProvider()
        if (cancelFrame != null && bridge != null) {
            bridge.injectCombinedTouchFrame(cancelFrame)
        } else {
            for (slot in snapshot) {
                bridge?.injectTouch(slot.pointerId, TouchAction.UP, slot.normX, slot.normY, 0f)
            }
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
