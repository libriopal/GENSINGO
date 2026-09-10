package com.discomplemented.ginseng.data.repository

import com.discomplemented.ginseng.data.local.database.dao.GinsengPatchDao
import com.discomplemented.ginseng.data.local.database.entity.GinsengPatchEntity
import com.discomplemented.ginseng.domain.repository.GinsengPatchRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Implementation of GinsengPatchRepository using Room database.
 */
class GinsengPatchRepositoryImpl @Inject constructor(
    private val ginsengPatchDao: GinsengPatchDao
) : GinsengPatchRepository {

    override suspend fun insert(patch: GinsengPatchEntity): Long {
        return ginsengPatchDao.insert(patch)
    }

    override suspend fun insertBatch(patches: List<GinsengPatchEntity>): List<Long> {
        return ginsengPatchDao.insertBatch(patches)
    }

    override suspend fun update(patch: GinsengPatchEntity) {
        ginsengPatchDao.update(patch)
    }

    override fun getAllFlow(): Flow<List<GinsengPatchEntity>> {
        return ginsengPatchDao.getAllFlow()
    }

    override suspend fun getAll(): List<GinsengPatchEntity> {
        return ginsengPatchDao.getAll()
    }

    override suspend fun getById(id: String): GinsengPatchEntity? {
        return ginsengPatchDao.getById(id)
    }

    override suspend fun queryBoundingBox(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double
    ): List<GinsengPatchEntity> {
        return ginsengPatchDao.queryBoundingBox(minLat, maxLat, minLon, maxLon)
    }

    override suspend fun getUnsynced(batchSize: Int): List<GinsengPatchEntity> {
        return ginsengPatchDao.getUnsynced(batchSize)
    }

    override suspend fun markSynced(ids: List<String>) {
        ginsengPatchDao.markSynced(ids)
    }

    override suspend fun delete(id: String): Int {
        return ginsengPatchDao.delete(id)
    }
}
