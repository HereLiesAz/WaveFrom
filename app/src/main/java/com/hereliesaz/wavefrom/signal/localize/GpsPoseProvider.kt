package com.hereliesaz.wavefrom.signal.localize

import com.hereliesaz.wavefrom.signal.model.Vec3
import kotlin.math.PI
import kotlin.math.cos

/**
 * Converts GPS fixes (lat/lon in degrees, altitude in metres) into local positions
 * in WaveFrom's world convention (x = east, y = up, z = -north), relative to the
 * first fix as the origin. An equirectangular tangent-plane projection: accurate to
 * well under a metre over the hundreds-of-metres scale a walking user covers — far
 * below GPS's own several-metre noise — so the approximation never dominates the
 * error budget.
 *
 * This is the sensor-path analog of ARCore's VIO translation, but GPS-coarse. It
 * exists only to give [SyntheticApertureLocalizer] *some* motion aperture on devices
 * without ARCore. Because GPS position noise is metres, solves built on it are
 * deliberately low-confidence; they become genuinely useful only when paired with
 * true Wi-Fi-RTT ranges (sub-metre), where GPS supplies *where* each range was taken
 * and RTT supplies the accurate distance.
 *
 * Pure math, no Android types — fully unit-testable. Not thread-safe; feed it from a
 * single location callback.
 */
class GpsPoseProvider {
    private var lat0 = Double.NaN
    private var lon0 = 0.0
    private var alt0 = 0.0
    private var cosLat0 = 1.0

    /** True once the first fix has fixed the local origin. */
    val hasOrigin: Boolean get() = !lat0.isNaN()

    /**
     * Local position (metres) for a fix in WaveFrom's world frame. The first call
     * sets the origin, so it returns [Vec3.ZERO]; subsequent calls are relative to it.
     */
    fun toLocal(latDeg: Double, lonDeg: Double, altM: Double): Vec3 {
        if (lat0.isNaN()) {
            lat0 = latDeg
            lon0 = lonDeg
            alt0 = altM
            cosLat0 = cos(latDeg * PI / 180.0)
        }
        val east = (lonDeg - lon0) * DEG_TO_M * cosLat0
        val north = (latDeg - lat0) * DEG_TO_M
        val up = altM - alt0
        // World convention: x east, y up, z = -north (matches ArcoreMath.bearing).
        return Vec3(east.toFloat(), up.toFloat(), (-north).toFloat())
    }

    private companion object {
        /** Metres per degree of latitude (mean Earth radius); longitude scaled by cos(lat0). */
        const val DEG_TO_M = 111_320.0
    }
}
