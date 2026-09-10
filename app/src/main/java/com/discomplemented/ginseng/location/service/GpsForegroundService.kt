package com.discomplemented.ginseng.location.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.discomplemented.ginseng.R
import com.discomplemented.ginseng.location.batcher.LocationBatcher
import com.discomplemented.ginseng.location.tracker.LocationTracker
import dagger.hilt.android.lifecycle.HiltAndroidService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import javax.inject.Inject

@HiltAndroidService
class GpsForegroundService : Service() {

    @Inject
    lateinit var locationTracker: com.discomplemented.ginseng.location.tracker.LocationTracker

    @Inject
    lateinit var locationBatcher: com.discomplemented.ginseng.location.batcher.LocationBatcher

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "gps_tracking_channel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())

        serviceScope.launch {
            locationTracker.startTracking()
            locationBatcher.collectAndBatch(locationTracker.getLocationUpdates())
        }

        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.launch {
            locationTracker.stopTracking()
            locationBatcher.stop()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ginseng Scout Active")
            .setContentText("Tracking your location for mapping...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation) // Placeholder icon
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GPS Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
