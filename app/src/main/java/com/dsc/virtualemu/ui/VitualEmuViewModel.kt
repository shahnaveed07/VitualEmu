package com.dsc.virtualemu.ui

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dsc.virtualemu.cloud.EntitlementCloudService
import com.dsc.virtualemu.data.repository.VitualEmuRepository
import com.dsc.virtualemu.domain.model.BridgeHealthState
import com.dsc.virtualemu.domain.model.BridgeStatus
import com.dsc.virtualemu.domain.model.BridgeType
import com.dsc.virtualemu.domain.model.DeviceInfo
import com.dsc.virtualemu.domain.model.Game
import com.dsc.virtualemu.domain.model.Macro
import com.dsc.virtualemu.domain.model.Mapping
import com.dsc.virtualemu.domain.model.MouseLookConfig
import com.dsc.virtualemu.domain.model.PreflightResult
import com.dsc.virtualemu.domain.model.Profile
import com.dsc.virtualemu.domain.model.ProfileSnapshot
import com.dsc.virtualemu.domain.model.SessionState
import com.dsc.virtualemu.domain.policy.SafetyPolicy
import com.dsc.virtualemu.domain.usecase.HeldInputRegistry
import com.dsc.virtualemu.platform.activation.rootless.RootlessWirelessAdbBridgeAdapter
import com.dsc.virtualemu.platform.activation.shizuku.ShizukuBridgeAdapter
import com.dsc.virtualemu.platform.devices.DeviceManager
import com.dsc.virtualemu.runtime.bridge.InputBridgeAdapter
import com.dsc.virtualemu.runtime.diagnostics.DiagnosticsSnapshot
import com.dsc.virtualemu.runtime.diagnostics.RuntimeDiagnostics
import com.dsc.virtualemu.runtime.input.ActivePointerSlot
import com.dsc.virtualemu.runtime.input.CombinedTouchFrame
import com.dsc.virtualemu.runtime.input.MultiTouchDispatcher
import com.dsc.virtualemu.runtime.macro.MacroEngine
import com.dsc.virtualemu.runtime.mapping.KeymapTranslationEngine
import com.dsc.virtualemu.runtime.mouselook.MouseLookEngine
import com.dsc.virtualemu.runtime.mouselook.MouseLookState
import com.dsc.virtualemu.runtime.overlay.PlayModeOverlayRuntimeBridge
import com.dsc.virtualemu.runtime.overlay.PlayModeOverlayService
import com.dsc.virtualemu.runtime.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class VitualEmuViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VitualEmuRepository(application)
    private val deviceManager = DeviceManager(application)
    private val sessionManager = SessionManager()
    private val cloudService = EntitlementCloudService()
    private val diagnostics = RuntimeDiagnostics()

    private var activeBridge: InputBridgeAdapter = ShizukuBridgeAdapter()

    val multiTouchDispatcher = MultiTouchDispatcher(bridgeProvider = { activeBridge })
    val mouseLookEngine = MouseLookEngine(multiTouchDispatcher)
    val macroEngine = MacroEngine(multiTouchDispatcher, viewModelScope)
    val keymapEngine = KeymapTranslationEngine(multiTouchDispatcher, mouseLookEngine, macroEngine)

    val games: StateFlow<List<Game>> = repository.getAllGames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeGame = MutableStateFlow<Game?>(null)
    val activeGame: StateFlow<Game?> = _activeGame.asStateFlow()

    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _activeProfile = MutableStateFlow<Profile?>(null)
    val activeProfile: StateFlow<Profile?> = _activeProfile.asStateFlow()

    private val _bridgeStatus = MutableStateFlow(
        BridgeStatus(
            type = BridgeType.SHIZUKU,
            state = BridgeHealthState.NOT_INITIALIZED,
            isAuthorized = false,
            detailMessage = "Shizuku bridge not initialized"
        )
    )
    val bridgeStatus: StateFlow<BridgeStatus> = _bridgeStatus.asStateFlow()

    val connectedDevices: StateFlow<List<DeviceInfo>> = deviceManager.devicesFlow
    val hotPlugLog: StateFlow<List<String>> = deviceManager.hotPlugLog

    val sessionState: StateFlow<SessionState> = sessionManager.sessionState
    val preflightResult: StateFlow<PreflightResult?> = sessionManager.preflightResult

    val mouseLookState: StateFlow<MouseLookState> = mouseLookEngine.state
    val activePointerSlots: StateFlow<List<ActivePointerSlot>> = multiTouchDispatcher.slotsFlow
    val lastCombinedFrame: StateFlow<CombinedTouchFrame?> = multiTouchDispatcher.lastCombinedFrame
    val runningMacros: StateFlow<Set<String>> = macroEngine.runningMacroIds
    val diagnosticsSnapshot: StateFlow<DiagnosticsSnapshot> = diagnostics.snapshot

    val isOverlayServiceRunning: StateFlow<Boolean> = PlayModeOverlayRuntimeBridge.isOverlayServiceRunning
    val isInputGrabEnabled: StateFlow<Boolean> = PlayModeOverlayRuntimeBridge.isInputGrabEnabled

    val isProUnlocked: StateFlow<Boolean> = cloudService.entitlement
        .map { it.isProUnlocked }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    init {
        val dm = application.resources.displayMetrics
        val landscapeW = maxOf(dm.widthPixels, dm.heightPixels).coerceAtLeast(1280)
        val landscapeH = minOf(dm.widthPixels, dm.heightPixels).coerceAtLeast(720)
        mouseLookEngine.updateDisplaySize(landscapeW, landscapeH)

        // Wire physical peripheral hot-plug disconnect directly into full multi-touch & bridge release
        deviceManager.setOnPeripheralDisconnectedListener { reason ->
            viewModelScope.launch {
                keymapEngine.neutralizeAll("peripheral_unplug")
            }
            sessionManager.onFocusLost()
            diagnostics.recordSafeStop(reason)
            showFeedback(reason)
        }

        // Wire system overlay physical capture callbacks into KeymapTranslationEngine & MouseLookEngine
        PlayModeOverlayRuntimeBridge.onPhysicalKeyCaptured = { keyCode, isDown ->
            dispatchSandboxKeyEvent(keyCode, isDown)
        }
        PlayModeOverlayRuntimeBridge.onPhysicalMouseDeltaCaptured = { dx, dy ->
            dispatchSandboxMouseDelta(dx, dy)
        }
        PlayModeOverlayRuntimeBridge.onToggleShootingModeRequested = {
            toggleShootingMode()
        }
        PlayModeOverlayRuntimeBridge.onEmergencyStopRequested = { reason ->
            emergencySafeStop(reason)
        }

        viewModelScope.launch {
            games.collect { list ->
                if (_activeGame.value == null && list.isNotEmpty()) {
                    selectGame(list.first())
                }
            }
        }
        refreshBridgeHealth()
    }

    fun showFeedback(message: String) {
        _snackbarMessage.value = message
    }

    fun clearFeedback() {
        _snackbarMessage.value = null
    }

    fun selectGame(game: Game) {
        _activeGame.value = game
        viewModelScope.launch {
            repository.updateGame(game.copy(lastPlayedAt = System.currentTimeMillis()))
            repository.getProfilesForGame(game.gameId).collect { profileList ->
                _profiles.value = profileList
                if (profileList.isNotEmpty()) {
                    val currentId = _activeProfile.value?.profileId
                    val matched = profileList.find { it.profileId == currentId }
                    if (matched != null) {
                        selectProfile(matched)
                    } else if (_activeProfile.value == null || _activeProfile.value?.gameId != game.gameId) {
                        selectProfile(profileList.first())
                    }
                } else {
                    _activeProfile.value = null
                }
            }
        }
    }

    fun addManualGame(packageName: String, displayName: String) {
        if (packageName.isBlank()) {
            showFeedback("Package name cannot be empty")
            return
        }
        val finalDisplayName = displayName.ifBlank { packageName.substringAfterLast('.') }
        viewModelScope.launch {
            val game = Game(
                packageName = packageName.trim(),
                displayName = finalDisplayName.trim(),
                isTargeted = true
            )
            repository.insertGame(game)
            selectGame(game)
            showFeedback("Added game: ${game.displayName}")
        }
    }

    fun selectProfile(profile: Profile) {
        _activeProfile.value = profile
        mouseLookEngine.updateConfig(profile.mouseLookConfig)
    }

    fun createNewProfile(name: String) {
        val game = _activeGame.value ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            val newProfile = Profile(
                gameId = game.gameId,
                name = name.trim()
            )
            repository.saveProfile(newProfile, "Initial profile creation")
            _activeProfile.value = newProfile
            mouseLookEngine.updateConfig(newProfile.mouseLookConfig)
            showFeedback("Created profile: $name")
        }
    }

    fun updateActiveProfile(profile: Profile) {
        _activeProfile.value = profile
        mouseLookEngine.updateConfig(profile.mouseLookConfig)
        viewModelScope.launch {
            repository.saveProfile(profile, "Manual canvas save")
        }
    }

    fun updateMouseLookConfig(newConfig: MouseLookConfig) {
        val profile = _activeProfile.value ?: return
        val sanitized = SafetyPolicy.sanitizeMouseLookConfig(newConfig)
        mouseLookEngine.updateConfig(sanitized)
        val updated = profile.copy(
            mouseLookConfig = sanitized,
            updatedAt = System.currentTimeMillis()
        )
        _activeProfile.value = updated
        viewModelScope.launch {
            repository.saveProfile(updated)
        }
    }

    fun addMacro(macro: Macro) {
        val profile = _activeProfile.value ?: return
        val cleanMacro = SafetyPolicy.sanitizeMacro(macro)
        val updated = profile.copy(
            macros = profile.macros + cleanMacro,
            updatedAt = System.currentTimeMillis()
        )
        _activeProfile.value = updated
        viewModelScope.launch {
            repository.saveProfile(updated, "Added macro ${cleanMacro.name}")
            showFeedback("Saved bounded macro: ${cleanMacro.name}")
        }
    }

    fun removeMacro(macroId: String) {
        val profile = _activeProfile.value ?: return
        val updated = profile.copy(
            macros = profile.macros.filter { it.id != macroId },
            updatedAt = System.currentTimeMillis()
        )
        _activeProfile.value = updated
        viewModelScope.launch {
            repository.saveProfile(updated)
            showFeedback("Removed macro")
        }
    }

    fun duplicateActiveProfile(newName: String = "Copy of Profile") {
        val current = _activeProfile.value ?: return
        val finalName = if (newName == "Copy of Profile") "Copy of ${current.name}" else newName
        viewModelScope.launch {
            val duplicated = repository.duplicateProfile(current, finalName)
            _activeProfile.value = duplicated
            showFeedback("Duplicated profile as: $finalName")
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            repository.deleteProfile(profileId)
            showFeedback("Profile deleted")
            val remaining = _profiles.value.filter { it.profileId != profileId }
            _activeProfile.value = remaining.firstOrNull()
        }
    }

    fun exportActiveProfileJson(): String? {
        val profile = _activeProfile.value ?: return null
        return repository.serializeProfileToJson(profile)
    }

    fun importProfileJson(json: String): Boolean {
        val imported = repository.deserializeProfileFromJson(json) ?: return false
        val game = _activeGame.value ?: return false
        viewModelScope.launch {
            val adjusted = imported.copy(
                profileId = UUID.randomUUID().toString(),
                gameId = game.gameId,
                name = "${imported.name} (Imported)"
            )
            repository.saveProfile(adjusted, "Imported from JSON")
            _activeProfile.value = adjusted
            mouseLookEngine.updateConfig(adjusted.mouseLookConfig)
            showFeedback("Imported profile successfully")
        }
        return true
    }

    fun getSnapshots(profileId: String): StateFlow<List<ProfileSnapshot>> {
        return repository.getSnapshotsForProfile(profileId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun rollbackToSnapshot(snapshotId: String) {
        viewModelScope.launch {
            val restored = repository.rollbackToSnapshot(snapshotId)
            if (restored != null) {
                _activeProfile.value = restored
                mouseLookEngine.updateConfig(restored.mouseLookConfig)
                showFeedback("Restored profile to snapshot")
            } else {
                showFeedback("Failed to restore snapshot")
            }
        }
    }

    fun rollbackToSnapshot(snapshot: ProfileSnapshot) {
        rollbackToSnapshot(snapshot.id)
    }

    fun setBridge(type: BridgeType) {
        activeBridge.shutdown()
        activeBridge = when (type) {
            BridgeType.SHIZUKU -> ShizukuBridgeAdapter()
            BridgeType.ROOTLESS_WIRELESS_DEBUGGING -> RootlessWirelessAdbBridgeAdapter()
        }
        refreshBridgeHealth()
    }

    fun requestShizukuPermission() {
        val shizuku = activeBridge as? ShizukuBridgeAdapter
            ?: ShizukuBridgeAdapter().also { activeBridge = it }
        val requested = shizuku.requestShizukuPermission()
        if (requested) {
            showFeedback("Requested Shizuku authorization prompt")
        } else {
            showFeedback("Shizuku binder not detected. Start Shizuku app first.")
        }
        refreshBridgeHealth()
    }

    fun pairWirelessAdb(port: Int, pairingCode: String) {
        val adbAdapter = activeBridge as? RootlessWirelessAdbBridgeAdapter
            ?: RootlessWirelessAdbBridgeAdapter().also { activeBridge = it }
        viewModelScope.launch {
            val status = adbAdapter.pairAndConnectEphemeral(port, pairingCode)
            _bridgeStatus.value = status
            showFeedback(status.detailMessage)
        }
    }

    fun refreshBridgeHealth() {
        viewModelScope.launch {
            val status = activeBridge.checkHealth()
            _bridgeStatus.value = status
        }
    }

    fun checkOverlayPermission(): Boolean {
        val context = getApplication<Application>()
        return Settings.canDrawOverlays(context)
    }

    fun runPreflight(): PreflightResult {
        return sessionManager.runPreflight(
            bridgeStatus = _bridgeStatus.value,
            hasOverlayPermission = checkOverlayPermission(),
            deviceManager = deviceManager,
            game = _activeGame.value,
            profile = _activeProfile.value
        )
    }

    fun startPlayMode(): Boolean {
        val context = getApplication<Application>()
        if (sessionState.value == SessionState.ACTIVE) {
            sessionManager.pauseSession()
            PlayModeOverlayService.stop(context)
            viewModelScope.launch {
                keymapEngine.neutralizeAll("play_mode_paused")
            }
            showFeedback("Play Mode Paused. All held inputs neutralized.")
            return false
        }
        val preflight = runPreflight()
        if (!preflight.isReady) {
            showFeedback("Preflight check failed. Resolve blockers to start.")
            return false
        }
        val started = sessionManager.startSession(activeBridge)
        if (started) {
            PlayModeOverlayService.start(context)
            showFeedback("Play Mode Activated! Floating HUD & Multi-Touch Engine active.")
        }
        return started
    }

    fun toggleShootingMode() {
        viewModelScope.launch {
            val active = mouseLookEngine.toggleShootingMode()
            diagnostics.recordTouchDispatch(
                _bridgeStatus.value.lastLatencyMs,
                if (active) "Shooting Mode: ON (Pointer 1 Aim Lock)" else "Cursor Mode: ON"
            )
        }
    }

    fun toggleFreeLookPreview() {
        viewModelScope.launch {
            val next = !mouseLookEngine.state.value.isFreeLookActive
            mouseLookEngine.setFreeLookActive(next)
            diagnostics.recordTouchDispatch(
                _bridgeStatus.value.lastLatencyMs,
                if (next) "Free Look (Eye): ON" else "Free Look (Eye): OFF"
            )
        }
    }

    fun dispatchSandboxKeyEvent(keyCode: Int, isDown: Boolean, onResult: (String) -> Unit = {}) {
        val profile = _activeProfile.value ?: return
        viewModelScope.launch {
            val summary = keymapEngine.handleKeyEvent(profile, keyCode, isDown)
            diagnostics.recordTouchDispatch(_bridgeStatus.value.lastLatencyMs, summary)
            onResult(summary)
        }
    }

    fun dispatchSandboxMouseDelta(deltaX: Float, deltaY: Float) {
        viewModelScope.launch {
            mouseLookEngine.onMouseDelta(deltaX, deltaY)
        }
    }

    fun emergencySafeStop(reason: String = "User Emergency Safe-Stop") {
        val context = getApplication<Application>()
        PlayModeOverlayService.stop(context)
        viewModelScope.launch {
            keymapEngine.neutralizeAll("emergency_safe_stop")
        }
        sessionManager.emergencySafeStop()
        diagnostics.recordSafeStop(reason)
        showFeedback("EMERGENCY SAFE STOP: All 10 pointer slots & macros neutralized.")
    }

    fun onFocusLoss() {
        viewModelScope.launch {
            keymapEngine.neutralizeAll("window_focus_loss")
        }
        sessionManager.onFocusLost()
        diagnostics.recordSafeStop("Window Focus Loss Auto-Neutralize")
    }

    // Mapping Editor Actions
    fun updateMapping(updated: Mapping) {
        val profile = _activeProfile.value ?: return
        val updatedList = profile.mappings.map { if (it.id == updated.id) updated else it }
        val updatedProfile = profile.copy(mappings = updatedList, updatedAt = System.currentTimeMillis())
        _activeProfile.value = updatedProfile
        viewModelScope.launch {
            repository.saveProfile(updatedProfile)
        }
    }

    fun addMapping(newMapping: Mapping) {
        val profile = _activeProfile.value ?: return
        val updatedList = profile.mappings + newMapping
        val updatedProfile = profile.copy(mappings = updatedList, updatedAt = System.currentTimeMillis())
        _activeProfile.value = updatedProfile
        viewModelScope.launch {
            repository.saveProfile(updatedProfile)
        }
    }

    fun removeMapping(mappingId: String) {
        val profile = _activeProfile.value ?: return
        val updatedList = profile.mappings.filter { it.id != mappingId }
        val updatedProfile = profile.copy(mappings = updatedList, updatedAt = System.currentTimeMillis())
        _activeProfile.value = updatedProfile
        viewModelScope.launch {
            repository.saveProfile(updatedProfile)
        }
    }

    fun unlockProSimulated(key: String = "LOCAL") {
        cloudService.verifyAndUnlockPro(key)
        showFeedback("All VitualEmu features are unlocked locally.")
    }

    fun simulateAccountDeletion() {
        viewModelScope.launch {
            keymapEngine.neutralizeAll("account_deletion")
            cloudService.deleteCloudAccountAndEntitlements()
            showFeedback("Cloud account & entitlement records deleted. Local profiles preserved.")
        }
    }

    override fun onCleared() {
        super.onCleared()
        deviceManager.unregister()
        activeBridge.shutdown()
        HeldInputRegistry.releaseAll()
    }
}
