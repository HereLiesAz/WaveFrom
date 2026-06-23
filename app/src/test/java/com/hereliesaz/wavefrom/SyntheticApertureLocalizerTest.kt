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
        direction = Direction.RssiOnly(estimatedDistanceM = rangeM, distanceConfidence = 0.9f),
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
}
