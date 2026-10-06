package com.ginsengo.steward.ui

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Which way the phone faces, in degrees from TRUE north (N.1): the rotation-vector sensor, its
 * magnetic north corrected by the declination at the fix. Null without the sensor, while disabled,
 * or before the first reading.
 *
 * Battery: the sensor runs only while the screen is up (it stops at ON_STOP), at the UI rate, and
 * the value changes (so the map redraws) only when it turns by more than 2° and at most every
 * 150 ms. Smoothed on the unit circle, so 359° → 1° does not swing through south.
 */
@Composable
fun rememberHeading(enabled: Boolean, lat: Double?, lng: Double?): Float? {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var heading by remember { mutableStateOf<Float?>(null) }
    val where by rememberUpdatedState(lat to lng)
    DisposableEffect(enabled, lifecycle) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (!enabled || sm == null || sensor == null) {
            heading = null
            return@DisposableEffect onDispose { }
        }
        val rot = FloatArray(9); val remapped = FloatArray(9); val o = FloatArray(3)
        var sx = 0.0; var sy = 0.0; var primed = false
        var shownAt = 0L; var shown = Float.NaN
        var declination: Float? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                val (ax, ay) = when (displayRotation(context)) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(rot, ax, ay, remapped)
                SensorManager.getOrientation(remapped, o)
                val a = o[0].toDouble()
                if (!primed) { sx = cos(a); sy = sin(a); primed = true }
                else { sx = sx * 0.8 + cos(a) * 0.2; sy = sy * 0.8 + sin(a) * 0.2 }
                if (declination == null) {
                    val (la, lo) = where
                    if (la != null && lo != null) declination =
                        GeomagneticField(la.toFloat(), lo.toFloat(), 0f, System.currentTimeMillis()).declination
                }
                val h = ((Math.toDegrees(atan2(sy, sx)) + (declination ?: 0f) + 360.0) % 360.0).toFloat()
                val now = SystemClock.uptimeMillis()
                val turned = if (shown.isNaN()) 360f else abs(((h - shown + 540f) % 360f) - 180f)
                if (turned > 2f && now - shownAt >= 150) { shown = h; shownAt = now; heading = h }
            }

            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = Unit
        }
        var registered = false
        fun start() { if (!registered) { sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI); registered = true } }
        fun stop() { if (registered) { sm.unregisterListener(listener); registered = false } }
        val obs = LifecycleEventObserver { _, ev ->
            when (ev) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        onDispose { lifecycle.removeObserver(obs); stop() }
    }
    return heading
}

@Suppress("DEPRECATION")   // Context.display needs API 30; minSdk is 26
private fun displayRotation(context: Context): Int =
    context.getSystemService(WindowManager::class.java)?.defaultDisplay?.rotation ?: Surface.ROTATION_0
