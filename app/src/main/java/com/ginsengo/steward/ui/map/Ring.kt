package com.ginsengo.steward.ui.map

import kotlin.math.cos
import kotlin.math.sin

/**
 * A geodesic circle (the scan's 10-mile ring) as [vertices] + 1 (lat, lng) points, closed: the
 * first and last are the same point. Pure; moved out of the flat map when it went (wave M.1).
 */
fun ring(lat: Double, lng: Double, radiusM: Double, vertices: Int = 96): List<Pair<Double, Double>> {
    val d = radiusM / 6_371_000.0
    val la = Math.toRadians(lat); val lo = Math.toRadians(lng)
    return (0..vertices).map { i ->
        val b = 2 * Math.PI * i / vertices
        val lat2 = Math.asin(sin(la) * cos(d) + cos(la) * sin(d) * cos(b))
        val lng2 = lo + Math.atan2(sin(b) * sin(d) * cos(la), cos(d) - sin(la) * sin(lat2))
        Math.toDegrees(lat2) to Math.toDegrees(lng2)
    }
}
