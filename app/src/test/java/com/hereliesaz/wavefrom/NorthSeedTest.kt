package com.hereliesaz.wavefrom

import android.hardware.SensorManager
import com.hereliesaz.wavefrom.ar.frame.CalibrationConfig
import com.hereliesaz.wavefrom.ar.frame.FrameMath
import com.hereliesaz.wavefrom.ui.arview.computeNorthSeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The ARCore session→north seed decision, verified without a live ARCore session.
 * `SENSOR_STATUS_ACCURACY_*` are compile-time `static final int` constants (HIGH=3,
 * MEDIUM=2, LOW=1, UNRELIABLE=0), inlined here — no Robolectric needed.
 */
class NorthSeedTest {

    private val eps = 0.001f
    private val cfg = CalibrationConfig.State(declinationDeg = 10f, manualNorthNudgeDeg = -5f)

    @Test
    fun belowMediumAccuracyDoesNotSeed() {
        assertNull(seed(SensorManager.SENSOR_STATUS_ACCURACY_LOW))
        assertNull(seed(SensorManager.SENSOR_STATUS_UNRELIABLE))
    }

    @Test
    fun mediumOrHighAccuracySeeds() {
        assertNotNull(seed(SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM))
        assertNotNull(seed(SensorManager.SENSOR_STATUS_ACCURACY_HIGH))
    }

    @Test
    fun seedValueMatchesFrameMath() {
        val offset = seed(SensorManager.SENSOR_STATUS_ACCURACY_HIGH)!!
        // trueHeading = 90 + 10 - 5 = 95; offset maps sessionYaw 200 → 95.
        val expected = FrameMath.seedSessionToTrue(200f, FrameMath.magneticToTrue(90f, 10f, -5f))
        assertEquals(expected, offset, eps)
        assertEquals(95f, FrameMath.sessionToTrue(200f, offset), eps)
    }

    @Test
    fun alreadySeededSkipsRegardlessOfAccuracy() {
        val offset = computeNorthSeed(
            alreadySeeded = true,
            accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
            sessionYawDeg = 200f,
            sensorAzimuthDeg = 90f,
            cfg = cfg,
        )
        assertNull(offset)
    }

    private fun seed(accuracy: Int): Float? = computeNorthSeed(
        alreadySeeded = false,
        accuracy = accuracy,
        sessionYawDeg = 200f,
        sensorAzimuthDeg = 90f,
        cfg = cfg,
    )
}
