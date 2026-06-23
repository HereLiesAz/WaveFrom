package com.hereliesaz.wavefrom

import com.hereliesaz.wavefrom.signal.model.Detection
import com.hereliesaz.wavefrom.signal.model.Direction
import com.hereliesaz.wavefrom.signal.model.Identity
import com.hereliesaz.wavefrom.signal.model.SignalBand
import com.hereliesaz.wavefrom.signal.model.SourceType
import com.hereliesaz.wavefrom.signal.repo.TrackAggregator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackAggregatorTest {

    private fun detection(key: String, dbm: Float, ts: Long) = Detection(
        sourceType = SourceType.WIFI,
        band = SignalBand.WIFI_2_4,
        frequencyHz = 2_437_000_000L,
        powerDbm = dbm,
        direction = Direction.RssiOnly(estimatedDistanceM = 5f, confidence = 0.5f),
        identity = Identity(key = key, label = key),
        timestampMs = ts,
    )

    @Test
    fun repeatedDetectionsCollapseToOneTrack() {
        val agg = TrackAggregator()
        agg.apply(detection("aa", -50f, 1_000))
        val tracks = agg.apply(detection("aa", -52f, 1_100))
        assertEquals(1, tracks.size)
        assertEquals(2, tracks.first().hitCount)
    }

    @Test
    fun distinctEmittersBecomeDistinctTracks() {
        val agg = TrackAggregator()
        agg.apply(detection("aa", -50f, 1_000))
        val tracks = agg.apply(detection("bb", -60f, 1_000))
        assertEquals(2, tracks.size)
    }

    @Test
    fun staleTracksExpire() {
        val agg = TrackAggregator(expiryMs = 1_000)
        agg.apply(detection("aa", -50f, 1_000))
        val tracks = agg.snapshot(now = 5_000)
        assertTrue(tracks.isEmpty())
    }

    @Test
    fun powerIsSmoothedTowardNewReadings() {
        val agg = TrackAggregator(emaAlpha = 0.5f)
        agg.apply(detection("aa", -40f, 1_000))
        val tracks = agg.apply(detection("aa", -60f, 1_100))
        // EMA: -40 + 0.5*(-60 - -40) = -50
        assertEquals(-50f, tracks.first().smoothedPowerDbm, 0.01f)
    }
}
