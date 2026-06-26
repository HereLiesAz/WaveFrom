package com.hereliesaz.wavefrom

import com.hereliesaz.wavefrom.signal.source.sdr.HackRfIq
import com.hereliesaz.wavefrom.signal.waveform.WaveformSource
import org.junit.Assert.assertEquals
import org.junit.Test

class HackRfIqTest {

    private val eps = 1e-4f

    @Test
    fun deinterleavesSignedInt8ToFloat() {
        // Interleaved I,Q signed int8: (0,127), (-128,64).
        val buf = byteArrayOf(0, 127, (-128).toByte(), 64)
        val re = FloatArray(2)
        val im = FloatArray(2)
        HackRfIq.toFloats(buf, 2, re, im)
        assertEquals(0f, re[0], eps)
        assertEquals(127f / 128f, im[0], eps)
        assertEquals(-1f, re[1], eps) // -128/128
        assertEquals(64f / 128f, im[1], eps)
    }

    @Test
    fun sampleCountIsHalfTheBytes() {
        assertEquals(3, HackRfIq.sampleCount(ByteArray(6)))
        assertEquals(3, HackRfIq.sampleCount(ByteArray(7))) // trailing odd byte ignored
    }

    @Test
    fun decimateProducesRealWindowOfRequestedSize() {
        val re = FloatArray(1024) { it.toFloat() }
        val im = FloatArray(1024) { -it.toFloat() }
        val w = HackRfIq.decimate(re, im, 256)
        assertEquals(256, w.size)
        assertEquals(WaveformSource.REAL, w.source)
        // First sample preserved; stride = 1024/256 = 4.
        assertEquals(0f, w.i[0], eps)
        assertEquals(4f, w.i[1], eps)
        assertEquals(-4f, w.q[1], eps)
    }
}
