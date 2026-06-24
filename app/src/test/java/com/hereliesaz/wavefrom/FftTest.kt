package com.hereliesaz.wavefrom

import com.hereliesaz.wavefrom.signal.dsp.Fft
import com.hereliesaz.wavefrom.signal.dsp.PowerSpectrum
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class FftTest {

    private fun tone(n: Int, bin: Int): Pair<FloatArray, FloatArray> {
        val re = FloatArray(n) { cos(2 * PI * bin * it / n).toFloat() }
        val im = FloatArray(n) { sin(2 * PI * bin * it / n).toFloat() }
        return re to im
    }

    @Test
    fun fftPeaksAtToneBin() {
        val n = 64
        val (re, im) = tone(n, 8)
        Fft.transform(re, im)
        var peak = 0
        var best = -1.0
        for (k in 0 until n) {
            val mag = (re[k] * re[k] + im[k] * im[k]).toDouble()
            if (mag > best) { best = mag; peak = k }
        }
        assertEquals(8, peak)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPowerOfTwo() {
        Fft.transform(FloatArray(48), FloatArray(48))
    }

    @Test
    fun powerSpectrumIsFftShifted() {
        val n = 64
        val (re, im) = tone(n, 8)
        val db = PowerSpectrum.computeDb(re, im)
        var peak = 0
        var best = -1e9f
        for (k in db.indices) if (db[k] > best) { best = db[k]; peak = k }
        // A +8-bin tone lands at index n/2 + 8 after the DC-centred shift.
        assertEquals(n / 2 + 8, peak)
    }

    @Test
    fun rtlTcpCommandEncodingIsBigEndian() {
        val cmd = com.hereliesaz.wavefrom.signal.source.sdr.RtlTcpClient.command(0x01, 0x12345678)
        assertArrayEquals(byteArrayOf(0x01, 0x12, 0x34, 0x56, 0x78), cmd)
    }
}
