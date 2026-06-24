package com.hereliesaz.wavefrom.signal.physics

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Great-circle geometry between two lat/lon points. Pure math, no Android types, so
 * it is JVM-unit-testable. Used to point an AR bearing from the phone toward a cell
 * tower whose position was resolved from a database.
 */
object GeoBearing {

    /**
     * Initial great-circle bearing from (`lat1`,`lon1`) to (`lat2`,`lon2`), in
     * degrees true (0..360, clockwise from north). Degrees in, degrees out.
     */
    fun azimuthDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        val deg = Math.toDegrees(atan2(y, x))
        return ((deg + 360.0) % 360.0).toFloat()
    }
}
