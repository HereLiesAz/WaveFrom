package com.hereliesaz.wavefrom

import com.hereliesaz.wavefrom.ar.sensor.ScreenProjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenProjectionTest {

    @Test
    fun normalizeWrapsAroundCircle() {
        assertEquals(0f, ScreenProjection.normalizeDeg(360f), 0.001f)
        assertEquals(-10f, ScreenProjection.normalizeDeg(350f), 0.001f)
        assertEquals(10f, ScreenProjection.normalizeDeg(370f), 0.001f)
    }

    @Test
    fun targetDeadAheadProjectsToCenter() {
        val p = ScreenProjection.project(
            targetAzimuthDeg = 90f,
            targetElevationDeg = 0f,
            headingDeg = 90f,
            pitchDeg = 0f,
            horizontalFovDeg = 60f,
            verticalFovDeg = 90f,
            widthPx = 1000f,
            heightPx = 2000f,
        )
        assertTrue(p != null)
        assertEquals(500f, p!!.x, 1f)
        assertEquals(1000f, p.y, 1f)
    }

    @Test
    fun targetBehindIsCulled() {
        val p = ScreenProjection.project(
            targetAzimuthDeg = 270f,
            targetElevationDeg = 0f,
            headingDeg = 90f,
            pitchDeg = 0f,
            horizontalFovDeg = 60f,
            verticalFovDeg = 90f,
            widthPx = 1000f,
            heightPx = 2000f,
        )
        assertNull(p)
    }
}
