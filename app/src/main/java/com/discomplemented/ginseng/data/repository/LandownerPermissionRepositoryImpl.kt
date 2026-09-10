package com.discomplemented.ginseng.data.repository

import com.discomplemented.ginseng.data.local.database.dao.LandownerPermissionDao
import com.discomplemented.ginseng.data.local.database.entity.LandownerPermissionEntity
import com.discomplemented.ginseng.domain.repository.LandownerPermissionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Implementation of LandownerPermissionRepository.
 */
class LandownerPermissionRepositoryImpl @Inject constructor(
    private val dao: LandownerPermissionDao
) : LandownerPermissionRepository {

    override suspend fun insert(permission: LandownerPermissionEntity): Long {
        return dao.insert(permission)
    }

    override fun getAllFlow(): Flow<List<LandownerPermissionEntity>> {
        return dao.getAllFlow()
    }

    override suspend fun getAll(): List<LandownerPermissionEntity> {
        return dao.getAll()
    }

    override suspend fun getById(id: String): LandownerPermissionEntity? {
        return dao.getById(id)
    }

    override suspend fun delete(id: String): Int {
        return dao.delete(id)
    }
}
