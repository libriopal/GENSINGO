package com.discomplemented.ginseng.domain.repository

import java.io.File

/**
 * Repository for managing and resolving Digital Elevation Model (DEM) files.
 * Provides access to high-resolution terrain data based on geographic location.
 */
interface DemRepository {
    /**
     * Returns the most appropriate DEM file for the given geographic location.
     *
     * @param lat The latitude.
     * @param lon The longitude.
     * @return The [File] containing the GeoTIFF/DEM data, or null if no suitable file is found.
     */
    suspend fun getDemFileForLocation(lat: Double, lon: Double): File?
}
