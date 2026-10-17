package com.dsc.virtualemu.runtime.mapping

import com.dsc.virtualemu.domain.model.Mapping
import com.dsc.virtualemu.domain.model.MappingType
import com.dsc.virtualemu.domain.model.Profile
import com.dsc.virtualemu.domain.usecase.HeldInputRegistry
import com.dsc.virtualemu.runtime.input.MultiTouchDispatcher
import com.dsc.virtualemu.runtime.macro.MacroEngine
import com.dsc.virtualemu.runtime.mouselook.MouseLookEngine
import kotlinx.coroutines.delay

class KeymapTranslationEngine(
    private val dispatcher: MultiTouchDispatcher,
    private val mouseLookEngine: MouseLookEngine,
    private val macroEngine: MacroEngine
) {
    companion object {
        const val KEYCODE_W = 51
        const val KEYCODE_S = 47
        const val KEYCODE_A = 29
        const val KEYCODE_D = 32
        const val KEYCODE_MOUSE_LCLICK = 102 // KEYCODE_BUTTON_L1
        const val KEYCODE_MOUSE_RCLICK = 103 // KEYCODE_BUTTON_R1
    }

    private val toggleStates = mutableSetOf<String>()

    suspend fun handleKeyEvent(profile: Profile, keyCode: Int, isDown: Boolean): String {
        if (isDown) {
            HeldInputRegistry.recordKeyDown(keyCode)
        } else {
            HeldInputRegistry.recordKeyUp(keyCode)
        }

        val heldKeys = HeldInputRegistry.heldKeysFlow.value
        val mouseCfg = profile.mouseLookConfig
        mouseLookEngine.updateConfig(mouseCfg)

        // 1. Check Shooting Mode Toggle Key
        if (isDown && keyCode == mouseCfg.toggleKeyCode) {
            val shooting = mouseLookEngine.toggleShootingMode()
            return if (shooting) "SHOOTING_MODE_ON (Pointer 1 Locked)" else "CURSOR_MODE_ON (Mouse Unlocked)"
        }

        // 2. Check Free-Look (Eye Button)
        val freeLookMapping = profile.mappings.find { it.type == MappingType.FREE_LOOK_EYE && it.keyCode == keyCode }
        if (keyCode == mouseCfg.freeLookKeyCode || freeLookMapping != null) {
            if (freeLookMapping != null) {
                mouseLookEngine.updateConfig(
                    mouseCfg.copy(
                        freeLookNormalizedX = freeLookMapping.normalizedX,
                        freeLookNormalizedY = freeLookMapping.normalizedY
                    )
                )
            }
            mouseLookEngine.setFreeLookActive(isDown)
            return if (isDown) "FREE_LOOK_ACTIVE (Eye Pointer 1)" else "FREE_LOOK_RELEASED (Main Camera)"
        }

        // 3. Check WASD Virtual Thumbstick (Pointer ID 0)
        if (keyCode == KEYCODE_W || keyCode == KEYCODE_S || keyCode == KEYCODE_A || keyCode == KEYCODE_D) {
            val wasdMapping = profile.mappings.find { it.type == MappingType.WASD_STICK }
            if (wasdMapping != null) {
                val up = heldKeys.contains(KEYCODE_W)
                val down = heldKeys.contains(KEYCODE_S)
                val left = heldKeys.contains(KEYCODE_A)
                val right = heldKeys.contains(KEYCODE_D)
                val radius = (wasdMapping.normalizedWidth / 2f).coerceIn(0.04f, 0.18f)
                dispatcher.updateWasdStick(
                    up = up,
                    down = down,
                    left = left,
                    right = right,
                    centerNormX = wasdMapping.normalizedX,
                    centerNormY = wasdMapping.normalizedY,
                    radius = radius
                )
                return "WASD_VECTOR(W=$up,A=$left,S=$down,D=$right)"
            }
        }

        // 4. In Cursor Mode, Left Click taps directly at the virtual cursor coordinate
        if (keyCode == KEYCODE_MOUSE_LCLICK && !mouseLookEngine.state.value.isShootingMode) {
            val cursorX = mouseLookEngine.state.value.cursorNormX
            val cursorY = mouseLookEngine.state.value.cursorNormY
            val ownerId = "CURSOR_CLICK"
            return if (isDown) {
                dispatcher.touchDownDynamic(ownerId, cursorX, cursorY)
                "CURSOR_TAP_DOWN(%.2f, %.2f)".format(cursorX, cursorY)
            } else {
                dispatcher.touchUpDynamic(ownerId)
                "CURSOR_TAP_UP(%.2f, %.2f)".format(cursorX, cursorY)
            }
        }

        // 5. Check Direct Mappings (TAP, HOLD, FIRE_WEAPON, SWIPE, TAP_REPEAT, MACRO_TRIGGER)
        val matchingMappings = profile.mappings.filter {
            (it.keyCode == keyCode || (it.altKeyCode != 0 && it.altKeyCode == keyCode)) &&
                    it.type != MappingType.WASD_STICK &&
                    it.type != MappingType.AIM_LOOK
        }
        for (mapping in matchingMappings) {
            dispatchMappingControl(profile, mapping, isDown, heldKeys)
        }

        // 6. Check standalone Macros triggered by keyCode
        val standaloneMacros = profile.macros.filter { it.triggerKeyCode == keyCode }
        for (macro in standaloneMacros) {
            macroEngine.triggerMacroOnKeyEvent(macro, isDown, heldKeys)
        }

        return if (matchingMappings.isNotEmpty() || standaloneMacros.isNotEmpty()) {
            val names = (matchingMappings.map { it.name } + standaloneMacros.map { it.name }).joinToString(", ")
            "${if (isDown) "TRIGGER_DOWN" else "TRIGGER_UP"}: $names"
        } else {
            "${if (isDown) "KEY_DOWN" else "KEY_UP"} ($keyCode)"
        }
    }

    private suspend fun dispatchMappingControl(
        profile: Profile,
        mapping: Mapping,
        isDown: Boolean,
        heldKeys: Set<Int>
    ) {
        val ownerId = "MAP_${mapping.id}"
        when (mapping.type) {
            MappingType.TAP, MappingType.HOLD -> {
                if (mapping.isToggle) {
                    if (isDown) {
                        if (toggleStates.contains(mapping.id)) {
                            toggleStates.remove(mapping.id)
                            dispatcher.touchUpDynamic(ownerId)
                        } else {
                            toggleStates.add(mapping.id)
                            dispatcher.touchDownDynamic(ownerId, mapping.normalizedX, mapping.normalizedY)
                        }
                    }
                } else {
                    if (isDown) {
                        dispatcher.touchDownDynamic(ownerId, mapping.normalizedX, mapping.normalizedY)
                    } else {
                        dispatcher.touchUpDynamic(ownerId)
                    }
                }
            }
            MappingType.FIRE_WEAPON -> {
                if (isDown) {
                    dispatcher.touchDownDynamic(ownerId, mapping.normalizedX, mapping.normalizedY)
                    mouseLookEngine.setSmartDragActive(true)
                } else {
                    mouseLookEngine.setSmartDragActive(false)
                    dispatcher.touchUpDynamic(ownerId)
                }
            }
            MappingType.SWIPE -> {
                if (isDown) {
                    dispatcher.touchDownDynamic(ownerId, mapping.normalizedX, mapping.normalizedY)
                    val steps = 4
                    for (i in 1..steps) {
                        val t = i.toFloat() / steps.toFloat()
                        val ix = mapping.normalizedX + (mapping.endNormalizedX - mapping.normalizedX) * t
                        val iy = mapping.normalizedY + (mapping.endNormalizedY - mapping.normalizedY) * t
                        delay(12L)
                        dispatcher.touchMoveDynamic(ownerId, ix, iy)
                    }
                    dispatcher.touchUpDynamic(ownerId)
                }
            }
            MappingType.TAP_REPEAT -> {
                if (isDown) {
                    val delayMs = mapping.repeatDelayMs.coerceAtLeast(20L)
                    dispatcher.touchDownDynamic(ownerId, mapping.normalizedX, mapping.normalizedY)
                    delay(delayMs)
                    dispatcher.touchUpDynamic(ownerId)
                } else {
                    dispatcher.touchUpDynamic(ownerId)
                }
            }
            MappingType.MACRO_TRIGGER -> {
                val targetMacro = profile.macros.find { it.id == mapping.macroId }
                if (targetMacro != null) {
                    macroEngine.triggerMacroOnKeyEvent(targetMacro, isDown, heldKeys)
                }
            }
            else -> Unit
        }
    }

    suspend fun neutralizeAll(sessionId: String = "default") {
        toggleStates.clear()
        macroEngine.cancelAllMacros()
        mouseLookEngine.resetAndRelease()
        dispatcher.releaseAllSlots(sessionId)
    }
}
