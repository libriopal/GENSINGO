package com.discomplemented.ginseng.data.local.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "ginseng_patches")
data class GinsengPatchEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,
    val uuid: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val confidence: Float,
    val metadata: String
)
