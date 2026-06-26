package com.hereliesaz.wavefrom

import com.hereliesaz.wavefrom.signal.model.Vec3
import com.hereliesaz.wavefrom.signal.waveform.OrbitCamera
import com.hereliesaz.wavefrom.signal.waveform.OrbitProjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbitProjectionTest {

    @Test
    fun targetProjectsToScreenCenter() {
        val cam = OrbitCamera(azimuthDeg = 0f, elevationDeg = 0f, distance = 5f, target = Vec3.ZERO)
        val p = OrbitProjection.project(Vec3.ZERO, cam, fovDeg = 60f, widthPx = 1000f, heightPx = 1000f)
        assertTrue(p != null)
        assertEquals(500f, p!!.x, 1f)
        assertEquals(500f, p.y, 1f)
    }

    @Test
    fun pointBehindCameraIsCulled() {
        val cam = OrbitCamera(azimuthDeg = 0f, elevationDeg = 0f, distance = 5f, target = Vec3.ZERO)
        // Camera sits on +z looking toward origin; a point far behind it (large +z) is culled.
        val behind = Vec3(0f, 0f, 10f)
        assertNull(OrbitProjection.project(behind, cam, 60f, 1000f, 1000f))
    }

    @Test
    fun orbitingKeepsTargetCenteredFromAnyAngle() {
        for (az in intArrayOf(0, 45, 90, 180, 270)) {
            val cam = OrbitCamera(az.toFloat(), elevationDeg = 20f, distance = 4f)
            val p = OrbitProjection.project(Vec3.ZERO, cam, 60f, 800f, 1200f)
            assertTrue("az=$az should keep target in front", p != null)
            assertEquals(400f, p!!.x, 1f)
            assertEquals(600f, p.y, 1f)
        }
    }

    @Test
    fun pointRightOfTargetIsRightOnScreen() {
        // Camera on +z axis looking at origin: world +x maps to screen-right.
        val cam = OrbitCamera(azimuthDeg = 0f, elevationDeg = 0f, distance = 5f)
        val p = OrbitProjection.project(Vec3(1f, 0f, 0f), cam, 60f, 1000f, 1000f)
        assertTrue(p != null)
        assertTrue("expected right of center, got ${p!!.x}", p.x > 500f)
    }
}
