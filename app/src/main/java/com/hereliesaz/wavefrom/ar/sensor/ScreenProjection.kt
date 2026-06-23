package com.hereliesaz.wavefrom.ar.sensor

import kotlin.math.abs
import kotlin.math.tan

/** A projected on-screen position in pixels. */
data class ScreenPoint(val x: Float, val y: Float)

/**
 * Projects a world bearing (azimuth/elevation) onto the camera viewport given
 * the device orientation and field of view, using a gnomonic (pinhole) mapping.
 * Pure math, no Android types, so it is unit-testable. Returns null when the
 * target falls outside the visible frustum (behind the camera or beyond the FOV).
 */
object ScreenProjection {

    fun project(
        targetAzimuthDeg: Float,
        targetElevationDeg: Float,
        headingDeg: Float,
        pitchDeg: Float,
        horizontalFovDeg: Float,
        verticalFovDeg: Float,
        widthPx: Float,
        heightPx: Float,
    ): ScreenPoint? {
        val relAz = normalizeDeg(targetAzimuthDeg - headingDeg)
        val relEl = targetElevationDeg - pitchDeg

        val halfH = horizontalFovDeg / 2f
        val halfV = verticalFovDeg / 2f
        if (abs(relAz) >= halfH || abs(relEl) >= halfV) return null

        val nx = tan(Math.toRadians(relAz.toDouble())) /
            tan(Math.toRadians(halfH.toDouble()))
        val ny = tan(Math.toRadians(relEl.toDouble())) /
            tan(Math.toRadians(halfV.toDouble()))

        val x = (0.5 + nx / 2.0) * widthPx
        val y = (0.5 - ny / 2.0) * heightPx
        return ScreenPoint(x.toFloat(), y.toFloat())
    }

    /** Wrap an angle difference into (-180, 180]. */
    fun normalizeDeg(deg: Float): Float {
        var d = deg % 360f
        if (d > 180f) d -= 360f
        if (d <= -180f) d += 360f
        return d
    }
}
