package com.discomplemented.ginseng.core.util

import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/**
 * Geographic and mathematical utilities.
 */
object GeoUtils {
    private const val EARTH_RADIUS_KM = 6371.0

    /**
     * Haversine formula: calculate distance between two lat/lon points.
     */
    fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * acos(kotlin.math.sqrt(a))
        return EARTH_RADIUS_KM * c
    }

    /**
     * Convert degrees to radians.
     */
    fun toRadians(degrees: Double): Double = degrees * PI / 180.0

    /**
     * Convert radians to degrees.
     */
    fun toDegrees(radians: Double): Double = radians * 180.0 / PI
}
