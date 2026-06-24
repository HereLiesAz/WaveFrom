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
 *  - [DeviceOrientation.accuracy]   latest magnetometer accuracy (a
 *    `SensorManager.SENSOR_STATUS_ACCURACY_*` constant). The whole true-north
 *    calibration derives from the compass, so a low value means every bearing is
 *    suspect — the HUD surfaces this.
 */
data class DeviceOrientation(
    val azimuthDeg: Float,
    val pitchDeg: Float,
    val rollDeg: Float,
    val accuracy: Int = SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
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
        // Updated on the same sensor thread as onSensorChanged, so a plain var is
        // safe; it rides out on the next orientation emission.
        var lastAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH

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
                        accuracy = lastAccuracy,
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                lastAccuracy = accuracy
            }
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
