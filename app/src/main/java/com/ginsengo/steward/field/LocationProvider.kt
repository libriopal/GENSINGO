package com.ginsengo.steward.field

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

data class FieldLocation(
    val lat: Double,
    val lng: Double,
    val accuracyM: Float,
    /** GPS altitude in metres, when the fix carries one. */
    val altitudeM: Double?,
    val timestamp: Long,
)

/**
 * GPS for the live on-screen position, via FusedLocationProviderClient.
 *
 * Only runs while the screen shows the map (the collector is tied to the UI lifecycle), with
 * the request shape decided by [PowerPolicy]. Recording a track with the screen off is
 * [TrackService]'s job, not this class's.
 */
class LocationProvider(private val context: Context) {

    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    /**
     * Live fixes: Google Play services' fused provider first, and the platform GPS provider
     * if the fused provider has delivered nothing for [FALLBACK_AFTER_MS].
     *
     * Found on the Phase 7 emulator run: Play services' location service crashed and the
     * app sat at "GPS..." indefinitely with the GNSS receiver idle. The same happens on any
     * phone without Play services. GPS itself needs neither Play services nor a network, so
     * an app that promises to work offline must not depend on either for its position.
     */
    fun updates(plan: PowerPolicy.Plan): Flow<FieldLocation> = channelFlow {
        var lastFused = 0L
        launch { fusedUpdates(plan).collect { lastFused = SystemClock.elapsedRealtime(); send(it) } }
        launch {
            delay(FALLBACK_AFTER_MS)
            platformGps(plan).collect {
                if (SystemClock.elapsedRealtime() - lastFused > FALLBACK_AFTER_MS) send(it)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun platformGps(plan: PowerPolicy.Plan): Flow<FieldLocation> = callbackFlow {
        val lm = context.getSystemService(LocationManager::class.java)
        if (!hasPermission() || lm == null || !lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            close()
            return@callbackFlow
        }
        val listener = LocationListener { trySend(it.toField()) }
        lm.requestLocationUpdates(
            LocationManager.GPS_PROVIDER, plan.intervalMs, plan.minDistanceM, listener, context.mainLooper,
        )
        awaitClose { lm.removeUpdates(listener) }
    }

    @SuppressLint("MissingPermission")
    private fun fusedUpdates(plan: PowerPolicy.Plan): Flow<FieldLocation> = callbackFlow {
        if (!hasPermission()) {
            close()
            return@callbackFlow
        }
        val priority = when (plan.accuracy) {
            PowerPolicy.Accuracy.HIGH -> Priority.PRIORITY_HIGH_ACCURACY
            PowerPolicy.Accuracy.BALANCED -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        val request = LocationRequest.Builder(priority, plan.intervalMs)
            .setMinUpdateIntervalMillis(plan.minIntervalMs)
            .setMaxUpdateDelayMillis(plan.maxDelayMs)
            .setMinUpdateDistanceMeters(plan.minDistanceM)
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

    private companion object {
        const val FALLBACK_AFTER_MS = 20_000L
    }

    private fun Location.toField() = FieldLocation(
        lat = latitude,
        lng = longitude,
        accuracyM = accuracy,
        altitudeM = if (hasAltitude()) altitude else null,
        timestamp = time,
    )
}
