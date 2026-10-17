package com.vitualemu.app

import com.vitualemu.app.domain.model.BridgeHealthState
import com.vitualemu.app.domain.model.BridgeStatus
import com.vitualemu.app.domain.model.BridgeType
import com.vitualemu.app.domain.model.DeviceInfo
import com.vitualemu.app.domain.model.DeviceType
import com.vitualemu.app.domain.model.Game
import com.vitualemu.app.domain.model.Macro
import com.vitualemu.app.domain.model.MacroStep
import com.vitualemu.app.domain.model.Mapping
import com.vitualemu.app.domain.model.MappingType
import com.vitualemu.app.domain.model.MouseLookConfig
import com.vitualemu.app.domain.model.Profile
import com.vitualemu.app.domain.policy.SafetyPolicy
import com.vitualemu.app.domain.usecase.HeldInputRegistry
import com.vitualemu.app.domain.usecase.PreflightCheckUseCase
import com.vitualemu.app.domain.validation.ValidationRules
import com.vitualemu.app.runtime.input.MultiTouchDispatcher
import com.vitualemu.app.runtime.mouselook.MouseLookEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GamingInputMappingTest {

    @Test
    fun testHeldInputRegistry_atomicReleaseAllNeutralizesTouchesAndKeys() {
        HeldInputRegistry.releaseAll()
        assertEquals(0, HeldInputRegistry.getHeldKeyCount())
        assertEquals(0, HeldInputRegistry.getActiveTouchCount())

        HeldInputRegistry.recordKeyDown(19)
        HeldInputRegistry.recordKeyDown(62)
        HeldInputRegistry.recordTouchDown(pointerId = 1, x = 0.2f, y = 0.7f, mappingId = "m1")
        HeldInputRegistry.recordTouchDown(pointerId = 2, x = 0.8f, y = 0.5f, mappingId = "m2")

        assertEquals(2, HeldInputRegistry.getHeldKeyCount())
        assertEquals(2, HeldInputRegistry.getActiveTouchCount())

        val releasedPointers = HeldInputRegistry.releaseAll()
        assertEquals(2, releasedPointers.size)
        assertEquals(0, HeldInputRegistry.getHeldKeyCount())
        assertEquals(0, HeldInputRegistry.getActiveTouchCount())

        val emptyRelease = HeldInputRegistry.releaseAll()
        assertEquals(0, emptyRelease.size)
    }

    @Test
    fun testMultiTouchDispatcher_allocatesDedicatedAndDynamicSlotsIndependently() = runBlocking {
        val dispatcher = MultiTouchDispatcher()
        dispatcher.releaseAllSlots()

        // 1. Activate WASD Stick on dedicated Slot 0
        dispatcher.updateWasdStick(
            up = true,
            down = false,
            left = false,
            right = true,
            centerNormX = 0.20f,
            centerNormY = 0.70f,
            radius = 0.08f
        )
        assertTrue(dispatcher.isSlotActive(SafetyPolicy.WASD_POINTER_SLOT))

        // 2. Simultaneously press Fire and ADS on dynamic slots (2..9)
        val fireSlot = dispatcher.touchDownDynamic("FIRE", 0.85f, 0.65f)
        val adsSlot = dispatcher.touchDownDynamic("ADS", 0.82f, 0.48f)
        assertNotNull(fireSlot)
        assertNotNull(adsSlot)
        assertEquals(2, fireSlot)
        assertEquals(3, adsSlot)
        assertEquals(3, dispatcher.getActiveSlotCount())

        // 3. Releasing Fire does NOT disturb WASD (Slot 0) or ADS (Slot 3)
        dispatcher.touchUpDynamic("FIRE")
        assertTrue(dispatcher.isSlotActive(SafetyPolicy.WASD_POINTER_SLOT))
        assertTrue(dispatcher.isSlotActive(3))
        assertFalse(dispatcher.isSlotActive(2))
        assertEquals(2, dispatcher.getActiveSlotCount())

        // 4. Atomic releaseAllSlots clears everything
        dispatcher.releaseAllSlots()
        assertEquals(0, dispatcher.getActiveSlotCount())
    }

    @Test
    fun testMouseLookEngine_shootingMode360AutoEdgeResetAndFreeLook() = runBlocking {
        val dispatcher = MultiTouchDispatcher()
        val engine = MouseLookEngine(dispatcher)
        engine.updateConfig(
            MouseLookConfig(
                sensitivityX = 1.0f,
                sensitivityY = 1.0f,
                smoothing = 0.0f,
                acceleration = 0.0f,
                autoEdgeReset = true,
                edgeThreshold = 0.10f,
                centerNormalizedX = 0.70f,
                centerNormalizedY = 0.50f,
                freeLookEnabled = true,
                freeLookNormalizedX = 0.82f,
                freeLookNormalizedY = 0.25f
            )
        )

        // Toggle into Shooting Mode -> locks Pointer 1 at center (0.70, 0.50)
        assertTrue(engine.toggleShootingMode())
        assertTrue(engine.state.value.isShootingMode)
        assertTrue(dispatcher.isSlotActive(SafetyPolicy.MOUSE_LOOK_POINTER_SLOT))
        assertEquals(0.70f, engine.state.value.cameraPointerX, 0.001f)

        // Move mouse right past the 0.90 right edge threshold -> triggers 360° Auto Center Reset
        engine.onMouseDelta(0.25f, 0.0f)
        assertEquals(1, engine.state.value.edgeResetCount)
        assertTrue(engine.state.value.cameraPointerX < 0.90f)

        // Hold Free-Look Eye -> shifts Pointer 1 to Eye coordinates (0.82, 0.25)
        engine.setFreeLookActive(true)
        assertTrue(engine.state.value.isFreeLookActive)
        assertEquals(0.82f, engine.state.value.cameraPointerX, 0.001f)
        assertEquals(0.25f, engine.state.value.cameraPointerY, 0.001f)

        // Release Free-Look Eye -> snaps back to main camera center (0.70, 0.50)
        engine.setFreeLookActive(false)
        assertFalse(engine.state.value.isFreeLookActive)
        assertEquals(0.70f, engine.state.value.cameraPointerX, 0.001f)

        engine.resetAndRelease()
        assertFalse(dispatcher.isSlotActive(SafetyPolicy.MOUSE_LOOK_POINTER_SLOT))
    }

    @Test
    fun testSafetyPolicy_enforcesMacroBoundsAndStepDelay() {
        val rawSteps = (0 until 45).map { idx ->
            MacroStep(
                macroId = "m1",
                stepIndex = idx,
                actionType = "TAP",
                normalizedX = -0.3f,
                normalizedY = 1.4f,
                delayMs = 2L,
                holdDurationMs = 5L
            )
        }
        val rawMacro = Macro(
            id = "m1",
            profileId = "p1",
            name = "Unbounded Macro",
            steps = rawSteps,
            maxLoopIterations = 999
        )

        val sanitized = SafetyPolicy.sanitizeMacro(rawMacro)
        assertEquals(SafetyPolicy.MAX_MACRO_STEPS, sanitized.steps.size)
        assertEquals(SafetyPolicy.MAX_LOOP_ITERATIONS, sanitized.maxLoopIterations)
        assertEquals(0.0f, sanitized.steps.first().normalizedX, 0.0001f)
        assertEquals(1.0f, sanitized.steps.first().normalizedY, 0.0001f)
        assertEquals(SafetyPolicy.MIN_STEP_DELAY_MS, sanitized.steps.first().delayMs)
        assertEquals(SafetyPolicy.MIN_HOLD_DURATION_MS, sanitized.steps.first().holdDurationMs)
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
