package com.hereliesaz.wavefrom.signal.model

/**
 * An aggregated, time-smoothed emitter built from many [Detection]s. This is
 * what the AR/HUD layer renders.
 */
data class Track(
    val id: String,
    val sourceType: SourceType,
    val band: SignalBand,
    val frequencyHz: Long?,
    val identity: Identity,
    /** EMA-smoothed received power in dBm. */
    val smoothedPowerDbm: Float,
    val direction: Direction,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val hitCount: Int,
) {
    /** Age in ms relative to [now]; the HUD fades stale tracks. */
    fun ageMs(now: Long): Long = now - lastSeenMs
}
