package com.hereliesaz.wavefrom

import com.hereliesaz.wavefrom.signal.model.Direction
import com.hereliesaz.wavefrom.signal.model.Identity
import com.hereliesaz.wavefrom.signal.model.SignalBand
import com.hereliesaz.wavefrom.signal.model.SourceType
import com.hereliesaz.wavefrom.signal.model.Track
import com.hereliesaz.wavefrom.signal.model.Vec3
import com.hereliesaz.wavefrom.signal.waveform.HelixGeometry
import com.hereliesaz.wavefrom.signal.waveform.IqWindow
import com.hereliesaz.wavefrom.signal.waveform.WaveformSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class HelixGeometryTest {

    private val eps = 1e-3f

    private fun track(freqHz: Long?, band: SignalBand, powerDbm: Float) = Track(
        id = "t",
        sourceType = SourceType.EXTERNAL_SDR,
        band = band,
        frequencyHz = freqHz,
        identity = Identity("k", "label"),
        smoothedPowerDbm = powerDbm,
        direction = Direction.TrueBearing(0f, 0f, 1f),
        firstSeenMs = 0,
        lastSeenMs = 0,
        hitCount = 1,
    )

    @Test
    fun parametricHasConstantRadiusFromPower() {
        val w = HelixGeometry.parametric(track(5_800_000_000L, SignalBand.WIFI_5, -40f))
        assertEquals(HelixGeometry.DEFAULT_SAMPLES, w.size)
        assertEquals(WaveformSource.PARAMETRIC, w.source)
        val expectedAmp = HelixGeometry.normalizedAmplitude(-40f)
        // Every sample sits on a circle of radius = amplitude.
        for (n in 0 until w.size) {
            assertEquals(expectedAmp, hypot(w.i[n], w.q[n]), eps)
        }
    }

    @Test
    fun strongerSignalHasLargerRadius() {
        val weak = HelixGeometry.parametric(track(2_437_000_000L, SignalBand.WIFI_2_4, -90f))
        val strong = HelixGeometry.parametric(track(2_437_000_000L, SignalBand.WIFI_2_4, -35f))
        assertTrue(strong.peakAmplitude() > weak.peakAmplitude())
    }

    @Test
    fun cyclesIncreaseWithFrequencyAndAreBounded() {
        val low = HelixGeometry.cyclesFor(100_000_000L)
        val high = HelixGeometry.cyclesFor(6_000_000_000L)
        assertTrue("higher carrier -> more twist", high > low)
        assertTrue(low >= 2f && high <= 12f)
    }

    @Test
    fun parametricFallsBackToBandCenterWhenFreqNull() {
        // No exact frequency -> uses the band center, so it still produces a valid helix.
        val w = HelixGeometry.parametric(track(null, SignalBand.WIFI_5, -50f))
        assertEquals(HelixGeometry.DEFAULT_SAMPLES, w.size)
        assertTrue(w.peakAmplitude() > 0f)
    }

    @Test
    fun fromIqSpreadsAlongZAndAutoscalesRadius() {
        val w = HelixGeometry.parametric(track(2_437_000_000L, SignalBand.WIFI_2_4, -80f))
        val pts = HelixGeometry.fromIq(w, radiusScale = 1f)
        assertEquals(w.size, pts.size)
        // Peak amplitude maps to radiusScale (1.0) regardless of input power.
        val maxR = pts.maxOf { hypot(it.x, it.y) }
        assertEquals(1f, maxR, eps)
        // z spans the full axis from -L/2 to +L/2.
        assertEquals(-HelixGeometry.AXIS_LENGTH / 2f, pts.first().z, eps)
        assertEquals(HelixGeometry.AXIS_LENGTH / 2f, pts.last().z, eps)
    }

    @Test
    fun orientAlongBearingAlignsAxisAndAnchorsNearEnd() {
        // A simple 2-point "helix" lying on the local z axis.
        val local = listOf(Vec3(0f, 0f, -HelixGeometry.AXIS_LENGTH / 2f), Vec3(0f, 0f, HelixGeometry.AXIS_LENGTH / 2f))
        val anchor = Vec3(1f, 2f, 3f)
        val world = HelixGeometry.orientAlongBearing(
            local, azimuthDeg = 90f, elevationDeg = 0f, anchor = anchor, lengthM = 4f,
        )
        // Near end sits at the anchor.
        assertEquals(anchor.x, world[0].x, eps)
        assertEquals(anchor.y, world[0].y, eps)
        assertEquals(anchor.z, world[0].z, eps)
        // az=90,el=0 -> forward points along +x; far end is lengthM away in +x.
        val axis = (world[1] - world[0])
        assertEquals(4f, axis.length(), eps)
        assertEquals(1f, axis.normalize().x, eps)
        assertTrue(kotlin.math.abs(axis.normalize().y) < eps)
        assertTrue(kotlin.math.abs(axis.normalize().z) < eps)
    }

    @Test
    fun iqWindowEqualityIsByContent() {
        val a = IqWindow(floatArrayOf(1f, 2f), floatArrayOf(3f, 4f), WaveformSource.REAL)
        val b = IqWindow(floatArrayOf(1f, 2f), floatArrayOf(3f, 4f), WaveformSource.REAL)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
