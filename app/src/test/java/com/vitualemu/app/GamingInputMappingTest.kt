package com.vitualemu.app

import android.view.MotionEvent
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
import com.vitualemu.app.domain.model.TouchAction
import com.vitualemu.app.domain.policy.SafetyPolicy
import com.vitualemu.app.domain.usecase.HeldInputRegistry
import com.vitualemu.app.domain.usecase.PreflightCheckUseCase
import com.vitualemu.app.domain.validation.ValidationRules
import com.vitualemu.app.platform.activation.rootless.LocalAdbShellTransport
import com.vitualemu.app.runtime.input.CombinedTouchFrame
import com.vitualemu.app.runtime.input.InputBridge
import com.vitualemu.app.runtime.input.MultiTouchDispatcher
import com.vitualemu.app.runtime.input.MultiTouchPointerCombiner
import com.vitualemu.app.runtime.input.WasdJoystickSynthesizer
import com.vitualemu.app.runtime.mouselook.MouseLookEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
    fun testMultiTouchPointerCombiner_combinesConcurrentPointersAndGeneratesEvdevTypeBFrames() {
        val combiner = MultiTouchPointerCombiner(screenWidthPx = 2400, screenHeightPx = 1080)

        // 1. First pointer down (WASD Slot 0) -> ACTION_DOWN, pointerCount = 1
        val frame0 = combiner.onPointerTransition(0, TouchAction.DOWN, 0.20f, 0.70f)
        assertNotNull(frame0)
        assertEquals(TouchAction.DOWN, frame0!!.resolvedTouchAction)
        assertEquals(MotionEvent.ACTION_DOWN, frame0.androidMaskedAction)
        assertEquals(1, frame0.pointerCount)
        assertTrue(frame0.evdevTypeBCommands.any { it.contains("47 0") }) // ABS_MT_SLOT 0
        assertTrue(frame0.evdevTypeBCommands.any { it.contains("330 1") }) // BTN_TOUCH 1

        // 2. Second concurrent pointer down (Mouse Look Slot 1) -> ACTION_POINTER_DOWN (index 1), pointerCount = 2
        val frame1 = combiner.onPointerTransition(1, TouchAction.DOWN, 0.72f, 0.48f)
        assertNotNull(frame1)
        assertEquals(TouchAction.POINTER_DOWN, frame1!!.resolvedTouchAction)
        val expectedAction1 = MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        assertEquals(expectedAction1, frame1.androidMaskedAction)
        assertEquals(2, frame1.pointerCount)

        // 3. Third concurrent pointer down (Fire Slot 2) -> ACTION_POINTER_DOWN (index 2), pointerCount = 3
        val frame2 = combiner.onPointerTransition(2, TouchAction.DOWN, 0.85f, 0.65f)
        assertNotNull(frame2)
        assertEquals(TouchAction.POINTER_DOWN, frame2!!.resolvedTouchAction)
        val expectedAction2 = MotionEvent.ACTION_POINTER_DOWN or (2 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        assertEquals(expectedAction2, frame2.androidMaskedAction)
        assertEquals(3, frame2.pointerCount)

        // 4. Release Fire Slot 2 while Slot 0 and Slot 1 remain held -> ACTION_POINTER_UP
        val frameUp2 = combiner.onPointerTransition(2, TouchAction.UP, 0.85f, 0.65f)
        assertNotNull(frameUp2)
        assertEquals(TouchAction.POINTER_UP, frameUp2!!.resolvedTouchAction)
        val expectedUp2 = MotionEvent.ACTION_POINTER_UP or (2 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        assertEquals(expectedUp2, frameUp2.androidMaskedAction)
        // Frame includes the lifted pointer at index 2 during the UP transition, then removes it
        assertEquals(3, frameUp2.pointerCount)
        assertEquals(2, combiner.getActivePointerCount())
        assertTrue(frameUp2.evdevTypeBCommands.any { it.contains("57 -1") }) // ABS_MT_TRACKING_ID -1

        // 5. Emergency cancelAll -> ACTION_CANCEL and 0 remaining pointers
        val cancelFrame = combiner.cancelAll()
        assertNotNull(cancelFrame)
        assertEquals(TouchAction.CANCEL, cancelFrame!!.resolvedTouchAction)
        assertEquals(MotionEvent.ACTION_CANCEL, cancelFrame.androidMaskedAction)
        assertEquals(0, combiner.getActivePointerCount())
    }

    @Test
    fun testWasdJoystickSynthesizer_8WayDiagonalVectorNormalization() {
        val synth = WasdJoystickSynthesizer()
        assertFalse(synth.isActive())

        // Press W + D simultaneously -> diagonal Up-Right normalized by 1/sqrt(2) (~0.7071)
        val diag = synth.evaluate(
            up = true,
            down = false,
            left = false,
            right = true,
            centerNormX = 0.20f,
            centerNormY = 0.70f,
            radius = 0.10f
        )
        assertTrue(diag.isActive)
        assertTrue(diag.isInitialTouchDown)
        assertEquals(0.7071f, diag.unitVectorX, 0.002f)
        assertEquals(-0.7071f, diag.unitVectorY, 0.002f)

        // Transition to W only while still holding stick -> isInitialTouchDown must be false
        val cardinalUp = synth.evaluate(
            up = true,
            down = false,
            left = false,
            right = false,
            centerNormX = 0.20f,
            centerNormY = 0.70f,
            radius = 0.10f
        )
        assertTrue(cardinalUp.isActive)
        assertFalse(cardinalUp.isInitialTouchDown)
        assertEquals(0.0f, cardinalUp.unitVectorX, 0.001f)
        assertEquals(-1.0f, cardinalUp.unitVectorY, 0.001f)

        // Release all keys -> inactive
        val released = synth.evaluate(
            up = false,
            down = false,
            left = false,
            right = false,
            centerNormX = 0.20f,
            centerNormY = 0.70f,
            radius = 0.10f
        )
        assertFalse(released.isActive)
        assertFalse(synth.isActive())
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
    fun testMouseLookEngine_shootingMode360AutoEdgeResetEmitsUpBeforeReanchor() = runBlocking {
        val recordedFrames = mutableListOf<CombinedTouchFrame>()
        val recordingBridge = object : InputBridge {
            override val statusFlow: StateFlow<BridgeStatus> = MutableStateFlow(BridgeStatus())
            override fun getStatus(): BridgeStatus = statusFlow.value
            override fun getCapabilities() = statusFlow.value.capabilities
            override suspend fun startSession(sessionId: String) = true
            override suspend fun injectTouch(pointerId: Int, action: TouchAction, normX: Float, normY: Float, pressure: Float) = true
            override suspend fun injectCombinedTouchFrame(frame: CombinedTouchFrame): Boolean {
                recordedFrames.add(frame)
                return true
            }
            override suspend fun executeMacroStep(step: MacroStep, sessionId: String) = true
            override suspend fun cancelAll(sessionId: String) {}
            override suspend fun releaseHeldInputs(sessionId: String) {}
            override suspend fun stopSession(sessionId: String) {}
        }

        val dispatcher = MultiTouchDispatcher(bridgeProvider = { recordingBridge })
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

        recordedFrames.clear()

        // Move mouse right past the 0.90 right edge threshold -> triggers atomic 360° Auto Center Reset
        engine.onMouseDelta(0.25f, 0.0f)
        assertEquals(1, engine.state.value.edgeResetCount)
        assertTrue(engine.state.value.cameraPointerX < 0.90f)

        // Verify atomic edge-recenter sequence emitted MOVE(edge) -> UP(edge) -> DOWN(anchor) -> MOVE(carry)
        val actions = recordedFrames.map { it.resolvedTouchAction }
        assertTrue(actions.contains(TouchAction.UP))
        assertTrue(actions.contains(TouchAction.DOWN))
        val upIndex = actions.indexOf(TouchAction.UP)
        val downIndex = actions.lastIndexOf(TouchAction.DOWN)
        assertTrue("Edge recenter must emit UP before re-anchoring DOWN", upIndex in 0 until downIndex)

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
    fun testLocalAdbShellTransport_buildsValidLittleEndianAdbProtocolPackets() {
        val transport = LocalAdbShellTransport()
        val payload = "shell:\u0000".toByteArray(Charsets.UTF_8)
        val packet = transport.buildAdbPacket(
            command = LocalAdbShellTransport.CMD_OPEN,
            arg0 = 101,
            arg1 = 0,
            payload = payload
        )
        assertEquals(24 + payload.size, packet.size)

        val buf = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(LocalAdbShellTransport.CMD_OPEN, buf.int)
        assertEquals(101, buf.int)
        assertEquals(0, buf.int)
        assertEquals(payload.size, buf.int)
        val checksum = buf.int
        assertTrue(checksum > 0)
        val magic = buf.int
        assertEquals(LocalAdbShellTransport.CMD_OPEN xor -0x1, magic)
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
