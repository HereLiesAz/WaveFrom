package com.hereliesaz.wavefrom

import com.hereliesaz.wavefrom.signal.model.Detection
import com.hereliesaz.wavefrom.signal.model.Direction
import com.hereliesaz.wavefrom.signal.model.Identity
import com.hereliesaz.wavefrom.signal.model.SignalBand
import com.hereliesaz.wavefrom.signal.model.SourceType
import com.hereliesaz.wavefrom.signal.model.Vec3
import com.hereliesaz.wavefrom.signal.record.DetectionCodec
import com.hereliesaz.wavefrom.signal.record.SessionFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionCodecTest {

    private fun roundTrip(d: Detection): Detection = DetectionCodec.decode(DetectionCodec.encode(d))

    private fun base(direction: Direction) = Detection(
        sourceType = SourceType.WIFI,
        band = SignalBand.WIFI_5,
        frequencyHz = 5_500_000_000L,
        powerDbm = -61.5f,
        direction = direction,
        identity = Identity(key = "ap-1", label = "AP", vendor = "Acme"),
        timestampMs = 1_700_000_000_123L,
    )

    @Test
    fun trueBearingRoundTrips() {
        val d = base(Direction.TrueBearing(134.5f, 3.0f, 0.82f))
        val r = roundTrip(d)
        assertEquals(d, r)
    }

    @Test
    fun trueBearingNullElevationRoundTrips() {
        val d = base(Direction.TrueBearing(90f, null, 0.5f))
        assertEquals(d, roundTrip(d))
    }

    @Test
    fun interferometricRoundTrips() {
        val d = base(Direction.InterferometricBearing(45f, 225f, 0.4f))
        assertEquals(d, roundTrip(d))
        val noMirror = base(Direction.InterferometricBearing(45f, null, 0.4f))
        assertEquals(noMirror, roundTrip(noMirror))
    }

    @Test
    fun motionEstimatedRoundTrips() {
        val d = base(Direction.MotionEstimated(Vec3(4f, 1.5f, -2f), 0.3f))
        assertEquals(d, roundTrip(d))
    }

    @Test
    fun rssiOnlyRoundTrips() {
        val d = base(Direction.RssiOnly(12.5f, 0.6f))
        assertEquals(d, roundTrip(d))
        val noDist = base(Direction.RssiOnly(null, 0.6f))
        assertEquals(noDist, roundTrip(noDist))
    }

    @Test
    fun nullFrequencyAndVendorRoundTrip() {
        val d = base(Direction.RssiOnly(1f, 0.5f)).copy(
            frequencyHz = null,
            identity = Identity(key = "k", label = "l", vendor = null),
        )
        assertEquals(d, roundTrip(d))
    }

    @Test
    fun unknownEnumsDecodeToFallback() {
        // Hand-built JSON with bogus enums must not throw; falls back conservatively.
        val det = DetectionCodec.decode(org.json.JSONObject(
            """{"st":"FUTURE_RADIO","band":"BAND_X","pwr":-70.0,"dir":{"t":"???","conf":0.5},"id":{"key":"k","label":"l"},"ts":1}""",
        ))
        assertEquals(SourceType.SIMULATED, det.sourceType)
        assertEquals(SignalBand.UNKNOWN, det.band)
        assertTrue(det.direction is Direction.RssiOnly)
    }

    @Test
    fun sessionLineRoundTripsAndHeaderIsSkipped() {
        val d = base(Direction.TrueBearing(10f, null, 0.9f))
        val line = SessionFormat.encodeLine(456L, d)
        val parsed = SessionFormat.parseLine(line)!!
        assertEquals(456L, parsed.offsetMs)
        assertEquals(d, parsed.detection)
        assertNull(SessionFormat.parseLine(SessionFormat.header()))
        assertNull(SessionFormat.parseLine(""))
        assertNull(SessionFormat.parseLine("not json"))
    }
}
