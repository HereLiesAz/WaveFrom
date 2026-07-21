package com.hereliesaz.wavefrom

import com.hereliesaz.wavefrom.ar.map.RadarBlip
import com.hereliesaz.wavefrom.ar.map.RadarModel
import com.hereliesaz.wavefrom.signal.model.Direction
import com.hereliesaz.wavefrom.signal.model.Identity
import com.hereliesaz.wavefrom.signal.model.SignalBand
import com.hereliesaz.wavefrom.signal.model.SourceType
import com.hereliesaz.wavefrom.signal.model.Track
import com.hereliesaz.wavefrom.signal.model.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarModelTest {

    private fun track(direction: Direction, source: SourceType = SourceType.WIFI, id: String = "t") = Track(
        id = id,
        sourceType = source,
        band = SignalBand.WIFI_5,
        frequencyHz = null,
        identity = Identity(key = id, label = id),
        smoothedPowerDbm = -60f,
        direction = direction,
        firstSeenMs = 0,
        lastSeenMs = 0,
        hitCount = 1,
    )

    private fun build(t: Track) =
        RadarModel.build(listOf(t), eye = Vec3.ZERO, sessionToTrueDeg = 0f, sdrArrayOffsetDeg = 0f).single()

    @Test
    fun motionEstimatedBecomesLocatedDotWithEastNorthRange() {
        // worldPos east=3, north=4 (z=-north), up=0 → range 5, az = atan2(3,4) ≈ 36.87°.
        val blip = build(track(Direction.MotionEstimated(Vec3(3f, 0f, -4f), 0.4f))) as RadarBlip.Located
        assertEquals(3f, blip.east, 1e-3f)
        assertEquals(4f, blip.north, 1e-3f)
        assertEquals(5f, blip.rangeM, 1e-3f)
        assertEquals(36.87f, blip.azimuthTrueDeg, 0.1f)
    }

    @Test
    fun sdrTrueBearingRotatesByArrayOffset() {
        val t = track(Direction.TrueBearing(10f, null, 0.8f), source = SourceType.EXTERNAL_SDR)
        val blip = RadarModel.build(listOf(t), Vec3.ZERO, sessionToTrueDeg = 0f, sdrArrayOffsetDeg = 90f)
            .single() as RadarBlip.Ray
        assertEquals(100f, blip.azimuthTrueDeg, 1e-3f) // 10° array + 90° offset
        assertNull(blip.mirrorTrueDeg)
    }

    @Test
    fun nonSdrTrueBearingIsAlreadyTrue() {
        val blip = build(track(Direction.TrueBearing(123f, null, 0.8f))) as RadarBlip.Ray
        assertEquals(123f, blip.azimuthTrueDeg, 1e-3f)
    }

    @Test
    fun interferometricKeepsMirror() {
        val blip = build(track(Direction.InterferometricBearing(45f, 225f, 0.5f))) as RadarBlip.Ray
        assertEquals(45f, blip.azimuthTrueDeg, 1e-3f)
        assertEquals(225f, blip.mirrorTrueDeg!!, 1e-3f)
    }

    @Test
    fun rssiWithDistanceIsRingAndWithoutIsDropped() {
        val ring = build(track(Direction.RssiOnly(12f, 0.3f))) as RadarBlip.Ring
        assertEquals(12f, ring.rangeM, 1e-3f)
        assertTrue(RadarModel.build(listOf(track(Direction.RssiOnly(null, 0.3f))), Vec3.ZERO, 0f, 0f).isEmpty())
    }
}
