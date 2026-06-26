package com.hereliesaz.wavefrom.signal.waveform

import com.hereliesaz.wavefrom.signal.model.SignalBand
import com.hereliesaz.wavefrom.signal.model.Track
import com.hereliesaz.wavefrom.signal.model.Vec3
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

/**
 * Builds the 3D IQ-helix geometry. Pure math (no Android types) so it is JVM-unit-
 * testable like [com.hereliesaz.wavefrom.ar.sensor.ScreenProjection]. Local frame:
 * x = I, y = Q, z = time (the helix advances along +z, twisting in the I/Q plane).
 */
object HelixGeometry {

    /** Default number of samples in a synthesized helix; capped for cheap drawing. */
    const val DEFAULT_SAMPLES = 256

    /** Total length of the time (z) axis in the local frame. */
    const val AXIS_LENGTH = 2f

    /**
     * Visual twist (number of full turns over the window) for a carrier. Real GHz
     * carriers can't be drawn literally, so the parametric helix maps frequency to a
     * pleasant, monotonic cycle count: ~2 turns at 100 MHz rising to ~10 at 6 GHz.
     * Documented + isolated here so it stays tunable and testable.
     */
    fun cyclesFor(frequencyHz: Long): Float {
        val hz = frequencyHz.coerceAtLeast(1L).toDouble()
        // log10(1e8)=8 -> ~2 turns, log10(6e9)=9.78 -> ~10 turns.
        val turns = (ln(hz) / ln(10.0) - 6.0) * 4.0
        return turns.coerceIn(2.0, 12.0).toFloat()
    }

    /** Map a received power (dBm) to a [0,1] helix radius: -100 dBm -> ~0.1, -30 -> 1. */
    fun normalizedAmplitude(powerDbm: Float): Float =
        ((powerDbm + 100f) / 70f).coerceIn(0.1f, 1f)

    /**
     * Synthesize a [WaveformSource.PARAMETRIC] window for any track from its frequency
     * (exact, else band center) and smoothed power. A perfect helix: constant radius,
     * uniform twist.
     */
    fun parametric(track: Track, samples: Int = DEFAULT_SAMPLES): IqWindow {
        val freq = track.band.frequencyHz(track.frequencyHz)
        val amp = normalizedAmplitude(track.smoothedPowerDbm)
        val cycles = cyclesFor(freq)
        val i = FloatArray(samples)
        val q = FloatArray(samples)
        for (n in 0 until samples) {
            val theta = 2.0 * Math.PI * cycles * (n.toDouble() / samples)
            i[n] = (amp * cos(theta)).toFloat()
            q[n] = (amp * sin(theta)).toFloat()
        }
        return IqWindow(i, q, WaveformSource.PARAMETRIC)
    }

    /**
     * Turn a window of samples into helix vertices in the local frame. Radius is
     * autoscaled so the peak amplitude maps to [radiusScale]; the window is spread
     * evenly along the z axis over [AXIS_LENGTH].
     */
    fun fromIq(window: IqWindow, radiusScale: Float = 1f): List<Vec3> {
        if (window.size == 0) return emptyList()
        val peak = window.peakAmplitude().coerceAtLeast(1e-6f)
        val s = radiusScale / peak
        val dz = if (window.size > 1) AXIS_LENGTH / (window.size - 1) else 0f
        return List(window.size) { n ->
            Vec3(window.i[n] * s, window.q[n] * s, -AXIS_LENGTH / 2f + n * dz)
        }
    }

    /**
     * Rotate a local-frame helix (long axis = +z) so its axis points along the bearing
     * given by [azimuthDeg]/[elevationDeg] (true-north frame), scale it to [lengthM],
     * and translate so its near end sits at [anchor]. Used to place the helix in the AR
     * world along an emitter's line of sight.
     *
     * The bearing convention matches [com.hereliesaz.wavefrom.ar.arcore.ArcoreMath]:
     * azimuth measured from -z toward +x, elevation lifting +y.
     */
    fun orientAlongBearing(
        localPoints: List<Vec3>,
        azimuthDeg: Float,
        elevationDeg: Float,
        anchor: Vec3,
        lengthM: Float,
    ): List<Vec3> {
        if (localPoints.isEmpty()) return emptyList()
        val az = Math.toRadians(azimuthDeg.toDouble())
        val el = Math.toRadians(elevationDeg.toDouble())
        // Forward (new +z axis) = bearing direction in world space.
        val cosEl = cos(el)
        val forward = Vec3(
            (cosEl * sin(az)).toFloat(),
            sin(el).toFloat(),
            (-cosEl * cos(az)).toFloat(),
        ).normalize()
        // Build an orthonormal basis (right, up, forward); world-up is +y.
        val worldUp = if (kotlin.math.abs(forward.y) > 0.99f) Vec3(1f, 0f, 0f) else Vec3(0f, 1f, 0f)
        val right = worldUp.cross(forward).normalize()
        val up = forward.cross(right).normalize()
        val half = AXIS_LENGTH / 2f
        val zScale = if (AXIS_LENGTH > 0f) lengthM / AXIS_LENGTH else 1f
        return localPoints.map { p ->
            // Map local (x=right, y=up, z=forward), scaling the axis to lengthM and
            // shifting the near end (local z = -half) to the anchor.
            val radial = right * p.x + up * p.y
            val along = forward * ((p.z + half) * zScale)
            anchor + radial + along
        }
    }
}
