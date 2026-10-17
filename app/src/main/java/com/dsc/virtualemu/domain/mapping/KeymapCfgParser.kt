package com.dsc.virtualemu.domain.mapping

import com.dsc.virtualemu.domain.model.Macro
import com.dsc.virtualemu.domain.model.MacroStep
import com.dsc.virtualemu.domain.model.MacroTriggerMode
import com.dsc.virtualemu.domain.model.Mapping
import com.dsc.virtualemu.domain.model.MappingType
import com.dsc.virtualemu.domain.model.MouseLookConfig
import com.dsc.virtualemu.domain.model.Profile
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ParsedCfgData(
    val parserVersion: String,
    val updateVersion: String,
    val profiles: List<Profile>,
    val totalControlCount: Int,
    val warnings: List<String>
)

object KeymapCfgParser {

    fun parseCfgJson(jsonString: String, gameId: String): ParsedCfgData {
        val warnings = mutableListOf<String>()
        val profiles = mutableListOf<Profile>()
        var totalControlCount = 0

        val root = try {
            JSONObject(jsonString)
        } catch (e: Exception) {
            return ParsedCfgData(
                parserVersion = "1.0",
                updateVersion = "1.0",
                profiles = emptyList(),
                totalControlCount = 0,
                warnings = listOf("Failed to parse JSON configuration: ${e.localizedMessage}")
            )
        }

        val metaObj = root.optJSONObject("MetaData")
        val parserVersion = metaObj?.optString("ParserVersion", "1.0") ?: "1.0"
        val updateVersion = metaObj?.optString("UpdateVersion", "1.0") ?: "1.0"

        val schemesArray = root.optJSONArray("ControlSchemes") ?: JSONArray()

        for (i in 0 until schemesArray.length()) {
            val schemeObj = schemesArray.optJSONObject(i) ?: continue
            val schemeName = schemeObj.optString("Name", "Scheme ${i + 1}")
            val isSelected = schemeObj.optBoolean("Selected", i == 0)
            val controlsArray = schemeObj.optJSONArray("GameControls") ?: JSONArray()

            val profileId = UUID.randomUUID().toString()
            val mappings = mutableListOf<Mapping>()
            val macros = mutableListOf<Macro>()
            var mouseLookConfig = MouseLookConfig()

            for (j in 0 until controlsArray.length()) {
                val ctrl = controlsArray.optJSONObject(j) ?: continue
                totalControlCount++

                val rawType = ctrl.optString("Type", ctrl.optString("\$type", "Tap")).substringBefore(",")
                val xPercent = ctrl.optDouble("X", 50.0)
                val yPercent = ctrl.optDouble("Y", 50.0)

                val normX = (xPercent / 100.0).coerceIn(0.0, 1.0).toFloat()
                val normY = (yPercent / 100.0).coerceIn(0.0, 1.0).toFloat()

                val primaryKeyStr = ctrl.optString("Key", "")
                val altKeyStr = ctrl.optString("Key_alt1", "")

                val primaryCode = KeyNameMapper.parseKeyNameToCode(primaryKeyStr)
                val altCode = KeyNameMapper.parseKeyNameToCode(altKeyStr)

                val category = ctrl.optString("GuidanceCategory", "Imported Controls")
                val guidanceObj = ctrl.optJSONObject("Guidance")
                val guidanceText = guidanceObj?.optString("Key", "") ?: ""

                when (rawType.trim()) {
                    "Tap" -> {
                        mappings.add(
                            Mapping(
                                id = UUID.randomUUID().toString(),
                                profileId = profileId,
                                name = guidanceText.ifBlank { "Tap ($primaryKeyStr)" },
                                type = MappingType.TAP,
                                keyCode = primaryCode,
                                keyLabel = primaryKeyStr,
                                altKeyCode = altCode,
                                altKeyLabel = altKeyStr,
                                normalizedX = normX,
                                normalizedY = normY,
                                guidanceCategory = category,
                                guidanceText = guidanceText
                            )
                        )
                    }

                    "Pan" -> {
                        val sensX = ctrl.optDouble("Sensitivity", 1.0).toFloat()
                        val sensYRatio = ctrl.optDouble("SensitivityRatioY", 1.0).toFloat()
                        val startStopKey = ctrl.optString("KeyStartStop", "X")
                        val startStopCode = KeyNameMapper.parseKeyNameToCode(startStopKey)

                        val lookAroundXPercent = ctrl.optDouble("LookAroundX", 65.0)
                        val lookAroundYPercent = ctrl.optDouble("LookAroundY", 35.0)

                        mouseLookConfig = MouseLookConfig(
                            sensitivityX = sensX,
                            sensitivityY = sensX * sensYRatio,
                            centerNormalizedX = normX,
                            centerNormalizedY = normY,
                            freeLookNormalizedX = (lookAroundXPercent / 100.0).coerceIn(0.0, 1.0).toFloat(),
                            freeLookNormalizedY = (lookAroundYPercent / 100.0).coerceIn(0.0, 1.0).toFloat(),
                            toggleKeyCode = startStopCode,
                            toggleKeyLabel = startStopKey
                        )

                        mappings.add(
                            Mapping(
                                id = UUID.randomUUID().toString(),
                                profileId = profileId,
                                name = "360° Mouse Aim & Look",
                                type = MappingType.AIM_LOOK,
                                keyCode = startStopCode,
                                keyLabel = startStopKey,
                                altKeyCode = altCode,
                                altKeyLabel = altKeyStr,
                                normalizedX = normX,
                                normalizedY = normY,
                                sensitivity = sensX,
                                sensitivityRatioY = sensYRatio,
                                guidanceCategory = category,
                                guidanceText = guidanceText
                            )
                        )
                    }

                    "Dpad" -> {
                        val keyUp = ctrl.optString("KeyUp", "W")
                        val keyDown = ctrl.optString("KeyDown", "S")
                        val keyLeft = ctrl.optString("KeyLeft", "A")
                        val keyRight = ctrl.optString("KeyRight", "D")
                        val radiusPercent = ctrl.optDouble("XRadius", 8.0)
                        val normRadius = (radiusPercent / 100.0).coerceIn(0.02, 0.4).toFloat()

                        mappings.add(
                            Mapping(
                                id = UUID.randomUUID().toString(),
                                profileId = profileId,
                                name = "WASD Movement Joystick",
                                type = MappingType.WASD_STICK,
                                keyCode = KeyNameMapper.parseKeyNameToCode(keyUp),
                                keyLabel = "$keyUp/$keyLeft/$keyDown/$keyRight",
                                normalizedX = normX,
                                normalizedY = normY,
                                normalizedWidth = normRadius * 2f,
                                normalizedHeight = normRadius * 2f,
                                guidanceCategory = category,
                                guidanceText = guidanceText
                            )
                        )
                    }

                    "TapRepeat" -> {
                        val count = ctrl.optInt("Count", 10)
                        val delay = ctrl.optLong("Delay", 50L).coerceAtLeast(20L)
                        val repeatUntilUp = ctrl.optBoolean("RepeatUntilKeyUp", true)

                        mappings.add(
                            Mapping(
                                id = UUID.randomUUID().toString(),
                                profileId = profileId,
                                name = guidanceText.ifBlank { "Rapid Tap ($primaryKeyStr)" },
                                type = MappingType.TAP_REPEAT,
                                keyCode = primaryCode,
                                keyLabel = primaryKeyStr,
                                altKeyCode = altCode,
                                altKeyLabel = altKeyStr,
                                normalizedX = normX,
                                normalizedY = normY,
                                repeatDelayMs = delay,
                                repeatUntilKeyUp = repeatUntilUp,
                                guidanceCategory = category,
                                guidanceText = guidanceText
                            )
                        )
                    }

                    "Script" -> {
                        val commandsArray = ctrl.optJSONArray("Commands") ?: JSONArray()
                        val macroId = UUID.randomUUID().toString()
                        val steps = mutableListOf<MacroStep>()

                        for (k in 0 until commandsArray.length()) {
                            val cmdStr = commandsArray.optString(k, "").trim()
                            if (cmdStr.isBlank()) continue

                            val parts = cmdStr.split("\\s+".toRegex())
                            val op = parts.getOrNull(0)?.uppercase() ?: ""

                            if (op == "TAP" && parts.size >= 3) {
                                val cx = parts[1].toFloatOrNull() ?: 50f
                                val cy = parts[2].toFloatOrNull() ?: 50f
                                steps.add(
                                    MacroStep(
                                        id = UUID.randomUUID().toString(),
                                        macroId = macroId,
                                        stepIndex = steps.size,
                                        actionType = "TAP",
                                        normalizedX = (cx / 100.0f).coerceIn(0f, 1f),
                                        normalizedY = (cy / 100.0f).coerceIn(0f, 1f),
                                        delayMs = 40L,
                                        holdDurationMs = 40L
                                    )
                                )
                            } else if (op == "WAIT" && parts.size >= 2) {
                                val waitMs = parts[1].toLongOrNull() ?: 50L
                                if (steps.isNotEmpty()) {
                                    val last = steps.last()
                                    steps[steps.size - 1] = last.copy(delayMs = waitMs)
                                }
                            } else {
                                warnings.add("Unsupported script command in scheme '$schemeName': $cmdStr")
                            }
                        }

                        val macro = Macro(
                            id = macroId,
                            profileId = profileId,
                            name = guidanceText.ifBlank { "Script ($primaryKeyStr)" },
                            triggerKeyCode = primaryCode,
                            triggerKeyLabel = primaryKeyStr,
                            triggerMode = MacroTriggerMode.ON_PRESS,
                            steps = steps
                        )
                        macros.add(macro)

                        mappings.add(
                            Mapping(
                                id = UUID.randomUUID().toString(),
                                profileId = profileId,
                                name = macro.name,
                                type = MappingType.MACRO_TRIGGER,
                                keyCode = primaryCode,
                                keyLabel = primaryKeyStr,
                                altKeyCode = altCode,
                                altKeyLabel = altKeyStr,
                                normalizedX = normX,
                                normalizedY = normY,
                                macroId = macroId,
                                guidanceCategory = category,
                                guidanceText = guidanceText
                            )
                        )
                    }

                    else -> {
                        warnings.add("Unrecognized control type '$rawType' in scheme '$schemeName'")
                    }
                }
            }

            profiles.add(
                Profile(
                    profileId = profileId,
                    gameId = gameId,
                    name = schemeName,
                    isFavorite = isSelected,
                    mappings = mappings,
                    macros = macros,
                    mouseLookConfig = mouseLookConfig
                )
            )
        }

        return ParsedCfgData(
            parserVersion = parserVersion,
            updateVersion = updateVersion,
            profiles = profiles,
            totalControlCount = totalControlCount,
            warnings = warnings
        )
    }
}
