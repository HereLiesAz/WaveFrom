package com.hereliesaz.wavefrom.signal.waveform

import com.hereliesaz.wavefrom.ar.sensor.ScreenPoint
import com.hereliesaz.wavefrom.signal.model.Vec3

/**
 * Projects a helix onto the AR overlay as a small foreshortened "spring" pinned at an
 * emitter's marker. The sensor overlay has no camera translation (only bearings), so a
 * true world-space perspective helix isn't well-defined there; instead the local helix
 * (x = I, y = Q, z = time) is billboarded around the marker [center], with its time axis
 * running along [axisX]/[axisY] on screen and its I/Q circle foreshortened to an ellipse
 * so it reads as 3D. Pure (returns [ScreenPoint]s), so it is JVM-unit-testable. The full,
 * honest perspective helix lives in the standalone viewer ([OrbitProjection]).
 */
object AnchoredHelixProjection {

    fun project(
        points: List<Vec3>,
        center: ScreenPoint,
        axisX: Float,
        axisY: Float,
        radiusPx: Float,
        lengthPx: Float,
        ellipse: Float = 0.4f,
    ): List<ScreenPoint> {
        if (points.isEmpty()) return emptyList()
        // Normalize the axis; fall back to "up" if degenerate.
        val mag = kotlin.math.hypot(axisX, axisY)
        val ax = if (mag < 1e-3f) 0f else axisX / mag
        val ay = if (mag < 1e-3f) -1f else axisY / mag
        // Right = axis rotated 90°.
        val rx = -ay
        val ry = ax
        val half = HelixGeometry.AXIS_LENGTH / 2f
        return points.map { p ->
            val t = (p.z + half) / HelixGeometry.AXIS_LENGTH // 0..1 along the time axis
            val along = (t - 0.5f) * lengthPx + p.y * radiusPx * ellipse
            val across = p.x * radiusPx
            ScreenPoint(
                center.x + rx * across + ax * along,
                center.y + ry * across + ay * along,
            )
        }
    }
}
