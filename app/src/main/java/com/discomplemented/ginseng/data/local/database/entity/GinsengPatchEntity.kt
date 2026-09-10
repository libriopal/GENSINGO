package com.discomplemented.ginseng.data.local.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Represents a discovered or logged ginseng patch.
 * Stores location, habitat suitability score, and metadata.
 */
@Entity(
    tableName = "ginseng_patches",
    indices = [
        Index("timestamp", name = "idx_patch_timestamp"),
        Index("latitude", "longitude", name = "idx_patch_location")
    ]
)
data class GinsengPatchEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    val latitude: Double,
    
    val longitude: Double,
    
    val altitude: Float,
    
    val timestamp: Long,
    
    val habitatSuitabilityScore: Float = 0.5f,
    
    val plantCount: Int = 0,
    
    val notes: String = "",
    
    val photoPath: String? = null,
    
    val synced: Boolean = false
)
