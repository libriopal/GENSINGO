package com.discomplemented.ginseng.data.local.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Represents a single GPS track point with high-frequency sampling.
 * Indexed by timestamp for fast temporal queries.
 * Uses R-Tree spatial indexing via separate rtree table.
 */
@Entity(
    tableName = "track_nodes",
    indices = [
        Index("timestamp", name = "idx_track_timestamp"),
        Index("synced", name = "idx_track_synced"),
        Index("session_id", name = "idx_track_session")
    ]
)
data class TrackNodeEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    val sessionId: String,
    
    val timestamp: Long,
    
    val latitude: Double,
    
    val longitude: Double,
    
    val altitude: Float,
    
    val accuracy: Float,
    
    val speed: Float,
    
    val bearing: Float,
    
    // Batch metadata
    val synced: Boolean = false,
    
    val syncedAt: Long? = null
)
