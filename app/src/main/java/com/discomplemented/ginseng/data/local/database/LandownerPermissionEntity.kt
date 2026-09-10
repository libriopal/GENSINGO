package com.discomplemented.ginseng.data.local.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "landowner_permissions")
data class LandownerPermissionEntity(
    @PrimaryKey
    val id: String,
    val ownerName: String,
    val imageUri: String,
    val expiryDate: Long,
    val isVerified: Boolean,
    val locationPolygonGeoJson: String,
    val latitude: Double,
    val longitude: Double
)
