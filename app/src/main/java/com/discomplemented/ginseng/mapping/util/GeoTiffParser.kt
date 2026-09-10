package com.discomplemented.ginseng.mapping.util

/**
 * A lightweight parser for GeoTIFF files used to extract elevation data
 * for high-resolution terrain rendering.
 *
 * Uses Memory-Mapped I/O (NIO) for high-performance random access to
 * large elevation datasets.
 */
interface GeoTiffParser {
    val width: Int
    val height: Int

    /**
     * The pixel coordinates (x, y, z) of the first tiepoint.
     * For GeoTIFF, typically [0, 0, 0, lat, lon, alt].
     */
    val modelTiepoint: DoubleArray

    /**
     * The pixel scale [scaleX, scaleY, scaleZ] representing the
     * size of a pixel in geographic units (e.g., degrees or meters).
     */
    val modelPixelScale: DoubleArray

    /**
     * Returns the elevation value at the specified pixel coordinates.
     *
     * @param x The pixel x-coordinate.
     * @param y The pixel y-coordinate.
     * @return The elevation value (usually as a Float).
     */
    fun getElevationAt(x: Int, y: Int): Float

    /**
     * Returns the elevation value at the specified geographic coordinates.
     *
     * @param lat The latitude.
     * @param lon The longitude.
     * @return The elevation value.
     */
    fun getElevationAt(lat: Double, lon: Double): Float
}
