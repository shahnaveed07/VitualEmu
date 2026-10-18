package com.dsc.virtualemu.data.local.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        GameEntity::class,
        ProfileEntity::class,
        MappingEntity::class,
        MacroEntity::class,
        MacroStepEntity::class,
        ProfileSnapshotEntity::class
    ],
    version = 4,
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val syntheticGameIds = "('game-codm', 'game-freefire', 'game-bgmi', 'game-pubgm')"
                val syntheticProfileIds = "('profile-codm-tactical', 'profile-ff-pro')"

                db.execSQL("DELETE FROM profile_snapshots WHERE profileId IN $syntheticProfileIds")
                db.execSQL("DELETE FROM macro_steps WHERE macroId IN (SELECT id FROM macros WHERE profileId IN $syntheticProfileIds)")
                db.execSQL("DELETE FROM macros WHERE profileId IN $syntheticProfileIds")
                db.execSQL("DELETE FROM mappings WHERE profileId IN $syntheticProfileIds")
                db.execSQL("DELETE FROM profiles WHERE profileId IN $syntheticProfileIds OR gameId IN $syntheticGameIds")
                db.execSQL("DELETE FROM games WHERE gameId IN $syntheticGameIds")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE mappings ADD COLUMN altKeyCode INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE mappings ADD COLUMN altKeyLabel TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE mappings ADD COLUMN sensitivityRatioY REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE mappings ADD COLUMN repeatDelayMs INTEGER NOT NULL DEFAULT 50")
                db.execSQL("ALTER TABLE mappings ADD COLUMN repeatUntilKeyUp INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE mappings ADD COLUMN guidanceCategory TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE mappings ADD COLUMN guidanceText TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): VitualEmuDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VitualEmuDatabase::class.java,
                    "vitualemu.db"
                )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }

        fun closeAndClearInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}

