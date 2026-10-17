package com.dsc.virtualemu.data.local.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "games",
    indices = [Index(value = ["packageName"], unique = true)]
)
data class GameEntity(
    @PrimaryKey val gameId: String,
    val packageName: String,
    val displayName: String,
    val iconUri: String?,
    val isTargeted: Boolean = true,
    val addedAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long? = null
)

@Entity(
    tableName = "profiles",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["gameId"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["gameId"])]
)
data class ProfileEntity(
    @PrimaryKey val profileId: String,
    val gameId: String,
    val name: String,
    val schemaVersion: Int = 2,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val mouseLookJson: String = ""
)

@Entity(
    tableName = "mappings",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["profileId"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["profileId"])]
)
data class MappingEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val name: String,
    val type: String,
    val keyCode: Int,
    val keyLabel: String,
    val altKeyCode: Int = 0,
    val altKeyLabel: String = "",
    val normalizedX: Float,
    val normalizedY: Float,
    val endNormalizedX: Float = normalizedX,
    val endNormalizedY: Float = normalizedY,
    val normalizedWidth: Float,
    val normalizedHeight: Float,
    val sensitivity: Float = 1.0f,
    val sensitivityRatioY: Float = 1.0f,
    val isToggle: Boolean = false,
    val repeatDelayMs: Long = 50L,
    val repeatUntilKeyUp: Boolean = true,
    val guidanceCategory: String = "",
    val guidanceText: String = "",
    val macroId: String? = null
)

@Entity(
    tableName = "macros",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["profileId"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["profileId"])]
)
data class MacroEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val name: String,
    val triggerKeyCode: Int,
    val triggerKeyLabel: String,
    val chordModifierKeyCode: Int = 0,
    val triggerMode: String = "ON_PRESS",
    val isLooping: Boolean = false,
    val maxLoopIterations: Int = 10
)

@Entity(
    tableName = "macro_steps",
    foreignKeys = [
        ForeignKey(
            entity = MacroEntity::class,
            parentColumns = ["id"],
            childColumns = ["macroId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["macroId"])]
)
data class MacroStepEntity(
    @PrimaryKey val id: String,
    val macroId: String,
    val stepIndex: Int,
    val actionType: String,
    val normalizedX: Float,
    val normalizedY: Float,
    val endNormalizedX: Float = normalizedX,
    val endNormalizedY: Float = normalizedY,
    val delayMs: Long,
    val holdDurationMs: Long
)

@Entity(
    tableName = "profile_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["profileId"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["profileId"])]
)
data class ProfileSnapshotEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val snapshotName: String,
    val reason: String,
    val checksum: String,
    val serializedJson: String,
    val createdAt: Long = System.currentTimeMillis()
)
