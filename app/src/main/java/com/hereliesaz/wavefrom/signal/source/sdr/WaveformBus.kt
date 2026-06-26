package com.hereliesaz.wavefrom.signal.source.sdr

import com.hereliesaz.wavefrom.signal.waveform.IqWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A window of real captured IQ samples tagged with the source it came from. [sourceId]
 * lets the 3D viewer match a window to what the user is inspecting (a single-antenna SDR
 * has no bearing track, so it is matched by this id rather than a [com.hereliesaz.wavefrom
 * .signal.model.Track]).
 */
data class IqFrame(
    val sourceId: String,
    val label: String,
    val frequencyHz: Long,
    val window: IqWindow,
    val timestampMs: Long,
)

/**
 * Carries the latest real IQ window from an SDR to the 3D helix viewer, mirroring
 * [SpectrumBus]. Time-domain IQ is a per-source burst (not a per-emitter detection), so
 * it travels outside the Detection pipeline on this lightweight bus. The on-phone USB SDR
 * publishes here directly; networked sources route their `waveform` messages here too.
 */
object WaveformBus {
    private val _latest = MutableStateFlow<IqFrame?>(null)
    val latest: StateFlow<IqFrame?> = _latest.asStateFlow()

    fun publish(frame: IqFrame) {
        _latest.value = frame
    }
}
