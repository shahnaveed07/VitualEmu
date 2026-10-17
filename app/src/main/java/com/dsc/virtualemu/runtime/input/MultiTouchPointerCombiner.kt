package com.dsc.virtualemu.runtime.input

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import com.dsc.virtualemu.domain.model.TouchAction
import com.dsc.virtualemu.domain.policy.SafetyPolicy
import kotlin.math.roundToInt

/**
 * Snapshot of a single pointer inside a combined multi-touch frame.
 */
data class PointerFrameEntry(
    val pointerId: Int,
    val normX: Float,
    val normY: Float,
    val pixelX: Float,
    val pixelY: Float,
    val pressure: Float,
    val trackingId: Int
)

/**
 * Unified multi-pointer frame produced by [MultiTouchPointerCombiner] on every touch transition.
 * Contains the masked Android [MotionEvent] action (including shifted actionIndex for
 * ACTION_POINTER_DOWN / ACTION_POINTER_UP), all active pointers participating in this frame,
 * and pre-built Linux evdev Protocol B command lines for the sendevent fallback transport.
 */
data class CombinedTouchFrame(
    val actionPointerId: Int,
    val actionIndex: Int,
    val resolvedTouchAction: TouchAction,
    val androidMaskedAction: Int,
    val downTimeMs: Long,
    val eventTimeMs: Long,
    val pointers: List<PointerFrameEntry>,
    val evdevTypeBCommands: List<String>
) {
    val pointerCount: Int get() = pointers.size

    /**
     * Builds a real Android [MotionEvent] with [MotionEvent.PointerProperties] and
     * [MotionEvent.PointerCoords] for all active pointers in this frame.
     * Returns null if called in a pure JVM unit test where Android native MotionEvent is stubbed.
     */
    fun obtainMotionEvent(): MotionEvent? {
        if (pointers.isEmpty()) return null
        return try {
            val count = pointers.size
            val properties = Array(count) { idx ->
                MotionEvent.PointerProperties().apply {
                    id = pointers[idx].pointerId
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            }
            val coords = Array(count) { idx ->
                MotionEvent.PointerCoords().apply {
                    x = pointers[idx].pixelX
                    y = pointers[idx].pixelY
                    pressure = pointers[idx].pressure
                    size = 0.05f
                }
            }
            MotionEvent.obtain(
                downTimeMs,
                eventTimeMs,
                androidMaskedAction,
                count,
                properties,
                coords,
                0,
                0,
                1.0f,
                1.0f,
                0,
                0,
                InputDevice.SOURCE_TOUCHSCREEN,
                0
            )
        } catch (_: Throwable) {
            null
        }
    }
}

/**
 * Combines up to 10 independent pointer slots (0..9) into unified Android multi-pointer
 * [MotionEvent] frames and Linux evdev Protocol B slot commands (`ABS_MT_SLOT`,
 * `ABS_MT_TRACKING_ID`, `ABS_MT_POSITION_X`, `ABS_MT_POSITION_Y`, `SYN_REPORT`).
 *
 * This resolves the P0 multi-touch defect where separate single-pointer events cancel each
 * other in Android's InputDispatcher when WASD + Mouse Look + Fire are active simultaneously.
 */
class MultiTouchPointerCombiner(
    screenWidthPx: Int = 2400,
    screenHeightPx: Int = 1080
) {
    companion object {
        // Linux input-event-codes.h constants for Protocol B multi-touch
        const val EV_SYN = 0x00
        const val EV_KEY = 0x01
        const val EV_ABS = 0x03

        const val SYN_REPORT = 0x00
        const val BTN_TOUCH = 0x14a // 330

        const val ABS_MT_SLOT = 0x2f // 47
        const val ABS_MT_POSITION_X = 0x35 // 53
        const val ABS_MT_POSITION_Y = 0x36 // 54
        const val ABS_MT_TRACKING_ID = 0x39 // 57
        const val ABS_MT_PRESSURE = 0x3a // 58
    }

    private val lock = Any()

    @Volatile
    var displayWidthPx: Int = screenWidthPx.coerceAtLeast(320)
        private set

    @Volatile
    var displayHeightPx: Int = screenHeightPx.coerceAtLeast(240)
        private set

    private val activePointers = linkedMapOf<Int, PointerFrameEntry>()
    private var gestureDownTimeMs: Long = 0L
    private var nextTrackingId: Int = 100

    fun updateDisplaySize(widthPx: Int, heightPx: Int) {
        synchronized(lock) {
            displayWidthPx = widthPx.coerceAtLeast(320)
            displayHeightPx = heightPx.coerceAtLeast(240)
        }
    }

    fun getActivePointerCount(): Int = synchronized(lock) { activePointers.size }

    fun getActivePointerIds(): Set<Int> = synchronized(lock) { activePointers.keys.toSet() }

    /**
     * Updates a pointer slot and returns the resulting [CombinedTouchFrame] representing
     * all currently participating pointers.
     */
    fun onPointerTransition(
        pointerId: Int,
        requestedAction: TouchAction,
        normX: Float,
        normY: Float,
        pressure: Float = 1.0f,
        deviceNode: String = "/dev/input/event2"
    ): CombinedTouchFrame? {
        if (!SafetyPolicy.isPointerSlotValid(pointerId)) return null

        synchronized(lock) {
            val now = safeUptimeMillis()
            val clampedX = normX.coerceIn(0.0f, 1.0f)
            val clampedY = normY.coerceIn(0.0f, 1.0f)
            val px = (clampedX * displayWidthPx).coerceIn(0f, displayWidthPx.toFloat())
            val py = (clampedY * displayHeightPx).coerceIn(0f, displayHeightPx.toFloat())

            return when (requestedAction) {
                TouchAction.DOWN, TouchAction.POINTER_DOWN -> {
                    val wasEmpty = activePointers.isEmpty()
                    if (wasEmpty) {
                        gestureDownTimeMs = now
                    }
                    val existing = activePointers[pointerId]
                    val trackingId = existing?.trackingId ?: (++nextTrackingId)
                    val entry = PointerFrameEntry(
                        pointerId = pointerId,
                        normX = clampedX,
                        normY = clampedY,
                        pixelX = px,
                        pixelY = py,
                        pressure = pressure.coerceIn(0.05f, 1.0f),
                        trackingId = trackingId
                    )
                    activePointers[pointerId] = entry

                    val sorted = activePointers.values.sortedBy { it.pointerId }
                    val actionIdx = sorted.indexOfFirst { it.pointerId == pointerId }.coerceAtLeast(0)
                    val resolvedAction = if (sorted.size == 1) TouchAction.DOWN else TouchAction.POINTER_DOWN
                    val androidAction = if (sorted.size == 1) {
                        MotionEvent.ACTION_DOWN
                    } else {
                        MotionEvent.ACTION_POINTER_DOWN or (actionIdx shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
                    }

                    val evdevCmds = buildEvdevDownOrMoveCommands(
                        deviceNode = deviceNode,
                        entry = entry,
                        isFirstTouchDown = wasEmpty
                    )

                    CombinedTouchFrame(
                        actionPointerId = pointerId,
                        actionIndex = actionIdx,
                        resolvedTouchAction = resolvedAction,
                        androidMaskedAction = androidAction,
                        downTimeMs = gestureDownTimeMs,
                        eventTimeMs = now,
                        pointers = sorted,
                        evdevTypeBCommands = evdevCmds
                    )
                }

                TouchAction.MOVE -> {
                    val existing = activePointers[pointerId]
                    val trackingId = existing?.trackingId ?: (++nextTrackingId)
                    if (activePointers.isEmpty()) {
                        gestureDownTimeMs = now
                    }
                    val entry = PointerFrameEntry(
                        pointerId = pointerId,
                        normX = clampedX,
                        normY = clampedY,
                        pixelX = px,
                        pixelY = py,
                        pressure = pressure.coerceIn(0.05f, 1.0f),
                        trackingId = trackingId
                    )
                    activePointers[pointerId] = entry

                    val sorted = activePointers.values.sortedBy { it.pointerId }
                    val actionIdx = sorted.indexOfFirst { it.pointerId == pointerId }.coerceAtLeast(0)
                    val evdevCmds = buildEvdevDownOrMoveCommands(
                        deviceNode = deviceNode,
                        entry = entry,
                        isFirstTouchDown = false
                    )

                    CombinedTouchFrame(
                        actionPointerId = pointerId,
                        actionIndex = actionIdx,
                        resolvedTouchAction = TouchAction.MOVE,
                        androidMaskedAction = MotionEvent.ACTION_MOVE,
                        downTimeMs = gestureDownTimeMs,
                        eventTimeMs = now,
                        pointers = sorted,
                        evdevTypeBCommands = evdevCmds
                    )
                }

                TouchAction.UP, TouchAction.POINTER_UP -> {
                    val existing = activePointers[pointerId] ?: PointerFrameEntry(
                        pointerId = pointerId,
                        normX = clampedX,
                        normY = clampedY,
                        pixelX = px,
                        pixelY = py,
                        pressure = 0f,
                        trackingId = -1
                    )
                    // In Android MotionEvent protocol, the pointer being lifted MUST still be present
                    // in the PointerProperties/PointerCoords arrays for the ACTION_POINTER_UP / ACTION_UP event,
                    // and removed from active state immediately after constructing the frame.
                    val upEntry = existing.copy(
                        normX = clampedX,
                        normY = clampedY,
                        pixelX = px,
                        pixelY = py,
                        pressure = 0f
                    )
                    activePointers[pointerId] = upEntry
                    val sortedForFrame = activePointers.values.sortedBy { it.pointerId }
                    val actionIdx = sortedForFrame.indexOfFirst { it.pointerId == pointerId }.coerceAtLeast(0)

                    // Remove lifted pointer from subsequent frames
                    activePointers.remove(pointerId)
                    val isLastPointer = activePointers.isEmpty()

                    val resolvedAction = if (isLastPointer) TouchAction.UP else TouchAction.POINTER_UP
                    val androidAction = if (isLastPointer) {
                        MotionEvent.ACTION_UP
                    } else {
                        MotionEvent.ACTION_POINTER_UP or (actionIdx shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
                    }

                    val evdevCmds = buildEvdevUpCommands(
                        deviceNode = deviceNode,
                        pointerId = pointerId,
                        isLastTouchUp = isLastPointer
                    )

                    CombinedTouchFrame(
                        actionPointerId = pointerId,
                        actionIndex = actionIdx,
                        resolvedTouchAction = resolvedAction,
                        androidMaskedAction = androidAction,
                        downTimeMs = gestureDownTimeMs,
                        eventTimeMs = now,
                        pointers = sortedForFrame,
                        evdevTypeBCommands = evdevCmds
                    )
                }

                TouchAction.CANCEL -> {
                    cancelAll(deviceNode)
                }
            }
        }
    }

    /**
     * Emits an atomic [MotionEvent.ACTION_CANCEL] frame containing all currently held pointers
     * and clears all slots + Type B tracking IDs.
     */
    fun cancelAll(deviceNode: String = "/dev/input/event2"): CombinedTouchFrame? {
        synchronized(lock) {
            if (activePointers.isEmpty()) return null
            val now = safeUptimeMillis()
            val sorted = activePointers.values.sortedBy { it.pointerId }
            val evdevCmds = mutableListOf<String>()
            for (ptr in sorted) {
                evdevCmds += "sendevent $deviceNode $EV_ABS $ABS_MT_SLOT ${ptr.pointerId}"
                evdevCmds += "sendevent $deviceNode $EV_ABS $ABS_MT_TRACKING_ID -1"
            }
            evdevCmds += "sendevent $deviceNode $EV_KEY $BTN_TOUCH 0"
            evdevCmds += "sendevent $deviceNode $EV_SYN $SYN_REPORT 0"

            activePointers.clear()
            return CombinedTouchFrame(
                actionPointerId = sorted.first().pointerId,
                actionIndex = 0,
                resolvedTouchAction = TouchAction.CANCEL,
                androidMaskedAction = MotionEvent.ACTION_CANCEL,
                downTimeMs = gestureDownTimeMs,
                eventTimeMs = now,
                pointers = sorted,
                evdevTypeBCommands = evdevCmds
            )
        }
    }

    private fun buildEvdevDownOrMoveCommands(
        deviceNode: String,
        entry: PointerFrameEntry,
        isFirstTouchDown: Boolean
    ): List<String> {
        val xInt = entry.pixelX.roundToInt()
        val yInt = entry.pixelY.roundToInt()
        val pressureInt = (entry.pressure * 255f).roundToInt().coerceIn(1, 255)
        val cmds = mutableListOf<String>()
        cmds += "sendevent $deviceNode $EV_ABS $ABS_MT_SLOT ${entry.pointerId}"
        cmds += "sendevent $deviceNode $EV_ABS $ABS_MT_TRACKING_ID ${entry.trackingId}"
        cmds += "sendevent $deviceNode $EV_ABS $ABS_MT_POSITION_X $xInt"
        cmds += "sendevent $deviceNode $EV_ABS $ABS_MT_POSITION_Y $yInt"
        cmds += "sendevent $deviceNode $EV_ABS $ABS_MT_PRESSURE $pressureInt"
        if (isFirstTouchDown) {
            cmds += "sendevent $deviceNode $EV_KEY $BTN_TOUCH 1"
        }
        cmds += "sendevent $deviceNode $EV_SYN $SYN_REPORT 0"
        return cmds
    }

    private fun buildEvdevUpCommands(
        deviceNode: String,
        pointerId: Int,
        isLastTouchUp: Boolean
    ): List<String> {
        val cmds = mutableListOf<String>()
        cmds += "sendevent $deviceNode $EV_ABS $ABS_MT_SLOT $pointerId"
        cmds += "sendevent $deviceNode $EV_ABS $ABS_MT_TRACKING_ID -1"
        if (isLastTouchUp) {
            cmds += "sendevent $deviceNode $EV_KEY $BTN_TOUCH 0"
        }
        cmds += "sendevent $deviceNode $EV_SYN $SYN_REPORT 0"
        return cmds
    }

    private fun safeUptimeMillis(): Long {
        return try {
            val uptime = SystemClock.uptimeMillis()
            if (uptime > 0L) uptime else System.currentTimeMillis()
        } catch (_: Throwable) {
            System.currentTimeMillis()
        }
    }
}
