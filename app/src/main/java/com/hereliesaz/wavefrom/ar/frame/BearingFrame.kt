package com.hereliesaz.wavefrom.ar.frame

/**
 * Which reference direction an azimuth is measured from. WaveFrom mixes bearings
 * that arrive in three different frames — an external SDR reports azimuth relative
 * to its own antenna array, the phone compass reports magnetic north, and ARCore
 * reports a session-relative yaw fixed at session start. Tagging each value with
 * its frame lets the renderer convert everything to a single common frame
 * ([TRUE_NORTH]) before projecting, instead of subtracting angles that don't share
 * an origin.
 */
enum class BearingFrame {
    /** Geographic ("true") north — the common frame everything converts to. */
    TRUE_NORTH,

    /** Magnetic north, as produced by Android's rotation-vector sensor. */
    MAGNETIC_NORTH,

    /** Relative to an external SDR's antenna-array boresight. */
    SDR_ARRAY,

    /** ARCore world yaw, with 0° fixed wherever the session started. */
    ARCORE_SESSION,
}
