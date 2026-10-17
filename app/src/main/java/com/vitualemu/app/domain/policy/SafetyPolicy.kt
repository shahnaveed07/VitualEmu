package com.vitualemu.app.domain.policy

import com.vitualemu.app.domain.model.Macro
import com.vitualemu.app.domain.model.MacroStep
import com.vitualemu.app.domain.model.MouseLookConfig

object SafetyPolicy {
    const val WASD_POINTER_SLOT = 0
    const val MOUSE_LOOK_POINTER_SLOT = 1
    const val FIRST_DYNAMIC_POINTER_SLOT = 2
    const val LAST_DYNAMIC_POINTER_SLOT = 9
    const val MAX_SIMULTANEOUS_POINTERS = 10

    const val MAX_MACRO_STEPS = 32
    const val MIN_STEP_DELAY_MS = 16L
    const val MIN_HOLD_DURATION_MS = 16L
    const val MAX_MACRO_DURATION_MS = 10_000L
    const val MAX_LOOP_ITERATIONS = 50

    fun isDynamicSlotValid(pointerId: Int): Boolean {
        return pointerId in FIRST_DYNAMIC_POINTER_SLOT..LAST_DYNAMIC_POINTER_SLOT
    }

    fun isPointerSlotValid(pointerId: Int): Boolean {
        return pointerId in 0 until MAX_SIMULTANEOUS_POINTERS
    }

    fun sanitizeMacroStep(step: MacroStep): MacroStep {
        return step.copy(
            normalizedX = step.normalizedX.coerceIn(0.0f, 1.0f),
            normalizedY = step.normalizedY.coerceIn(0.0f, 1.0f),
            endNormalizedX = step.endNormalizedX.coerceIn(0.0f, 1.0f),
            endNormalizedY = step.endNormalizedY.coerceIn(0.0f, 1.0f),
            delayMs = step.delayMs.coerceIn(MIN_STEP_DELAY_MS, MAX_MACRO_DURATION_MS),
            holdDurationMs = step.holdDurationMs.coerceIn(MIN_HOLD_DURATION_MS, MAX_MACRO_DURATION_MS)
        )
    }

    fun sanitizeMacro(macro: Macro): Macro {
        val boundedSteps = macro.steps
            .take(MAX_MACRO_STEPS)
            .mapIndexed { idx, step -> sanitizeMacroStep(step).copy(stepIndex = idx) }
        return macro.copy(
            steps = boundedSteps,
            maxLoopIterations = macro.maxLoopIterations.coerceIn(1, MAX_LOOP_ITERATIONS)
        )
    }

    fun validateMacroTotalDuration(macro: Macro): Boolean {
        if (macro.steps.size > MAX_MACRO_STEPS) return false
        val singlePassMs = macro.steps.sumOf { it.delayMs + it.holdDurationMs }
        return singlePassMs <= MAX_MACRO_DURATION_MS
    }

    fun sanitizeMouseLookConfig(config: MouseLookConfig): MouseLookConfig {
        return config.copy(
            sensitivityX = config.sensitivityX.coerceIn(0.1f, 5.0f),
            sensitivityY = config.sensitivityY.coerceIn(0.1f, 5.0f),
            smoothing = config.smoothing.coerceIn(0.0f, 0.9f),
            acceleration = config.acceleration.coerceIn(0.0f, 1.0f),
            edgeThreshold = config.edgeThreshold.coerceIn(0.03f, 0.25f),
            centerNormalizedX = config.centerNormalizedX.coerceIn(0.1f, 0.9f),
            centerNormalizedY = config.centerNormalizedY.coerceIn(0.1f, 0.9f),
            freeLookNormalizedX = config.freeLookNormalizedX.coerceIn(0.05f, 0.95f),
            freeLookNormalizedY = config.freeLookNormalizedY.coerceIn(0.05f, 0.95f),
            smartDragVerticalSpeed = config.smartDragVerticalSpeed.coerceIn(-0.5f, 0.5f),
            smartDragHorizontalSpeed = config.smartDragHorizontalSpeed.coerceIn(-0.5f, 0.5f)
        )
    }
}
