package com.hereliesaz.wavefrom.signal.localize

import com.hereliesaz.wavefrom.signal.model.Direction
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin

/**
 * Two-element interferometric direction finder (Tier 1b). Given the same emitter
 * measured on two antennas a known baseline apart — the phone's internal radio
 * plus a USB dongle, or two coherent dongles on the Pi pod — it converts the
 * inter-antenna phase delta into a bearing.
 *
 * For a baseline d and wavelength λ, a plane wave arriving at angle θ from
 * broadside produces a phase difference Δψ = 2π·d·sinθ / λ, so
 * θ = asin(Δψ·λ / (2π·d)). A linear two-element array cannot tell front from
 * back, so the mirror angle (180° − θ) is reported as the ambiguous alternative.
 *
 * When only amplitude (RSSI) is available — the realistic case for consumer
 * dongles without phase/CSI access — it falls back to a much coarser, lower-
 * confidence estimate from the power delta. Pure math, fully unit-testable.
 */
class TwoElementInterferometer : Interferometer {

    override fun solveBearing(
        baselineMeters: Float,
        powerDeltaDb: Float,
        phaseDeltaRad: Float?,
        frequencyHz: Long,
    ): Direction.InterferometricBearing {
        return if (phaseDeltaRad != null && baselineMeters > 0f && frequencyHz > 0) {
            fromPhase(baselineMeters, phaseDeltaRad, frequencyHz)
        } else {
            fromAmplitude(powerDeltaDb)
        }
    }

    private fun fromPhase(d: Float, phase: Float, freqHz: Long): Direction.InterferometricBearing {
        val lambda = SPEED_OF_LIGHT / freqHz
        val sinTheta = (phase * lambda / (2.0 * PI * d)).coerceIn(-1.0, 1.0)
        val theta = Math.toDegrees(asin(sinTheta)).toFloat()
        // High confidence near broadside, lower near endfire where sinθ saturates.
        val confidence = (0.6f * (1f - abs(sinTheta).toFloat() * 0.4f)).coerceIn(0.1f, 0.6f)
        return Direction.InterferometricBearing(
            azimuthDeg = theta,
            ambiguousMirrorDeg = mirror(theta),
            confidence = confidence,
        )
    }

    private fun fromAmplitude(powerDeltaDb: Float): Direction.InterferometricBearing {
        // The antenna facing the source reads stronger; map the delta to a coarse
        // angle. Heuristic and low-confidence — amplitude DF is weak.
        val theta = (powerDeltaDb / MAX_AMPLITUDE_DELTA_DB * 90f).coerceIn(-90f, 90f)
        val confidence = (0.25f * (abs(powerDeltaDb) / MAX_AMPLITUDE_DELTA_DB)).coerceIn(0.05f, 0.25f)
        return Direction.InterferometricBearing(
            azimuthDeg = theta,
            ambiguousMirrorDeg = mirror(theta),
            confidence = confidence,
        )
    }

    private fun mirror(thetaDeg: Float): Float = 180f - thetaDeg

    private companion object {
        const val SPEED_OF_LIGHT = 299_792_458.0
        const val MAX_AMPLITUDE_DELTA_DB = 10f
    }
}
