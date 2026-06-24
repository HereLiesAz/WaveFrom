package com.hereliesaz.wavefrom.ar.frame

import com.hereliesaz.wavefrom.ar.sensor.ScreenProjection

/**
 * Stateless conversions between the [BearingFrame]s. Pure math, no Android types,
 * so it is JVM-unit-testable like [ScreenProjection] and
 * [com.hereliesaz.wavefrom.ar.arcore.ArcoreMath].
 *
 * Conventions:
 *  - "Convert" helpers ([sdrArrayToTrue], [magneticToTrue], [sessionToTrue]) return
 *    an absolute heading wrapped to `[0, 360)`.
 *  - "Solve" helpers ([seedSessionToTrue], [solveNudgeFromKnownEmitter],
 *    [solveArrayOffset]) return an offset/correction and use
 *    [ScreenProjection.normalizeDeg] so the result is the shortest signed angle in
 *    `(-180, 180]`.
 */
object FrameMath {

    /** Wrap an angle to `[0, 360)`. */
    fun wrap360(deg: Float): Float {
        val d = deg % 360f
        return if (d < 0f) d + 360f else d
    }

    /** SDR array-relative azimuth → true-north azimuth. */
    fun sdrArrayToTrue(arrayAzimuthDeg: Float, arrayOffsetDeg: Float): Float =
        wrap360(arrayAzimuthDeg + arrayOffsetDeg)

    /**
     * Magnetic heading → true-north heading. [declinationDeg] is +east (added),
     * [nudgeDeg] is an extra user trim stacked on top.
     */
    fun magneticToTrue(magneticDeg: Float, declinationDeg: Float, nudgeDeg: Float = 0f): Float =
        wrap360(magneticDeg + declinationDeg + nudgeDeg)

    /** ARCore session-relative yaw → true-north heading. */
    fun sessionToTrue(sessionDeg: Float, sessionToTrueNorthDeg: Float): Float =
        wrap360(sessionDeg + sessionToTrueNorthDeg)

    /**
     * Convert a device heading reported in [frame] to true north, applying the live
     * [cfg] offsets. `TRUE_NORTH`/`SDR_ARRAY` headings are returned as-is (wrapped).
     * Shared by the overlay and the calibration controls so they agree.
     */
    fun headingToTrue(frame: BearingFrame, azimuthDeg: Float, cfg: CalibrationConfig.State): Float =
        when (frame) {
            BearingFrame.MAGNETIC_NORTH ->
                magneticToTrue(azimuthDeg, cfg.declinationDeg, cfg.manualNorthNudgeDeg)
            BearingFrame.ARCORE_SESSION ->
                sessionToTrue(azimuthDeg, cfg.sessionToTrueNorthDeg)
            BearingFrame.TRUE_NORTH, BearingFrame.SDR_ARRAY -> wrap360(azimuthDeg)
        }

    /**
     * Offset that maps a session yaw to true north, given the compass true-north
     * heading observed at the same instant. Feed the result back through
     * [sessionToTrue] to recover [trueHeadingDeg].
     */
    fun seedSessionToTrue(sessionYawDeg: Float, trueHeadingDeg: Float): Float =
        ScreenProjection.normalizeDeg(trueHeadingDeg - sessionYawDeg)

    /**
     * One-tap alignment: with the phone pointed at an emitter whose true bearing is
     * [knownTrueBearingDeg] while the phone's own (already-true) heading reads
     * [currentTrueHeadingDeg], the residual nudge to add so the emitter lands dead
     * centre. Shortest signed correction.
     */
    fun solveNudgeFromKnownEmitter(knownTrueBearingDeg: Float, currentTrueHeadingDeg: Float): Float =
        ScreenProjection.normalizeDeg(knownTrueBearingDeg - currentTrueHeadingDeg)

    /**
     * One-tap SDR-array solve: the SDR reports [arrayAzimuthDeg] for an emitter
     * whose true bearing is [trueBearingDeg]; returns the array offset that makes
     * [sdrArrayToTrue] agree. Shortest signed correction.
     */
    fun solveArrayOffset(arrayAzimuthDeg: Float, trueBearingDeg: Float): Float =
        ScreenProjection.normalizeDeg(trueBearingDeg - arrayAzimuthDeg)
}
