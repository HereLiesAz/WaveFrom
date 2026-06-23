package com.hereliesaz.wavefrom.signal.model

/**
 * How well an emitter's direction is known. Modeled as a sealed hierarchy so the
 * rendering layer cannot accidentally treat an RSSI-only emitter as if it had a
 * real bearing — each tier is handled explicitly. Tiers, strongest first:
 *
 *  - [TrueBearing]          phased-array SDR: real azimuth/elevation.
 *  - [InterferometricBearing] 2-element (phone + dongle / multi-dongle Pi): coarse,
 *                           often front/back-ambiguous bearing from a phase/RSSI delta.
 *  - [MotionEstimated]      mono-antenna depth-from-motion: a 3D world position
 *                           triangulated from device motion + radio measurements.
 *  - [RssiOnly]             discovery only: a fuzzy distance, no direction.
 */
sealed interface Direction {

    /** Confidence in [0,1] used to scale marker emphasis in the HUD. */
    val confidence: Float

    data class TrueBearing(
        val azimuthDeg: Float,
        /** Null when the array only resolves azimuth (no elevation). */
        val elevationDeg: Float?,
        override val confidence: Float,
    ) : Direction

    data class InterferometricBearing(
        val azimuthDeg: Float,
        /** The mirror-image azimuth a 2-element baseline can't disambiguate, if any. */
        val ambiguousMirrorDeg: Float?,
        override val confidence: Float,
    ) : Direction

    data class MotionEstimated(
        val worldPos: Vec3,
        override val confidence: Float,
    ) : Direction

    data class RssiOnly(
        /** Estimated range in metres from a path-loss model, or null if not modeled. */
        val estimatedDistanceM: Float?,
        override val confidence: Float,
    ) : Direction
}
