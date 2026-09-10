package com.discomplemented.ginseng.mapping.layers

import com.discomplemented.ginseng.domain.model.LatLng
import kotlin.math.*

/**
 * Utility for transforming geographic coordinates (WGS84) to local Cartesian coordinates
 * for high-precision micro-terrain rendering.
 */
class CoordinateTransformer(
    private val origin: LatLng,
    private val metersPerDegreeLat: Double = 111319.9,
    private val metersPerDegreeLon: Double = 111319.9 * cos(Math.toRadians(origin.latitude))
) {
    /**
     * Transforms a global [LatLng] to a local (x, y) coordinate in meters relative to [origin].
     */
    fun toLocalMeters(target: LatLng): Pair<Double, Double> {
        val x = (target.longitude - origin.longitude) * metersPerDegreeLon
        val y = (target.latitude - origin.latitude) * metersPerDegreeLat
        return Pair(x, y)
    }

    /**
     * Transforms local Cartesian coordinates back to [LatLng].
     */
    fun toGlobal(localX: Double, localY: Double): LatLng {
        val lat = origin.latitude + (localY / metersPerDegreeLat)
        val lon = origin.longitude + (localX / metersPerDegreeLon)
        return LatLng(lat, lon)
    }
}
