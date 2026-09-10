package com.discomplemented.ginseng.domain.repository

import com.discomplemented.ginseng.domain.model.GinsengPatch
import kotlinx.coroutines.flow.Flow

interface GinsengPatchRepository {
    fun getAllPatches(): Flow<List<GinsengPatch>>
    suspend fun savePatch(patch: GinsengPatch)
    suspend fun getPatchById(id: String): GinsengPatch?
    suspend fun getPatchesInBounds(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<GinsengPatch>
}
