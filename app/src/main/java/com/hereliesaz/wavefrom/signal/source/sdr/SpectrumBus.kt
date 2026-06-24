package com.hereliesaz.wavefrom.signal.source.sdr

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Carries the latest spectrum frame from any SDR source to the waterfall UI.
 * Spectrum is a wide snapshot (power per frequency bin), not a per-emitter
 * detection, so it travels outside the [com.hereliesaz.wavefrom.signal.model.Detection]
 * pipeline via this lightweight bus.
 */
object SpectrumBus {
    private val _latest = MutableStateFlow<SdrMessage.Spectrum?>(null)
    val latest: StateFlow<SdrMessage.Spectrum?> = _latest.asStateFlow()

    fun publish(spectrum: SdrMessage.Spectrum) {
        _latest.value = spectrum
    }
}
