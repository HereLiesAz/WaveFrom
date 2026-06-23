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
)
