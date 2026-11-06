package com.dsc.virtualemu.runtime.capture

import android.view.KeyEvent
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhysicalInputCaptureTest {

    @Test
    fun testGeteventParser_hexEvdevKeyMapping() {
        val accumulator = GeteventParser.FrameAccumulator(
            deviceId = 3,
            deviceName = "Logitech USB Keyboard"
        )

        // EV_KEY (0001), code 0011 (KEY_W = 17 dec), value 00000001 (DOWN)
        accumulator.feedLine("0001 0011 00000001")
        // EV_SYN (0000), code 0000, value 00000000
        val events = accumulator.feedLine("0000 0000 00000000")
        assertEquals(1, events.size)

        val downEvent = events[0]
        assertEquals(PhysicalInputType.KEY_DOWN, downEvent.type)
        assertEquals(KeyEvent.KEYCODE_W, downEvent.keyCode)
        assertEquals(CaptureSource.SHIZUKU_GETEVENT, downEvent.source)
        assertTrue(downEvent.isDown)

        // EV_KEY, code 0011, value 00000000 (UP) + EV_SYN
        accumulator.feedLine("0001 0011 00000000")
        val upEvents = accumulator.feedLine("0000 0000 00000000")
        assertEquals(1, upEvents.size)

        val upEvent = upEvents[0]
        assertEquals(PhysicalInputType.KEY_UP, upEvent.type)
        assertEquals(KeyEvent.KEYCODE_W, upEvent.keyCode)
        assertFalse(upEvent.isDown)

        // EV_KEY, code 0011, value 00000002 (REPEAT) + EV_SYN
        accumulator.feedLine("0001 0011 00000002")
        val repeatEvents = accumulator.feedLine("0000 0000 00000000")
        assertEquals(1, repeatEvents.size)

        val repeatEvent = repeatEvents[0]
        assertEquals(PhysicalInputType.KEY_REPEAT, repeatEvent.type)
        assertEquals(KeyEvent.KEYCODE_W, repeatEvent.keyCode)
    }

    @Test
    fun testGeteventParser_mouseRelativeMovementAndButtons() {
        val accumulator = GeteventParser.FrameAccumulator(
            deviceId = 4,
            deviceName = "Razer DeathAdder"
        )

        // EV_REL (0002), code 0000 (REL_X), value ffffffe8 (-24)
        accumulator.feedLine("0002 0000 ffffffe8")
        // EV_REL, code 0001 (REL_Y), value 00000010 (+16)
        accumulator.feedLine("0002 0001 00000010")
        // Flush frame with EV_SYN
        val moveEvents = accumulator.feedLine("0000 0000 00000000")
        assertEquals(1, moveEvents.size)

        val move = moveEvents[0]
        assertEquals(PhysicalInputType.MOUSE_MOVE, move.type)
        assertEquals(-24f, move.deltaX, 0.001f)
        assertEquals(16f, move.deltaY, 0.001f)
        assertEquals(CaptureSource.SHIZUKU_GETEVENT, move.source)

        // Mouse buttons: BTN_LEFT (0x110), value 1 (DOWN) + EV_SYN
        accumulator.feedLine("0001 0110 00000001")
        val btnEvents = accumulator.feedLine("0000 0000 00000000")
        assertEquals(1, btnEvents.size)

        val btnLeftDown = btnEvents[0]
        assertEquals(PhysicalInputType.MOUSE_BUTTON_DOWN, btnLeftDown.type)
        assertEquals(102, btnLeftDown.keyCode) // KEYCODE_BUTTON_L1
        assertTrue(btnLeftDown.isDown)

        // Mouse wheel: REL_WHEEL (0008), value 00000001 (+1) + EV_SYN
        accumulator.feedLine("0002 0008 00000001")
        val wheelEvents = accumulator.feedLine("0000 0000 00000000")
        assertEquals(1, wheelEvents.size)

        val wheel = wheelEvents[0]
        assertEquals(PhysicalInputType.MOUSE_WHEEL, wheel.type)
        assertEquals(1f, wheel.wheelDelta, 0.001f)
    }

    @Test
    fun testGeteventParser_linuxKeycodeTranslation() {
        assertEquals(KeyEvent.KEYCODE_W, GeteventParser.linuxToAndroidKeyCode(17)) // KEY_W
        assertEquals(KeyEvent.KEYCODE_A, GeteventParser.linuxToAndroidKeyCode(30)) // KEY_A
        assertEquals(KeyEvent.KEYCODE_S, GeteventParser.linuxToAndroidKeyCode(31)) // KEY_S
        assertEquals(KeyEvent.KEYCODE_D, GeteventParser.linuxToAndroidKeyCode(32)) // KEY_D
        assertEquals(KeyEvent.KEYCODE_SPACE, GeteventParser.linuxToAndroidKeyCode(57)) // KEY_SPACE
        assertEquals(KeyEvent.KEYCODE_ESCAPE, GeteventParser.linuxToAndroidKeyCode(1)) // KEY_ESC
    }

    @Test
    fun testPhysicalInputRouter_slidingWindowDeduplicationAndGating() = runTest {
        var neutralized = false
        val stateManager = CaptureStateManager(onNeutralizeRequired = {
            neutralized = true
        })
        val router = PhysicalInputRouter(captureStateManager = stateManager)

        // Initially NOT capturing, so events should be dropped
        val dropped = router.onRawEvent(
            PhysicalInputEvent(
                type = PhysicalInputType.KEY_DOWN,
                keyCode = KeyEvent.KEYCODE_W,
                source = CaptureSource.ACCESSIBILITY_SERVICE
            )
        )
        assertFalse("Events offered when not capturing must be dropped", dropped)

        // Transition to CAPTURING
        stateManager.transitionTo(CaptureState.READY)
        stateManager.startCapturing()
        assertTrue(stateManager.isCapturing())

        // Collect events
        val received = mutableListOf<PhysicalInputEvent>()
        val job = launch {
            router.events.collect { received.add(it) }
        }

        val event1 = PhysicalInputEvent(
            type = PhysicalInputType.KEY_DOWN,
            keyCode = KeyEvent.KEYCODE_W,
            source = CaptureSource.ACCESSIBILITY_SERVICE
        )
        val accepted1 = router.onRawEvent(event1)
        assertTrue(accepted1)

        // Duplicate event within 50ms should be rejected
        val acceptedDuplicate = router.onRawEvent(event1)
        assertFalse("Duplicate event within 50ms window must be dropped", acceptedDuplicate)

        // Key Repeat should be filtered
        val repeatEvent = PhysicalInputEvent(
            type = PhysicalInputType.KEY_REPEAT,
            keyCode = KeyEvent.KEYCODE_W,
            source = CaptureSource.ACCESSIBILITY_SERVICE
        )
        val repeatAccepted = router.onRawEvent(repeatEvent)
        assertFalse("Key repeat should be filtered", repeatAccepted)

        // Stop capturing triggers state neutralization
        stateManager.stopCapturing("User stopped")
        assertTrue("Stopping capture must trigger neutralization callback", neutralized)

        job.cancel()
    }
}
