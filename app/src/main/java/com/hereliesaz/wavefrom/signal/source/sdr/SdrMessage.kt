package com.hereliesaz.wavefrom.signal.source.sdr

/**
 * Decoded messages of the WaveFrom SDR wire protocol — the contract spoken by
 * external phased-array SDRs and the Raspberry Pi companion pod (see
 * `companion/`). Transport is newline-delimited JSON over UDP/TCP.
 */
sealed interface SdrMessage {

    /** A located emitter with a real direction-of-arrival. */
    data class Bearing(
        val trackId: String,
        val frequencyHz: Long,
        val powerDbm: Float,
        val azimuthDeg: Float,
        val elevationDeg: Float?,
        val confidence: Float,
        val label: String?,
        val timestampMs: Long,
    ) : SdrMessage

    /** A spectrum snapshot (power per frequency bin) for a waterfall view. */
    data class Spectrum(
        val startHz: Long,
        val binHz: Long,
        val powersDbm: FloatArray,
        val timestampMs: Long,
    ) : SdrMessage {
        override fun equals(other: Any?): Boolean =
            this === other || (other is Spectrum &&
                startHz == other.startHz && binHz == other.binHz &&
                timestampMs == other.timestampMs && powersDbm.contentEquals(other.powersDbm))

        override fun hashCode(): Int =
            ((startHz.hashCode() * 31 + binHz.hashCode()) * 31 +
                timestampMs.hashCode()) * 31 + powersDbm.contentHashCode()
    }

    /** Keep-alive carrying sensor identity / health. */
    data class Heartbeat(
        val podId: String,
        val antennaCount: Int,
        val timestampMs: Long,
    ) : SdrMessage
}
