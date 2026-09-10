package com.discomplemented.ginseng.compliance

import com.discomplemented.ginseng.domain.model.LatLng

/**
 * Geofencing compliance checker.
 * Validates locations against protected area polygons.
 */
class GeofenceCompliance {

    private val protectedAreas = listOf(
        // Example: Great Smoky Mountains
        ProtectedArea(
            name = "Great Smoky Mountains National Park",
            bounds = listOf(
                LatLng(35.4876, -83.1781),
                LatLng(35.7338, -83.1781),
                LatLng(35.7338, -82.0895),
                LatLng(35.4876, -82.0895)
            )
        )
    )

    /**
     * Check if location is within a protected area using Ray Casting algorithm.
     */
    fun isInProtectedArea(location: LatLng): Boolean {
        return protectedAreas.any { isInsidePolygon(location, it.bounds) }
    }

    /**
     * Ray Casting algorithm for point-in-polygon test.
     */
    private fun isInsidePolygon(point: LatLng, polygon: List<LatLng>): Boolean {
        var inside = false
        var j = polygon.size - 1

        for (i in polygon.indices) {
            val xi = polygon[i].longitude
            val yi = polygon[i].latitude
            val xj = polygon[j].longitude
            val yj = polygon[j].latitude

            val intersect = (
                (yi > point.latitude) != (yj > point.latitude) &&
                point.longitude < (xj - xi) * (point.latitude - yi) / (yj - yi) + xi
            )
            if (intersect) inside = !inside
            j = i
        }

        return inside
    }

    data class ProtectedArea(
        val name: String,
        val bounds: List<LatLng>
    )
}
