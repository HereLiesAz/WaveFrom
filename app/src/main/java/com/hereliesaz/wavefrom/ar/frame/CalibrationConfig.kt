package com.hereliesaz.wavefrom.ar.frame

/**
 * Live, in-memory calibration offsets that reconcile the three bearing frames (see
 * [BearingFrame]) into true north. Mirrors the existing [com.hereliesaz.wavefrom
 * .signal.physics.PathLoss.Config] pattern — a singleton mutated directly from the
 * in-app calibration controls, with no persistence. The defaults are all zero /
 * unseeded, so before any calibration the app behaves exactly as it did previously
 * (every frame treated as already-true).
 *
 * The values are held in a single immutable [State] behind one `@Volatile`
 * reference: writes are atomic (a reader never sees a half-applied [reset] or a
 * [declinationDeg] that disagrees with [declinationFromLocation]), while reads stay
 * lock-free for the render/projection loop. The per-field accessors are kept for
 * ergonomics; a consumer that needs several fields consistently together should read
 * [state] once and use that snapshot.
 */
object CalibrationConfig {

    /** Immutable snapshot of every calibration offset. */
    data class State(
        val sdrArrayOffsetDeg: Float = 0f,
        val declinationDeg: Float = 0f,
        val declinationFromLocation: Boolean = false,
        val manualNorthNudgeDeg: Float = 0f,
        val sessionToTrueNorthDeg: Float = 0f,
        val arcoreSeeded: Boolean = false,
    )

    /** The current snapshot. Read this directly for an atomic multi-field view. */
    @Volatile
    var state: State = State()
        private set

    @Synchronized
    private fun update(transform: (State) -> State) {
        state = transform(state)
    }

    /** Added to an SDR's array-relative azimuth to reach true north (degrees). */
    var sdrArrayOffsetDeg: Float
        get() = state.sdrArrayOffsetDeg
        set(value) = update { it.copy(sdrArrayOffsetDeg = value) }

    /**
     * Magnetic-to-true declination (degrees, +east). Populated from
     * [DeclinationProvider] when a location fix is available, otherwise left at the
     * manual default and trimmed via [manualNorthNudgeDeg].
     */
    var declinationDeg: Float
        get() = state.declinationDeg
        set(value) = update { it.copy(declinationDeg = value) }

    /** True once [declinationDeg] came from a real location fix (vs. the default). */
    var declinationFromLocation: Boolean
        get() = state.declinationFromLocation
        set(value) = update { it.copy(declinationFromLocation = value) }

    /** User trim added on top of [declinationDeg] for the sensor heading (degrees). */
    var manualNorthNudgeDeg: Float
        get() = state.manualNorthNudgeDeg
        set(value) = update { it.copy(manualNorthNudgeDeg = value) }

    /** Added to ARCore's session-relative yaw to reach true north (degrees). */
    var sessionToTrueNorthDeg: Float
        get() = state.sessionToTrueNorthDeg
        set(value) = update { it.copy(sessionToTrueNorthDeg = value) }

    /** True once the ARCore session has been seeded against the compass. */
    var arcoreSeeded: Boolean
        get() = state.arcoreSeeded
        set(value) = update { it.copy(arcoreSeeded = value) }

    /** Reset everything to the uncalibrated defaults (used when a session resets). */
    @Synchronized
    fun reset() {
        state = State()
    }
}
