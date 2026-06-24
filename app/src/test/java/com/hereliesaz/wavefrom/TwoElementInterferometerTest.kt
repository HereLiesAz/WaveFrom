package com.hereliesaz.wavefrom

import com.hereliesaz.wavefrom.signal.localize.TwoElementInterferometer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class TwoElementInterferometerTest {

    private val interf = TwoElementInterferometer()
    private val c = 299_792_458.0

    /** Forward model: phase delta for a source at [thetaDeg] from broadside. */
    private fun phaseFor(thetaDeg: Double, d: Double, freqHz: Double): Float {
        val lambda = c / freqHz
        return (2.0 * PI * d * sin(Math.toRadians(thetaDeg)) / lambda).toFloat()
    }

    @Test
    fun recoversBearingFromPhaseDelta() {
        val d = 0.05
        val f = 2_450_000_000.0
        val phase = phaseFor(30.0, d, f)
        val b = interf.solveBearing(d.toFloat(), 0f, phase, f.toLong())
        assertEquals(30f, b.azimuthDeg, 1f)
        assertTrue("two-element baseline has a front/back mirror", b.ambiguousMirrorDeg != null)
        assertEquals(150f, b.ambiguousMirrorDeg!!, 1f)
    }

    @Test
    fun broadsideSourceIsZeroDegrees() {
        val b = interf.solveBearing(0.05f, 0f, 0f, 2_450_000_000L)
        assertEquals(0f, b.azimuthDeg, 0.5f)
    }

    @Test
    fun amplitudeFallbackIsLowerConfidence() {
        val phaseResult = interf.solveBearing(0.05f, 0f, 1.0f, 2_450_000_000L)
        val ampResult = interf.solveBearing(0.05f, 6f, null, 2_450_000_000L)
        assertTrue(ampResult.confidence < phaseResult.confidence)
        // Positive power delta points to one side.
        assertTrue(ampResult.azimuthDeg > 0f)
    }
}
