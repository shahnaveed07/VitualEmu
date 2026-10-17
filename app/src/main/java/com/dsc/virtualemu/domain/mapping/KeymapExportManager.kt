package com.dsc.virtualemu.domain.mapping

import com.dsc.virtualemu.domain.model.MappingType
import com.dsc.virtualemu.domain.model.Profile
import org.json.JSONArray
import org.json.JSONObject

object KeymapExportManager {

    fun exportToBlueStacksCfg(profiles: List<Profile>): String {
        val root = JSONObject()

        val metaObj = JSONObject()
        metaObj.put("ParserVersion", "17")
        metaObj.put("UpdateVersion", "3")
        metaObj.put("CloudUpdateTimeUTC", "2026-09-17 13:20:21")
        root.put("MetaData", metaObj)

        val schemesArray = JSONArray()

        for (profile in profiles) {
            val schemeObj = JSONObject()
            schemeObj.put("Name", profile.name)
            schemeObj.put("BuiltIn", false)
            schemeObj.put("Selected", profile.isFavorite)
            schemeObj.put("IsBookMarked", false)
            schemeObj.put("IsCategoryVisible", true)
            schemeObj.put("KeyboardLayout", "United States")

            val controlsArray = JSONArray()

            for (m in profile.mappings) {
                val ctrlObj = JSONObject()
                val xPercent = (m.normalizedX * 100.0f).toDouble()
                val yPercent = (m.normalizedY * 100.0f).toDouble()

                ctrlObj.put("X", xPercent)
                ctrlObj.put("Y", yPercent)
                ctrlObj.put("Key", m.keyLabel.ifBlank { KeyNameMapper.androidCodeToKeyName(m.keyCode) })
                ctrlObj.put("Key_alt1", m.altKeyLabel.ifBlank { if (m.altKeyCode != 0) KeyNameMapper.androidCodeToKeyName(m.altKeyCode) else "" })
                ctrlObj.put("ShowOnOverlay", true)
                ctrlObj.put("GuidanceCategory", m.guidanceCategory.ifBlank { "Basic controls" })

                val guidanceObj = JSONObject()
                guidanceObj.put("Key", m.guidanceText.ifBlank { m.name })
                ctrlObj.put("Guidance", guidanceObj)

                when (m.type) {
                    MappingType.TAP, MappingType.HOLD, MappingType.FIRE_WEAPON, MappingType.FREE_LOOK_EYE -> {
                        ctrlObj.put("Type", "Tap")
                        ctrlObj.put("\$type", "Tap, Bluestacks")
                    }

                    MappingType.AIM_LOOK -> {
                        ctrlObj.put("Type", "Pan")
                        ctrlObj.put("\$type", "Pan, Bluestacks")
                        ctrlObj.put("Sensitivity", m.sensitivity.toDouble())
                        ctrlObj.put("SensitivityRatioY", m.sensitivityRatioY.toDouble())
                        ctrlObj.put("KeyStartStop", m.keyLabel)
                        ctrlObj.put("LookAroundX", (profile.mouseLookConfig.freeLookNormalizedX * 100.0f).toDouble())
                        ctrlObj.put("LookAroundY", (profile.mouseLookConfig.freeLookNormalizedY * 100.0f).toDouble())
                    }

                    MappingType.WASD_STICK -> {
                        ctrlObj.put("Type", "Dpad")
                        ctrlObj.put("\$type", "Dpad, Bluestacks")
                        ctrlObj.put("KeyUp", "W")
                        ctrlObj.put("KeyDown", "S")
                        ctrlObj.put("KeyLeft", "A")
                        ctrlObj.put("KeyRight", "D")
                        ctrlObj.put("XRadius", (m.normalizedWidth / 2.0f * 100.0f).toDouble())
                    }

                    MappingType.TAP_REPEAT -> {
                        ctrlObj.put("Type", "TapRepeat")
                        ctrlObj.put("\$type", "TapRepeat, Bluestacks")
                        ctrlObj.put("Delay", m.repeatDelayMs)
                        ctrlObj.put("RepeatUntilKeyUp", m.repeatUntilKeyUp)
                    }

                    MappingType.MACRO_TRIGGER -> {
                        ctrlObj.put("Type", "Script")
                        ctrlObj.put("\$type", "Script, Bluestacks")
                        val commandsArray = JSONArray()

                        val matchedMacro = profile.macros.find { it.id == m.macroId }
                        if (matchedMacro != null) {
                            for (step in matchedMacro.steps) {
                                val sx = (step.normalizedX * 100.0f).toDouble()
                                val sy = (step.normalizedY * 100.0f).toDouble()
                                commandsArray.put("tap $sx $sy")
                                if (step.delayMs > 0) {
                                    commandsArray.put("wait ${step.delayMs}")
                                }
                            }
                        } else {
                            commandsArray.put("tap $xPercent $yPercent")
                        }
                        ctrlObj.put("Commands", commandsArray)
                    }

                    else -> {
                        ctrlObj.put("Type", "Tap")
                        ctrlObj.put("\$type", "Tap, Bluestacks")
                    }
                }

                controlsArray.put(ctrlObj)
            }

            schemeObj.put("GameControls", controlsArray)
            schemesArray.put(schemeObj)
        }

        root.put("ControlSchemes", schemesArray)
        return root.toString(4)
    }
}
