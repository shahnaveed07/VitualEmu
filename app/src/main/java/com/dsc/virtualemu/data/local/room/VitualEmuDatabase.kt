package com.dsc.virtualemu.data.local.room

import android.content.Context
import android.view.KeyEvent
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

@Database(
    entities = [
        GameEntity::class,
        ProfileEntity::class,
        MappingEntity::class,
        MacroEntity::class,
        MacroStepEntity::class,
        ProfileSnapshotEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class VitualEmuDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun profileDao(): ProfileDao
    abstract fun mappingDao(): MappingDao
    abstract fun macroDao(): MacroDao
    abstract fun profileSnapshotDao(): ProfileSnapshotDao

    companion object {
        @Volatile
        private var INSTANCE: VitualEmuDatabase? = null

        fun getInstance(context: Context): VitualEmuDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VitualEmuDatabase::class.java,
                    "vitualemu.db"
                )
                .fallbackToDestructiveMigration()
                .addCallback(DatabaseCallback())
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            CoroutineScope(Dispatchers.IO).launch {
                INSTANCE?.let { database ->
                    seedDefaultGamesAndProfiles(database)
                }
            }
        }

        private suspend fun seedDefaultGamesAndProfiles(db: VitualEmuDatabase) {
            val gameCodmId = "game-codm"
            val gameFreeFireId = "game-freefire"
            val gameBgmiId = "game-bgmi"
            val gamePubgId = "game-pubgm"

            db.gameDao().insertGame(
                GameEntity(
                    gameId = gameCodmId,
                    packageName = "com.activision.callofduty.shooter",
                    displayName = "Call of Duty: Mobile",
                    iconUri = null,
                    isTargeted = true,
                    lastPlayedAt = System.currentTimeMillis()
                )
            )
            db.gameDao().insertGame(
                GameEntity(
                    gameId = gameFreeFireId,
                    packageName = "com.dts.freefireth",
                    displayName = "Free Fire MAX / TH",
                    iconUri = null,
                    isTargeted = true,
                    lastPlayedAt = System.currentTimeMillis() - 3600000L
                )
            )
            db.gameDao().insertGame(
                GameEntity(
                    gameId = gameBgmiId,
                    packageName = "com.pubg.imobile",
                    displayName = "Battlegrounds Mobile India (BGMI)",
                    iconUri = null,
                    isTargeted = true,
                    lastPlayedAt = System.currentTimeMillis() - 7200000L
                )
            )
            db.gameDao().insertGame(
                GameEntity(
                    gameId = gamePubgId,
                    packageName = "com.tencent.ig",
                    displayName = "PUBG Mobile",
                    iconUri = null,
                    isTargeted = true,
                    lastPlayedAt = System.currentTimeMillis() - 86400000L
                )
            )

            // Seed default CODM Tactical profile
            val codProfileId = "profile-codm-tactical"
            db.profileDao().insertProfile(
                ProfileEntity(
                    profileId = codProfileId,
                    gameId = gameCodmId,
                    name = "Tactical FPS (WASD + 360° Mouse Look)",
                    isFavorite = true
                )
            )

            val glooMacroId = "macro-quick-slide-aim"
            db.macroDao().insertMacro(
                MacroEntity(
                    id = glooMacroId,
                    profileId = codProfileId,
                    name = "Quick Slide + ADS Combo",
                    triggerKeyCode = KeyEvent.KEYCODE_Q,
                    triggerKeyLabel = "Q",
                    triggerMode = "ON_PRESS",
                    isLooping = false,
                    maxLoopIterations = 1
                )
            )
            db.macroDao().insertMacroSteps(
                listOf(
                    MacroStepEntity(
                        id = UUID.randomUUID().toString(),
                        macroId = glooMacroId,
                        stepIndex = 0,
                        actionType = "TAP",
                        normalizedX = 0.92f,
                        normalizedY = 0.88f,
                        delayMs = 30L,
                        holdDurationMs = 45L
                    ),
                    MacroStepEntity(
                        id = UUID.randomUUID().toString(),
                        macroId = glooMacroId,
                        stepIndex = 1,
                        actionType = "TAP",
                        normalizedX = 0.82f,
                        normalizedY = 0.48f,
                        delayMs = 40L,
                        holdDurationMs = 50L
                    )
                )
            )

            val codMappings = listOf(
                MappingEntity(
                    id = UUID.randomUUID().toString(),
                    profileId = codProfileId,
                    name = "WASD Movement Stick",
                    type = "WASD_STICK",
                    keyCode = KeyEvent.KEYCODE_W,
                    keyLabel = "WASD",
                    normalizedX = 0.18f,
                    normalizedY = 0.72f,
                    normalizedWidth = 0.18f,
                    normalizedHeight = 0.18f
                ),
                MappingEntity(
                    id = UUID.randomUUID().toString(),
                    profileId = codProfileId,
                    name = "360° Aim Look Camera",
                    type = "AIM_LOOK",
                    keyCode = KeyEvent.KEYCODE_GRAVE,
                    keyLabel = "GRAVE (~)",
                    normalizedX = 0.72f,
                    normalizedY = 0.48f,
                    normalizedWidth = 0.40f,
                    normalizedHeight = 0.45f,
                    sensitivity = 1.2f
                ),
                MappingEntity(
                    id = UUID.randomUUID().toString(),
                    profileId = codProfileId,
                    name = "Free Look (Eye Button)",
                    type = "FREE_LOOK_EYE",
                    keyCode = KeyEvent.KEYCODE_ALT_LEFT,
                    keyLabel = "ALT_LEFT",
                    normalizedX = 0.80f,
                    normalizedY = 0.25f,
                    normalizedWidth = 0.07f,
                    normalizedHeight = 0.07f,
                    sensitivity = 1.1f
                ),
                MappingEntity(
                    id = UUID.randomUUID().toString(),
                    profileId = codProfileId,
                    name = "Primary Fire + Smart Drag",
                    type = "FIRE_WEAPON",
                    keyCode = KeyEvent.KEYCODE_BUTTON_L1,
                    keyLabel = "Mouse LClick",
                    normalizedX = 0.85f,
                    normalizedY = 0.65f,
                    normalizedWidth = 0.08f,
                    normalizedHeight = 0.08f
                ),
                MappingEntity(
                    id = UUID.randomUUID().toString(),
                    profileId = codProfileId,
                    name = "Aim Down Sight (ADS)",
                    type = "HOLD",
                    keyCode = KeyEvent.KEYCODE_BUTTON_R1,
                    keyLabel = "Mouse RClick",
                    normalizedX = 0.82f,
                    normalizedY = 0.48f,
                    normalizedWidth = 0.08f,
                    normalizedHeight = 0.08f
                ),
                MappingEntity(
                    id = UUID.randomUUID().toString(),
                    profileId = codProfileId,
                    name = "Jump",
                    type = "TAP",
                    keyCode = KeyEvent.KEYCODE_SPACE,
                    keyLabel = "SPACE",
                    normalizedX = 0.92f,
                    normalizedY = 0.72f,
                    normalizedWidth = 0.07f,
                    normalizedHeight = 0.07f
                ),
                MappingEntity(
                    id = UUID.randomUUID().toString(),
                    profileId = codProfileId,
                    name = "Crouch / Slide",
                    type = "TAP",
                    keyCode = KeyEvent.KEYCODE_C,
                    keyLabel = "C",
                    normalizedX = 0.92f,
                    normalizedY = 0.86f,
                    normalizedWidth = 0.07f,
                    normalizedHeight = 0.07f
                ),
                MappingEntity(
                    id = UUID.randomUUID().toString(),
                    profileId = codProfileId,
                    name = "Reload",
                    type = "TAP",
                    keyCode = KeyEvent.KEYCODE_R,
                    keyLabel = "R",
                    normalizedX = 0.75f,
                    normalizedY = 0.86f,
                    normalizedWidth = 0.07f,
                    normalizedHeight = 0.07f
                ),
                MappingEntity(
                    id = UUID.randomUUID().toString(),
                    profileId = codProfileId,
                    name = "Quick Slide Combo",
                    type = "MACRO_TRIGGER",
                    keyCode = KeyEvent.KEYCODE_Q,
                    keyLabel = "Q",
                    normalizedX = 0.66f,
                    normalizedY = 0.84f,
                    normalizedWidth = 0.07f,
                    normalizedHeight = 0.07f,
                    macroId = glooMacroId
                )
            )
            db.mappingDao().insertMappings(codMappings)

            // Seed Free Fire Pro Headshot & Gloo Wall profile
            val ffProfileId = "profile-ff-pro"
            db.profileDao().insertProfile(
                ProfileEntity(
                    profileId = ffProfileId,
                    gameId = gameFreeFireId,
                    name = "Free Fire Pro (Gloo Wall + Smart Drag)",
                    isFavorite = true
                )
            )
            db.mappingDao().insertMappings(
                codMappings.map {
                    it.copy(
                        id = UUID.randomUUID().toString(),
                        profileId = ffProfileId
                    )
                }
            )
        }
    }
}
