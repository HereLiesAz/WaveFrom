package com.hereliesaz.wavefrom.ar.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Where the back camera is pointing, derived from the fused rotation-vector
 * sensor. This is the fallback orientation source for the sensor overlay (the
 * ARCore path supersedes it in Phase 2). Angles are degrees:
 *  - [DeviceOrientation.azimuthDeg] camera heading, 0..360 clockwise from magnetic north
 *  - [DeviceOrientation.pitchDeg]   up-tilt of the camera, +up
 *  - [DeviceOrientation.rollDeg]    device roll
 */
data class DeviceOrientation(
    val azimuthDeg: Float,
    val pitchDeg: Float,
    val rollDeg: Float,
)

class HeadingProvider(context: Context) {

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    fun orientation(): Flow<DeviceOrientation> = callbackFlow {
        val sensor = rotationSensor ?: run { close(); return@callbackFlow }
        val rotation = FloatArray(9)
        val remapped = FloatArray(9)
        val angles = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                // Remap so the result describes where the BACK camera points
                // while the phone is held upright in portrait.
                SensorManager.remapCoordinateSystem(
                    rotation,
                    SensorManager.AXIS_X,
                    SensorManager.AXIS_Z,
                    remapped,
                )
                SensorManager.getOrientation(remapped, angles)
                val azimuth = ((Math.toDegrees(angles[0].toDouble()) + 360.0) % 360.0).toFloat()
                trySend(
                    DeviceOrientation(
                        azimuthDeg = azimuth,
                        pitchDeg = Math.toDegrees(angles[1].toDouble()).toFloat(),
                        rollDeg = Math.toDegrees(angles[2].toDouble()).toFloat(),
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
