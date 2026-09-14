package com.ginsengo.steward.field

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class FieldLocation(
    val lat: Double,
    val lng: Double,
    val accuracyM: Float,
    /** GPS altitude in metres, when the fix carries one. */
    val altitudeM: Double?,
    val timestamp: Long,
)

/**
 * GPS via FusedLocationProviderClient (PRD §7), throttled to a 1000 ms minimum interval.
 *
 * The throttle is the whole point: this app is carried for hours on a hillside with no
 * charger, so it asks for the slowest update rate that still feels live.
 */
class LocationProvider(private val context: Context) {

    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun updates(): Flow<FieldLocation> = callbackFlow {
        if (!hasPermission()) {
            close()
            return@callbackFlow
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L)
            .setMinUpdateIntervalMillis(1_000L)   // PRD §7 battery throttle
            .setMinUpdateDistanceMeters(2f)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it.toField()) }
            }
        }
        client.requestLocationUpdates(request, callback, context.mainLooper)
        awaitClose { client.removeLocationUpdates(callback) }
    }

    @SuppressLint("MissingPermission")
    fun lastKnown(onResult: (FieldLocation?) -> Unit) {
        if (!hasPermission()) return onResult(null)
        client.lastLocation
            .addOnSuccessListener { onResult(it?.toField()) }
            .addOnFailureListener { onResult(null) }
    }

    private fun Location.toField() = FieldLocation(
        lat = latitude,
        lng = longitude,
        accuracyM = accuracy,
        altitudeM = if (hasAltitude()) altitude else null,
        timestamp = time,
    )
}
