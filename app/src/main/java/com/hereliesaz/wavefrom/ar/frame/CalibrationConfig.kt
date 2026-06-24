package com.hereliesaz.wavefrom.ar.frame

/**
 * Live, in-memory calibration offsets that reconcile the three bearing frames (see
 * [BearingFrame]) into true north. Mirrors the existing [com.hereliesaz.wavefrom
 * .signal.physics.PathLoss.Config] pattern — a singleton of `@Volatile` fields
 * mutated directly from the in-app calibration controls, with no persistence. The
 * defaults are all zero / unseeded, so before any calibration the app behaves
 * exactly as it did previously (every frame treated as already-true).
 */
object CalibrationConfig {

    /** Added to an SDR's array-relative azimuth to reach true north (degrees). */
    @Volatile
    var sdrArrayOffsetDeg: Float = 0f

    /**
     * Magnetic-to-true declination (degrees, +east). Populated from
     * [DeclinationProvider] when a location fix is available, otherwise left at the
     * manual default and trimmed via [manualNorthNudgeDeg].
     */
    @Volatile
    var declinationDeg: Float = 0f

    /** True once [declinationDeg] came from a real location fix (vs. the default). */
    @Volatile
    var declinationFromLocation: Boolean = false

    /** User trim added on top of [declinationDeg] for the sensor heading (degrees). */
    @Volatile
    var manualNorthNudgeDeg: Float = 0f

    /** Added to ARCore's session-relative yaw to reach true north (degrees). */
    @Volatile
    var sessionToTrueNorthDeg: Float = 0f

    /** True once the ARCore session has been seeded against the compass. */
    @Volatile
    var arcoreSeeded: Boolean = false

    /** Reset everything to the uncalibrated defaults (used when a session resets). */
    fun reset() {
        sdrArrayOffsetDeg = 0f
        declinationDeg = 0f
        declinationFromLocation = false
        manualNorthNudgeDeg = 0f
        sessionToTrueNorthDeg = 0f
        arcoreSeeded = false
    }
}
