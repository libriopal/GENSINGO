package com.discomplemented.ginseng.domain.repository

import com.discomplemented.ginseng.data.local.database.entity.TrackNodeEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for track node operations.
 */
interface TrackRepository {
    suspend fun insertTrack(node: TrackNodeEntity): Long
    suspend fun insertBatch(nodes: List<TrackNodeEntity>): List<Long>
    suspend fun getById(id: String): TrackNodeEntity?
    fun getBySessionIdFlow(sessionId: String): Flow<List<TrackNodeEntity>>
    suspend fun getBySessionId(sessionId: String, limit: Int = 1000): List<TrackNodeEntity>
    suspend fun queryBoundingBox(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        minTime: Long,
        maxTime: Long
    ): List<TrackNodeEntity>
    suspend fun getUnsynced(batchSize: Int = 50): List<TrackNodeEntity>
    suspend fun markSynced(ids: List<String>, syncedAt: Long)
    suspend fun deleteOldSynced(cutoffTime: Long): Int
    suspend fun getUnsyncedCount(): Int
}
