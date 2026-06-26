package com.hereliesaz.wavefrom

import com.hereliesaz.wavefrom.ar.sensor.ScreenPoint
import com.hereliesaz.wavefrom.signal.model.Vec3
import com.hereliesaz.wavefrom.signal.waveform.AnchoredHelixProjection
import com.hereliesaz.wavefrom.signal.waveform.HelixGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnchoredHelixProjectionTest {

    private val eps = 0.5f
    private val center = ScreenPoint(100f, 200f)
    private val half = HelixGeometry.AXIS_LENGTH / 2f

    @Test
    fun midpointMapsToCenter() {
        val pts = AnchoredHelixProjection.project(
            points = listOf(Vec3(0f, 0f, 0f)),
            center = center, axisX = 0f, axisY = -1f, radiusPx = 50f, lengthPx = 80f,
        )
        assertEquals(center.x, pts[0].x, eps)
        assertEquals(center.y, pts[0].y, eps)
    }

    @Test
    fun timeAxisRunsAlongGivenDirection() {
        // Axis pointing up (-y on screen): the far end (z = +half) sits above center.
        val far = AnchoredHelixProjection.project(
            points = listOf(Vec3(0f, 0f, half)),
            center = center, axisX = 0f, axisY = -1f, radiusPx = 50f, lengthPx = 80f,
        )[0]
        assertEquals(center.x, far.x, eps)
        assertEquals(center.y - 40f, far.y, eps) // (t-0.5)=0.5 * length(80) = 40 up
    }

    @Test
    fun inPhaseDisplacesAcrossTheAxis() {
        // I = 1 with axis up -> displacement along +x (right of center).
        val p = AnchoredHelixProjection.project(
            points = listOf(Vec3(1f, 0f, 0f)),
            center = center, axisX = 0f, axisY = -1f, radiusPx = 50f, lengthPx = 80f,
        )[0]
        assertTrue("expected right of center, got ${p.x}", p.x > center.x)
        assertEquals(center.y, p.y, eps)
    }

    @Test
    fun emptyInputYieldsEmpty() {
        assertTrue(AnchoredHelixProjection.project(emptyList(), center, 0f, -1f, 10f, 10f).isEmpty())
    }
}
