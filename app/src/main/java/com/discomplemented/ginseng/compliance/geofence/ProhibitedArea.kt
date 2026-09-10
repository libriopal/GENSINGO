package com.discomplemented.ginseng.compliance.geofence

import com.discomplemented.ginseng.domain.model.LocationPoint

/**
 * Represents a prohibited area (e.g., National Park) that users must not enter for harvesting.
 */
data class ProhibitedArea(
    val id: String,
    val name: String,
    val boundaryGeoJson: String // GeoJSON Polygon
)

/**
 * Result of a geofence proximity check.
 */
sealed class GeofenceResult {
    object Safe : GeofenceResult()
    data class Warning(val areaName: String, val distanceMeters: Double) : GeofenceResult()
    data class Violation(val areaName: String) : GeofenceResult()
}
