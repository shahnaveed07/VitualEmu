package com.vitualemu.app.data.local.room

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
    version = 1,
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
            val game1Id = "game-codm"
            val game2Id = "game-pubgm"
            val game3Id = "game-genshin"

            db.gameDao().insertGame(
                GameEntity(
                    gameId = game1Id,
                    packageName = "com.activision.callofduty.shooter",
                    displayName = "Call of Duty: Mobile",
                    iconUri = null,
                    isTargeted = true,
                    lastPlayedAt = System.currentTimeMillis()
                )
            )
            db.gameDao().insertGame(
                GameEntity(
                    gameId = game2Id,
                    packageName = "com.tencent.ig",
                    displayName = "PUBG Mobile",
                    iconUri = null,
                    isTargeted = true,
                    lastPlayedAt = System.currentTimeMillis() - 86400000L
                )
            )
            db.gameDao().insertGame(
                GameEntity(
                    gameId = game3Id,
                    packageName = "com.miHoYo.GenshinImpact",
                    displayName = "Genshin Impact",
                    iconUri = null,
                    isTargeted = true,
                    lastPlayedAt = null
                )
            )

            // Seed default COD profile
            val codProfileId = "profile-codm-tactical"
            db.profileDao().insertProfile(
                ProfileEntity(
                    profileId = codProfileId,
                    gameId = game1Id,
                    name = "Tactical FPS (WASD + Mouse)",
                    isFavorite = true
                )
            )

            val codMappings = listOf(
                MappingEntity(
                    id = UUID.randomUUID().toString(),
                    profileId = codProfileId,
                    name = "WASD Movement",
                    type = "WASD_STICK",
                    keyCode = KeyEvent.KEYCODE_W,
                    keyLabel = "WASD",
                    normalizedX = 0.20f,
                    normalizedY = 0.70f,
                    normalizedWidth = 0.18f,
                    normalizedHeight = 0.18f
                ),
                MappingEntity(
                    id = UUID.randomUUID().toString(),
                    profileId = codProfileId,
                    name = "Aim / Look Camera",
                    type = "AIM_LOOK",
                    keyCode = 0,
                    keyLabel = "Mouse",
                    normalizedX = 0.72f,
                    normalizedY = 0.45f,
                    normalizedWidth = 0.40f,
                    normalizedHeight = 0.45f,
                    sensitivity = 1.2f
                ),
                MappingEntity(
                    id = UUID.randomUUID().toString(),
                    profileId = codProfileId,
                    name = "Fire Weapon",
                    type = "TAP",
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
                    keyLabel = "Space",
                    normalizedX = 0.92f,
                    normalizedY = 0.75f,
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
                    normalizedY = 0.88f,
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
                    normalizedY = 0.85f,
                    normalizedWidth = 0.07f,
                    normalizedHeight = 0.07f
                ),
                MappingEntity(
                    id = UUID.randomUUID().toString(),
                    profileId = codProfileId,
                    name = "Sprint Boost",
                    type = "HOLD",
                    keyCode = KeyEvent.KEYCODE_SHIFT_LEFT,
                    keyLabel = "LShift",
                    normalizedX = 0.20f,
                    normalizedY = 0.50f,
                    normalizedWidth = 0.06f,
                    normalizedHeight = 0.06f
                )
            )
            db.mappingDao().insertMappings(codMappings)
        }
    }
}
