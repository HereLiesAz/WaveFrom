package com.hereliesaz.wavefrom.signal.localize

import com.hereliesaz.wavefrom.signal.model.Direction

/**
 * Tier-1b two-element direction finder. Given the same emitter measured on two
 * antennas with a known baseline (phone internal radio + USB dongle, or two
 * coherent dongles on the Raspberry Pi pod), it converts the inter-antenna
 * RSSI/phase delta into an [Direction.InterferometricBearing]. Two elements
 * leave a front/back ambiguity that motion (or a third element) resolves.
 *
 * Phase 4 supplies the implementation; defined here so the model and registry
 * already account for the interferometric tier.
 */
interface Interferometer {
    /**
     * @param baselineMeters antenna separation
     * @param powerDeltaDb    RSSI difference between the two antennas (a..b)
     * @param phaseDeltaRad   phase difference if available (preferred), else null
     */
    fun solveBearing(
        baselineMeters: Float,
        powerDeltaDb: Float,
        phaseDeltaRad: Float?,
        frequencyHz: Long,
    ): Direction.InterferometricBearing
}
