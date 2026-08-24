package com.hereliesaz.wavefrom.signal.source.sdr

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Carries the latest occupancy reading per zone from any CSI-capable source to
 * the (future) occupancy UI. Occupancy describes a zone, not a located
 * emitter, so — like [SpectrumBus]/[WaveformBus] — it travels outside the
 * [com.hereliesaz.wavefrom.signal.model.Detection] pipeline via this
 * lightweight bus, keyed by [SdrMessage.Occupancy.zoneId] since more than one
 * pod/zone can be live at once.
 */
object OccupancyBus {
    private val _latest = MutableStateFlow<Map<String, SdrMessage.Occupancy>>(emptyMap())
    val latest: StateFlow<Map<String, SdrMessage.Occupancy>> = _latest.asStateFlow()

    fun publish(occupancy: SdrMessage.Occupancy) {
        _latest.value = _latest.value + (occupancy.zoneId to occupancy)
    }
}
