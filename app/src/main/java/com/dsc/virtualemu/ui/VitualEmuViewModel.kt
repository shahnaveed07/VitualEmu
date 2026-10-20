package com.dsc.virtualemu.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dsc.virtualemu.cloud.EntitlementCloudService
import com.dsc.virtualemu.data.repository.VitualEmuRepository
import com.dsc.virtualemu.domain.mapping.KeymapCfgParser
import com.dsc.virtualemu.domain.mapping.KeymapExportManager
import com.dsc.virtualemu.domain.mapping.ParsedCfgData
import com.dsc.virtualemu.domain.model.BridgeHealthState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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
import com.dsc.virtualemu.domain.usecase.InstalledApp
import com.dsc.virtualemu.domain.usecase.InstalledAppDiscoveryUseCase
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
    private val appDiscoveryUseCase = InstalledAppDiscoveryUseCase(application)

    private var activeBridge: InputBridgeAdapter = ShizukuBridgeAdapter()

    val multiTouchDispatcher = MultiTouchDispatcher(bridgeProvider = { activeBridge })
    val mouseLookEngine = MouseLookEngine(multiTouchDispatcher)
    val macroEngine = MacroEngine(multiTouchDispatcher, viewModelScope)
    val keymapEngine = KeymapTranslationEngine(multiTouchDispatcher, mouseLookEngine, macroEngine)

    val allSavedGames: StateFlow<List<Game>> = repository.getAllGames()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val games: StateFlow<List<Game>> = repository.getTargetedGames()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _activeGame = MutableStateFlow<Game?>(null)
    val activeGame: StateFlow<Game?> = _activeGame.asStateFlow()

    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _activeProfile = MutableStateFlow<Profile?>(null)
    val activeProfile: StateFlow<Profile?> = _activeProfile.asStateFlow()

    private val _parsedImportData = MutableStateFlow<ParsedCfgData?>(null)
    val parsedImportData: StateFlow<ParsedCfgData?> = _parsedImportData.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val _isDiscoveringApps = MutableStateFlow(false)
    val isDiscoveringApps: StateFlow<Boolean> = _isDiscoveringApps.asStateFlow()

    private val _isSyncingSelection = MutableStateFlow(false)
    val isSyncingSelection: StateFlow<Boolean> = _isSyncingSelection.asStateFlow()

    private val _bridgeStatus = MutableStateFlow(
        BridgeStatus(
            type = BridgeType.SHIZUKU,
            state = BridgeHealthState.NOT_INITIALIZED,
            isAuthorized = false,
            detailMessage = "Shizuku bridge not initialized"
        )
    )
    val bridgeStatus: StateFlow<BridgeStatus> = _bridgeStatus.asStateFlow()

    private val _pendingStartGamePkg = MutableStateFlow<String?>(null)
    val pendingStartGamePkg: StateFlow<String?> = _pendingStartGamePkg.asStateFlow()

    private val _isUiTestModeEnabled = MutableStateFlow(repository.isUiTestModeEnabled())
    val isUiTestModeEnabled: StateFlow<Boolean> = _isUiTestModeEnabled.asStateFlow()

    fun setUiTestModeEnabled(enabled: Boolean) {
        _isUiTestModeEnabled.value = enabled
        repository.setUiTestModeEnabled(enabled)
        PlayModeOverlayRuntimeBridge.isTestModePreview = enabled
        showFeedback(if (enabled) "UI Overlay Test Mode Enabled" else "UI Overlay Test Mode Disabled")
    }

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

        // Sync active game whenever targeted games flow updates
        viewModelScope.launch {
            games.collect { targetedList ->
                if (targetedList.isEmpty()) {
                    if (_activeGame.value != null) {
                        _activeGame.value = null
                        _activeProfile.value = null
                        _profiles.value = emptyList()
                    }
                } else {
                    val savedPkg = repository.getSelectedGamePackage()
                    val savedMatch = targetedList.find { it.packageName == savedPkg }
                    val currentMatch = targetedList.find { it.gameId == _activeGame.value?.gameId }
                    val targetToSelect = savedMatch ?: currentMatch ?: targetedList.first()

                    if (_activeGame.value?.gameId != targetToSelect.gameId) {
                        selectGame(targetToSelect)
                    }
                }
            }
        }

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
        PlayModeOverlayRuntimeBridge.onUpdateSensitivityRequested = { sensX, sensY ->
            val currentCfg = _activeProfile.value?.mouseLookConfig ?: MouseLookConfig()
            updateMouseLookConfig(currentCfg.copy(sensitivityX = sensX, sensitivityY = sensY))
        }

        refreshBridgeHealth()
    }

    fun showFeedback(message: String) {
        _snackbarMessage.value = message
    }

    fun clearFeedback() {
        _snackbarMessage.value = null
    }

    fun discoverInstalledApps() {
        viewModelScope.launch {
            _isDiscoveringApps.value = true
            val savedGames = repository.getAllSavedGamesList()
            val targetedPackages = savedGames.filter { it.isTargeted }.map { it.packageName }.toSet()
            val apps = appDiscoveryUseCase.discoverInstalledApps(targetedPackages)
            _installedApps.value = apps
            _isDiscoveringApps.value = false
        }
    }

    fun syncSelection(selectedPackageNames: Set<String>, onComplete: () -> Unit = {}) {
        if (_isSyncingSelection.value) return
        viewModelScope.launch {
            _isSyncingSelection.value = true
            val existingTargetedPkgs = games.value.map { it.packageName }.toSet()
            val newPkgs = selectedPackageNames - existingTargetedPkgs

            val appMap = _installedApps.value.associateBy { it.packageName }
            repository.syncGameSelections(selectedPackageNames) { pkg ->
                appMap[pkg]?.displayName ?: pkg.substringAfterLast('.')
            }

            val freshTargetedGames = repository.getTargetedGames().first()
            val newlyAddedPkg = newPkgs.lastOrNull()
            if (newlyAddedPkg != null) {
                val newlyAddedGame = freshTargetedGames.find { it.packageName == newlyAddedPkg }
                if (newlyAddedGame != null) {
                    selectGame(newlyAddedGame)
                }
            } else if (freshTargetedGames.isNotEmpty()) {
                val currentPkg = _activeGame.value?.packageName
                val currentMatch = freshTargetedGames.find { it.packageName == currentPkg }
                if (currentMatch != null) {
                    selectGame(currentMatch)
                } else {
                    selectGame(freshTargetedGames.first())
                }
            } else {
                _activeGame.value = null
                _activeProfile.value = null
                _profiles.value = emptyList()
            }

            discoverInstalledApps()
            _isSyncingSelection.value = false
            showFeedback("Selection saved (${selectedPackageNames.size} game(s) selected)")
            onComplete()
        }
    }

    fun isPackageInstalledOnDevice(packageName: String): Boolean {
        return appDiscoveryUseCase.isPackageInstalled(packageName)
    }

    fun launchGame(packageName: String) {
        if (packageName.isBlank()) return
        val context = getApplication<Application>()
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                showFeedback("Launching ${packageName.substringAfterLast('.')}...")
            } else {
                showFeedback("Unable to launch app: Launch intent not found for $packageName")
            }
        } catch (e: Exception) {
            showFeedback("Failed to launch $packageName: ${e.localizedMessage}")
        }
    }

    fun addInstalledGame(app: InstalledApp) {
        if (app.packageName.isBlank()) return
        viewModelScope.launch {
            val existing = repository.getGameByPackage(app.packageName)
            if (existing != null) {
                repository.updateGame(existing.copy(isTargeted = true))
                selectGame(existing.copy(isTargeted = true))
                showFeedback("Game added to Home: ${existing.displayName}")
                return@launch
            }
            val game = Game(
                packageName = app.packageName,
                displayName = app.displayName,
                isTargeted = true
            )
            repository.insertGame(game)
            selectGame(game)
            showFeedback("Added game: ${game.displayName}")
        }
    }

    fun addMultipleInstalledGames(apps: List<InstalledApp>) {
        if (apps.isEmpty()) return
        val packageNames = apps.map { it.packageName }.toSet()
        syncSelection(packageNames)
    }

    fun removeGame(gameId: String) {
        viewModelScope.launch {
            val game = repository.getGameById(gameId)
            if (game != null) {
                repository.updateGame(game.copy(isTargeted = false))
                if (_activeGame.value?.gameId == gameId) {
                    val remaining = games.value.filter { it.gameId != gameId }
                    if (remaining.isNotEmpty()) {
                        selectGame(remaining.first())
                    } else {
                        _activeGame.value = null
                        _activeProfile.value = null
                        _profiles.value = emptyList()
                    }
                }
                showFeedback("Removed game: ${game.displayName}")
            }
        }
    }

    private var _profileCollectJob: kotlinx.coroutines.Job? = null

    fun selectGame(game: Game) {
        if (_activeGame.value?.gameId == game.gameId && _profileCollectJob?.isActive == true) {
            return
        }
        _activeGame.value = game
        PlayModeOverlayRuntimeBridge.activeGameName = game.displayName
        repository.setSelectedGamePackage(game.packageName)
        _profileCollectJob?.cancel()
        _profileCollectJob = viewModelScope.launch {
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
        val trimmedPkg = packageName.trim()
        if (trimmedPkg.isBlank()) {
            showFeedback("Package name cannot be empty")
            return
        }
        if (!appDiscoveryUseCase.isPackageInstalled(trimmedPkg)) {
            showFeedback("Warning: Package '$trimmedPkg' is not installed on this device")
        }
        val finalDisplayName = displayName.ifBlank { trimmedPkg.substringAfterLast('.') }
        viewModelScope.launch {
            val existing = repository.getGameByPackage(trimmedPkg)
            if (existing != null) {
                selectGame(existing)
                showFeedback("Game already added: ${existing.displayName}")
                return@launch
            }
            val game = Game(
                packageName = trimmedPkg,
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
        PlayModeOverlayRuntimeBridge.activeProfileName = profile.name
        PlayModeOverlayRuntimeBridge.sensitivityX = profile.mouseLookConfig.sensitivityX
        PlayModeOverlayRuntimeBridge.sensitivityY = profile.mouseLookConfig.sensitivityY
        mouseLookEngine.updateConfig(profile.mouseLookConfig)
        val game = _activeGame.value ?: return
        viewModelScope.launch {
            repository.setSelectedScheme(game.gameId, profile.profileId)
        }
    }

    fun parseCfgFileFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val content = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().readText()
                    } ?: ""
                } catch (e: Exception) {
                    ""
                }
            }
            if (content.isBlank()) {
                showFeedback("Failed to read configuration file from picker")
                return@launch
            }
            val gameId = _activeGame.value?.gameId ?: ""
            val parsed = KeymapCfgParser.parseCfgJson(content, gameId)
            _parsedImportData.value = parsed
            if (parsed.profiles.isEmpty()) {
                showFeedback("No valid control schemes found in file")
            } else if (parsed.warnings.isNotEmpty()) {
                showFeedback("Parsed configuration with ${parsed.warnings.size} warning(s)")
            }
        }
    }

    fun confirmImportSelectedSchemes(gameId: String, selectedProfiles: List<Profile>) {
        viewModelScope.launch {
            repository.importSchemesForGame(gameId, selectedProfiles)
            _parsedImportData.value = null
            showFeedback("Imported ${selectedProfiles.size} scheme(s) successfully")
            _activeGame.value?.let { selectGame(it) }
        }
    }

    fun clearImportPreview() {
        _parsedImportData.value = null
    }

    fun exportActiveSchemeToCfg(context: Context, uri: Uri) {
        val active = _activeProfile.value ?: return
        viewModelScope.launch {
            val cfgString = KeymapExportManager.exportToBlueStacksCfg(listOf(active))
            val success = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(cfgString.toByteArray(Charsets.UTF_8))
                    }
                    true
                } catch (_: Exception) {
                    false
                }
            }
            if (success) showFeedback("Exported active scheme '${active.name}' successfully")
            else showFeedback("Failed to write export file")
        }
    }

    fun exportAllSchemesToCfg(context: Context, uri: Uri) {
        val allProfiles = _profiles.value
        if (allProfiles.isEmpty()) return
        viewModelScope.launch {
            val cfgString = KeymapExportManager.exportToBlueStacksCfg(allProfiles)
            val success = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(cfgString.toByteArray(Charsets.UTF_8))
                    }
                    true
                } catch (_: Exception) {
                    false
                }
            }
            if (success) showFeedback("Exported all ${allProfiles.size} schemes successfully")
            else showFeedback("Failed to write export file")
        }
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
        val requested = shizuku.requestShizukuPermission { granted ->
            viewModelScope.launch {
                refreshBridgeHealth()
                if (granted) {
                    showFeedback("Shizuku permission granted! Bridge active.")
                }
            }
        }
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

    fun requestStartGameGated(packageName: String): Boolean {
        if (packageName.isBlank()) {
            showFeedback("No game selected")
            return false
        }
        val preflight = runPreflight()
        val hasOverlay = checkOverlayPermission()

        if (_isUiTestModeEnabled.value) {
            if (!hasOverlay) {
                _pendingStartGamePkg.value = packageName
                showFeedback("Overlay permission required for UI Test Mode")
                return false
            }
            _pendingStartGamePkg.value = null
            PlayModeOverlayRuntimeBridge.isTestModePreview = true
            launchGame(packageName)
            val context = getApplication<Application>()
            PlayModeOverlayService.start(context)
            showFeedback("Started UI Test Mode overlay over ${packageName.substringAfterLast('.')}")
            return true
        }

        if (preflight.isReady && hasOverlay) {
            _pendingStartGamePkg.value = null
            PlayModeOverlayRuntimeBridge.isTestModePreview = false
            launchGame(packageName)
            startPlayMode()
            return true
        } else {
            _pendingStartGamePkg.value = packageName
            return false
        }
    }

    fun resumePendingStartGameLaunchIfNeeded(): Boolean {
        val targetPkg = _pendingStartGamePkg.value ?: return false
        return requestStartGameGated(targetPkg)
    }

    fun cancelPendingStartGameLaunch() {
        _pendingStartGamePkg.value = null
    }

    fun generateShareCode(): String {
        val active = _activeProfile.value ?: return "mkmffkmm5i2"
        val json = KeymapExportManager.exportToBlueStacksCfg(listOf(active))
        return try {
            val encoded = java.util.Base64.getUrlEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))
            "VE1-$encoded"
        } catch (_: Throwable) {
            "mkmffkmm5i2"
        }
    }

    fun importShareCode(code: String): Boolean {
        val trimmed = code.trim()
        if (trimmed.isBlank()) {
            showFeedback("Share code cannot be empty")
            return false
        }
        val game = _activeGame.value ?: return false
        val jsonToParse = try {
            val rawPayload = if (trimmed.startsWith("VE1-")) trimmed.substringAfter("VE1-") else trimmed
            val decodedBytes = java.util.Base64.getUrlDecoder().decode(rawPayload)
            String(decodedBytes, Charsets.UTF_8)
        } catch (_: Throwable) {
            trimmed
        }

        val parsed = KeymapCfgParser.parseCfgJson(jsonToParse, game.gameId)
        if (parsed.profiles.isNotEmpty()) {
            val importedProfile = parsed.profiles.first().copy(
                profileId = UUID.randomUUID().toString(),
                gameId = game.gameId,
                name = "${parsed.profiles.first().name} (Imported)"
            )
            viewModelScope.launch {
                repository.saveProfile(importedProfile, "Imported via Share Code $trimmed")
                selectProfile(importedProfile)
                showFeedback("Successfully imported keymap with ${importedProfile.mappings.size} component(s)!")
            }
            return true
        } else if (parsed.warnings.isNotEmpty()) {
            showFeedback(parsed.warnings.first())
            return false
        } else {
            showFeedback("Invalid or unrecognized share code format")
            return false
        }
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

    fun clearDiagnosticsCache() {
        diagnostics.recordSafeStop("Diagnostics Log Reset")
        showFeedback("Local diagnostics log reset.")
    }

    override fun onCleared() {
        super.onCleared()
        deviceManager.unregister()
        activeBridge.shutdown()
        HeldInputRegistry.releaseAll()
    }
}
