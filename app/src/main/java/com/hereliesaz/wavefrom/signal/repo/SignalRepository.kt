package com.hereliesaz.wavefrom.signal.repo

import android.content.Context
import com.hereliesaz.wavefrom.signal.model.Detection
import com.hereliesaz.wavefrom.signal.model.Track
import com.hereliesaz.wavefrom.signal.source.SignalSource
import com.hereliesaz.wavefrom.signal.source.ble.BleScanSource
import com.hereliesaz.wavefrom.signal.source.cellular.CellularScanSource
import com.hereliesaz.wavefrom.signal.source.debug.FakeBearingSource
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
                    .map<Detection, Input> { Input.Det(it) }
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
                add(BleScanSource(context))
                add(CellularScanSource(context))
                if (includeSimulated) add(FakeBearingSource())
            }
    }
}
