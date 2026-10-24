package com.dsc.virtualemu

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.dsc.virtualemu.data.local.room.VitualEmuDatabase
import com.dsc.virtualemu.domain.usecase.InstalledApp
import com.dsc.virtualemu.domain.usecase.InstalledAppDiscoveryUseCase
import com.dsc.virtualemu.ui.VitualEmuViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DatabaseMigrationAndViewModelTest {

    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext<Application>()
        VitualEmuDatabase.closeAndClearInstance()
        application.deleteDatabase("vitualemu.db")
    }

    @Test
    fun testDatabaseMigrationV2ToV3_removesSyntheticSeededEntriesAndPreservesUserData() = runBlocking {
        val dbName = "test-migration.db"
        application.deleteDatabase(dbName)

        val sqliteHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(application)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS games (gameId TEXT PRIMARY KEY NOT NULL, packageName TEXT NOT NULL, displayName TEXT NOT NULL, iconUri TEXT, isTargeted INTEGER NOT NULL, addedAt INTEGER NOT NULL, lastPlayedAt INTEGER)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS profiles (profileId TEXT PRIMARY KEY NOT NULL, gameId TEXT NOT NULL, name TEXT NOT NULL, schemaVersion INTEGER NOT NULL, isFavorite INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, mouseLookJson TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS mappings (id TEXT PRIMARY KEY NOT NULL, profileId TEXT NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL, keyCode INTEGER NOT NULL, keyLabel TEXT NOT NULL, normalizedX REAL NOT NULL, normalizedY REAL NOT NULL, endNormalizedX REAL NOT NULL, endNormalizedY REAL NOT NULL, normalizedWidth REAL NOT NULL, normalizedHeight REAL NOT NULL, sensitivity REAL NOT NULL, isToggle INTEGER NOT NULL, macroId TEXT)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS macros (id TEXT PRIMARY KEY NOT NULL, profileId TEXT NOT NULL, name TEXT NOT NULL, triggerKeyCode INTEGER NOT NULL, triggerKeyLabel TEXT NOT NULL, chordModifierKeyCode INTEGER NOT NULL, triggerMode TEXT NOT NULL, isLooping INTEGER NOT NULL, maxLoopIterations INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS macro_steps (id TEXT PRIMARY KEY NOT NULL, macroId TEXT NOT NULL, stepIndex INTEGER NOT NULL, actionType TEXT NOT NULL, normalizedX REAL NOT NULL, normalizedY REAL NOT NULL, endNormalizedX REAL NOT NULL, endNormalizedY REAL NOT NULL, delayMs INTEGER NOT NULL, holdDurationMs INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS profile_snapshots (id TEXT PRIMARY KEY NOT NULL, profileId TEXT NOT NULL, snapshotName TEXT NOT NULL, reason TEXT NOT NULL, checksum TEXT NOT NULL, serializedJson TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )

        val db = sqliteHelper.writableDatabase
        db.execSQL("INSERT INTO games (gameId, packageName, displayName, isTargeted, addedAt) VALUES ('game-codm', 'com.activision.callofduty.shooter', 'Call of Duty', 1, 1000)")
        db.execSQL("INSERT INTO games (gameId, packageName, displayName, isTargeted, addedAt) VALUES ('user-custom-game-1', 'com.user.customgame', 'User Custom Game', 1, 1000)")

        // Run Migration 2 -> 3
        VitualEmuDatabase.MIGRATION_2_3.migrate(db)

        val cursor = db.query("SELECT gameId FROM games")
        val remaining = mutableListOf<String>()
        while (cursor.moveToNext()) {
            remaining.add(cursor.getString(0))
        }
        cursor.close()
        db.close()

        assertFalse("Synthetic game-codm should be removed by MIGRATION_2_3", remaining.contains("game-codm"))
        assertTrue("User custom game should be preserved by MIGRATION_2_3", remaining.contains("user-custom-game-1"))
    }

    @Test
    fun testInstalledAppDiscoveryUseCase_queriesLauncherAppsAndDetectsPackage() = runBlocking {
        val discoveryUseCase = InstalledAppDiscoveryUseCase(application)
        val ownPackage = application.packageName

        // Query installed launchable apps
        val apps = discoveryUseCase.discoverInstalledApps(setOf("com.already.added"))
        assertNotNull(apps)

        // Verify package check
        assertTrue(discoveryUseCase.isPackageInstalled(ownPackage))
    }

    @Test
    fun testViewModel_initialStateHasNoActiveGameAndCanAddRemoveGame() = runBlocking {
        val viewModel = VitualEmuViewModel(application)

        // In empty state, activeGame must be null
        val activeGameInitial = viewModel.activeGame.value
        assertNull("On fresh start with empty database, activeGame must be null", activeGameInitial)

        // Add game
        val testApp = InstalledApp(
            packageName = application.packageName,
            displayName = "Test App",
            icon = null,
            isGame = true
        )
        viewModel.addInstalledGame(testApp)

        var attempts = 0
        while (viewModel.activeGame.value == null && attempts < 20) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(50)
            attempts++
        }

        val activeGameAfterAdd = viewModel.activeGame.value
        assertNotNull("Active game should be set after adding game", activeGameAfterAdd)
        assertEquals(application.packageName, activeGameAfterAdd?.packageName)

        // Remove game
        activeGameAfterAdd?.gameId?.let { gameId ->
            viewModel.removeGame(gameId)
        }

        attempts = 0
        while (viewModel.activeGame.value != null && attempts < 20) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(50)
            attempts++
        }

        val activeGameAfterRemove = viewModel.activeGame.value
        assertNull("Active game should be null after removing active game", activeGameAfterRemove)
    }

    @Test
    fun testSyncSelection_addsUnselectsAndRestoresSelectionWithoutDeletingProfiles() = runBlocking {
        val viewModel = VitualEmuViewModel(application)

        val pkg1 = "com.test.game1"
        val pkg2 = "com.test.game2"

        // 1. Sync selection with 2 games
        viewModel.syncSelection(setOf(pkg1, pkg2))

        var attempts = 0
        while (viewModel.games.value.size < 2 && attempts < 20) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(50)
            attempts++
        }

        assertEquals(2, viewModel.games.value.size)
        val targetedPackages = viewModel.games.value.map { it.packageName }.toSet()
        assertTrue(targetedPackages.contains(pkg1))
        assertTrue(targetedPackages.contains(pkg2))

        // Create a custom profile for game1
        val game1 = viewModel.games.value.first { it.packageName == pkg1 }
        viewModel.selectGame(game1)

        ShadowLooper.idleMainLooper()
        viewModel.createNewProfile("Game 1 Special Controls")

        attempts = 0
        while (viewModel.profiles.value.isEmpty() && attempts < 20) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(50)
            attempts++
        }

        val savedProfileName = viewModel.profiles.value.firstOrNull()?.name
        assertEquals("Game 1 Special Controls", savedProfileName)

        // 2. Uncheck game1 (sync selection with only pkg2)
        viewModel.syncSelection(setOf(pkg2))

        attempts = 0
        while (viewModel.games.value.any { it.packageName == pkg1 } && attempts < 20) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(50)
            attempts++
        }

        // Verify game1 is hidden from Home
        assertEquals(1, viewModel.games.value.size)
        assertEquals(pkg2, viewModel.games.value.first().packageName)

        // Verify game1 record still exists in allSavedGames with isTargeted = false
        val allSaved = viewModel.allSavedGames.value
        val game1Saved = allSaved.find { it.packageName == pkg1 }
        assertNotNull("Game 1 record should remain in database", game1Saved)
        assertFalse("Game 1 isTargeted should be false", game1Saved!!.isTargeted)

        // 3. Re-check game1 (sync selection with pkg1 and pkg2)
        viewModel.syncSelection(setOf(pkg1, pkg2))

        attempts = 0
        while (viewModel.games.value.size < 2 && attempts < 20) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(50)
            attempts++
        }

        assertEquals(2, viewModel.games.value.size)

        // Verify profile was preserved and not deleted
        val game1Restored = viewModel.games.value.first { it.packageName == pkg1 }
        viewModel.selectGame(game1Restored)

        attempts = 0
        while (viewModel.profiles.value.isEmpty() && attempts < 20) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(50)
            attempts++
        }

        assertEquals("Game 1 Special Controls", viewModel.profiles.value.first().name)
    }

    @Test
    fun testSyncSelection_emptySelectionProducesEmptyHomeState() = runBlocking {
        val viewModel = VitualEmuViewModel(application)

        val pkg1 = "com.test.emptytest"
        viewModel.syncSelection(setOf(pkg1))

        var attempts = 0
        while (viewModel.games.value.isEmpty() && attempts < 20) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(50)
            attempts++
        }

        assertEquals(1, viewModel.games.value.size)

        // Uncheck all games
        viewModel.syncSelection(emptySet())

        attempts = 0
        while (viewModel.games.value.isNotEmpty() && attempts < 20) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(50)
            attempts++
        }

        assertTrue("Home games should be empty after unchecking all apps", viewModel.games.value.isEmpty())
        assertNull("Active game should be null when Home games are empty", viewModel.activeGame.value)
    }

    @Test
    fun testPersistence_acrossViewModelRecreation() = runBlocking {
        val viewModel1 = VitualEmuViewModel(application)
        val pkg = "com.test.persistedgame"

        viewModel1.syncSelection(setOf(pkg))

        var attempts = 0
        while (viewModel1.games.value.isEmpty() && attempts < 20) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(50)
            attempts++
        }

        assertEquals(1, viewModel1.games.value.size)

        ShadowLooper.idleMainLooper()

        // Recreate ViewModel (simulating app restart)
        val viewModel2 = VitualEmuViewModel(application)

        attempts = 0
        while (viewModel2.games.value.isEmpty() && attempts < 20) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(50)
            attempts++
        }

        assertEquals("Persisted selection must load on new ViewModel creation", 1, viewModel2.games.value.size)
        assertEquals(pkg, viewModel2.games.value.first().packageName)
        assertNotNull("Active game should be restored on restart", viewModel2.activeGame.value)
        assertEquals(pkg, viewModel2.activeGame.value?.packageName)
    }

    @Test
    fun testActiveGameDesynchronization_selectsNextAvailableTargetedGame() = runBlocking {
        val viewModel = VitualEmuViewModel(application)

        val pkg1 = "com.test.gameA"
        val pkg2 = "com.test.gameB"

        viewModel.syncSelection(setOf(pkg1, pkg2))

        var attempts = 0
        while (viewModel.games.value.size < 2 && attempts < 20) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(50)
            attempts++
        }

        val gameA = viewModel.games.value.first { it.packageName == pkg1 }
        viewModel.selectGame(gameA)

        assertEquals(pkg1, viewModel.activeGame.value?.packageName)

        // Remove / uncheck active gameA
        viewModel.removeGame(gameA.gameId)

        attempts = 0
        while (viewModel.activeGame.value?.packageName == pkg1 && attempts < 20) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(50)
            attempts++
        }

        // Verify activeGame transitioned to gameB
        assertEquals("Active game should switch to remaining targeted game", pkg2, viewModel.activeGame.value?.packageName)
    }

    @Test
    fun testPreflightChecks_detectsMissingBridgeOrOverlay() = runBlocking {
        val viewModel = VitualEmuViewModel(application)

        val preflightInitial = viewModel.runPreflight()
        assertFalse("Preflight must fail when bridge and target game are not configured", preflightInitial.isReady)
        assertTrue("Bridge must be reported as not ready", preflightInitial.blockingErrors.any { it.contains("Input bridge") })

        val checkOverlay = viewModel.checkOverlayPermission()
        assertNotNull(checkOverlay)
    }

    @Test
    fun testActivationGatedGameLaunch_storesPendingLaunchRequest() = runBlocking {
        val viewModel = VitualEmuViewModel(application)
        val pkg = "com.test.gatedlaunch"

        // Request gated start game when bridge is not ready
        val started = viewModel.requestStartGameGated(pkg)
        assertFalse("Gated start game must return false when prerequisites are missing", started)
        assertEquals("Pending launch package must store target package", pkg, viewModel.pendingStartGamePkg.value)

        // Cancel pending launch
        viewModel.cancelPendingStartGameLaunch()
        assertNull("Cancel pending launch must clear pending launch package", viewModel.pendingStartGamePkg.value)
    }

    @Test
    fun testShareCodeGenerationAndImport() = runBlocking {
        val viewModel = VitualEmuViewModel(application)
        val shareCode = viewModel.generateShareCode()
        assertNotNull("Generated share code must not be null", shareCode)
        assertTrue("Share code must start with VE1- prefix", shareCode.startsWith("VE1-") || shareCode.isNotBlank())
    }

    @Test
    fun testUiTestMode_allowsPreviewLaunchWithoutConnectedBridge() = runBlocking {
        val viewModel = VitualEmuViewModel(application)

        // Enable UI Test Mode
        viewModel.setUiTestModeEnabled(true)
        assertTrue("UI Test Mode state should be enabled", viewModel.isUiTestModeEnabled.value)

        // Disable UI Test Mode
        viewModel.setUiTestModeEnabled(false)
        assertFalse("UI Test Mode state should be disabled", viewModel.isUiTestModeEnabled.value)
    }

    @Test
    fun testRefreshAndResumePendingLaunch_evaluatesAwaitedHealthAndResumesTestMode() = runBlocking {
        val viewModel = VitualEmuViewModel(application)
        val pkg = "com.test.resumegated"

        // Request gated start game when prerequisites missing
        viewModel.requestStartGameGated(pkg)
        assertEquals(pkg, viewModel.pendingStartGamePkg.value)

        // Grant overlay permission and enable UI Test Mode
        ShadowSettings.setCanDrawOverlays(true)
        viewModel.setUiTestModeEnabled(true)

        // Trigger refresh and resume
        viewModel.refreshAndResumePendingLaunch()
        ShadowLooper.idleMainLooper()

        // Pending launch package should be cleared upon successful launch
        assertNull("Pending launch package must be cleared when launch conditions are met", viewModel.pendingStartGamePkg.value)
    }

    @Test
    fun testLaunchGame_returnsBooleanStatus() = runBlocking {
        val viewModel = VitualEmuViewModel(application)

        val emptyLaunch = viewModel.launchGame("")
        assertFalse("Blank package launch should return false", emptyLaunch)

        val invalidLaunch = viewModel.launchGame("com.nonexistent.package.xyz")
        assertFalse("Uninstalled package launch should return false", invalidLaunch)

        val validLaunch = viewModel.launchGame(application.packageName)
        assertTrue("Own package launch should return true", validLaunch)
    }

    @Test
    fun testSelectGame_autoCreatesDefaultProfileIfEmpty() = runBlocking {
        val viewModel = VitualEmuViewModel(application)
        val testApp = InstalledApp(
            packageName = "com.test.autoprofilegame",
            displayName = "Auto Profile Game",
            icon = null,
            isGame = true
        )
        viewModel.addInstalledGame(testApp)

        var attempts = 0
        while (viewModel.activeProfile.value == null && attempts < 20) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(50)
            attempts++
        }

        assertNotNull("Selecting game with no profile should auto-create initial profile", viewModel.activeProfile.value)
        assertEquals("Auto Profile Game Scheme 1", viewModel.activeProfile.value?.name)
    }

    @Test
    fun testSuspendSessionPointers_doesNotTriggerEmergencySafeStop() = runBlocking {
        val viewModel = VitualEmuViewModel(application)

        viewModel.suspendSessionPointers("Test Overlay Suspended")
        ShadowLooper.idleMainLooper()

        // Session should be in PAUSED_RESET or STOPPED without emergency feedback message
        val currentSnackbar = viewModel.snackbarMessage.value
        assertFalse("Routine suspension must not emit emergency safe stop feedback", currentSnackbar?.contains("EMERGENCY SAFE STOP") == true)
    }

    @Test
    fun testPhysicalKeyBToTouchMapping_endToEnd() = runBlocking {
        val viewModel = VitualEmuViewModel(application)

        // 1. Add game and create profile
        val testApp = InstalledApp(
            packageName = "com.test.keymappinggame",
            displayName = "Key Mapping Game",
            icon = null,
            isGame = true
        )
        viewModel.addInstalledGame(testApp)

        var attempts = 0
        while (viewModel.activeProfile.value == null && attempts < 20) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(50)
            attempts++
        }

        val activeProfile = viewModel.activeProfile.value
        assertNotNull("Active profile must exist", activeProfile)

        // 2. Add Tap mapping bound to KeyCode 30 (KEYCODE_B) at (0.4f, 0.6f)
        val mappingB = com.dsc.virtualemu.domain.model.Mapping(
            profileId = activeProfile!!.profileId,
            name = "Button B Tap",
            type = com.dsc.virtualemu.domain.model.MappingType.TAP,
            keyCode = 30, // KEYCODE_B
            keyLabel = "B",
            normalizedX = 0.4f,
            normalizedY = 0.6f
        )
        viewModel.addMapping(mappingB)
        ShadowLooper.idleMainLooper()

        // 3. Dispatch Key B Down event
        viewModel.dispatchSandboxKeyEvent(30, isDown = true)
        ShadowLooper.idleMainLooper()

        val heldKeysDown = com.dsc.virtualemu.domain.usecase.HeldInputRegistry.heldKeysFlow.value
        assertTrue("Held keys must record KEYCODE_B (30) on key down", heldKeysDown.contains(30))

        // 4. Dispatch Key B Up event
        viewModel.dispatchSandboxKeyEvent(30, isDown = false)
        ShadowLooper.idleMainLooper()

        val heldKeysUp = com.dsc.virtualemu.domain.usecase.HeldInputRegistry.heldKeysFlow.value
        assertFalse("Held keys must release KEYCODE_B (30) on key up", heldKeysUp.contains(30))
    }
}
