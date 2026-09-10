package com.discomplemented.ginseng.location.tracker

import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.discomplemented.ginseng.data.local.database.entity.TrackNodeEntity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

/**
 * High-accuracy location tracker using FusedLocationProviderClient.
 * Emits track nodes with full sensor data (altitude, accuracy, speed, bearing).
 */
class LocationTrackerImpl(
    private val fusedLocationClient: FusedLocationProviderClient,
    private val context: Context
) : LocationTracker {

    private var isTracking = false
    private var callback: LocationCallback? = null

    override fun startTracking(): Flow<TrackNodeEntity> = callbackFlow {
        isTracking = true

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000 // 1-second updates
        ).apply {
            setMinUpdateDistanceMeters(2.0f)
        }.build()

        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    val trackNode = location.toTrackNode()
                    trySend(trackNode)
                }
            }
        }

        try {
            @Suppress("MissingPermission")
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                callback!!,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            close(e)
        }

        awaitClose {
            stopTracking()
        }
    }

    override fun stopTracking() {
        if (callback != null) {
            fusedLocationClient.removeLocationUpdates(callback!!)
            callback = null
        }
        isTracking = false
    }

    override fun isTracking(): Boolean = isTracking

    private fun Location.toTrackNode(): TrackNodeEntity {
        return TrackNodeEntity(
            id = UUID.randomUUID().toString(),
            sessionId = UUID.randomUUID().toString(), // TODO: Inject session ID
            timestamp = time,
            latitude = latitude,
            longitude = longitude,
            altitude = altitude.toFloat(),
            accuracy = accuracy,
            speed = speed,
            bearing = bearing,
            synced = false
        )
    }
}
