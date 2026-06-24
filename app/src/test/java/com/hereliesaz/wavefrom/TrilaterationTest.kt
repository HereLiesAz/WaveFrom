package com.hereliesaz.wavefrom

import com.hereliesaz.wavefrom.signal.localize.RangeSample
import com.hereliesaz.wavefrom.signal.localize.Trilateration
import com.hereliesaz.wavefrom.signal.model.Vec3
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrilaterationTest {

    private fun samplesFor(emitter: Vec3, anchors: List<Vec3>) =
        anchors.map { RangeSample(it, emitter.distanceTo(it)) }

    @Test
    fun recoversPositionFromNoiselessRanges() {
        val emitter = Vec3(4f, 1f, 2f)
        val anchors = listOf(
            Vec3(0f, 0f, 0f), Vec3(2f, 0f, 0f), Vec3(0f, 3f, 0f),
            Vec3(0f, 0f, 2.5f), Vec3(1f, 1f, 0.5f), Vec3(3f, 2f, 1f),
        )
        val loc = Trilateration.solve(samplesFor(emitter, anchors))
        assertNotNull(loc)
        assertTrue("residual should be tiny", loc!!.residualM < 0.05f)
        assertTrue(emitter.distanceTo(loc.position) < 0.05f)
        assertTrue("confident with a wide, well-fit aperture", loc.confidence > 0.3f)
    }

    @Test
    fun tooFewSamplesReturnsNull() {
        val emitter = Vec3(4f, 1f, 2f)
        val anchors = listOf(Vec3(0f, 0f, 0f), Vec3(2f, 0f, 0f), Vec3(0f, 3f, 0f))
        assertNull(Trilateration.solve(samplesFor(emitter, anchors)))
    }

    @Test
    fun tooSmallApertureReturnsNull() {
        val emitter = Vec3(40f, 0f, 0f)
        // Anchors clustered within a few cm — no usable parallax.
        val anchors = listOf(
            Vec3(0f, 0f, 0f), Vec3(0.05f, 0f, 0f), Vec3(0f, 0.05f, 0f),
            Vec3(0f, 0f, 0.05f), Vec3(0.05f, 0.05f, 0f), Vec3(0.02f, 0.02f, 0.02f),
        )
        assertNull(Trilateration.solve(samplesFor(emitter, anchors)))
    }

    @Test
    fun solve3x3MatchesKnownSolution() {
        // System with solution (1, 2, 3).
        val a = arrayOf(
            floatArrayOf(2f, 0f, 0f),
            floatArrayOf(0f, 3f, 0f),
            floatArrayOf(0f, 0f, 4f),
        )
        val b = floatArrayOf(2f, 6f, 12f)
        val x = Trilateration.solve3x3(a, b)!!
        assertTrue(kotlin.math.abs(x[0] - 1f) < 1e-4)
        assertTrue(kotlin.math.abs(x[1] - 2f) < 1e-4)
        assertTrue(kotlin.math.abs(x[2] - 3f) < 1e-4)
    }
}
