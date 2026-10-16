package com.vitualemu.app.data.repository

import android.content.Context
import com.vitualemu.app.data.local.room.GameEntity
import com.vitualemu.app.data.local.room.MacroEntity
import com.vitualemu.app.data.local.room.MacroStepEntity
import com.vitualemu.app.data.local.room.MappingEntity
import com.vitualemu.app.data.local.room.ProfileEntity
import com.vitualemu.app.data.local.room.ProfileSnapshotEntity
import com.vitualemu.app.data.local.room.VitualEmuDatabase
import com.vitualemu.app.domain.model.Game
import com.vitualemu.app.domain.model.Macro
import com.vitualemu.app.domain.model.MacroStep
import com.vitualemu.app.domain.model.Mapping
import com.vitualemu.app.domain.model.MappingType
import com.vitualemu.app.domain.model.Profile
import com.vitualemu.app.domain.model.ProfileSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

class VitualEmuRepository(context: Context) {
    private val database = VitualEmuDatabase.getInstance(context)
    private val gameDao = database.gameDao()
    private val profileDao = database.profileDao()
    private val mappingDao = database.mappingDao()
    private val macroDao = database.macroDao()
    private val snapshotDao = database.profileSnapshotDao()

    fun getAllGames(): Flow<List<Game>> {
        return gameDao.getAllGames().map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun insertGame(game: Game) = withContext(Dispatchers.IO) {
        gameDao.insertGame(game.toEntity())
    }

    suspend fun updateGame(game: Game) = withContext(Dispatchers.IO) {
        gameDao.updateGame(game.toEntity())
    }

    suspend fun deleteGame(game: Game) = withContext(Dispatchers.IO) {
        gameDao.deleteGame(game.toEntity())
    }

    fun getProfilesForGame(gameId: String): Flow<List<Profile>> {
        return profileDao.getProfilesForGame(gameId).map { entities ->
            entities.map { entity ->
                val mappings = mappingDao.getMappingsListForProfile(entity.profileId).map { it.toDomain() }
                val macros = macroDao.getMacrosListForProfile(entity.profileId).map { m ->
                    val steps = macroDao.getStepsForMacro(m.id).map { it.toDomain() }
                    m.toDomain(steps)
                }
                entity.toDomain(mappings, macros)
            }
        }
    }

    suspend fun getProfileWithDetails(profileId: String): Profile? = withContext(Dispatchers.IO) {
        val entity = profileDao.getProfileById(profileId) ?: return@withContext null
        val mappings = mappingDao.getMappingsListForProfile(profileId).map { it.toDomain() }
        val macros = macroDao.getMacrosListForProfile(profileId).map { m ->
            val steps = macroDao.getStepsForMacro(m.id).map { it.toDomain() }
            m.toDomain(steps)
        }
        entity.toDomain(mappings, macros)
    }

    suspend fun saveProfile(profile: Profile, snapshotReason: String? = null) = withContext(Dispatchers.IO) {
        profileDao.insertProfile(profile.toEntity())
        mappingDao.deleteMappingsForProfile(profile.profileId)
        mappingDao.insertMappings(profile.mappings.map { it.toEntity() })

        macroDao.deleteMacrosForProfile(profile.profileId)
        for (macro in profile.macros) {
            macroDao.insertMacro(macro.toEntity())
            macroDao.insertMacroSteps(macro.steps.map { it.toEntity() })
        }

        if (snapshotReason != null) {
            createSnapshot(profile, snapshotReason)
        }
    }

    suspend fun deleteProfile(profileId: String) = withContext(Dispatchers.IO) {
        profileDao.deleteProfileById(profileId)
    }

    suspend fun duplicateProfile(original: Profile, newName: String): Profile = withContext(Dispatchers.IO) {
        val newProfileId = UUID.randomUUID().toString()
        val duplicated = original.copy(
            profileId = newProfileId,
            name = newName,
            isFavorite = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            mappings = original.mappings.map { it.copy(id = UUID.randomUUID().toString(), profileId = newProfileId) },
            macros = original.macros.map { m ->
                val newMacroId = UUID.randomUUID().toString()
                m.copy(
                    id = newMacroId,
                    profileId = newProfileId,
                    steps = m.steps.map { it.copy(id = UUID.randomUUID().toString(), macroId = newMacroId) }
                )
            }
        )
        saveProfile(duplicated, "Duplicated from ${original.name}")
        duplicated
    }

    fun getSnapshotsForProfile(profileId: String): Flow<List<ProfileSnapshot>> {
        return snapshotDao.getSnapshotsForProfile(profileId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun createSnapshot(profile: Profile, reason: String) = withContext(Dispatchers.IO) {
        val json = serializeProfileToJson(profile)
        val checksum = sha256(json)
        val snapshot = ProfileSnapshotEntity(
            id = UUID.randomUUID().toString(),
            profileId = profile.profileId,
            snapshotName = "${profile.name} - ${System.currentTimeMillis()}",
            reason = reason,
            checksum = checksum,
            serializedJson = json,
            createdAt = System.currentTimeMillis()
        )
        snapshotDao.insertSnapshot(snapshot)
    }

    suspend fun rollbackToSnapshot(snapshotId: String): Profile? = withContext(Dispatchers.IO) {
        val snapshot = snapshotDao.getSnapshotById(snapshotId) ?: return@withContext null
        val restored = deserializeProfileFromJson(snapshot.serializedJson) ?: return@withContext null
        saveProfile(restored, "Rollback to snapshot ${snapshot.reason}")
        restored
    }

    fun serializeProfileToJson(profile: Profile): String {
        val root = JSONObject()
        root.put("schemaVersion", profile.schemaVersion)
        root.put("profileId", profile.profileId)
        root.put("gameId", profile.gameId)
        root.put("name", profile.name)
        root.put("createdAt", profile.createdAt)
        root.put("updatedAt", profile.updatedAt)

        val mappingsArray = JSONArray()
        for (m in profile.mappings) {
            val mo = JSONObject()
            mo.put("id", m.id)
            mo.put("name", m.name)
            mo.put("type", m.type.name)
            mo.put("keyCode", m.keyCode)
            mo.put("keyLabel", m.keyLabel)
            mo.put("normalizedX", m.normalizedX.toDouble())
            mo.put("normalizedY", m.normalizedY.toDouble())
            mo.put("normalizedWidth", m.normalizedWidth.toDouble())
            mo.put("normalizedHeight", m.normalizedHeight.toDouble())
            mo.put("sensitivity", m.sensitivity.toDouble())
            mo.put("isToggle", m.isToggle)
            mo.put("macroId", m.macroId ?: "")
            mappingsArray.put(mo)
        }
        root.put("mappings", mappingsArray)
        return root.toString(2)
    }

    fun deserializeProfileFromJson(jsonStr: String): Profile? {
        return try {
            val root = JSONObject(jsonStr)
            val profileId = root.optString("profileId", UUID.randomUUID().toString())
            val gameId = root.optString("gameId", "")
            val name = root.optString("name", "Imported Profile")
            val schemaVersion = root.optInt("schemaVersion", 1)

            val mappingsList = mutableListOf<Mapping>()
            val mappingsArray = root.optJSONArray("mappings")
            if (mappingsArray != null) {
                for (i in 0 until mappingsArray.length()) {
                    val mo = mappingsArray.getJSONObject(i)
                    val typeStr = mo.optString("type", "TAP")
                    val type = try { MappingType.valueOf(typeStr) } catch (_: Exception) { MappingType.TAP }
                    mappingsList.add(
                        Mapping(
                            id = mo.optString("id", UUID.randomUUID().toString()),
                            profileId = profileId,
                            name = mo.optString("name", "Control $i"),
                            type = type,
                            keyCode = mo.optInt("keyCode", 0),
                            keyLabel = mo.optString("keyLabel", ""),
                            normalizedX = mo.optDouble("normalizedX", 0.5).toFloat(),
                            normalizedY = mo.optDouble("normalizedY", 0.5).toFloat(),
                            normalizedWidth = mo.optDouble("normalizedWidth", 0.1).toFloat(),
                            normalizedHeight = mo.optDouble("normalizedHeight", 0.1).toFloat(),
                            sensitivity = mo.optDouble("sensitivity", 1.0).toFloat(),
                            isToggle = mo.optBoolean("isToggle", false)
                        )
                    )
                }
            }

            Profile(
                profileId = profileId,
                gameId = gameId,
                name = name,
                schemaVersion = schemaVersion,
                mappings = mappingsList
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // Entity Mappings
    private fun GameEntity.toDomain() = Game(
        gameId = gameId,
        packageName = packageName,
        displayName = displayName,
        iconUri = iconUri,
        isTargeted = isTargeted,
        addedAt = addedAt,
        lastPlayedAt = lastPlayedAt
    )

    private fun Game.toEntity() = GameEntity(
        gameId = gameId,
        packageName = packageName,
        displayName = displayName,
        iconUri = iconUri,
        isTargeted = isTargeted,
        addedAt = addedAt,
        lastPlayedAt = lastPlayedAt
    )

    private fun ProfileEntity.toDomain(mappings: List<Mapping>, macros: List<Macro>) = Profile(
        profileId = profileId,
        gameId = gameId,
        name = name,
        schemaVersion = schemaVersion,
        isFavorite = isFavorite,
        createdAt = createdAt,
        updatedAt = updatedAt,
        mappings = mappings,
        macros = macros
    )

    private fun Profile.toEntity() = ProfileEntity(
        profileId = profileId,
        gameId = gameId,
        name = name,
        schemaVersion = schemaVersion,
        isFavorite = isFavorite,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun MappingEntity.toDomain() = Mapping(
        id = id,
        profileId = profileId,
        name = name,
        type = try { MappingType.valueOf(type) } catch (_: Exception) { MappingType.TAP },
        keyCode = keyCode,
        keyLabel = keyLabel,
        normalizedX = normalizedX,
        normalizedY = normalizedY,
        normalizedWidth = normalizedWidth,
        normalizedHeight = normalizedHeight,
        sensitivity = sensitivity,
        isToggle = isToggle,
        macroId = macroId
    )

    private fun Mapping.toEntity() = MappingEntity(
        id = id,
        profileId = profileId,
        name = name,
        type = type.name,
        keyCode = keyCode,
        keyLabel = keyLabel,
        normalizedX = normalizedX,
        normalizedY = normalizedY,
        normalizedWidth = normalizedWidth,
        normalizedHeight = normalizedHeight,
        sensitivity = sensitivity,
        isToggle = isToggle,
        macroId = macroId
    )

    private fun MacroEntity.toDomain(steps: List<MacroStep>) = Macro(
        id = id,
        profileId = profileId,
        name = name,
        triggerKeyCode = triggerKeyCode,
        triggerKeyLabel = triggerKeyLabel,
        steps = steps,
        isLooping = isLooping
    )

    private fun Macro.toEntity() = MacroEntity(
        id = id,
        profileId = profileId,
        name = name,
        triggerKeyCode = triggerKeyCode,
        triggerKeyLabel = triggerKeyLabel,
        isLooping = isLooping
    )

    private fun MacroStepEntity.toDomain() = MacroStep(
        id = id,
        macroId = macroId,
        stepIndex = stepIndex,
        actionType = actionType,
        normalizedX = normalizedX,
        normalizedY = normalizedY,
        delayMs = delayMs,
        holdDurationMs = holdDurationMs
    )

    private fun MacroStep.toEntity() = MacroStepEntity(
        id = id,
        macroId = macroId,
        stepIndex = stepIndex,
        actionType = actionType,
        normalizedX = normalizedX,
        normalizedY = normalizedY,
        delayMs = delayMs,
        holdDurationMs = holdDurationMs
    )

    private fun ProfileSnapshotEntity.toDomain() = ProfileSnapshot(
        id = id,
        profileId = profileId,
        snapshotName = snapshotName,
        reason = reason,
        checksum = checksum,
        serializedJson = serializedJson,
        createdAt = createdAt
    )
}
