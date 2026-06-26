package com.hereliesaz.wavefrom.signal.waveform

import kotlin.math.atan2
import kotlin.math.hypot

/** Whether an [IqWindow] is synthesized from a track's parameters or real captured samples. */
enum class WaveformSource { PARAMETRIC, REAL }

/**
 * A window of complex baseband samples (I = in-phase, Q = quadrature) for a single
 * emitter, plus where it came from. This is the data behind the 3D IQ-helix: each
 * sample becomes one point on the corkscrew (radius = amplitude, angle = phase,
 * advancing along the time axis).
 *
 * [PARAMETRIC] windows are synthesized from a track's frequency + power (a perceptual
 * stand-in, since GHz carriers can't be drawn literally). [REAL] windows are actual
 * post-downconversion samples from an SDR, so their twist reflects true modulation.
 */
data class IqWindow(
    val i: FloatArray,
    val q: FloatArray,
    val source: WaveformSource,
) {
    init {
        require(i.size == q.size) { "I/Q arrays must be the same length (${i.size} vs ${q.size})" }
    }

    val size: Int get() = i.size

    /** Magnitude of sample [n]: sqrt(I² + Q²). */
    fun amplitude(n: Int): Float = hypot(i[n], q[n])

    /** Phase of sample [n] in radians, atan2(Q, I). */
    fun phase(n: Int): Float = atan2(q[n], i[n])

    /** Peak amplitude over the window (0 if empty), used for autoscaling. */
    fun peakAmplitude(): Float {
        var peak = 0f
        for (n in 0 until size) {
            val a = amplitude(n)
            if (a > peak) peak = a
        }
        return peak
    }

    // Identity over array *contents* so equal samples compare equal (data class would
    // otherwise compare array references).
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IqWindow) return false
        return source == other.source && i.contentEquals(other.i) && q.contentEquals(other.q)
    }

    override fun hashCode(): Int =
        (i.contentHashCode() * 31 + q.contentHashCode()) * 31 + source.hashCode()
}
