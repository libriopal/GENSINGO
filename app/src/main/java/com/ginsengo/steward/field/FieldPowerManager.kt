package com.ginsengo.steward.field

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages power profiles, battery life estimation, and offline GPS duty-cycling
 * for extended Appalachian forest expeditions.
 */
class FieldPowerManager(private val context: Context) {

    enum class PowerMode(
        val title: String,
        val subtitle: String,
        val powerWattsEst: Double,
        val gpsIntervalMs: Long,
        val mapFpsLimit: Int,
        val isOledPureBlack: Boolean,
    ) {
        OFFICE_PLANNING(
            title = "Office Planning",
            subtitle = "GPS idle · 3D Model Explorer · Zero field battery drain",
            powerWattsEst = 0.10,
            gpsIntervalMs = 0L,
            mapFpsLimit = 60,
            isOledPureBlack = false,
        ),
        ULTRA_POWER_SAVER(
            title = "Ultra Backcountry",
            subtitle = "60s GPS pulse · Static Map · Pure Black OLED · 18+ hrs",
            powerWattsEst = 0.45,
            gpsIntervalMs = 60_000L,
            mapFpsLimit = 5,
            isOledPureBlack = true,
        ),
        BALANCED_FIELD(
            title = "Balanced Scout",
            subtitle = "10s GPS update · 30 FPS · Deep Charcoal · 9 hrs",
            powerWattsEst = 1.10,
            gpsIntervalMs = 10_000L,
            mapFpsLimit = 30,
            isOledPureBlack = false,
        ),
        PRECISION_NAV(
            title = "Precision Stalking",
            subtitle = "1s Real-time GPS · 60 FPS · 3D Shaders · 4 hrs",
            powerWattsEst = 2.40,
            gpsIntervalMs = 1_000L,
            mapFpsLimit = 60,
            isOledPureBlack = false,
        )
    }

    data class PowerStatus(
        val mode: PowerMode = PowerMode.BALANCED_FIELD,
        val batteryPct: Int = 85,
        val isCharging: Boolean = false,
        val estimatedHoursRemaining: Double = 9.5,
        val lastSatellitePingMs: Long = System.currentTimeMillis(),
        val isPingingSatellite: Boolean = false,
        val gpsFixQuality: String = "Good (< 6m accuracy)",
        val canopyShadowDetected: Boolean = false,
    )

    private val _status = MutableStateFlow(readInitialStatus())
    val status: StateFlow<PowerStatus> = _status.asStateFlow()

    fun setPowerMode(mode: PowerMode) {
        val current = _status.value
        val hours = calculateEstHours(current.batteryPct, mode)
        _status.value = current.copy(
            mode = mode,
            estimatedHoursRemaining = hours
        )
    }

    fun triggerSatellitePing() {
        val current = _status.value
        _status.value = current.copy(
            isPingingSatellite = true,
            lastSatellitePingMs = System.currentTimeMillis()
        )
    }

    fun completeSatellitePing(accuracyMeters: Float?) {
        val current = _status.value
        val quality = when {
            accuracyMeters == null -> "No GPS Fix (Terrain Shadow)"
            accuracyMeters <= 5f -> "High Precision (±${accuracyMeters.toInt()}m)"
            accuracyMeters <= 15f -> "Moderate (±${accuracyMeters.toInt()}m, Canopy Filter)"
            else -> "Degraded (±${accuracyMeters.toInt()}m, Deep Hollow)"
        }
        val canopyShadow = (accuracyMeters ?: 99f) > 12f
        _status.value = current.copy(
            isPingingSatellite = false,
            lastSatellitePingMs = System.currentTimeMillis(),
            gpsFixQuality = quality,
            canopyShadowDetected = canopyShadow
        )
    }

    fun updateBatteryState() {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val statusInt = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = statusInt == BatteryManager.BATTERY_STATUS_CHARGING ||
                statusInt == BatteryManager.BATTERY_STATUS_FULL

        val pct = if (level >= 0 && scale > 0) ((level * 100) / scale) else 80
        val current = _status.value
        val hours = calculateEstHours(pct, current.mode)
        _status.value = current.copy(
            batteryPct = pct,
            isCharging = isCharging,
            estimatedHoursRemaining = hours
        )
    }

    private fun readInitialStatus(): PowerStatus {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 85
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        val pct = if (level >= 0 && scale > 0) ((level * 100) / scale) else 85
        return PowerStatus(
            mode = PowerMode.BALANCED_FIELD,
            batteryPct = pct,
            estimatedHoursRemaining = calculateEstHours(pct, PowerMode.BALANCED_FIELD)
        )
    }

    private fun calculateEstHours(batteryPct: Int, mode: PowerMode): Double {
        // Typical modern mobile battery ~15 Wh capacity
        val batteryWh = 15.0 * (batteryPct / 100.0)
        val hours = batteryWh / mode.powerWattsEst
        return (hours * 10).toInt() / 10.0
    }
}
