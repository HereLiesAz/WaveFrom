package com.hereliesaz.wavefrom.ar.map

import kotlin.math.cos
import kotlin.math.sin

/** A point in map pixels. Kept free of Compose types so the projection is unit-testable. */
data class RadarPoint(val x: Float, val y: Float)

/**
 * North-up top-down projection for the map: metres → pixels around a centre (the
 * user). North (azimuth 0) points up the screen, east points right. Pure math.
 */
object RadarProjection {

    /** Project a metric (east, north) offset from the user to screen pixels. */
    fun metricToScreen(east: Float, north: Float, cx: Float, cy: Float, metersPerPx: Float): RadarPoint {
        val mpp = if (metersPerPx <= 0f) 1f else metersPerPx
        return RadarPoint(cx + east / mpp, cy - north / mpp)
    }

    /** Project a polar (azimuth-from-true-north, range) position to screen pixels. */
    fun polarToScreen(azimuthTrueDeg: Float, rangeM: Float, cx: Float, cy: Float, metersPerPx: Float): RadarPoint {
        val a = Math.toRadians(azimuthTrueDeg.toDouble())
        return metricToScreen((rangeM * sin(a)).toFloat(), (rangeM * cos(a)).toFloat(), cx, cy, metersPerPx)
    }

    /**
     * Metres-per-pixel so that [maxRangeM] just fits within [radiusPx] of the
     * centre, clamped to at least [minMetersPerPx] so an all-close scene doesn't
     * zoom in absurdly. Falls back to [minMetersPerPx] when there's nothing to show.
     */
    fun autoScale(maxRangeM: Float, radiusPx: Float, minMetersPerPx: Float = 0.25f): Float {
        if (maxRangeM <= 0f || radiusPx <= 0f) return minMetersPerPx
        return (maxRangeM / radiusPx).coerceAtLeast(minMetersPerPx)
    }
}
