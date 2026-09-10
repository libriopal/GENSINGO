package com.discomplemented.ginseng.location.tracker

import android.annotation.SuppressLint
import android.content.Context
import com.discomplemented.ginseng.domain.model.TrackNode
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

class LocationTrackerImpl @Inject constructor(
    @SuppressLint("MissingPermission")
    private val fusedLocationClient: FusedLocationProviderClient,
    private val context: Context
) : LocationTracker {

    private var isTracking = false

    @SuppressLint("MissingPermission")
    override fun getLocationUpdates(): Flow<TrackNode> = callbackFlow {
        if (!isTracking) {
            close(Exception("Location tracking not started. Call startTracking() first."))
            return@callbackFlow
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(2000L)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    trySend(
                        TrackNode(
                            timestamp = System.currentTimeMillis(),
                            latitude = location.latitude,
                            longitude = location.longitude,
                            altitude = location.altitude.toFloat(),
                            accuracy = location.accuracy
                        )
                    )
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            callback,
            context.mainLooper
        )

        awaitClose {
            fusedLocationClient.removeLocationUpdates(callback)
        }
    }

    @SuppressLint("MissingPermission")
    override fun startTracking() {
        isTracking = true
    }

    override fun stopTracking() {
        isTracking = false
    }
}
