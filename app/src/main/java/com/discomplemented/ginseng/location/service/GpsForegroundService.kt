package com.discomplemented.ginseng.location.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.discomplemented.ginseng.R
import com.discomplemented.ginseng.location.batcher.LocationBatcher
import com.discomplemented.ginseng.location.tracker.LocationTracker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground Service for continuous GPS tracking.
 * Complies with Android 14+ FOREGROUND_SERVICE_LOCATION requirements.
 * Implements emergency flush on service termination.
 */
@AndroidEntryPoint
class GpsForegroundService : Service() {

    @Inject
    lateinit var locationTracker: LocationTracker

    @Inject
    lateinit var locationBatcher: LocationBatcher

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "ginseng_location_tracking"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Display foreground notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ginseng Scout")
            .setContentText("Location tracking active")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )

        // Start location tracking
        serviceScope.launch {
            locationTracker.startTracking().collect { trackNode ->
                locationBatcher.addNode(trackNode)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        // Emergency flush: persist any remaining in-memory state
        serviceScope.launch {
            locationBatcher.flush()
            locationTracker.stopTracking()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "Location Tracking",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
