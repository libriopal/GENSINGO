package com.discomplemented.ginseng.core.util

import com.discomplemented.ginseng.domain.model.LatLng
import kotlin.math.*

/**
 * Utility functions for geospatial calculations.
 */
object GeoUtils {

    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Calculates the great-circle distance between two points on the Earth's surface
     * using the Haversine formula.
     *
     * @param start The starting [LatLng].
     * @param end The ending [LatLng].
     * @return The distance in meters.
     */
    fun distanceBetween(start: LatLng, end: LatLng): Double {
        val lat1Rad = Math.toRadians(start.latitude)
        val lon1Rad = Math.toRadians(start.longitude)
        val lat2Rad = Math.toRadians(end.latitude)
        val lon2Rad = Math.toRadians(end.longitude)

        val dLat = lat2Rad - lat1Rad
        val dLon = lon2Rad - lon1Rad

        val a = sin(dLat / 2).pow(2.0) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(dLon / 2).pow(2.0)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS_METERS * c
    }
}
