package com.dsc.virtualemu

import android.app.Application
import androidx.room.Room
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DatabaseMigrationAndViewModelTest {

    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext<Application>()
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
        val db = Room.inMemoryDatabaseBuilder(application, VitualEmuDatabase::class.java)
            .allowMainThreadQueries()
            .build()

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
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
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
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            Thread.sleep(50)
            attempts++
        }

        val activeGameAfterRemove = viewModel.activeGame.value
        assertNull("Active game should be null after removing active game", activeGameAfterRemove)

        db.close()
    }
}
