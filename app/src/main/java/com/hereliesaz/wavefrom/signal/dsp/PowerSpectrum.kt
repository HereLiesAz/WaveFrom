package com.hereliesaz.wavefrom.signal.dsp

import kotlin.math.log10

/**
 * Computes a DC-centred power spectrum (dB per bin) from complex IQ. Mirrors the
 * companion `dsp.power_spectrum` so the phone and Pi agree. Consumes (mutates)
 * the input arrays via the in-place [Fft]; pass copies if you need to keep them.
 */
object PowerSpectrum {

    /** Returns powers in dB, ascending in frequency (index 0 = −fs/2, n/2 = DC). */
    fun computeDb(re: FloatArray, im: FloatArray): FloatArray {
        val n = re.size
        if (n == 0) return FloatArray(0)
        Fft.transform(re, im)
        val out = FloatArray(n)
        val half = n / 2
        for (k in 0 until n) {
            val src = (k + half) % n // fftshift: negative freqs first
            val mag2 = re[src] * re[src] + im[src] * im[src]
            out[k] = (10.0 * log10(mag2.toDouble() / n + 1e-12)).toFloat()
        }
        return out
    }
}
