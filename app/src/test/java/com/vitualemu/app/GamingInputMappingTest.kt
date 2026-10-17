package com.vitualemu.app

import com.vitualemu.app.domain.model.BridgeHealthState
import com.vitualemu.app.domain.model.BridgeStatus
import com.vitualemu.app.domain.model.BridgeType
import com.vitualemu.app.domain.model.DeviceInfo
import com.vitualemu.app.domain.model.DeviceType
import com.vitualemu.app.domain.model.Game
import com.vitualemu.app.domain.model.Mapping
import com.vitualemu.app.domain.model.MappingType
import com.vitualemu.app.domain.model.Profile
import com.vitualemu.app.domain.usecase.HeldInputRegistry
import com.vitualemu.app.domain.usecase.PreflightCheckUseCase
import com.vitualemu.app.domain.validation.ValidationRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GamingInputMappingTest {

    @Test
    fun testHeldInputRegistry_atomicReleaseAllNeutralizesTouchesAndKeys() {
        HeldInputRegistry.releaseAll()
        assertEquals(0, HeldInputRegistry.getHeldKeyCount())
        assertEquals(0, HeldInputRegistry.getActiveTouchCount())

        // Simulate holding W (keycode 19), Space (keycode 62) and 2 touch pointers
        HeldInputRegistry.recordKeyDown(19)
        HeldInputRegistry.recordKeyDown(62)
        HeldInputRegistry.recordTouchDown(pointerId = 1, x = 0.2f, y = 0.7f, mappingId = "m1")
        HeldInputRegistry.recordTouchDown(pointerId = 2, x = 0.8f, y = 0.5f, mappingId = "m2")

        assertEquals(2, HeldInputRegistry.getHeldKeyCount())
        assertEquals(2, HeldInputRegistry.getActiveTouchCount())

        // Atomic release all
        val releasedPointers = HeldInputRegistry.releaseAll()
        assertEquals(2, releasedPointers.size)
        assertEquals(0, HeldInputRegistry.getHeldKeyCount())
        assertEquals(0, HeldInputRegistry.getActiveTouchCount())

        // Subsequent release is idempotent
        val emptyRelease = HeldInputRegistry.releaseAll()
        assertEquals(0, emptyRelease.size)
    }

    @Test
    fun testValidationRules_clampsCoordinatesBetweenZeroAndOne() {
        assertEquals(0.0f, ValidationRules.clampCoordinate(-0.5f), 0.0001f)
        assertEquals(1.0f, ValidationRules.clampCoordinate(1.5f), 0.0001f)
        assertEquals(0.75f, ValidationRules.clampCoordinate(0.75f), 0.0001f)
    }

    @Test
    fun testValidationRules_detectsDuplicateKeyConflicts() {
        val mapping1 = Mapping(
            id = "m1",
            profileId = "p1",
            name = "Fire",
            keyCode = 19,
            keyLabel = "W",
            type = MappingType.TAP,
            normalizedX = 0.2f,
            normalizedY = 0.2f
        )
        val mapping2 = Mapping(
            id = "m2",
            profileId = "p1",
            name = "Jump",
            keyCode = 19,
            keyLabel = "W",
            type = MappingType.TAP,
            normalizedX = 0.5f,
            normalizedY = 0.5f
        )

        val conflicts = ValidationRules.findConflicts(listOf(mapping1, mapping2))
        assertEquals(1, conflicts.size)
        assertTrue(conflicts[0].description.contains("assigned to multiple controls"))
    }

    @Test
    fun testPreflightCheckUseCase_failsWhenBridgeNotReady() {
        val useCase = PreflightCheckUseCase()
        val disconnectedStatus = BridgeStatus(
            type = BridgeType.SHIZUKU,
            state = BridgeHealthState.DISCONNECTED,
            isAuthorized = false,
            lastLatencyMs = -1L,
            detailMessage = "Shizuku service not running"
        )
        val devices = listOf(
            DeviceInfo(id = 1, name = "USB Gaming Keyboard", type = DeviceType.KEYBOARD, isConnected = true),
            DeviceInfo(id = 2, name = "USB Gaming Mouse", type = DeviceType.MOUSE, isConnected = true)
        )
        val game = Game(
            gameId = "g1",
            displayName = "Call of Duty",
            packageName = "com.activision.callofduty.shooter"
        )
        val profile = Profile(
            profileId = "p1",
            gameId = "g1",
            name = "Default",
            mappings = listOf(
                Mapping(profileId = "p1", name = "Fire", type = MappingType.TAP, keyCode = 19)
            )
        )

        val result = useCase(
            bridgeStatus = disconnectedStatus,
            hasOverlayPermission = true,
            connectedDevices = devices,
            selectedGame = game,
            activeProfile = profile
        )

        assertFalse(result.isReady)
        assertTrue(result.blockingErrors.any { it.contains("Input bridge") })
    }

    @Test
    fun testPreflightCheckUseCase_passesWhenAllConditionsMet() {
        val useCase = PreflightCheckUseCase()
        val readyStatus = BridgeStatus(
            type = BridgeType.SHIZUKU,
            state = BridgeHealthState.CONNECTED,
            isAuthorized = true,
            lastLatencyMs = 3L,
            detailMessage = "Connected"
        )
        val devices = listOf(
            DeviceInfo(id = 1, name = "USB Gaming Keyboard", type = DeviceType.KEYBOARD, isConnected = true),
            DeviceInfo(id = 2, name = "USB Gaming Mouse", type = DeviceType.MOUSE, isConnected = true)
        )
        val game = Game(
            gameId = "g1",
            displayName = "Call of Duty",
            packageName = "com.activision.callofduty.shooter"
        )
        val profile = Profile(
            profileId = "p1",
            gameId = "g1",
            name = "Default",
            mappings = listOf(
                Mapping(profileId = "p1", name = "Fire", type = MappingType.TAP, keyCode = 19)
            )
        )

        val result = useCase(
            bridgeStatus = readyStatus,
            hasOverlayPermission = true,
            connectedDevices = devices,
            selectedGame = game,
            activeProfile = profile
        )

        assertTrue(result.isReady)
        assertTrue(result.blockingErrors.isEmpty())
        assertTrue(result.bridgeReady)
        assertTrue(result.devicesConnected)
    }
}
