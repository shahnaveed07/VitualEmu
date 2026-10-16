package com.vitualemu.app.domain.validation

import com.vitualemu.app.domain.model.Macro
import com.vitualemu.app.domain.model.Mapping
import com.vitualemu.app.domain.model.Profile

enum class ConflictResolutionPolicy {
    WARN_AND_ALLOW,
    PRIORITIZE_PRIMARY,
    BLOCK
}

data class MappingConflict(
    val keyCode: Int,
    val keyLabel: String,
    val conflictingMappingIds: List<String>,
    val description: String
)

object ValidationRules {
    const val MAX_MACRO_STEPS = 10
    const val MIN_DELAY_MS = 10L
    const val MAX_DELAY_MS = 2000L

    fun clampCoordinate(value: Float): Float {
        return value.coerceIn(0.0f, 1.0f)
    }

    fun validateMapping(mapping: Mapping): Mapping {
        return mapping.copy(
            normalizedX = clampCoordinate(mapping.normalizedX),
            normalizedY = clampCoordinate(mapping.normalizedY),
            normalizedWidth = mapping.normalizedWidth.coerceIn(0.02f, 0.5f),
            normalizedHeight = mapping.normalizedHeight.coerceIn(0.02f, 0.5f),
            sensitivity = mapping.sensitivity.coerceIn(0.1f, 5.0f)
        )
    }

    fun findConflicts(mappings: List<Mapping>): List<MappingConflict> {
        val conflicts = mutableListOf<MappingConflict>()
        val byKey = mappings.filter { it.keyCode > 0 }.groupBy { it.keyCode }

        for ((keyCode, group) in byKey) {
            if (group.size > 1) {
                val label = group.firstOrNull()?.keyLabel?.ifBlank { "KeyCode $keyCode" } ?: "KeyCode $keyCode"
                val names = group.joinToString(", ") { it.name }
                conflicts.add(
                    MappingConflict(
                        keyCode = keyCode,
                        keyLabel = label,
                        conflictingMappingIds = group.map { it.id },
                        description = "Key '$label' is assigned to multiple controls: $names"
                    )
                )
            }
        }
        return conflicts
    }

    fun validateProfile(profile: Profile): List<String> {
        val errors = mutableListOf<String>()
        if (profile.name.isBlank()) {
            errors.add("Profile name cannot be blank")
        }
        if (profile.mappings.isEmpty()) {
            errors.add("Profile contains no mapped controls")
        }
        for (macro in profile.macros) {
            if (macro.steps.size > MAX_MACRO_STEPS) {
                errors.add("Macro '${macro.name}' exceeds maximum allowed $MAX_MACRO_STEPS steps")
            }
        }
        return errors
    }
}
