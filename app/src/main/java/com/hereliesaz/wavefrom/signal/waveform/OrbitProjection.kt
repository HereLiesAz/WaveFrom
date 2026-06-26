package com.hereliesaz.wavefrom.signal.waveform

import com.hereliesaz.wavefrom.ar.sensor.ScreenPoint
import com.hereliesaz.wavefrom.signal.model.Vec3
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * An orbit ("turntable") camera looking at [target] from a point on a sphere:
 * [azimuthDeg] around, [elevationDeg] up/down, [distance] away. Driven by drag/pinch
 * gestures in the standalone 3D viewer.
 */
data class OrbitCamera(
    val azimuthDeg: Float,
    val elevationDeg: Float,
    val distance: Float,
    val target: Vec3 = Vec3.ZERO,
)

/**
 * Pure perspective projection of world vertices for the standalone 3D viewer. No
 * Android types, so it is JVM-unit-testable. Separate from
 * [com.hereliesaz.wavefrom.ar.sensor.ScreenProjection] (which projects bearings for an
 * AR camera whose pose comes from sensors); here the camera is a free orbit camera.
 */
object OrbitProjection {

    /** Camera position in world space for [cam]. */
    fun cameraPosition(cam: OrbitCamera): Vec3 {
        val az = Math.toRadians(cam.azimuthDeg.toDouble())
        val el = Math.toRadians(cam.elevationDeg.toDouble())
        val cosEl = cos(el)
        val dir = Vec3(
            (cosEl * sin(az)).toFloat(),
            sin(el).toFloat(),
            (cosEl * cos(az)).toFloat(),
        )
        return cam.target + dir * cam.distance
    }

    /**
     * Project [vertex] to screen pixels, or null when it is behind the camera (or at the
     * eye). [fovDeg] is the horizontal field of view; the vertical FOV is derived from
     * the aspect ratio so the image isn't stretched.
     */
    fun project(
        vertex: Vec3,
        cam: OrbitCamera,
        fovDeg: Float,
        widthPx: Float,
        heightPx: Float,
    ): ScreenPoint? {
        val eye = cameraPosition(cam)
        val forward = (cam.target - eye).normalize()
        val worldUp = if (kotlin.math.abs(forward.y) > 0.99f) Vec3(0f, 0f, 1f) else Vec3(0f, 1f, 0f)
        val right = forward.cross(worldUp).normalize()
        val up = right.cross(forward).normalize()

        val rel = vertex - eye
        val depth = rel.dot(forward)
        if (depth <= 1e-3f) return null // behind or on the camera plane

        val xc = rel.dot(right)
        val yc = rel.dot(up)
        val halfH = tan(Math.toRadians((fovDeg / 2f).toDouble())).toFloat()
        val aspect = if (heightPx > 0f) widthPx / heightPx else 1f
        val halfV = halfH / aspect

        val ndcX = (xc / depth) / halfH
        val ndcY = (yc / depth) / halfV
        val x = (0.5f + ndcX / 2f) * widthPx
        val y = (0.5f - ndcY / 2f) * heightPx
        return ScreenPoint(x, y)
    }
}
