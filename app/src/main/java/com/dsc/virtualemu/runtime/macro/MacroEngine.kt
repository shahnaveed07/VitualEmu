package com.dsc.virtualemu.runtime.macro

import com.dsc.virtualemu.domain.model.Macro
import com.dsc.virtualemu.domain.model.MacroStep
import com.dsc.virtualemu.domain.model.MacroTriggerMode
import com.dsc.virtualemu.domain.policy.SafetyPolicy
import com.dsc.virtualemu.runtime.input.MultiTouchDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MacroEngine(
    private val dispatcher: MultiTouchDispatcher,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val activeJobs = mutableMapOf<String, Job>()
    private val _runningMacroIds = MutableStateFlow<Set<String>>(emptySet())
    val runningMacroIds: StateFlow<Set<String>> = _runningMacroIds.asStateFlow()

    fun triggerMacroOnKeyEvent(
        macro: Macro,
        isKeyDown: Boolean,
        heldKeyCodes: Set<Int>
    ) {
        val sanitized = SafetyPolicy.sanitizeMacro(macro)
        if (sanitized.steps.isEmpty()) return

        when (sanitized.triggerMode) {
            MacroTriggerMode.ON_PRESS -> {
                if (isKeyDown) launchSinglePass(sanitized)
            }
            MacroTriggerMode.ON_RELEASE -> {
                if (!isKeyDown) launchSinglePass(sanitized)
            }
            MacroTriggerMode.WHILE_HELD -> {
                if (isKeyDown) {
                    launchBoundedLoop(sanitized)
                } else {
                    stopMacro(sanitized.id)
                }
            }
            MacroTriggerMode.PRESS_AND_RELEASE -> {
                launchSinglePass(sanitized)
            }
            MacroTriggerMode.CHORD -> {
                if (isKeyDown) {
                    val modifierRequired = sanitized.chordModifierKeyCode
                    if (modifierRequired == 0 || heldKeyCodes.contains(modifierRequired)) {
                        launchSinglePass(sanitized)
                    }
                }
            }
        }
    }

    fun launchSinglePass(macro: Macro) {
        val sanitized = SafetyPolicy.sanitizeMacro(macro)
        stopMacro(sanitized.id)

        val ownerId = "MACRO_${sanitized.id}"
        val job = scope.launch {
            markRunning(sanitized.id, true)
            try {
                for (step in sanitized.steps) {
                    if (!isActive) break
                    executeStep(ownerId, step)
                }
            } finally {
                dispatcher.touchUpDynamic(ownerId)
                markRunning(sanitized.id, false)
            }
        }
        synchronized(activeJobs) {
            activeJobs[sanitized.id] = job
        }
    }

    fun launchBoundedLoop(macro: Macro) {
        val sanitized = SafetyPolicy.sanitizeMacro(macro)
        if (synchronized(activeJobs) { activeJobs[sanitized.id]?.isActive == true }) return

        val ownerId = "MACRO_${sanitized.id}"
        val maxIterations = sanitized.maxLoopIterations.coerceIn(1, SafetyPolicy.MAX_LOOP_ITERATIONS)

        val job = scope.launch {
            markRunning(sanitized.id, true)
            val startTime = System.currentTimeMillis()
            try {
                var iteration = 0
                while (isActive && iteration < maxIterations) {
                    if (System.currentTimeMillis() - startTime > SafetyPolicy.MAX_MACRO_DURATION_MS) {
                        break
                    }
                    for (step in sanitized.steps) {
                        if (!isActive) break
                        executeStep(ownerId, step)
                    }
                    iteration++
                }
            } finally {
                dispatcher.touchUpDynamic(ownerId)
                markRunning(sanitized.id, false)
            }
        }
        synchronized(activeJobs) {
            activeJobs[sanitized.id] = job
        }
    }

    suspend fun executeStep(ownerId: String, rawStep: MacroStep) {
        val step = SafetyPolicy.sanitizeMacroStep(rawStep)
        if (step.delayMs > 0) {
            delay(step.delayMs)
        }
        when (step.actionType.uppercase()) {
            "TAP" -> {
                dispatcher.touchDownDynamic(ownerId, step.normalizedX, step.normalizedY)
                delay(step.holdDurationMs)
                dispatcher.touchUpDynamic(ownerId)
            }
            "PRESS_HOLD" -> {
                dispatcher.touchDownDynamic(ownerId, step.normalizedX, step.normalizedY)
                delay(step.holdDurationMs)
            }
            "RELEASE" -> {
                dispatcher.touchUpDynamic(ownerId)
            }
            "SWIPE" -> {
                val slices = 5
                val sliceDelay = (step.holdDurationMs / slices).coerceAtLeast(8L)
                dispatcher.touchDownDynamic(ownerId, step.normalizedX, step.normalizedY)
                for (i in 1..slices) {
                    val t = i.toFloat() / slices.toFloat()
                    val ix = step.normalizedX + (step.endNormalizedX - step.normalizedX) * t
                    val iy = step.normalizedY + (step.endNormalizedY - step.normalizedY) * t
                    delay(sliceDelay)
                    dispatcher.touchMoveDynamic(ownerId, ix, iy)
                }
                dispatcher.touchUpDynamic(ownerId)
            }
            "WAIT" -> {
                delay(step.holdDurationMs)
            }
            else -> {
                dispatcher.touchDownDynamic(ownerId, step.normalizedX, step.normalizedY)
                delay(step.holdDurationMs)
                dispatcher.touchUpDynamic(ownerId)
            }
        }
    }

    fun stopMacro(macroId: String) {
        val job = synchronized(activeJobs) { activeJobs.remove(macroId) }
        job?.cancel()
        scope.launch {
            dispatcher.touchUpDynamic("MACRO_$macroId")
        }
        markRunning(macroId, false)
    }

    suspend fun cancelAllMacros() {
        val macroIds: List<String>
        synchronized(activeJobs) {
            macroIds = activeJobs.keys.toList()
            activeJobs.values.forEach { it.cancel() }
            activeJobs.clear()
        }
        scope.coroutineContext.cancelChildren()
        _runningMacroIds.value = emptySet()
        for (id in macroIds) {
            dispatcher.touchUpDynamic("MACRO_$id")
        }
    }

    private fun markRunning(macroId: String, running: Boolean) {
        _runningMacroIds.value = if (running) {
            _runningMacroIds.value + macroId
        } else {
            _runningMacroIds.value - macroId
        }
    }
}
