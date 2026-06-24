package com.hereliesaz.wavefrom

import com.hereliesaz.wavefrom.ar.frame.CalibrationConfig
import com.hereliesaz.wavefrom.ar.frame.FrameMath
import com.hereliesaz.wavefrom.signal.model.Direction
import com.hereliesaz.wavefrom.signal.model.Identity
import com.hereliesaz.wavefrom.signal.model.SignalBand
import com.hereliesaz.wavefrom.signal.model.SourceType
import com.hereliesaz.wavefrom.signal.model.Track
import com.hereliesaz.wavefrom.ui.arview.CalibrationActions
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Selection + solve behind the one-tap "Align to crosshair" control, verified without
 * Compose or hardware.
 */
class CalibrationActionsTest {

    private val eps = 0.001f

    @Before fun setUp() = CalibrationConfig.reset()
    @After fun tearDown() = CalibrationConfig.reset()

    private fun sdrTrack(id: String, azimuthDeg: Float): Track = track(
        id = id,
        sourceType = SourceType.EXTERNAL_SDR,
        direction = Direction.TrueBearing(azimuthDeg, elevationDeg = null, confidence = 0.9f),
    )

    private fun track(
        id: String,
        sourceType: SourceType,
        direction: Direction,
    ): Track = Track(
        id = id,
        sourceType = sourceType,
        band = SignalBand.UNKNOWN,
        frequencyHz = null,
        identity = Identity(key = id, label = id),
        smoothedPowerDbm = -50f,
        direction = direction,
        firstSeenMs = 0L,
        lastSeenMs = 0L,
        hitCount = 1,
    )

    @Test
    fun picksNearestSdrToHeading() {
        val near = sdrTrack("near", azimuthDeg = 95f)
        val far = sdrTrack("far", azimuthDeg = 200f)
        // No offset, heading 100° true → "near" (95°) wins over "far" (200°).
        val centered = CalibrationActions.centeredSdrTrack(listOf(far, near), headingTrue = 100f, arrayOffsetDeg = 0f)
        assertEquals("near", centered!!.first.id)
        assertEquals(95f, centered.second, eps)
    }

    @Test
    fun ignoresNonSdrAndNonTrueBearing() {
        val wifi = track(
            "wifi",
            SourceType.WIFI,
            Direction.RssiOnly(estimatedDistanceM = 10f, confidence = 0.5f),
        )
        val sdrRssi = track(
            "sdr-rssi",
            SourceType.EXTERNAL_SDR,
            Direction.RssiOnly(estimatedDistanceM = 10f, confidence = 0.5f),
        )
        assertNull(CalibrationActions.centeredSdrTrack(listOf(wifi, sdrRssi), headingTrue = 100f, arrayOffsetDeg = 0f))
    }

    @Test
    fun emptyListIsNull() {
        assertNull(CalibrationActions.centeredSdrTrack(emptyList(), headingTrue = 100f, arrayOffsetDeg = 0f))
    }

    @Test
    fun solveAlignOffsetIsNullWhenNothingCentered() {
        assertNull(CalibrationActions.solveAlignOffset(null, headingTrue = 100f))
    }

    @Test
    fun solveAlignOffsetMatchesFrameMathAndRoundTrips() {
        val centered = sdrTrack("s", azimuthDeg = 80f) to 80f
        val solved = CalibrationActions.solveAlignOffset(centered, headingTrue = 130f)!!
        assertEquals(FrameMath.solveArrayOffset(80f, 130f), solved, eps)
        // Applying the solved offset pins the raw azimuth onto the heading.
        assertEquals(130f, FrameMath.sdrArrayToTrue(80f, solved), eps)
    }
}
