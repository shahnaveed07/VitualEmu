package com.dsc.virtualemu.runtime.input

import kotlin.math.sqrt

/**
 * Result of synthesizing WASD directional keys into an 8-way normalized analog thumbstick vector
 * on [com.dsc.virtualemu.domain.policy.SafetyPolicy.WASD_POINTER_SLOT] (Pointer Slot 0).
 */
data class WasdVectorState(
    val isActive: Boolean,
    val isInitialTouchDown: Boolean,
    val centerNormX: Float,
    val centerNormY: Float,
    val targetNormX: Float,
    val targetNormY: Float,
    val unitVectorX: Float,
    val unitVectorY: Float
)

/**
 * Dedicated state machine for synthesizing simultaneous physical directional keys (W, A, S, D)
 * into a smooth 8-way virtual analog thumbstick gesture without releasing Slot 0 during
 * direction transitions.
 */
class WasdJoystickSynthesizer {
    private val lock = Any()
    private var isStickHeldDown: Boolean = false
    private var lastCenterX: Float = 0.20f
    private var lastCenterY: Float = 0.72f

    fun isActive(): Boolean = synchronized(lock) { isStickHeldDown }

    /**
     * Computes the normalized thumbstick position from the currently held directional states.
     * Diagonal movement (e.g., W + D) is normalized by 1/sqrt(2) (~0.7071) so diagonal speed
     * never exceeds cardinal radius bounds.
     */
    fun evaluate(
        up: Boolean,
        down: Boolean,
        left: Boolean,
        right: Boolean,
        centerNormX: Float,
        centerNormY: Float,
        radius: Float = 0.08f
    ): WasdVectorState {
        synchronized(lock) {
            val clampedCenterX = centerNormX.coerceIn(0.05f, 0.95f)
            val clampedCenterY = centerNormY.coerceIn(0.05f, 0.95f)
            val clampedRadius = radius.coerceIn(0.02f, 0.25f)

            lastCenterX = clampedCenterX
            lastCenterY = clampedCenterY

            val rawDx = (if (right) 1f else 0f) - (if (left) 1f else 0f)
            val rawDy = (if (down) 1f else 0f) - (if (up) 1f else 0f)

            val isMoving = rawDx != 0f || rawDy != 0f
            if (!isMoving) {
                isStickHeldDown = false
                return WasdVectorState(
                    isActive = false,
                    isInitialTouchDown = false,
                    centerNormX = clampedCenterX,
                    centerNormY = clampedCenterY,
                    targetNormX = clampedCenterX,
                    targetNormY = clampedCenterY,
                    unitVectorX = 0f,
                    unitVectorY = 0f
                )
            }

            val magnitude = sqrt(rawDx * rawDx + rawDy * rawDy)
            val unitX = if (magnitude > 0f) rawDx / magnitude else 0f
            val unitY = if (magnitude > 0f) rawDy / magnitude else 0f

            val targetX = (clampedCenterX + unitX * clampedRadius).coerceIn(0.01f, 0.99f)
            val targetY = (clampedCenterY + unitY * clampedRadius).coerceIn(0.01f, 0.99f)

            val initialDown = !isStickHeldDown
            isStickHeldDown = true

            return WasdVectorState(
                isActive = true,
                isInitialTouchDown = initialDown,
                centerNormX = clampedCenterX,
                centerNormY = clampedCenterY,
                targetNormX = targetX,
                targetNormY = targetY,
                unitVectorX = unitX,
                unitVectorY = unitY
            )
        }
    }

    fun reset() {
        synchronized(lock) {
            isStickHeldDown = false
        }
    }
}
