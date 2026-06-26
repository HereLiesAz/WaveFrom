package com.hereliesaz.wavefrom.signal.localize

import com.hereliesaz.wavefrom.signal.model.Detection
import com.hereliesaz.wavefrom.signal.model.Direction

/**
 * Upgrades RSSI-only detections into located ones by fusing device motion with
 * radio measurements (synthetic aperture for RSSI; multilateration for WiFi-RTT
 * ranges). This is the mono-antenna, depth-from-motion path: as the user moves,
 * the same emitter is sampled from many known positions until its direction or
 * 3D position can be solved.
 *
 * [SyntheticApertureLocalizer] is the real solver and the pipeline default;
 * [PassthroughLocalizer] is a no-op kept for tests and headless callers that
 * have no pose stream.
 */
interface MotionAidedLocalizer {
    /** Record a device pose sample (ARCore or sensor-fused). */
    fun onPose(pose: Pose)

    /**
     * Offer a fresh detection for refinement. Returns an upgraded [Direction]
     * (e.g. [Direction.MotionEstimated]) when enough motion-diverse samples have
     * accumulated, or null to keep the detection's existing direction.
     */
    fun refine(detection: Detection): Direction?
}

/** Phase 1 no-op: never upgrades a direction. */
class PassthroughLocalizer : MotionAidedLocalizer {
    override fun onPose(pose: Pose) = Unit
    override fun refine(detection: Detection): Direction? = null
}
