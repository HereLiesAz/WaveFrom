package com.hereliesaz.wavefrom

import com.hereliesaz.wavefrom.signal.localize.Pose
import com.hereliesaz.wavefrom.signal.localize.SyntheticApertureLocalizer
import com.hereliesaz.wavefrom.signal.model.Detection
import com.hereliesaz.wavefrom.signal.model.Direction
import com.hereliesaz.wavefrom.signal.model.Identity
import com.hereliesaz.wavefrom.signal.model.SignalBand
import com.hereliesaz.wavefrom.signal.model.SourceType
import com.hereliesaz.wavefrom.signal.model.Vec3
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntheticApertureLocalizerTest {

    private fun rangedDetection(rangeM: Float) = Detection(
        sourceType = SourceType.WIFI,
        band = SignalBand.WIFI_5,
        frequencyHz = 5_500_000_000L,
        powerDbm = -60f,
        // Simulate a Wi-Fi-RTT range: RssiOnly carries a trusted distance.
        direction = Direction.RssiOnly(estimatedDistanceM = rangeM, confidence = 0.9f),
        identity = Identity(key = "ap-1", label = "AP"),
        timestampMs = 0,
    )

    @Test
    fun withoutPoseItCannotLocalize() {
        val loc = SyntheticApertureLocalizer()
        // No onPose() called → no motion frame → no upgrade.
        assertNull(loc.refine(rangedDetection(5f)))
    }

    @Test
    fun motionPlusRangesYieldsMotionEstimated() {
        val loc = SyntheticApertureLocalizer()
        val emitter = Vec3(4f, 1f, 2f)
        val path = listOf(
            Vec3(0f, 0f, 0f), Vec3(1f, 0f, 0f), Vec3(2f, 0.5f, 0f),
            Vec3(2f, 2f, 0.3f), Vec3(1f, 2f, 1f), Vec3(0.5f, 1f, 1.5f),
            Vec3(0f, 0f, 2f), Vec3(2.5f, 1f, 1.5f),
        )
        var result: Direction? = null
        for ((i, p) in path.withIndex()) {
            loc.onPose(Pose(position = p, forward = Vec3(1f, 0f, 0f), timestampMs = i.toLong()))
            result = loc.refine(rangedDetection(emitter.distanceTo(p)))
        }
        val estimate = result as? Direction.MotionEstimated
        assertTrue("should have produced a MotionEstimated", estimate != null)
        assertTrue(emitter.distanceTo(estimate!!.worldPos) < 0.5f)
    }

    /** Run the solver over a path whose poses all carry the given position accuracy. */
    private fun solveOverPath(path: List<Vec3>, emitter: Vec3, accuracyM: Float): Direction? {
        val loc = SyntheticApertureLocalizer()
        var result: Direction? = null
        for ((i, p) in path.withIndex()) {
            loc.onPose(Pose(p, forward = Vec3(1f, 0f, 0f), timestampMs = i.toLong(), positionAccuracyM = accuracyM))
            result = loc.refine(rangedDetection(emitter.distanceTo(p)))
        }
        return result
    }

    @Test
    fun gpsScaleJitterOverSmallStepsAddsNoAperture() {
        // ~1 m steps with 8 m position noise: every step is below the noise floor, so
        // no anchor is admitted and the solver never claims a position (no fake motion).
        val emitter = Vec3(4f, 1f, 2f)
        val smallPath = listOf(
            Vec3(0f, 0f, 0f), Vec3(1f, 0f, 0f), Vec3(2f, 0.5f, 0f),
            Vec3(2f, 2f, 0.3f), Vec3(1f, 2f, 1f), Vec3(0.5f, 1f, 1.5f),
            Vec3(0f, 0f, 2f), Vec3(2.5f, 1f, 1.5f),
        )
        assertNull(solveOverPath(smallPath, emitter, accuracyM = 8f))
    }

    @Test
    fun gpsAperturePenalizesConfidenceVersusVio() {
        // A wide path (steps > GPS noise) localizes from both VIO-clean and GPS-noisy
        // poses — ranges are exact, so the position matches — but the GPS solve's
        // confidence is capped well below the zero-noise one.
        val emitter = Vec3(40f, 2f, 30f)
        val widePath = listOf(
            Vec3(0f, 0f, 0f), Vec3(15f, 0f, 0f), Vec3(30f, 1f, 0f),
            Vec3(30f, 1f, 15f), Vec3(15f, 2f, 15f), Vec3(0f, 1f, 30f),
            Vec3(0f, 0f, 45f), Vec3(35f, 1f, 20f),
        )
        val clean = solveOverPath(widePath, emitter, accuracyM = 0f) as? Direction.MotionEstimated
        val gps = solveOverPath(widePath, emitter, accuracyM = 8f) as? Direction.MotionEstimated
        assertTrue("VIO-clean poses should localize", clean != null)
        assertTrue("GPS-wide poses should still localize", gps != null)
        assertTrue("GPS solve must be lower-confidence", gps!!.confidence < clean!!.confidence)
        assertTrue(emitter.distanceTo(gps.worldPos) < 1f)
    }
}
