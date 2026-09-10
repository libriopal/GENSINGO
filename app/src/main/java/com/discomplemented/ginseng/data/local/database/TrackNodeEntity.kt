package com.discomplemented.ginseng.data.local.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "track_nodes")
data class TrackNodeEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,
    val uuid: String,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Float,
    val accuracy: Float
)
