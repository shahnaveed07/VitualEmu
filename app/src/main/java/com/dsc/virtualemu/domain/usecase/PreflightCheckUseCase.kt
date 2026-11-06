package com.dsc.virtualemu.domain.usecase

import com.dsc.virtualemu.domain.model.BridgeHealthState
import com.dsc.virtualemu.domain.model.BridgeStatus
import com.dsc.virtualemu.domain.model.DeviceInfo
import com.dsc.virtualemu.domain.model.DeviceType
import com.dsc.virtualemu.domain.model.Game
import com.dsc.virtualemu.domain.model.PreflightResult
import com.dsc.virtualemu.domain.model.Profile
import com.dsc.virtualemu.domain.validation.ValidationRules
import com.dsc.virtualemu.runtime.capture.CaptureSource
import com.dsc.virtualemu.runtime.capture.CaptureSourceStatus

class PreflightCheckUseCase {
    operator fun invoke(
        bridgeStatus: BridgeStatus,
        hasOverlayPermission: Boolean,
        connectedDevices: List<DeviceInfo>,
        selectedGame: Game?,
        activeProfile: Profile?,
        captureSourceStatus: CaptureSourceStatus? = null
    ): PreflightResult {
        val blockingErrors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val bridgeReady = bridgeStatus.state == BridgeHealthState.CONNECTED && bridgeStatus.isAuthorized
        if (!bridgeReady) {
            blockingErrors.add("Input bridge (${bridgeStatus.type.displayName}) is not connected or authorized.")
        }

        if (!hasOverlayPermission) {
            warnings.add("Overlay permission not granted; floating HUD and transparent touch capture will be unavailable.")
        }

        val hasKeyboard = connectedDevices.any { it.type == DeviceType.KEYBOARD && it.isConnected }
        val hasMouse = connectedDevices.any { it.type == DeviceType.MOUSE && it.isConnected }

        if (!hasKeyboard && !hasMouse) {
            warnings.add("No physical keyboard or mouse detected. Input translation requires connected peripherals.")
        } else if (!hasKeyboard) {
            warnings.add("No physical keyboard detected. Only mouse events can be captured.")
        }

        var captureReady = true
        if (captureSourceStatus != null) {
            val hasKbCapture = captureSourceStatus.keyboardSource != CaptureSource.NONE
            val hasMouseCapture = captureSourceStatus.mouseSource != CaptureSource.NONE

            if (!hasKbCapture && !hasMouseCapture) {
                captureReady = false
                warnings.add("No physical input capture source active. Enable Accessibility Service for keyboard or run Shizuku for mouse.")
            } else {
                if (hasKeyboard && !hasKbCapture) {
                    warnings.add("Keyboard connected but no keyboard capture source active. Enable Accessibility Service.")
                }
                if (hasMouse && !hasMouseCapture) {
                    warnings.add("Mouse connected but Shizuku is not running. Relative mouse-look requires Shizuku getevent.")
                }
            }
        }

        if (selectedGame == null) {
            blockingErrors.add("No target game selected. Choose a game from the Games library.")
        }

        var profileValid = false
        if (activeProfile == null) {
            blockingErrors.add("No active control profile selected for this game.")
        } else {
            val profileErrors = ValidationRules.validateProfile(activeProfile)
            if (profileErrors.isNotEmpty()) {
                blockingErrors.addAll(profileErrors)
            } else {
                profileValid = true
            }

            val conflicts = ValidationRules.findConflicts(activeProfile.mappings)
            if (conflicts.isNotEmpty()) {
                warnings.add("Profile has ${conflicts.size} key conflict(s): ${conflicts.first().description}")
            }
        }

        val isReady = blockingErrors.isEmpty()

        return PreflightResult(
            isReady = isReady,
            bridgeReady = bridgeReady,
            overlayReady = hasOverlayPermission,
            devicesConnected = hasKeyboard || hasMouse,
            profileValid = profileValid,
            captureReady = captureReady,
            warnings = warnings,
            blockingErrors = blockingErrors
        )
    }
}
