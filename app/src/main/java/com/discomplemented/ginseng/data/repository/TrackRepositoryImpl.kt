package com.discomplemented.ginseng.data.repository

import com.discomplemented.ginseng.data.local.database.dao.TrackNodeDao
import com.discomplemented.ginseng.data.local.database.entity.TrackNodeEntity
import com.discomplemented.ginseng.domain.repository.TrackRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Implementation of TrackRepository using Room database.
 * Follows persist-first pattern: writes immediately to Room.
 */
class TrackRepositoryImpl @Inject constructor(
    private val trackNodeDao: TrackNodeDao
) : TrackRepository {

    override suspend fun insertTrack(node: TrackNodeEntity): Long {
        return trackNodeDao.insert(node)
    }

    override suspend fun insertBatch(nodes: List<TrackNodeEntity>): List<Long> {
        return trackNodeDao.insertBatch(nodes)
    }

    override suspend fun getById(id: String): TrackNodeEntity? {
        return trackNodeDao.getById(id)
    }

    override fun getBySessionIdFlow(sessionId: String): Flow<List<TrackNodeEntity>> {
        return trackNodeDao.getBySessionIdFlow(sessionId)
    }

    override suspend fun getBySessionId(sessionId: String, limit: Int): List<TrackNodeEntity> {
        return trackNodeDao.getBySessionId(sessionId, limit)
    }

    override suspend fun queryBoundingBox(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        minTime: Long,
        maxTime: Long
    ): List<TrackNodeEntity> {
        return trackNodeDao.queryBoundingBox(minLat, maxLat, minLon, maxLon, minTime, maxTime)
    }

    override suspend fun getUnsynced(batchSize: Int): List<TrackNodeEntity> {
        return trackNodeDao.getUnsynced(batchSize)
    }

    override suspend fun markSynced(ids: List<String>, syncedAt: Long) {
        trackNodeDao.markSynced(ids, syncedAt)
    }

    override suspend fun deleteOldSynced(cutoffTime: Long): Int {
        return trackNodeDao.deleteOldSynced(cutoffTime)
    }

    override suspend fun getUnsyncedCount(): Int {
        return trackNodeDao.getUnsyncedCount()
    }
}
