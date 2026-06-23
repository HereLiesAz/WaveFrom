package com.hereliesaz.wavefrom

import com.hereliesaz.wavefrom.signal.model.SignalBand
import com.hereliesaz.wavefrom.signal.physics.PathLoss
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PathLossTest {

    @Test
    fun strongerSignalIsCloser() {
        val near = PathLoss.estimateDistanceM(-45f, SignalBand.WIFI_2_4)!!
        val far = PathLoss.estimateDistanceM(-85f, SignalBand.WIFI_2_4)!!
        assertTrue("stronger RSSI should map to a smaller distance", near < far)
    }

    @Test
    fun invalidPowerReturnsNull() {
        assertNull(PathLoss.estimateDistanceM(5f, SignalBand.BLUETOOTH))
        assertNull(PathLoss.estimateDistanceM(-200f, SignalBand.BLUETOOTH))
    }

    @Test
    fun validPowerProducesEstimate() {
        assertNotNull(PathLoss.estimateDistanceM(-60f, SignalBand.WIFI_5))
    }

    @Test
    fun confidenceIsClamped() {
        assertTrue(PathLoss.confidenceFor(-200f) >= 0.1f)
        assertTrue(PathLoss.confidenceFor(0f) <= 1f)
    }
}
