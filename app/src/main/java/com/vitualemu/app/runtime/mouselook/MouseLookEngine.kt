package com.vitualemu.app.runtime.mouselook

import com.vitualemu.app.domain.model.MouseLookConfig
import com.vitualemu.app.domain.model.TouchAction
import com.vitualemu.app.domain.policy.SafetyPolicy
import com.vitualemu.app.runtime.input.MultiTouchDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sqrt

data class MouseLookState(
    val isShootingMode: Boolean = false,
    val isFreeLookActive: Boolean = false,
    val isSmartDragActive: Boolean = false,
    val cursorNormX: Float = 0.5f,
    val cursorNormY: Float = 0.5f,
    val cameraPointerX: Float = 0.72f,
    val cameraPointerY: Float = 0.48f,
    val edgeResetCount: Int = 0
)

class MouseLookEngine(
    private val dispatcher: MultiTouchDispatcher
) {
    private var config: MouseLookConfig = MouseLookConfig()
    private val _state = MutableStateFlow(MouseLookState())
    val state: StateFlow<MouseLookState> = _state.asStateFlow()

    private var smoothedDx: Float = 0f
    private var smoothedDy: Float = 0f
    private var isCameraPointerDown: Boolean = false

    fun updateConfig(newConfig: MouseLookConfig) {
        config = SafetyPolicy.sanitizeMouseLookConfig(newConfig)
        if (!_state.value.isShootingMode) {
            _state.value = _state.value.copy(
                cameraPointerX = config.centerNormalizedX,
                cameraPointerY = config.centerNormalizedY
            )
        }
    }

    fun getConfig(): MouseLookConfig = config

    /**
     * Toggles between Cursor Mode (visible loot/map cursor) and Shooting Mode (Aim Lock on Pointer ID 1).
     */
    suspend fun toggleShootingMode(): Boolean {
        val nextMode = !_state.value.isShootingMode
        setShootingMode(nextMode)
        return nextMode
    }

    suspend fun setShootingMode(enabled: Boolean) {
        if (enabled == _state.value.isShootingMode) return
        if (enabled) {
            val startX = if (_state.value.isFreeLookActive) config.freeLookNormalizedX else config.centerNormalizedX
            val startY = if (_state.value.isFreeLookActive) config.freeLookNormalizedY else config.centerNormalizedY
            _state.value = _state.value.copy(
                isShootingMode = true,
                cameraPointerX = startX,
                cameraPointerY = startY
            )
            dispatcher.updateMouseLookPointer(TouchAction.DOWN, startX, startY, currentOwnerTag())
            isCameraPointerDown = true
        } else {
            if (isCameraPointerDown) {
                dispatcher.updateMouseLookPointer(TouchAction.UP, _state.value.cameraPointerX, _state.value.cameraPointerY, currentOwnerTag())
                isCameraPointerDown = false
            }
            _state.value = _state.value.copy(
                isShootingMode = false,
                isFreeLookActive = false,
                isSmartDragActive = false,
                cameraPointerX = config.centerNormalizedX,
                cameraPointerY = config.centerNormalizedY
            )
            smoothedDx = 0f
            smoothedDy = 0f
        }
    }

    /**
     * Free Look (Eye Button): Shifts Pointer ID 1 to Eye coordinates while held,
     * and snaps back to main camera center immediately on release.
     */
    suspend fun setFreeLookActive(active: Boolean) {
        if (!config.freeLookEnabled && active) return
        if (_state.value.isFreeLookActive == active) return

        if (isCameraPointerDown) {
            dispatcher.updateMouseLookPointer(TouchAction.UP, _state.value.cameraPointerX, _state.value.cameraPointerY, currentOwnerTag())
            isCameraPointerDown = false
        }

        val targetX = if (active) config.freeLookNormalizedX else config.centerNormalizedX
        val targetY = if (active) config.freeLookNormalizedY else config.centerNormalizedY

        _state.value = _state.value.copy(
            isFreeLookActive = active,
            cameraPointerX = targetX,
            cameraPointerY = targetY
        )

        if (active || _state.value.isShootingMode) {
            dispatcher.updateMouseLookPointer(TouchAction.DOWN, targetX, targetY, currentOwnerTag())
            isCameraPointerDown = true
        }
    }

    /**
     * Processes physical mouse relative deltas (in normalized screen fraction units, e.g. -0.05..+0.05).
     */
    suspend fun onMouseDelta(rawDeltaX: Float, rawDeltaY: Float) {
        val current = _state.value

        // In Cursor Mode (when Free Look is not held), move the on-screen virtual gaming cursor
        if (!current.isShootingMode && !current.isFreeLookActive) {
            val newCursorX = (current.cursorNormX + rawDeltaX * config.sensitivityX).coerceIn(0.0f, 1.0f)
            val newCursorY = (current.cursorNormY + rawDeltaY * config.sensitivityY).coerceIn(0.0f, 1.0f)
            _state.value = current.copy(cursorNormX = newCursorX, cursorNormY = newCursorY)
            return
        }

        // In Shooting Mode or Free Look Mode: apply invert, acceleration, sensitivity, and smoothing
        val signedDx = if (config.invertX) -rawDeltaX else rawDeltaX
        val signedDy = if (config.invertY) -rawDeltaY else rawDeltaY

        val speed = sqrt(signedDx * signedDx + signedDy * signedDy)
        val accelMultiplier = 1.0f + (speed * config.acceleration * 10.0f)

        val scaledDx = signedDx * config.sensitivityX * accelMultiplier
        val scaledDy = signedDy * config.sensitivityY * accelMultiplier

        val alpha = 1.0f - config.smoothing.coerceIn(0.0f, 0.85f)
        smoothedDx = alpha * scaledDx + (1.0f - alpha) * smoothedDx
        smoothedDy = alpha * scaledDy + (1.0f - alpha) * smoothedDy

        var nextX = current.cameraPointerX + smoothedDx
        var nextY = current.cameraPointerY + smoothedDy

        // Check Infinite 360° Turn Auto Edge-Reset
        val minBound = config.edgeThreshold
        val maxBound = 1.0f - config.edgeThreshold
        if (config.autoEdgeReset && !current.isFreeLookActive &&
            (nextX <= minBound || nextX >= maxBound || nextY <= minBound || nextY >= maxBound)
        ) {
            // Instant invisible lift-and-recenter
            val clampedEdgeX = nextX.coerceIn(0.02f, 0.98f)
            val clampedEdgeY = nextY.coerceIn(0.02f, 0.98f)
            dispatcher.updateMouseLookPointer(TouchAction.MOVE, clampedEdgeX, clampedEdgeY, currentOwnerTag())
            dispatcher.updateMouseLookPointer(TouchAction.UP, clampedEdgeX, clampedEdgeY, currentOwnerTag())

            val resetX = config.centerNormalizedX
            val resetY = config.centerNormalizedY
            dispatcher.updateMouseLookPointer(TouchAction.DOWN, resetX, resetY, currentOwnerTag())
            isCameraPointerDown = true

            // Carry over residual delta after recenter so turn remains completely seamless
            val carryX = (resetX + smoothedDx * 0.5f).coerceIn(minBound, maxBound)
            val carryY = (resetY + smoothedDy * 0.5f).coerceIn(minBound, maxBound)
            dispatcher.updateMouseLookPointer(TouchAction.MOVE, carryX, carryY, currentOwnerTag())

            _state.value = _state.value.copy(
                cameraPointerX = carryX,
                cameraPointerY = carryY,
                edgeResetCount = _state.value.edgeResetCount + 1
            )
            return
        }

        nextX = nextX.coerceIn(0.02f, 0.98f)
        nextY = nextY.coerceIn(0.02f, 0.98f)

        if (!isCameraPointerDown) {
            dispatcher.updateMouseLookPointer(TouchAction.DOWN, current.cameraPointerX, current.cameraPointerY, currentOwnerTag())
            isCameraPointerDown = true
        }
        dispatcher.updateMouseLookPointer(TouchAction.MOVE, nextX, nextY, currentOwnerTag())
        _state.value = _state.value.copy(cameraPointerX = nextX, cameraPointerY = nextY)
    }

    /**
     * Smart Drag / Vertical Aim Compensation while primary fire is held in Shooting Mode.
     */
    fun setSmartDragActive(isFiring: Boolean) {
        val active = isFiring && config.smartDragEnabled && _state.value.isShootingMode
        _state.value = _state.value.copy(isSmartDragActive = active)
    }

    suspend fun applySmartDragTick(deltaTimeSec: Float = 0.016f) {
        val current = _state.value
        if (!current.isShootingMode || !current.isSmartDragActive || !config.smartDragEnabled) return
        val dy = config.smartDragVerticalSpeed * deltaTimeSec
        val dx = config.smartDragHorizontalSpeed * deltaTimeSec
        if (abs(dx) > 0.00001f || abs(dy) > 0.00001f) {
            onMouseDelta(dx, dy)
        }
    }

    suspend fun resetAndRelease() {
        if (isCameraPointerDown) {
            dispatcher.updateMouseLookPointer(TouchAction.UP, _state.value.cameraPointerX, _state.value.cameraPointerY, currentOwnerTag())
            isCameraPointerDown = false
        }
        smoothedDx = 0f
        smoothedDy = 0f
        _state.value = MouseLookState(
            isShootingMode = false,
            isFreeLookActive = false,
            isSmartDragActive = false,
            cursorNormX = 0.5f,
            cursorNormY = 0.5f,
            cameraPointerX = config.centerNormalizedX,
            cameraPointerY = config.centerNormalizedY,
            edgeResetCount = _state.value.edgeResetCount
        )
    }

    private fun currentOwnerTag(): String {
        return if (_state.value.isFreeLookActive) "FREE_LOOK_EYE" else "MOUSE_LOOK"
    }
}
