package com.example.poc.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.poc.model.CandidateBridgeId
import com.example.poc.model.FeasibilityExperimentId
import com.example.poc.runner.FeasibilityTestRunner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FeasibilityViewModel : ViewModel() {
    val runner = FeasibilityTestRunner()

    val selectedCandidate = runner.selectedCandidate
    val deviceEnv = runner.deviceEnv
    val experiments = runner.experiments
    val overallStatus = runner.overallStatus
    val bridgeState = runner.bridgeState
    val capabilities = runner.capabilities
    val heldCount = runner.heldCount
    val lastActionFeedback = runner.lastActionFeedback

    private val _rawInputLog = MutableStateFlow<List<String>>(emptyList())
    val rawInputLog: StateFlow<List<String>> = _rawInputLog.asStateFlow()

    fun selectCandidate(candidate: CandidateBridgeId) {
        runner.selectCandidate(candidate)
    }

    fun connectAndAuthorize() {
        viewModelScope.launch {
            runner.connectAndAuthorize()
        }
    }

    fun startSession() {
        viewModelScope.launch {
            runner.startFeasibilitySession()
        }
    }

    fun executeSingleTap() {
        viewModelScope.launch {
            runner.executeTouchExperiment("SINGLE_TAP", 0.50f, 0.50f)
        }
    }

    fun executeMovementAction() {
        viewModelScope.launch {
            runner.executeTouchExperiment("WASD_MOVE_FORWARD", 0.20f, 0.70f)
            runner.executeTouchExperiment("ACTION_BUTTON_TAP", 0.85f, 0.65f)
        }
    }

    fun executeMouseLookTest() {
        viewModelScope.launch {
            runner.executeMouseLookExperiment()
        }
    }

    fun triggerSafeStop() {
        viewModelScope.launch {
            runner.triggerSafeStop()
        }
    }

    fun runBenchmark() {
        viewModelScope.launch {
            runner.runRepeatabilityBenchmark()
        }
    }

    fun recordRawInput(event: String) {
        val updated = listOf(event) + _rawInputLog.value.take(19)
        _rawInputLog.value = updated
    }

    fun onFocusLoss() {
        runner.onFocusLost()
    }

    fun onPeripheralDisconnect(name: String) {
        runner.onPeripheralDetached(name)
    }
}
