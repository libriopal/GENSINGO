package com.discomplemented.ginseng.domain.repository

import com.discomplemented.ginseng.domain.model.LandownerPermission
import kotlinx.coroutines.flow.Flow

interface LandownerPermissionRepository {
    fun getAllPermissions(): Flow<List<LandownerPermission>>
    suspend fun savePermission(permission: LandownerPermission)
    suspend fun getPermissionById(id: String): LandownerPermission?
    suspend fun getPermissionsInBounds(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<LandownerPermission>
}
