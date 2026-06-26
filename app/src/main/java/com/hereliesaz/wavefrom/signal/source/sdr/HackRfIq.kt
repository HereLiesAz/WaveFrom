package com.hereliesaz.wavefrom.signal.source.sdr

import com.hereliesaz.wavefrom.signal.waveform.IqWindow
import com.hereliesaz.wavefrom.signal.waveform.WaveformSource

/**
 * Pure conversions for HackRF sample buffers, kept out of [HackRfSource] so the math is
 * JVM-unit-testable. HackRF delivers **signed 8-bit** interleaved IQ (I,Q,I,Q…), unlike
 * RTL-SDR's unsigned-8-bit-centred-at-127.5 (see [RtlTcpClient]); here a byte maps
 * directly to roughly [-1, 1) via `/128`.
 */
object HackRfIq {

    /**
     * De-interleave the first [n] complex samples from [buf] (signed int8 I,Q pairs)
     * into [re]/[im], scaled to ~[-1, 1). [re]/[im] must each hold at least [n] floats.
     */
    fun toFloats(buf: ByteArray, n: Int, re: FloatArray, im: FloatArray) {
        for (k in 0 until n) {
            re[k] = buf[2 * k].toInt() / 128f
            im[k] = buf[2 * k + 1].toInt() / 128f
        }
    }

    /** Largest whole complex sample count available in [buf] (2 bytes per sample). */
    fun sampleCount(buf: ByteArray): Int = buf.size / 2

    /** Evenly subsample [re]/[im] down to [out] points as a REAL [IqWindow] for the helix. */
    fun decimate(re: FloatArray, im: FloatArray, out: Int): IqWindow {
        val n = re.size
        val take = minOf(out, n)
        val stride = if (take > 0) n / take else 1
        val i = FloatArray(take)
        val q = FloatArray(take)
        for (k in 0 until take) {
            i[k] = re[k * stride]
            q[k] = im[k * stride]
        }
        return IqWindow(i, q, WaveformSource.REAL)
    }
}
