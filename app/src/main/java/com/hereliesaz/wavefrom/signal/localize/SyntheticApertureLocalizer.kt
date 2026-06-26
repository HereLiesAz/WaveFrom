package com.hereliesaz.wavefrom.signal.localize

import com.hereliesaz.wavefrom.signal.model.Detection
import com.hereliesaz.wavefrom.signal.model.Direction
import com.hereliesaz.wavefrom.signal.physics.PathLoss

/**
 * Mono-antenna, motion-aided localizer — the RF analog of ARCore's
 * depth-from-motion. As the device moves, each emitter is sampled from many
 * known 3D positions (the device pose stream); each sample contributes a range
 * (true Wi-Fi-RTT distance when available, else a path-loss estimate from RSSI).
 * Once the samples span a wide enough aperture, [Trilateration] solves the
 * emitter's 3D position and the detection is upgraded to
 * [Direction.MotionEstimated].
 *
 * Inert until a real pose stream with position is supplied (ARCore) — the
 * sensor-only fallback has orientation but no translation, so [onPose] is
 * simply never called with motion and tracks stay RSSI-only. That's correct:
 * GPS-level position noise (metres) would swamp the synthetic aperture, so the
 * solver intentionally runs only on cm-level VIO translation.
 */
class SyntheticApertureLocalizer(
    private val maxSamplesPerTrack: Int = 48,
    private val minSamples: Int = 6,
    private val minConfidence: Float = 0.05f,
) : MotionAidedLocalizer {

    private var latestPose: Pose? = null
    private val samples = LinkedHashMap<String, ArrayDeque<RangeSample>>()

    @Synchronized
    override fun onPose(pose: Pose) {
        latestPose = pose
    }

    @Synchronized
    override fun refine(detection: Detection): Direction? {
        val pose = latestPose ?: return null
        val (range, weight) = rangeAndWeight(detection) ?: return null

        // Bound the number of tracked emitters; evict the oldest (LRU-ish) so a
        // long session can't grow the map without limit.
        if (!samples.containsKey(detection.trackId) && samples.size >= MAX_TRACKS) {
            samples.keys.firstOrNull()?.let { samples.remove(it) }
        }
        val buf = samples.getOrPut(detection.trackId) { ArrayDeque() }
        // A new anchor only counts as aperture when the device moved further than its
        // own position noise — otherwise GPS jitter (metres) while standing still would
        // flood the buffer with fake motion. For ARCore (accuracy 0) this is MIN_STEP_M.
        val step = maxOf(MIN_STEP_M, pose.positionAccuracyM)
        if (buf.isEmpty() || buf.last().anchor.distanceTo(pose.position) > step) {
            buf.addLast(RangeSample(pose.position, range, weight))
            while (buf.size > maxSamplesPerTrack) buf.removeFirst()
        }
        if (buf.size < minSamples) return null

        val loc = Trilateration.solve(buf.toList()) ?: return null
        // Cap confidence by position noise: a solve is only as trustworthy as the
        // anchors it was built from. ARCore (0 m) → no penalty; GPS (~8 m) → ~0.11×,
        // so GPS-aperture estimates stay honestly coarse and usually only surface when
        // backed by accurate Wi-Fi-RTT ranges.
        val accPenalty = 1f / (1f + pose.positionAccuracyM)
        val confidence = loc.confidence * accPenalty
        if (confidence < minConfidence) return null
        return Direction.MotionEstimated(loc.position, confidence)
    }

    /** Range to use for this sample, and how much to trust it. */
    private fun rangeAndWeight(detection: Detection): Pair<Float, Float>? {
        val rssiRange = (detection.direction as? Direction.RssiOnly)?.estimatedDistanceM
        val rssiConf = (detection.direction as? Direction.RssiOnly)?.confidence
        val range = rssiRange
            ?: PathLoss.estimateDistanceM(detection.powerDbm, detection.band)
            ?: return null
        val weight = rssiConf ?: PathLoss.confidenceFor(detection.powerDbm)
        return range to weight
    }

    private companion object {
        const val MIN_STEP_M = 0.15f
        const val MAX_TRACKS = 128
    }
}
