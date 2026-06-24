package com.hereliesaz.wavefrom.signal.repo

import android.content.Context
import com.hereliesaz.wavefrom.signal.localize.MotionAidedLocalizer
import com.hereliesaz.wavefrom.signal.localize.PassthroughLocalizer
import com.hereliesaz.wavefrom.signal.model.Detection
import com.hereliesaz.wavefrom.signal.model.Track
import com.hereliesaz.wavefrom.signal.source.SignalSource
import com.hereliesaz.wavefrom.signal.source.ble.BleScanSource
import com.hereliesaz.wavefrom.signal.source.cellular.CellularScanSource
import com.hereliesaz.wavefrom.signal.source.debug.FakeBearingSource
import com.hereliesaz.wavefrom.signal.source.dualradio.DualRadioSource
import com.hereliesaz.wavefrom.signal.source.sdr.NetworkSdrSource
import com.hereliesaz.wavefrom.signal.source.sdr.UsbSdrSource
import com.hereliesaz.wavefrom.signal.source.wifi.WifiRttSource
import com.hereliesaz.wavefrom.signal.source.wifi.WifiScanSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay

/**
 * Merges every [SignalSource] into a single observable [tracks] stream the UI/AR
 * layer consumes. A heartbeat tick is merged in so stale tracks expire and fade
 * even when no new detections arrive. Adding a new source (e.g. an SDR pod) is a
 * one-line change to the source list.
 */
class SignalRepository(
    private val sources: List<SignalSource>,
    scope: CoroutineScope,
    private val aggregator: TrackAggregator = TrackAggregator(),
    private val localizer: MotionAidedLocalizer = PassthroughLocalizer(),
) {
    private sealed interface Input {
        data class Det(val detection: Detection) : Input
        data object Tick : Input
    }

    private val ticker: Flow<Input> = flow {
        while (true) {
            emit(Input.Tick)
            delay(TICK_MS)
        }
    }

    val tracks: StateFlow<List<Track>> =
        merge(
            *sources.map { src ->
                src.detections()
                    .catch { /* fail soft: a dead source never kills the merge */ }
                    .map<Detection, Input> { det ->
                        // Tier-2 upgrade: motion-aided localizer may resolve a 3D
                        // position from accumulated motion; otherwise unchanged.
                        val refined = localizer.refine(det)?.let { det.copy(direction = it) } ?: det
                        Input.Det(refined)
                    }
            }.toTypedArray(),
            ticker,
        ).scan(emptyList<Track>()) { _, input ->
            when (input) {
                is Input.Det -> aggregator.apply(input.detection)
                Input.Tick -> aggregator.snapshot(System.currentTimeMillis())
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        private const val TICK_MS = 1_000L

        /**
         * Default Phase 1 source set: the phone's own radios plus, in debug, a
         * synthetic bearing source. Network/USB/dual-radio sources are added in
         * later phases.
         */
        fun defaultSources(context: Context, includeSimulated: Boolean): List<SignalSource> =
            buildList {
                add(WifiScanSource(context))
                add(WifiRttSource(context))     // true FTM ranging where supported
                add(BleScanSource(context))
                add(CellularScanSource(context))
                // Listens for the Raspberry Pi pod / external SDR, which broadcasts
                // bearings on the LAN by default — zero-config on the same network.
                add(NetworkSdrSource())
                // USB OTG paths report availability now; emit once DSP lands.
                add(UsbSdrSource(context))
                add(DualRadioSource(context))
                if (includeSimulated) add(FakeBearingSource())
            }
    }
}
