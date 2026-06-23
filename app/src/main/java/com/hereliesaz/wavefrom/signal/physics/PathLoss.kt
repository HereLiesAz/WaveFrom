package com.hereliesaz.wavefrom.signal.physics

import com.hereliesaz.wavefrom.signal.model.SignalBand
import kotlin.math.log10
import kotlin.math.pow

/**
 * Log-distance path-loss model: converts a received-power reading into a rough
 * range estimate. This is intentionally fuzzy — RSSI-to-distance is corrupted by
 * multipath, walls and antenna patterns, so the result must be presented as an
 * uncertain radius, never a precise fix (see [confidenceFor]).
 *
 * distance = 10 ^ ((refPowerAt1m - rssi) / (10 * pathLossExponent))
 */
object PathLoss {

    /** Reference received power at 1 m, per band (dBm). Empirical ballpark values. */
    private fun refPowerAt1m(band: SignalBand): Double = when (band) {
        SignalBand.BLUETOOTH -> -59.0
        SignalBand.WIFI_2_4 -> -45.0
        SignalBand.WIFI_5, SignalBand.WIFI_6 -> -50.0
        SignalBand.CELL_LOW, SignalBand.CELL_MID, SignalBand.CELL_HIGH -> -65.0
        SignalBand.UNKNOWN -> -50.0
    }

    /** Path-loss exponent: 2 = free space, ~3 = light indoor, ~4 = obstructed. */
    private const val DEFAULT_EXPONENT = 2.7

    /**
     * Estimate distance in metres from a power reading. Returns null for clearly
     * invalid input. Monotonic: a stronger signal yields a smaller distance.
     */
    fun estimateDistanceM(
        powerDbm: Float,
        band: SignalBand,
        pathLossExponent: Double = DEFAULT_EXPONENT,
    ): Float? {
        if (powerDbm >= 0f || powerDbm < -127f) return null
        val ref = refPowerAt1m(band)
        val distance = 10.0.pow((ref - powerDbm) / (10.0 * pathLossExponent))
        return distance.coerceIn(0.1, 500.0).toFloat()
    }

    /**
     * A crude confidence in the distance estimate in [0,1]: strong, recent
     * signals are trusted more than faint ones near the noise floor.
     */
    fun confidenceFor(powerDbm: Float): Float {
        // Map -100 dBm -> ~0.1, -40 dBm -> ~1.0
        val c = (powerDbm + 100f) / 60f
        return c.coerceIn(0.1f, 1f)
    }

    /** Convenience: free-space path loss (dB) at a frequency and distance. */
    fun freeSpaceLossDb(frequencyHz: Long, distanceM: Double): Double {
        if (frequencyHz <= 0 || distanceM <= 0) return 0.0
        // FSPL(dB) = 20log10(d) + 20log10(f) + 20log10(4π/c)
        return 20 * log10(distanceM) + 20 * log10(frequencyHz.toDouble()) - 147.55
    }
}
