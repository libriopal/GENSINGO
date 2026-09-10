package com.discomplemented.ginseng.domain.repository

import com.discomplemented.ginseng.data.local.database.entity.GinsengPatchEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for ginseng patch operations.
 */
interface GinsengPatchRepository {
    suspend fun insert(patch: GinsengPatchEntity): Long
    suspend fun insertBatch(patches: List<GinsengPatchEntity>): List<Long>
    suspend fun update(patch: GinsengPatchEntity)
    fun getAllFlow(): Flow<List<GinsengPatchEntity>>
    suspend fun getAll(): List<GinsengPatchEntity>
    suspend fun getById(id: String): GinsengPatchEntity?
    suspend fun queryBoundingBox(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double
    ): List<GinsengPatchEntity>
    suspend fun getUnsynced(batchSize: Int = 50): List<GinsengPatchEntity>
    suspend fun markSynced(ids: List<String>)
    suspend fun delete(id: String): Int
}
