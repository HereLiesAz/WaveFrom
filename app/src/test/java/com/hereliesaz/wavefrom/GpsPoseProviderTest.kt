package com.hereliesaz.wavefrom

import com.hereliesaz.wavefrom.signal.localize.GpsPoseProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlin.math.cos
import org.junit.Test

class GpsPoseProviderTest {

    @Test
    fun firstFixIsTheOrigin() {
        val gps = GpsPoseProvider()
        assertFalse(gps.hasOrigin)
        val p = gps.toLocal(45.0, -122.0, 30.0)
        assertTrue(gps.hasOrigin)
        assertEquals(0f, p.x, 1e-3f)
        assertEquals(0f, p.y, 1e-3f)
        assertEquals(0f, p.z, 1e-3f)
    }

    @Test
    fun northDisplacementMapsToNegativeZ() {
        val gps = GpsPoseProvider()
        gps.toLocal(45.0, -122.0, 0.0)
        // +0.001° latitude ≈ 111.32 m north → z = -north in WaveFrom's world frame.
        val p = gps.toLocal(45.001, -122.0, 0.0)
        assertEquals(0f, p.x, 0.5f)
        assertEquals(-111.32f, p.z, 0.5f)
    }

    @Test
    fun eastDisplacementScalesByCosLatitude() {
        val gps = GpsPoseProvider()
        val lat = 45.0
        gps.toLocal(lat, -122.0, 0.0)
        // +0.001° longitude → east shrinks by cos(latitude).
        val p = gps.toLocal(lat, -121.999, 0.0)
        val expectedEast = (0.001 * 111_320.0 * cos(Math.toRadians(lat))).toFloat()
        assertEquals(expectedEast, p.x, 0.5f)
        assertEquals(0f, p.z, 0.5f)
    }

    @Test
    fun altitudeMapsToUp() {
        val gps = GpsPoseProvider()
        gps.toLocal(45.0, -122.0, 100.0)
        val p = gps.toLocal(45.0, -122.0, 112.5)
        assertEquals(12.5f, p.y, 1e-2f)
    }
}
