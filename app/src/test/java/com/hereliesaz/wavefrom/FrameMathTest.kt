package com.hereliesaz.wavefrom

import com.hereliesaz.wavefrom.ar.frame.FrameMath
import com.hereliesaz.wavefrom.ar.sensor.ScreenProjection
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-math checks for the frame conversions. The keystone is
 * [frameConvergenceProducesCorrectRelAz]: it proves an SDR bearing and a sensor
 * heading, each arriving in a different frame, resolve to the same true-north frame
 * and yield the on-screen angle the renderer actually uses.
 */
class FrameMathTest {

    private val eps = 0.001f

    @Test
    fun sdrArrayOffsetAppliesAndWraps() {
        assertEquals(10f, FrameMath.sdrArrayToTrue(350f, 20f), eps)
        assertEquals(350f, FrameMath.sdrArrayToTrue(10f, -20f), eps)
    }

    @Test
    fun declinationSignEast() {
        // East declination adds.
        assertEquals(115f, FrameMath.magneticToTrue(100f, 15f), eps)
        // West declination subtracts and wraps.
        assertEquals(355f, FrameMath.magneticToTrue(5f, -10f), eps)
    }

    @Test
    fun nudgeStacksOnDeclination() {
        assertEquals(110f, FrameMath.magneticToTrue(100f, 15f, -5f), eps)
    }

    @Test
    fun sessionToTrueWraps() {
        assertEquals(20f, FrameMath.sessionToTrue(350f, 30f), eps)
    }

    @Test
    fun seedSolvesSessionOffsetRoundTrip() {
        val offset = FrameMath.seedSessionToTrue(sessionYawDeg = 200f, trueHeadingDeg = 50f)
        assertEquals(210f, FrameMath.wrap360(offset), eps)
        // Feeding the offset back recovers the true heading.
        assertEquals(50f, FrameMath.sessionToTrue(200f, offset), eps)
    }

    @Test
    fun frameConvergenceProducesCorrectRelAz() {
        // SDR reports 100° in its array frame; the array is offset +30° from north.
        val targetTrue = FrameMath.sdrArrayToTrue(arrayAzimuthDeg = 100f, arrayOffsetDeg = 30f)
        // Phone compass reads 90° magnetic; declination +10° east.
        val headingTrue = FrameMath.magneticToTrue(magneticDeg = 90f, declinationDeg = 10f)
        assertEquals(130f, targetTrue, eps)
        assertEquals(100f, headingTrue, eps)

        // After both are in true north, the emitter sits 30° right of centre.
        val relAz = ScreenProjection.normalizeDeg(targetTrue - headingTrue)
        assertEquals(30f, relAz, eps)

        // Cross-check: the renderer projects it right-of-centre with the same inputs.
        val p = ScreenProjection.project(
            targetAzimuthDeg = targetTrue,
            targetElevationDeg = 0f,
            headingDeg = headingTrue,
            pitchDeg = 0f,
            horizontalFovDeg = 90f,
            verticalFovDeg = 90f,
            widthPx = 1000f,
            heightPx = 1000f,
        )
        requireNotNull(p)
        assertEquals(true, p.x > 500f)
    }

    @Test
    fun oneTapSolvesNudge() {
        val nudge = FrameMath.solveNudgeFromKnownEmitter(
            knownTrueBearingDeg = 120f,
            currentTrueHeadingDeg = 108f,
        )
        assertEquals(12f, nudge, eps)
    }

    @Test
    fun oneTapSolvesArrayOffset() {
        val offset = FrameMath.solveArrayOffset(arrayAzimuthDeg = 80f, trueBearingDeg = 130f)
        assertEquals(50f, offset, eps)
        assertEquals(130f, FrameMath.sdrArrayToTrue(80f, offset), eps)
    }

    @Test
    fun solveChoosesShortestSignedCorrection() {
        // Known bearing 5°, heading 355° → +10°, not −350°.
        val nudge = FrameMath.solveNudgeFromKnownEmitter(
            knownTrueBearingDeg = 5f,
            currentTrueHeadingDeg = 355f,
        )
        assertEquals(10f, nudge, eps)
    }
}
