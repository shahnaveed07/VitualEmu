package com.vitualemu.app

import com.vitualemu.app.poc.bridge.SafeStopHeldRegistry
import com.vitualemu.app.poc.model.BridgeConnectionState
import com.vitualemu.app.poc.model.CandidateBridgeId
import com.vitualemu.app.poc.model.ExperimentStatus
import com.vitualemu.app.poc.model.FeasibilityExperimentId
import com.vitualemu.app.poc.model.Gate0Verdict
import com.vitualemu.app.poc.runner.FeasibilityTestRunner
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeasibilityPoCTest {

    @Test
    fun testSafeStopHeldRegistry_guaranteesCompleteInputNeutralization() {
        val registry = SafeStopHeldRegistry()
        assertEquals(0, registry.getHeldCount())

        // Simulate held physical keys (e.g. W, Shift) and active pointer
        registry.recordKeyDown(19) // KEYCODE_W
        registry.recordKeyDown(59) // KEYCODE_SHIFT_LEFT
        registry.recordPointerDown(1, 0.5f, 0.5f)

        assertEquals(3, registry.getHeldCount())

        // Safe stop is triggered
        val releasedCount = registry.releaseAll()
        assertEquals(3, releasedCount)
        assertEquals(0, registry.getHeldCount())

        // Idempotent secondary call
        val secondaryRelease = registry.releaseAll()
        assertEquals(0, secondaryRelease)
        assertEquals(0, registry.getHeldCount())
    }

    @Test
    fun testFeasibilityTestRunner_initializesInNotTestedState() {
        val runner = FeasibilityTestRunner()
        assertEquals(ExperimentStatus.NOT_TESTED, runner.overallStatus.value)
        assertEquals(15, runner.experiments.value.size)

        // All 15 experiments must start as NOT_TESTED
        runner.experiments.value.values.forEach { record ->
            assertEquals(ExperimentStatus.NOT_TESTED, record.status)
        }
    }

    @Test
    fun testConnectAndAuthorize_updatesBridgeStateAndCapabilities() = runTest {
        val runner = FeasibilityTestRunner()
        runner.selectCandidate(CandidateBridgeId.SHIZUKU)

        val state = runner.connectAndAuthorize()
        assertEquals(BridgeConnectionState.AUTHORIZED, state)
        assertTrue(runner.capabilities.value.canInjectSingleTap)
        assertTrue(runner.capabilities.value.canInjectMultiTouchJoystick)
        // Android focus isolation: Relative pointer capture is not supported when 3rd-party game owns focus
        assertFalse(runner.capabilities.value.canCaptureRelativeMouseLook)
    }

    @Test
    fun testSafeStop_clearsActiveSessionAndHeldInputs() = runTest {
        val runner = FeasibilityTestRunner()
        runner.connectAndAuthorize()
        runner.startFeasibilitySession()
        assertNotNull(runner.activeSessionId.value)

        // Inject action
        runner.executeTouchExperiment("TAP", 0.5f, 0.5f)

        // Trigger safe stop
        val released = runner.triggerSafeStop()
        assertEquals(0, runner.heldCount.value)
        assertEquals(null, runner.activeSessionId.value)

        val safeStopRecord = runner.experiments.value[FeasibilityExperimentId.SAFE_STOP_HELD_RELEASE]
        assertEquals(ExperimentStatus.PASS, safeStopRecord?.status)
    }

    @Test
    fun testRedactedReport_excludesSecretsAndTokens() = runTest {
        val runner = FeasibilityTestRunner()
        runner.connectAndAuthorize()
        runner.runRepeatabilityBenchmark()

        val report = runner.generateRedactedReport()
        val reportString = com.vitualemu.app.poc.ui.buildRedactedReportString(report)

        // Verify report contains no pairing secrets or raw credential tokens
        assertFalse(reportString.contains("pairing_code", ignoreCase = true))
        assertFalse(reportString.contains("auth_token", ignoreCase = true))
        assertFalse(reportString.contains("private_key", ignoreCase = true))
        assertTrue(reportString.contains("Xiaomi Redmi Note 13 5G"))
        assertTrue(reportString.contains("Gate 0 Verdict"))
    }
}
