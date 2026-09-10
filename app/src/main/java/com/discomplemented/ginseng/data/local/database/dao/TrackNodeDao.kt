package com.discomplemented.ginseng.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.discomplemented.ginseng.data.local.database.entity.TrackNodeEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for track nodes.
 * Handles all database operations with proper indexing and batch support.
 */
@Dao
interface TrackNodeDao {
    
    @Insert
    suspend fun insert(node: TrackNodeEntity): Long
    
    @Insert
    suspend fun insertBatch(nodes: List<TrackNodeEntity>): List<Long>
    
    @Update
    suspend fun update(node: TrackNodeEntity)
    
    @Query("SELECT * FROM track_nodes WHERE id = :id")
    suspend fun getById(id: String): TrackNodeEntity?
    
    @Query("SELECT * FROM track_nodes WHERE session_id = :sessionId ORDER BY timestamp DESC")
    fun getBySessionIdFlow(sessionId: String): Flow<List<TrackNodeEntity>>
    
    @Query("SELECT * FROM track_nodes WHERE session_id = :sessionId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getBySessionId(sessionId: String, limit: Int = 1000): List<TrackNodeEntity>
    
    @Query("""
        SELECT * FROM track_nodes 
        WHERE latitude BETWEEN :minLat AND :maxLat
        AND longitude BETWEEN :minLon AND :maxLon
        AND timestamp BETWEEN :minTime AND :maxTime
        ORDER BY timestamp DESC
    """)
    suspend fun queryBoundingBox(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        minTime: Long,
        maxTime: Long
    ): List<TrackNodeEntity>
    
    @Query("SELECT * FROM track_nodes WHERE synced = 0 ORDER BY timestamp ASC LIMIT :batchSize")
    suspend fun getUnsynced(batchSize: Int = 50): List<TrackNodeEntity>
    
    @Query("UPDATE track_nodes SET synced = 1, synced_at = :syncedAt WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>, syncedAt: Long)
    
    @Query("DELETE FROM track_nodes WHERE synced = 1 AND synced_at < :cutoffTime")
    suspend fun deleteOldSynced(cutoffTime: Long): Int
    
    @Query("SELECT COUNT(*) FROM track_nodes WHERE synced = 0")
    suspend fun getUnsyncedCount(): Int
    
    @Query("DELETE FROM track_nodes WHERE session_id = :sessionId")
    suspend fun deleteBySessionId(sessionId: String): Int
}
