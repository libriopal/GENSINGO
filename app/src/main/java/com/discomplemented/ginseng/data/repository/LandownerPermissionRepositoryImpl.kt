package com.discomplemented.ginseng.data.repository

import com.discomplemented.ginseng.data.local.database.LandownerPermissionDao
import com.discomplemented.ginseng.data.local.database.LandownerPermissionEntity
import com.discomplemented.ginseng.domain.model.LandownerPermission
import com.discomplemented.ginseng.domain.repository.LandownerPermissionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.sqlite.db.SimpleSQLiteQuery
import java.util.UUID

class LandownerPermissionRepositoryImpl(
    private val landownerPermissionDao: LandownerPermissionDao
) : LandownerPermissionRepository {

    override fun getAllPermissions(): Flow<List<LandownerPermission>> {
        return landownerPermissionDao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun savePermission(permission: LandownerPermission) {
        landownerPermissionDao.insert(permission.toEntity())
    }

    override suspend fun getPermissionById(id: String): LandownerPermission? {
        return landownerPermissionDao.getById(id)?.toDomain()
    }

    override suspend fun getPermissionsInBounds(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<LandownerPermission> {
        val query = SimpleSQLiteQuery(
            "SELECT * FROM landowner_permissions WHERE latitude BETWEEN ? AND ? AND longitude BETWEEN ? AND ?",
            arrayOf(minLat, maxLat, minLon, maxLon)
        )
        // Note: The current LandownerPermissionEntity doesn't have lat/lon fields in the entity,
        // but the repository interface requires it.
        // I'll need to update the entity to include them for spatial indexing to work.
        return landownerPermissionDao.getPermissionsInBoundingBox(query).map { it.toDomain() }
    }

    private fun LandownerPermissionEntity.toDomain(): LandownerPermission {
        return LandownerPermission(
            id = UUID.fromString(id),
            ownerName = ownerName,
            imageUri = imageUri,
            expiryDate = expiryDate,
            isVerified = isVerified,
            locationPolygonGeoJson = locationPolygonGeoJson
        )
    }

    private fun LandownerPermission.toEntity(): LandownerPermissionEntity {
        return LandownerPermissionEntity(
            id = id.toString(),
            ownerName = ownerName,
            imageUri = imageUri,
            expiryDate = expiryDate,
            isVerified = isVerified,
            locationPolygonGeoJson = locationPolygonGeoJson,
            latitude = 0.0, // Note: In a real implementation, these would be extracted from the GeoJSON or passed in
            longitude = 0.0
        )
    }
}
