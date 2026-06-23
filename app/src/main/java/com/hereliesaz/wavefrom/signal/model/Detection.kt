package com.hereliesaz.wavefrom.signal.model

/**
 * A single observation of an emitter from one source at one instant. Sources
 * emit a stream of these; the repository folds them into [Track]s.
 */
data class Detection(
    val sourceType: SourceType,
    val band: SignalBand,
    /** Exact frequency in Hz when known (e.g. Wi-Fi channel), else null. */
    val frequencyHz: Long?,
    /** Received power in dBm (RSSI / RSRP). */
    val powerDbm: Float,
    val direction: Direction,
    val identity: Identity,
    val timestampMs: Long,
) {
    /** Stable per-emitter id used for aggregation: source + hardware key. */
    val trackId: String get() = "${sourceType.name}:${identity.key}"
}
