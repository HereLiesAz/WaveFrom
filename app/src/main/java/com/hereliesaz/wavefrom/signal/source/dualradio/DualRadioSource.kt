package com.hereliesaz.wavefrom.signal.source.dualradio

import android.content.Context
import com.hereliesaz.wavefrom.signal.model.Detection
import com.hereliesaz.wavefrom.signal.model.SourceType
import com.hereliesaz.wavefrom.signal.source.SignalSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Phase 4 stub: pairs the phone's internal radio with an external USB Wi-Fi/BT
 * dongle to form a 2-element interferometer. Measuring the *same* emitter on
 * both antennas yields an [com.hereliesaz.wavefrom.signal.model.Direction.InterferometricBearing]
 * from the RSSI (ideally phase/CSI) delta, with the classic 2-element front/back
 * ambiguity resolved by user motion.
 *
 * Biggest unknown is Android USB dongle/driver support and raw-phase access (see
 * plan Risks). Where the phone lacks drivers, the same interferometry runs on the
 * Raspberry Pi companion pod, which has full Linux drivers and true phase access
 * across multiple coherent dongles, and streams the result via [com.hereliesaz.wavefrom.signal.source.sdr.NetworkSdrSource].
 */
class DualRadioSource(@Suppress("unused") private val context: Context) : SignalSource {
    override val sourceType = SourceType.DUAL_RADIO
    override fun isAvailable(): Boolean = false // TODO(phase4): detect a usable USB dongle
    override fun detections(): Flow<Detection> = emptyFlow()
}
