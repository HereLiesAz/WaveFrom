package com.hereliesaz.wavefrom

import com.hereliesaz.wavefrom.ar.map.RadarProjection
import org.junit.Assert.assertEquals
import org.junit.Test

class RadarProjectionTest {

    @Test
    fun eastGoesRightNorthGoesUp() {
        val east = RadarProjection.metricToScreen(10f, 0f, cx = 100f, cy = 100f, metersPerPx = 1f)
        assertEquals(110f, east.x, 1e-3f)
        assertEquals(100f, east.y, 1e-3f)
        val north = RadarProjection.metricToScreen(0f, 10f, 100f, 100f, 1f)
        assertEquals(100f, north.x, 1e-3f)
        assertEquals(90f, north.y, 1e-3f) // north is up → smaller y
    }

    @Test
    fun polarMatchesCompassConvention() {
        // Azimuth 90° (due east) at 10 m, 2 m/px → 5 px east.
        val p = RadarProjection.polarToScreen(90f, 10f, cx = 0f, cy = 0f, metersPerPx = 2f)
        assertEquals(5f, p.x, 1e-3f)
        assertEquals(0f, p.y, 1e-3f)
        // Azimuth 0° (due north) → up.
        val n = RadarProjection.polarToScreen(0f, 10f, 0f, 0f, 2f)
        assertEquals(0f, n.x, 1e-3f)
        assertEquals(-5f, n.y, 1e-3f)
    }

    @Test
    fun autoScaleFitsMaxRangeAndClamps() {
        assertEquals(0.5f, RadarProjection.autoScale(maxRangeM = 100f, radiusPx = 200f), 1e-3f)
        // Tiny scene clamps to the minimum so it doesn't over-zoom.
        assertEquals(0.25f, RadarProjection.autoScale(maxRangeM = 1f, radiusPx = 200f, minMetersPerPx = 0.25f), 1e-3f)
        assertEquals(0.25f, RadarProjection.autoScale(maxRangeM = 0f, radiusPx = 200f), 1e-3f)
    }
}
