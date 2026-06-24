package com.hereliesaz.wavefrom.ar.frame

import android.hardware.GeomagneticField

/**
 * Computes magnetic declination (the angle between magnetic and true north) for a
 * location using the platform [GeomagneticField] world magnetic model, and writes
 * it into [CalibrationConfig]. This is the only non-testable piece of the frame
 * stack — a thin wrapper over an Android API; the conversion math it feeds lives in
 * [FrameMath].
 *
 * If no location fix is available (permission denied, no recent fix), this is simply
 * never called and declination stays at its manual default — the north-nudge slider
 * is the deliberate fallback.
 */
class DeclinationProvider {

    /** Update [CalibrationConfig.declinationDeg] from a fix. [timeMs] is epoch millis. */
    fun update(latDeg: Double, lonDeg: Double, altM: Double, timeMs: Long) {
        val field = GeomagneticField(
            latDeg.toFloat(),
            lonDeg.toFloat(),
            altM.toFloat(),
            timeMs,
        )
        CalibrationConfig.declinationDeg = field.declination
        CalibrationConfig.declinationFromLocation = true
    }
}
