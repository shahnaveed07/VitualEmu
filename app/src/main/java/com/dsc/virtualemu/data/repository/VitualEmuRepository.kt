package com.dsc.virtualemu.data.repository

import android.content.Context
import com.dsc.virtualemu.data.local.room.GameEntity
import com.dsc.virtualemu.data.local.room.MacroEntity
import com.dsc.virtualemu.data.local.room.MacroStepEntity
import com.dsc.virtualemu.data.local.room.MappingEntity
import com.dsc.virtualemu.data.local.room.ProfileEntity
import com.dsc.virtualemu.data.local.room.ProfileSnapshotEntity
import com.dsc.virtualemu.data.local.room.VitualEmuDatabase
import com.dsc.virtualemu.domain.model.Game
import com.dsc.virtualemu.domain.model.Macro
import com.dsc.virtualemu.domain.model.MacroStep
import com.dsc.virtualemu.domain.model.MacroTriggerMode
import com.dsc.virtualemu.domain.model.Mapping
import com.dsc.virtualemu.domain.model.MappingType
import com.dsc.virtualemu.domain.model.MouseLookConfig
import com.dsc.virtualemu.domain.model.Profile
import com.dsc.virtualemu.domain.model.ProfileSnapshot
import com.dsc.virtualemu.domain.policy.SafetyPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
    private val prefs = context.getSharedPreferences("vitualemu_prefs", Context.MODE_PRIVATE)

    fun getSelectedGamePackage(): String? {
        return prefs.getString("selected_game_package", null)
    }

    fun setSelectedGamePackage(packageName: String) {
        prefs.edit().putString("selected_game_package", packageName).apply()
    }

    fun getTargetedGames(): Flow<List<Game>> {
        return gameDao.getTargetedGames().map { list ->
            list.map { it.toDomain() }
        }
    }

    fun getAllGames(): Flow<List<Game>> {
        return gameDao.getAllGames().map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun getAllSavedGamesList(): List<Game> = withContext(Dispatchers.IO) {
        gameDao.getAllGamesList().map { it.toDomain() }
    }

    suspend fun getGameByPackage(packageName: String): Game? = withContext(Dispatchers.IO) {
        gameDao.getGameByPackage(packageName)?.toDomain()
    }

    suspend fun getGameById(gameId: String): Game? = withContext(Dispatchers.IO) {
        gameDao.getGameById(gameId)?.toDomain()
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

    suspend fun syncGameSelections(
        selectedPackageNames: Set<String>,
        getDisplayName: (String) -> String
    ) = withContext(Dispatchers.IO) {
        database.runInTransaction {
            kotlinx.coroutines.runBlocking {
                val existingEntities = gameDao.getAllGamesList()
                val existingMap = existingEntities.associateBy { it.packageName }

                // Update existing records
                for (entity in existingEntities) {
                    val shouldBeTargeted = selectedPackageNames.contains(entity.packageName)
                    if (entity.isTargeted != shouldBeTargeted) {
                        gameDao.updateGame(entity.copy(isTargeted = shouldBeTargeted))
                    }
                }

                // Insert new selections that do not exist yet in Room
                for (pkg in selectedPackageNames) {
                    if (!existingMap.containsKey(pkg)) {
                        val displayName = getDisplayName(pkg).ifBlank { pkg.substringAfterLast('.') }
                        val newGame = GameEntity(
                            gameId = UUID.randomUUID().toString(),
                            packageName = pkg,
                            displayName = displayName,
                            iconUri = null,
                            isTargeted = true,
                            addedAt = System.currentTimeMillis()
                        )
                        gameDao.insertGame(newGame)
                    }
                }
            }
        }
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
        val sanitizedMacros = profile.macros.map { SafetyPolicy.sanitizeMacro(it) }
        val sanitizedMouseLook = SafetyPolicy.sanitizeMouseLookConfig(profile.mouseLookConfig)
        val cleanProfile = profile.copy(
            macros = sanitizedMacros,
            mouseLookConfig = sanitizedMouseLook
        )

        profileDao.insertProfile(cleanProfile.toEntity())
        mappingDao.deleteMappingsForProfile(cleanProfile.profileId)
        mappingDao.insertMappings(cleanProfile.mappings.map { it.toEntity() })

        macroDao.deleteMacrosForProfile(cleanProfile.profileId)
        for (macro in cleanProfile.macros) {
            macroDao.insertMacro(macro.toEntity())
            macroDao.insertMacroSteps(macro.steps.map { it.toEntity() })
        }

        if (snapshotReason != null) {
            createSnapshot(cleanProfile, snapshotReason)
        }
    }

    suspend fun deleteProfile(profileId: String) = withContext(Dispatchers.IO) {
        profileDao.deleteProfileById(profileId)
    }

    suspend fun setSelectedScheme(gameId: String, selectedProfileId: String) = withContext(Dispatchers.IO) {
        val entities = profileDao.getProfilesForGame(gameId).first()
        for (p in entities) {
            val updated = p.copy(isFavorite = (p.profileId == selectedProfileId), updatedAt = System.currentTimeMillis())
            profileDao.updateProfile(updated)
        }
    }

    suspend fun importSchemesForGame(gameId: String, importedProfiles: List<Profile>) = withContext(Dispatchers.IO) {
        for (p in importedProfiles) {
            val clean = p.copy(gameId = gameId)
            saveProfile(clean, "Imported Scheme: ${clean.name}")
        }
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
        root.put("mouseLookConfig", serializeMouseLook(profile.mouseLookConfig))

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
            mo.put("endNormalizedX", m.endNormalizedX.toDouble())
            mo.put("endNormalizedY", m.endNormalizedY.toDouble())
            mo.put("normalizedWidth", m.normalizedWidth.toDouble())
            mo.put("normalizedHeight", m.normalizedHeight.toDouble())
            mo.put("sensitivity", m.sensitivity.toDouble())
            mo.put("isToggle", m.isToggle)
            mo.put("macroId", m.macroId ?: "")
            mappingsArray.put(mo)
        }
        root.put("mappings", mappingsArray)

        val macrosArray = JSONArray()
        for (macro in profile.macros) {
            val macObj = JSONObject()
            macObj.put("id", macro.id)
            macObj.put("name", macro.name)
            macObj.put("triggerKeyCode", macro.triggerKeyCode)
            macObj.put("triggerKeyLabel", macro.triggerKeyLabel)
            macObj.put("chordModifierKeyCode", macro.chordModifierKeyCode)
            macObj.put("triggerMode", macro.triggerMode.name)
            macObj.put("isLooping", macro.isLooping)
            macObj.put("maxLoopIterations", macro.maxLoopIterations)
            val stepsArray = JSONArray()
            for (step in macro.steps) {
                val stObj = JSONObject()
                stObj.put("id", step.id)
                stObj.put("stepIndex", step.stepIndex)
                stObj.put("actionType", step.actionType)
                stObj.put("normalizedX", step.normalizedX.toDouble())
                stObj.put("normalizedY", step.normalizedY.toDouble())
                stObj.put("endNormalizedX", step.endNormalizedX.toDouble())
                stObj.put("endNormalizedY", step.endNormalizedY.toDouble())
                stObj.put("delayMs", step.delayMs)
                stObj.put("holdDurationMs", step.holdDurationMs)
                stepsArray.put(stObj)
            }
            macObj.put("steps", stepsArray)
            macrosArray.put(macObj)
        }
        root.put("macros", macrosArray)
        return root.toString(2)
    }

    fun deserializeProfileFromJson(jsonStr: String): Profile? {
        return try {
            val root = JSONObject(jsonStr)
            val profileId = root.optString("profileId", UUID.randomUUID().toString())
            val gameId = root.optString("gameId", "")
            val name = root.optString("name", "Imported Profile")
            val schemaVersion = root.optInt("schemaVersion", 2)
            val mouseLookConfig = root.optJSONObject("mouseLookConfig")?.let { deserializeMouseLook(it) }
                ?: MouseLookConfig()

            val mappingsList = mutableListOf<Mapping>()
            val mappingsArray = root.optJSONArray("mappings")
            if (mappingsArray != null) {
                for (i in 0 until mappingsArray.length()) {
                    val mo = mappingsArray.getJSONObject(i)
                    val typeStr = mo.optString("type", "TAP")
                    val type = try { MappingType.valueOf(typeStr) } catch (_: Exception) { MappingType.TAP }
                    val normX = mo.optDouble("normalizedX", 0.5).toFloat().coerceIn(0f, 1f)
                    val normY = mo.optDouble("normalizedY", 0.5).toFloat().coerceIn(0f, 1f)
                    mappingsList.add(
                        Mapping(
                            id = mo.optString("id", UUID.randomUUID().toString()),
                            profileId = profileId,
                            name = mo.optString("name", "Control $i"),
                            type = type,
                            keyCode = mo.optInt("keyCode", 0),
                            keyLabel = mo.optString("keyLabel", ""),
                            normalizedX = normX,
                            normalizedY = normY,
                            endNormalizedX = mo.optDouble("endNormalizedX", normX.toDouble()).toFloat().coerceIn(0f, 1f),
                            endNormalizedY = mo.optDouble("endNormalizedY", normY.toDouble()).toFloat().coerceIn(0f, 1f),
                            normalizedWidth = mo.optDouble("normalizedWidth", 0.1).toFloat(),
                            normalizedHeight = mo.optDouble("normalizedHeight", 0.1).toFloat(),
                            sensitivity = mo.optDouble("sensitivity", 1.0).toFloat(),
                            isToggle = mo.optBoolean("isToggle", false),
                            macroId = mo.optString("macroId", "").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }

            val macrosList = mutableListOf<Macro>()
            val macrosArray = root.optJSONArray("macros")
            if (macrosArray != null) {
                for (i in 0 until macrosArray.length()) {
                    val macObj = macrosArray.getJSONObject(i)
                    val macroId = macObj.optString("id", UUID.randomUUID().toString())
                    val stepsList = mutableListOf<MacroStep>()
                    val stepsArray = macObj.optJSONArray("steps")
                    if (stepsArray != null) {
                        for (j in 0 until stepsArray.length()) {
                            val stObj = stepsArray.getJSONObject(j)
                            val nx = stObj.optDouble("normalizedX", 0.5).toFloat()
                            val ny = stObj.optDouble("normalizedY", 0.5).toFloat()
                            stepsList.add(
                                SafetyPolicy.sanitizeMacroStep(
                                    MacroStep(
                                        id = stObj.optString("id", UUID.randomUUID().toString()),
                                        macroId = macroId,
                                        stepIndex = stObj.optInt("stepIndex", j),
                                        actionType = stObj.optString("actionType", "TAP"),
                                        normalizedX = nx,
                                        normalizedY = ny,
                                        endNormalizedX = stObj.optDouble("endNormalizedX", nx.toDouble()).toFloat(),
                                        endNormalizedY = stObj.optDouble("endNormalizedY", ny.toDouble()).toFloat(),
                                        delayMs = stObj.optLong("delayMs", 50L),
                                        holdDurationMs = stObj.optLong("holdDurationMs", 50L)
                                    )
                                )
                            )
                        }
                    }
                    val modeStr = macObj.optString("triggerMode", "ON_PRESS")
                    val mode = try { MacroTriggerMode.valueOf(modeStr) } catch (_: Exception) { MacroTriggerMode.ON_PRESS }
                    macrosList.add(
                        SafetyPolicy.sanitizeMacro(
                            Macro(
                                id = macroId,
                                profileId = profileId,
                                name = macObj.optString("name", "Macro $i"),
                                triggerKeyCode = macObj.optInt("triggerKeyCode", 0),
                                triggerKeyLabel = macObj.optString("triggerKeyLabel", ""),
                                chordModifierKeyCode = macObj.optInt("chordModifierKeyCode", 0),
                                triggerMode = mode,
                                steps = stepsList,
                                isLooping = macObj.optBoolean("isLooping", false),
                                maxLoopIterations = macObj.optInt("maxLoopIterations", 10)
                            )
                        )
                    )
                }
            }

            Profile(
                profileId = profileId,
                gameId = gameId,
                name = name,
                schemaVersion = schemaVersion,
                mappings = mappingsList,
                macros = macrosList,
                mouseLookConfig = SafetyPolicy.sanitizeMouseLookConfig(mouseLookConfig)
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun serializeMouseLook(cfg: MouseLookConfig): JSONObject {
        return JSONObject().apply {
            put("toggleKeyCode", cfg.toggleKeyCode)
            put("toggleKeyLabel", cfg.toggleKeyLabel)
            put("sensitivityX", cfg.sensitivityX.toDouble())
            put("sensitivityY", cfg.sensitivityY.toDouble())
            put("invertX", cfg.invertX)
            put("invertY", cfg.invertY)
            put("smoothing", cfg.smoothing.toDouble())
            put("acceleration", cfg.acceleration.toDouble())
            put("autoEdgeReset", cfg.autoEdgeReset)
            put("edgeThreshold", cfg.edgeThreshold.toDouble())
            put("centerNormalizedX", cfg.centerNormalizedX.toDouble())
            put("centerNormalizedY", cfg.centerNormalizedY.toDouble())
            put("freeLookEnabled", cfg.freeLookEnabled)
            put("freeLookKeyCode", cfg.freeLookKeyCode)
            put("freeLookKeyLabel", cfg.freeLookKeyLabel)
            put("freeLookNormalizedX", cfg.freeLookNormalizedX.toDouble())
            put("freeLookNormalizedY", cfg.freeLookNormalizedY.toDouble())
            put("smartDragEnabled", cfg.smartDragEnabled)
            put("smartDragVerticalSpeed", cfg.smartDragVerticalSpeed.toDouble())
            put("smartDragHorizontalSpeed", cfg.smartDragHorizontalSpeed.toDouble())
        }
    }

    private fun deserializeMouseLook(obj: JSONObject): MouseLookConfig {
        return MouseLookConfig(
            toggleKeyCode = obj.optInt("toggleKeyCode", 68),
            toggleKeyLabel = obj.optString("toggleKeyLabel", "GRAVE (~) / RClick"),
            sensitivityX = obj.optDouble("sensitivityX", 1.2).toFloat(),
            sensitivityY = obj.optDouble("sensitivityY", 1.0).toFloat(),
            invertX = obj.optBoolean("invertX", false),
            invertY = obj.optBoolean("invertY", false),
            smoothing = obj.optDouble("smoothing", 0.25).toFloat(),
            acceleration = obj.optDouble("acceleration", 0.10).toFloat(),
            autoEdgeReset = obj.optBoolean("autoEdgeReset", true),
            edgeThreshold = obj.optDouble("edgeThreshold", 0.08).toFloat(),
            centerNormalizedX = obj.optDouble("centerNormalizedX", 0.72).toFloat(),
            centerNormalizedY = obj.optDouble("centerNormalizedY", 0.48).toFloat(),
            freeLookEnabled = obj.optBoolean("freeLookEnabled", true),
            freeLookKeyCode = obj.optInt("freeLookKeyCode", 57),
            freeLookKeyLabel = obj.optString("freeLookKeyLabel", "ALT_LEFT"),
            freeLookNormalizedX = obj.optDouble("freeLookNormalizedX", 0.82).toFloat(),
            freeLookNormalizedY = obj.optDouble("freeLookNormalizedY", 0.26).toFloat(),
            smartDragEnabled = obj.optBoolean("smartDragEnabled", false),
            smartDragVerticalSpeed = obj.optDouble("smartDragVerticalSpeed", 0.04).toFloat(),
            smartDragHorizontalSpeed = obj.optDouble("smartDragHorizontalSpeed", 0.0).toFloat()
        )
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

    private fun ProfileEntity.toDomain(mappings: List<Mapping>, macros: List<Macro>): Profile {
        val mouseLook = if (mouseLookJson.isNotBlank()) {
            try { deserializeMouseLook(JSONObject(mouseLookJson)) } catch (_: Exception) { MouseLookConfig() }
        } else {
            MouseLookConfig()
        }
        return Profile(
            profileId = profileId,
            gameId = gameId,
            name = name,
            schemaVersion = schemaVersion,
            isFavorite = isFavorite,
            createdAt = createdAt,
            updatedAt = updatedAt,
            mappings = mappings,
            macros = macros,
            mouseLookConfig = mouseLook
        )
    }

    private fun Profile.toEntity() = ProfileEntity(
        profileId = profileId,
        gameId = gameId,
        name = name,
        schemaVersion = schemaVersion,
        isFavorite = isFavorite,
        createdAt = createdAt,
        updatedAt = updatedAt,
        mouseLookJson = serializeMouseLook(mouseLookConfig).toString()
    )

    private fun MappingEntity.toDomain() = Mapping(
        id = id,
        profileId = profileId,
        name = name,
        type = try { MappingType.valueOf(type) } catch (_: Exception) { MappingType.TAP },
        keyCode = keyCode,
        keyLabel = keyLabel,
        altKeyCode = altKeyCode,
        altKeyLabel = altKeyLabel,
        normalizedX = normalizedX,
        normalizedY = normalizedY,
        endNormalizedX = endNormalizedX,
        endNormalizedY = endNormalizedY,
        normalizedWidth = normalizedWidth,
        normalizedHeight = normalizedHeight,
        sensitivity = sensitivity,
        sensitivityRatioY = sensitivityRatioY,
        isToggle = isToggle,
        repeatDelayMs = repeatDelayMs,
        repeatUntilKeyUp = repeatUntilKeyUp,
        guidanceCategory = guidanceCategory,
        guidanceText = guidanceText,
        macroId = macroId
    )

    private fun Mapping.toEntity() = MappingEntity(
        id = id,
        profileId = profileId,
        name = name,
        type = type.name,
        keyCode = keyCode,
        keyLabel = keyLabel,
        altKeyCode = altKeyCode,
        altKeyLabel = altKeyLabel,
        normalizedX = normalizedX,
        normalizedY = normalizedY,
        endNormalizedX = endNormalizedX,
        endNormalizedY = endNormalizedY,
        normalizedWidth = normalizedWidth,
        normalizedHeight = normalizedHeight,
        sensitivity = sensitivity,
        sensitivityRatioY = sensitivityRatioY,
        isToggle = isToggle,
        repeatDelayMs = repeatDelayMs,
        repeatUntilKeyUp = repeatUntilKeyUp,
        guidanceCategory = guidanceCategory,
        guidanceText = guidanceText,
        macroId = macroId
    )

    private fun MacroEntity.toDomain(steps: List<MacroStep>) = Macro(
        id = id,
        profileId = profileId,
        name = name,
        triggerKeyCode = triggerKeyCode,
        triggerKeyLabel = triggerKeyLabel,
        chordModifierKeyCode = chordModifierKeyCode,
        triggerMode = try { MacroTriggerMode.valueOf(triggerMode) } catch (_: Exception) { MacroTriggerMode.ON_PRESS },
        steps = steps,
        isLooping = isLooping,
        maxLoopIterations = maxLoopIterations
    )

    private fun Macro.toEntity() = MacroEntity(
        id = id,
        profileId = profileId,
        name = name,
        triggerKeyCode = triggerKeyCode,
        triggerKeyLabel = triggerKeyLabel,
        chordModifierKeyCode = chordModifierKeyCode,
        triggerMode = triggerMode.name,
        isLooping = isLooping,
        maxLoopIterations = maxLoopIterations
    )

    private fun MacroStepEntity.toDomain() = MacroStep(
        id = id,
        macroId = macroId,
        stepIndex = stepIndex,
        actionType = actionType,
        normalizedX = normalizedX,
        normalizedY = normalizedY,
        endNormalizedX = endNormalizedX,
        endNormalizedY = endNormalizedY,
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
        endNormalizedX = endNormalizedX,
        endNormalizedY = endNormalizedY,
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
