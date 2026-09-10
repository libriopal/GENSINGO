package com.discomplemented.ginseng.data.repository

import android.content.Context
import com.discomplemented.ginseng.domain.repository.DemRepository
import java.io.File
import javax.inject.Inject

/**
 * Implementation of [DemRepository] that searches for GeoTIFF/DEM files
 * within the application's internal storage.
 */
class DemRepositoryImpl @Inject constructor(
    @JvmSuppressWildcards private val context: Context
) : DemRepository {

    override suspend fun getDemFileForLocation(lat: Double, lon: Double): File? {
        // In a production app, this would query a database of available DEM tiles
        // or check a directory structure organized by geohash/quadtree.
        // For now, we search a dedicated 'dem' directory in internal storage.

        val demDir = File(context.filesDir, "dem")
        if (!demDir.exists()) return null

        // Placeholder logic: find the first .tif file in the dem directory.
        // Real implementation would use spatial indexing to find the correct file.
        return demDir.listFiles { _, name ->
            name.endsWith(".tif") || name.endsWith(".tiff")
        }?.firstOrNull()
    }
}
