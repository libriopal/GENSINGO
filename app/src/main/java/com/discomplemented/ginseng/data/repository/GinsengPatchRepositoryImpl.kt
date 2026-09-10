package com.discomplemented.ginseng.data.repository

import com.discomplemented.ginseng.data.local.database.GinsengPatchDao
import com.discomplemented.ginseng.data.local.database.GinsengPatchEntity
import com.discomplemented.ginseng.domain.model.GinsengPatch
import com.discomplemented.ginseng.domain.repository.GinsengPatchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.sqlite.db.SimpleSQLiteQuery
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

class GinsengPatchRepositoryImpl(
    private val ginsengPatchDao: GinsengPatchDao,
    private val gson: Gson
) : GinsengPatchRepository {

    override fun getAllPatches(): Flow<List<GinsengPatch>> {
        return ginsengPatchDao.getAll().map { entities ->
            entities.map { it.toDomain(gson) }
        }
    }

    override suspend fun savePatch(patch: GinsengPatch) {
        ginsengPatchDao.insert(patch.toEntity(gson))
    }

    override suspend fun getPatchById(id: String): GinsengPatch? {
        return ginsengPatchDao.getById(id)?.toDomain(gson)
    }

    override suspend fun getPatchesInBounds(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<GinsengPatch> {
        val query = SimpleSQLiteQuery(
            "SELECT p.* FROM ginseng_patches p JOIN ginseng_patches_rtree r ON p.uuid = r.id WHERE r.minLat <= ? AND r.maxLat >= ? AND r.minLon <= ? AND r.maxLon >= ?",
            arrayOf(maxLat, minLat, maxLon, minLon)
        )
        return ginsengPatchDao.getPatchesInBoundingBox(query).map { it.toDomain(gson) }
    }

    private fun GinsengPatchEntity.toDomain(gson: Gson): GinsengPatch {
        val type = object : TypeToken<Map<String, String>>() {}.type
        val metadata: Map<String, String> = gson.fromJson(metadata, type)
        return GinsengPatch(
            id = UUID.fromString(id),
            latitude = latitude,
            longitude = longitude,
            timestamp = timestamp,
            confidence = confidence,
            metadata = metadata
        )
    }

    private fun GinsengPatch.toEntity(gson: Gson): GinsengPatchEntity {
        return GinsengPatchEntity(
            id = id.toString(),
            latitude = latitude,
            longitude = longitude,
            timestamp = timestamp,
            confidence = confidence,
            metadata = gson.toJson(metadata)
        )
    }
}
