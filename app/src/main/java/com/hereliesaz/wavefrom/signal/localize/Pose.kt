package com.hereliesaz.wavefrom.signal.localize

import com.hereliesaz.wavefrom.signal.model.Vec3

/**
 * A timestamped device pose in the AR world frame. Supplied by ARCore (6DoF) or,
 * on the fallback path, by sensor fusion. The motion-aided localizer fuses a
 * stream of these with radio measurements to triangulate emitters — the RF
 * analog of ARCore's depth-from-motion.
 */
data class Pose(
    val position: Vec3,
    /** Forward (look) direction unit vector in world space. */
    val forward: Vec3,
    val timestampMs: Long,
    /**
     * 1-sigma horizontal position uncertainty in metres. 0 for ARCore's cm-level
     * VIO; several metres for the GPS sensor-path fallback. The localizer uses it
     * to gate motion (only a step larger than the noise counts as real aperture)
     * and to cap solve confidence, so GPS-driven estimates stay honestly coarse.
     */
    val positionAccuracyM: Float = 0f,
)
