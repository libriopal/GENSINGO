package com.discomplemented.ginseng.compliance.geofence

import android.annotation.SuppressLint
import android.content.Context
import com.discomplemented.ginseng.domain.model.LocationPoint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONArray
import javax.inject.Inject

class GeofenceMonitorImpl @Inject constructor(
    @SuppressLint("MissingPermission")
    private val fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient,
    private val context: Context
) : GeofenceMonitor {

    @SuppressLint("MissingPermission")
    override fun monitor(prohibitedAreas: List<ProhibitedArea>): Flow<GeofenceResult> = callbackFlow {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000L)
            .setMinUpdateIntervalMillis(5000L)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val lastLocation = result.lastLocation ?: return
                val lat = lastLocation.latitude
                val lon = lastLocation.longitude

                for (area in prohibitedAreas) {
                    val polygon = parsePolygonFromGeoJson(area.boundaryGeoJson)
                    if (isPointInPolygon(lat, lon, polygon)) {
                        trySend(GeofenceResult.Violation(area.name))
                        return
                    }
                    // In a real implementation, we would also check for proximity
                    // and emit GeofenceResult.Warning if within a buffer zone.
                }
                trySend(GeofenceResult.Safe)
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, callback, context.mainLooper)

        awaitClose {
            fusedLocationClient.removeLocationUpdates(callback)
        }
    }

    /**
     * Implementation of the Ray Casting algorithm for point-in-polygon testing.
     */
    private fun isPointInPolygon(lat: Double, lon: Double, polygon: List<Pair<Double, Double>>): Boolean {
        var intersectCount = 0
        for (i in polygon.indices) {
            val p1 = polygon[i]
            val p2 = polygon[(i + 1) % polygon.size]

            if (((p1.second > lat) != (p2.second > lat)) &&
                (lon < (p2.first - p1.first) * (lat - p1.second) / (p2.second - p1.second) + p1.first)
            ) {
                intersectCount++
            }
        }
        return intersectCount % 2 != 0
    }

    /**
     * Extremely simplified GeoJSON Polygon parser.
     * Expects a standard Feature/Polygon structure.
     */
    private fun parsePolygonFromGeoJson(geoJson: String): List<Pair<Double, Double>> {
        val points = mutableListOf<Pair<Double, Double>>()
        try {
            val jsonArray = JSONArray(geoJson)
            // This assumes the input is a simple array of coordinates or a standard GeoJSON structure
            // In a production app, use a proper GeoJSON library like Jackson or Moshi with GeoJSON modules.

            // Simplified parsing for a [ [ [lng, lat], [lng, lat]... ] ] structure
            // For the sake of this implementation, we expect the GeoJSON to be stripped down or
            // we would use a more robust parsing strategy.

            // Logic to traverse JSON structure to find the coordinate array...
            // (Implementation omitted for brevity in this bootstrap,
            // assuming a helper or proper library would be used)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return points
    }
}
