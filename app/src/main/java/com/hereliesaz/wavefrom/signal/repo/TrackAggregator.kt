package com.hereliesaz.wavefrom.signal.repo

import com.hereliesaz.wavefrom.signal.model.Detection
import com.hereliesaz.wavefrom.signal.model.Track

/**
 * Folds a stream of [Detection]s into stable [Track]s: de-duplicates by
 * [Detection.trackId], EMA-smooths received power, and expires tracks not seen
 * recently. Pure logic, single-threaded by contract (the repository collects on
 * one coroutine) — no synchronization needed.
 */
class TrackAggregator(
    private val emaAlpha: Float = 0.3f,
    private val expiryMs: Long = 15_000L,
) {
    private val tracks = LinkedHashMap<String, Track>()

    /** Apply one detection and return the current (pruned) track list. */
    fun apply(detection: Detection): List<Track> {
        val existing = tracks[detection.trackId]
        val smoothed = if (existing == null) detection.powerDbm
        else existing.smoothedPowerDbm + emaAlpha * (detection.powerDbm - existing.smoothedPowerDbm)

        tracks[detection.trackId] = Track(
            id = detection.trackId,
            sourceType = detection.sourceType,
            band = detection.band,
            frequencyHz = detection.frequencyHz ?: existing?.frequencyHz,
            identity = detection.identity,
            smoothedPowerDbm = smoothed,
            direction = detection.direction,
            firstSeenMs = existing?.firstSeenMs ?: detection.timestampMs,
            lastSeenMs = detection.timestampMs,
            hitCount = (existing?.hitCount ?: 0) + 1,
        )
        return snapshot(detection.timestampMs)
    }

    /** Prune stale tracks relative to [now] and return what remains. */
    fun snapshot(now: Long): List<Track> {
        tracks.entries.removeAll { now - it.value.lastSeenMs > expiryMs }
        return tracks.values.sortedByDescending { it.smoothedPowerDbm }
    }
}
