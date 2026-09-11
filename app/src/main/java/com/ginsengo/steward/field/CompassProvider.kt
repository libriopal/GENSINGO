package com.ginsengo.steward.field

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Compass bearing from TYPE_ROTATION_VECTOR (PRD Workflow A step 3).
 *
 * Rotation vector is used rather than the deprecated accelerometer+magnetometer pair
 * because it is already sensor-fused and far steadier in the hand, which matters when the
 * reading decides whether a slope reads as north-facing.
 */
class CompassProvider(context: Context) {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    val isAvailable: Boolean get() = rotationSensor != null

    /** Emits azimuth in degrees clockwise from true-ish north, 0..360. */
    fun bearings(): Flow<Float> = callbackFlow {
        val sm = sensorManager
        val sensor = rotationSensor
        if (sm == null || sensor == null) {
            close()
            return@callbackFlow
        }
        val rotation = FloatArray(9)
        val orientation = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                SensorManager.getOrientation(rotation, orientation)
                val deg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                trySend(((deg % 360f) + 360f) % 360f)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sm.unregisterListener(listener) }
    }
}
