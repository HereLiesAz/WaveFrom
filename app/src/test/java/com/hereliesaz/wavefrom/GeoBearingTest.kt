package com.hereliesaz.wavefrom

import com.hereliesaz.wavefrom.signal.physics.GeoBearing
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoBearingTest {

    private val eps = 0.5f

    @Test
    fun cardinalDirectionsFromEquator() {
        // Due east along the equator.
        assertEquals(90f, GeoBearing.azimuthDeg(0.0, 0.0, 0.0, 1.0), eps)
        // Due west wraps to 270.
        assertEquals(270f, GeoBearing.azimuthDeg(0.0, 0.0, 0.0, -1.0), eps)
        // Due north.
        assertEquals(0f, GeoBearing.azimuthDeg(0.0, 0.0, 1.0, 0.0), eps)
        // Due south.
        assertEquals(180f, GeoBearing.azimuthDeg(1.0, 0.0, 0.0, 0.0), eps)
    }

    @Test
    fun knownPairRoughlyNortheast() {
        // From central London toward a point NE of it should be in the first quadrant.
        val az = GeoBearing.azimuthDeg(51.5074, -0.1278, 52.0, 0.5)
        assertEquals(true, az in 30f..70f)
    }
}
