package com.dsc.virtualemu.data.local.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM games ORDER BY lastPlayedAt DESC, addedAt DESC")
    fun getAllGames(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE gameId = :gameId")
    suspend fun getGameById(gameId: String): GameEntity?

    @Query("SELECT * FROM games WHERE packageName = :packageName")
    suspend fun getGameByPackage(packageName: String): GameEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGame(game: GameEntity)

    @Update
    suspend fun updateGame(game: GameEntity)

    @Delete
    suspend fun deleteGame(game: GameEntity)
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles WHERE gameId = :gameId ORDER BY isFavorite DESC, updatedAt DESC")
    fun getProfilesForGame(gameId: String): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE profileId = :profileId")
    suspend fun getProfileById(profileId: String): ProfileEntity?

    @Query("SELECT * FROM profiles ORDER BY updatedAt DESC")
    fun getAllProfiles(): Flow<List<ProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity)

    @Update
    suspend fun updateProfile(profile: ProfileEntity)

    @Delete
    suspend fun deleteProfile(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE profileId = :profileId")
    suspend fun deleteProfileById(profileId: String)
}

@Dao
interface MappingDao {
    @Query("SELECT * FROM mappings WHERE profileId = :profileId")
    fun getMappingsForProfile(profileId: String): Flow<List<MappingEntity>>

    @Query("SELECT * FROM mappings WHERE profileId = :profileId")
    suspend fun getMappingsListForProfile(profileId: String): List<MappingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMappings(mappings: List<MappingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMapping(mapping: MappingEntity)

    @Query("DELETE FROM mappings WHERE profileId = :profileId")
    suspend fun deleteMappingsForProfile(profileId: String)

    @Query("DELETE FROM mappings WHERE id = :mappingId")
    suspend fun deleteMappingById(mappingId: String)
}

@Dao
interface MacroDao {
    @Query("SELECT * FROM macros WHERE profileId = :profileId")
    fun getMacrosForProfile(profileId: String): Flow<List<MacroEntity>>

    @Query("SELECT * FROM macros WHERE profileId = :profileId")
    suspend fun getMacrosListForProfile(profileId: String): List<MacroEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMacro(macro: MacroEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMacroSteps(steps: List<MacroStepEntity>)

    @Query("SELECT * FROM macro_steps WHERE macroId = :macroId ORDER BY stepIndex ASC")
    suspend fun getStepsForMacro(macroId: String): List<MacroStepEntity>

    @Query("DELETE FROM macros WHERE profileId = :profileId")
    suspend fun deleteMacrosForProfile(profileId: String)
}

@Dao
interface ProfileSnapshotDao {
    @Query("SELECT * FROM profile_snapshots WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun getSnapshotsForProfile(profileId: String): Flow<List<ProfileSnapshotEntity>>

    @Query("SELECT * FROM profile_snapshots WHERE id = :snapshotId")
    suspend fun getSnapshotById(snapshotId: String): ProfileSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: ProfileSnapshotEntity)

    @Query("DELETE FROM profile_snapshots WHERE profileId = :profileId")
    suspend fun deleteSnapshotsForProfile(profileId: String)
}
