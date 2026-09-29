package com.ginsengo.steward.field

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ginsengo.steward.GensingoApp
import com.ginsengo.steward.MainActivity
import com.ginsengo.steward.R
import com.ginsengo.steward.data.db.TrackPoint
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Records where the user walks, with the screen off, for as long as they ask it to.
 *
 * A foreground service of type `location`, started only from a tap while the app is in the
 * foreground. That is what lets it keep recording in a pocket WITHOUT
 * ACCESS_BACKGROUND_LOCATION, which this app does not request: a location service started
 * from the foreground keeps the while-in-use grant. It stops from its notification.
 *
 * Battery: every batch of fixes re-reads battery, charging and screen state and asks
 * [PowerPolicy] for a plan; the location request is replaced only when the plan changes.
 * With the screen off, fixes are batched so the processor can sleep between deliveries.
 */
class TrackService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val client by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val filter = TrackFilter()
    private var plan: PowerPolicy.Plan? = null
    private var sessionId: String = ""
    private var lastMoveTime = 0L
    private var lastSpeed: Double? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            lastFusedDelivery = SystemClock.elapsedRealtime()
            handle(result.locations)
        }
    }

    /**
     * Platform GPS, started only if the fused provider has delivered nothing for
     * [FALLBACK_AFTER_MS]: the same failure the live view had on the Phase 7 device run
     * (Play services' location service down, or absent), where recording would silently
     * store nothing. Its fixes are ignored while the fused provider is delivering.
     */
    private val platformListener = LocationListener { loc ->
        if (SystemClock.elapsedRealtime() - lastFusedDelivery > FALLBACK_AFTER_MS) handle(listOf(loc))
    }
    private var lastFusedDelivery = 0L
    private var platformOn = false

    private fun handle(locations: List<android.location.Location>) {
        val accepted = ArrayList<TrackPoint>()
        for (loc in locations) {
            if (loc.hasSpeed()) lastSpeed = loc.speed.toDouble()
            val fix = TrackFilter.Fix(
                loc.latitude, loc.longitude, loc.accuracy, loc.time,
                if (loc.hasSpeed()) loc.speed else null,
            )
            val keep = filter.offer(fix) ?: continue
            lastMoveTime = keep.time
            accepted += TrackPoint(
                sessionId = sessionId, lat = keep.lat, lng = keep.lng,
                accuracyM = keep.accuracyM,
                altitudeM = if (loc.hasAltitude()) loc.altitude else null,
                time = keep.time,
            )
        }
        val app = application as GensingoApp
        if (accepted.isNotEmpty()) scope.launch { app.container.memory.storeTrack(accepted) }
        _state.value = State(true, sessionId, filter.distanceM, plan?.mode)
        replanIfNeeded()
        notifyProgress()
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = replanIfNeeded()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!hasLocationPermission()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString()
            lastMoveTime = System.currentTimeMillis()
            ensureChannel()
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
            ContextCompat.registerReceiver(
                this, screenReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            _state.value = State(true, sessionId, 0.0, null)
            replanIfNeeded(force = true)
            scope.launch {
                delay(FALLBACK_AFTER_MS)
                if (lastFusedDelivery == 0L) {
                    kotlinx.coroutines.withContext(Dispatchers.Main) { plan?.let { requestPlatform(it) } }
                }
            }
        }
        // Not sticky: a location service cannot legally restart itself from the background on
        // API 34+, and silently not recording is worse than visibly stopped.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching { client.removeLocationUpdates(callback) }
        runCatching { getSystemService(LocationManager::class.java)?.removeUpdates(platformListener) }
        runCatching { unregisterReceiver(screenReceiver) }
        scope.cancel()
        _state.value = State(false, null, 0.0, null)
        super.onDestroy()
    }

    @SuppressLint("MissingPermission")
    private fun replanIfNeeded(force: Boolean = false) {
        val next = currentPlan()
        if (!force && next == plan) return
        plan = next
        if (!hasLocationPermission()) return
        val priority = when (next.accuracy) {
            PowerPolicy.Accuracy.HIGH -> Priority.PRIORITY_HIGH_ACCURACY
            PowerPolicy.Accuracy.BALANCED -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        val request = LocationRequest.Builder(priority, next.intervalMs)
            .setMinUpdateIntervalMillis(next.minIntervalMs)
            .setMaxUpdateDelayMillis(next.maxDelayMs)
            .setMinUpdateDistanceMeters(next.minDistanceM)
            .build()
        client.removeLocationUpdates(callback)
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        if (platformOn) requestPlatform(next)
        _state.value = _state.value.copy(mode = next.mode)
    }

    @SuppressLint("MissingPermission")
    private fun requestPlatform(p: PowerPolicy.Plan) {
        val lm = getSystemService(LocationManager::class.java) ?: return
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) return
        lm.removeUpdates(platformListener)
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, p.intervalMs, p.minDistanceM, platformListener, Looper.getMainLooper())
        platformOn = true
    }

    private fun currentPlan(): PowerPolicy.Plan {
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else null
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        val screenOn = (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        return PowerPolicy.plan(
            tracking = true,
            screenOn = screenOn,
            batteryPct = pct,
            charging = charging,
            speedMps = lastSpeed,
            stillForMs = System.currentTimeMillis() - lastMoveTime,
        )
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Track recording", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    /** No coordinates in the notification: it is visible on the lock screen. */
    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, TrackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val km = filter.distanceM / 1000.0
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Recording your track")
            .setContentText("%.2f km · %s".format(km, plan?.mode?.label ?: "starting"))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .build()
    }

    private fun notifyProgress() {
        runCatching {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
        }
    }

    data class State(
        val recording: Boolean,
        val sessionId: String?,
        val distanceM: Double,
        val mode: PowerPolicy.Mode?,
    )

    companion object {
        private const val CHANNEL = "tracking"
        private const val NOTIFICATION_ID = 42
        private const val FALLBACK_AFTER_MS = 20_000L
        const val ACTION_STOP = "com.ginsengo.steward.STOP_TRACK"

        private val _state = MutableStateFlow(State(false, null, 0.0, null))
        val state: StateFlow<State> = _state.asStateFlow()

        fun start(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, TrackService::class.java))

        fun stop(context: Context) =
            context.startService(Intent(context, TrackService::class.java).setAction(ACTION_STOP))
    }
}
