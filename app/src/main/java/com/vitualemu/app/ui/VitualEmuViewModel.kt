package com.vitualemu.app.ui

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vitualemu.app.data.repository.VitualEmuRepository
import com.vitualemu.app.domain.model.BridgeHealthState
import com.vitualemu.app.domain.model.BridgeStatus
import com.vitualemu.app.domain.model.BridgeType
import com.vitualemu.app.domain.model.DeviceInfo
import com.vitualemu.app.domain.model.Game
import com.vitualemu.app.domain.model.Mapping
import com.vitualemu.app.domain.model.PreflightResult
import com.vitualemu.app.domain.model.Profile
import com.vitualemu.app.domain.model.ProfileSnapshot
import com.vitualemu.app.domain.model.SessionState
import com.vitualemu.app.domain.usecase.HeldInputRegistry
import com.vitualemu.app.platform.devices.DeviceManager
import com.vitualemu.app.runtime.bridge.InputBridgeAdapter
import com.vitualemu.app.runtime.bridge.RootlessAdbBridge
import com.vitualemu.app.runtime.bridge.ShizukuBridge
import com.vitualemu.app.runtime.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class VitualEmuViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VitualEmuRepository(application)
    private val deviceManager = DeviceManager(application)
    private val sessionManager = SessionManager()

    private var activeBridge: InputBridgeAdapter = ShizukuBridge()

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

    private val _isProUnlocked = MutableStateFlow(false)
    val isProUnlocked: StateFlow<Boolean> = _isProUnlocked.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    init {
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
                    if (_activeProfile.value == null || _activeProfile.value?.gameId != game.gameId) {
                        selectProfile(profileList.first())
                    }
                } else {
                    _activeProfile.value = null
                }
            }
        }
    }

    fun addManualGame(packageName: String, displayName: String) {
        if (packageName.isBlank() || displayName.isBlank()) {
            showFeedback("Package and name cannot be empty")
            return
        }
        viewModelScope.launch {
            val game = Game(
                packageName = packageName.trim(),
                displayName = displayName.trim(),
                isTargeted = true
            )
            repository.insertGame(game)
            selectGame(game)
            showFeedback("Added game: ${game.displayName}")
        }
    }

    fun selectProfile(profile: Profile) {
        _activeProfile.value = profile
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
            showFeedback("Created profile: $name")
        }
    }

    fun updateActiveProfile(profile: Profile) {
        _activeProfile.value = profile
        viewModelScope.launch {
            repository.saveProfile(profile)
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
            BridgeType.SHIZUKU -> ShizukuBridge()
            BridgeType.ROOTLESS_WIRELESS_DEBUGGING -> RootlessAdbBridge()
        }
        refreshBridgeHealth()
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
        val preflight = runPreflight()
        if (!preflight.isReady) {
            showFeedback("Preflight check failed. Resolve blockers to start.")
            return false
        }
        val started = sessionManager.startSession(activeBridge)
        if (started) {
            showFeedback("Play Mode Activated! Controls mapped.")
        }
        return started
    }

    fun emergencySafeStop() {
        sessionManager.emergencySafeStop()
        showFeedback("EMERGENCY SAFE STOP: All inputs released.")
    }

    fun onFocusLoss() {
        sessionManager.onFocusLost()
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

    fun unlockProSimulated(key: String = "VITUALEMU-PRO") {
        if (key.trim().equals("VITUALEMU-PRO", ignoreCase = true) || key.length >= 8) {
            _isProUnlocked.value = true
            showFeedback("VitualEmu Pro features unlocked!")
        } else {
            showFeedback("Invalid activation license key")
        }
    }

    fun simulateAccountDeletion() {
        viewModelScope.launch {
            HeldInputRegistry.releaseAll()
            _isProUnlocked.value = false
            showFeedback("Local session data and preferences cleared")
        }
    }

    override fun onCleared() {
        super.onCleared()
        deviceManager.unregister()
        activeBridge.shutdown()
        HeldInputRegistry.releaseAll()
    }
}
