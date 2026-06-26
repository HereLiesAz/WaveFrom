package com.hereliesaz.wavefrom

import com.hereliesaz.wavefrom.signal.source.sdr.SdrMessage
import com.hereliesaz.wavefrom.signal.source.sdr.WireProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WireProtocolWaveformTest {

    @Test
    fun decodesWaveformMessage() {
        val line = """{"type":"waveform","trackId":"krk-0","freqHz":5800000000,""" +
            """"i":[0.0,1.0,-0.5],"q":[1.0,0.0,0.5],"ts":1700000000000}"""
        val msg = WireProtocol.decode(line)
        assertTrue(msg is SdrMessage.Waveform)
        msg as SdrMessage.Waveform
        assertEquals("krk-0", msg.trackId)
        assertEquals(5_800_000_000L, msg.frequencyHz)
        assertEquals(1_700_000_000_000L, msg.timestampMs)
        assertArrayEquals(floatArrayOf(0f, 1f, -0.5f), msg.i)
        assertArrayEquals(floatArrayOf(1f, 0f, 0.5f), msg.q)
    }

    @Test
    fun mismatchedLengthsTruncateToShorter() {
        val line = """{"type":"waveform","trackId":"x","freqHz":1,"i":[0.0,1.0,2.0],"q":[3.0]}"""
        val msg = WireProtocol.decode(line) as SdrMessage.Waveform
        assertEquals(1, msg.i.size)
        assertEquals(1, msg.q.size)
    }

    @Test
    fun missingArraysAreRejected() {
        assertNull(WireProtocol.decode("""{"type":"waveform","trackId":"x","freqHz":1}"""))
    }

    private fun assertArrayEquals(expected: FloatArray, actual: FloatArray) {
        assertEquals(expected.size, actual.size)
        for (n in expected.indices) assertEquals(expected[n], actual[n], 1e-6f)
    }
}
