package com.discomplemented.ginseng.domain.repository

import com.discomplemented.ginseng.data.local.database.entity.LandownerPermissionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for landowner permission operations.
 */
interface LandownerPermissionRepository {
    suspend fun insert(permission: LandownerPermissionEntity): Long
    fun getAllFlow(): Flow<List<LandownerPermissionEntity>>
    suspend fun getAll(): List<LandownerPermissionEntity>
    suspend fun getById(id: String): LandownerPermissionEntity?
    suspend fun delete(id: String): Int
}
