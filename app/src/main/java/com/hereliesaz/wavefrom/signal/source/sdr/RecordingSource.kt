package com.hereliesaz.wavefrom.signal.source.sdr

import com.hereliesaz.wavefrom.signal.model.Detection
import com.hereliesaz.wavefrom.signal.model.SourceType
import com.hereliesaz.wavefrom.signal.source.SignalSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/**
 * Replays a captured stream of wire-protocol JSON lines (bearings/spectrum) as a
 * normal [SignalSource]. Useful for development and for reviewing a field
 * capture without the hardware present. Capture a stream with e.g.
 * `nc -ul 50505 > capture.jsonl` while a pod is broadcasting.
 */
class RecordingSource(
    private val lines: List<String>,
    private val rateHz: Float = 10f,
    private val loop: Boolean = true,
) : SignalSource {

    override val sourceType = SourceType.EXTERNAL_SDR

    override fun isAvailable(): Boolean = lines.isNotEmpty()

    override fun detections(): Flow<Detection> = flow {
        val stepMs = (1000f / rateHz).toLong().coerceAtLeast(1L)
        do {
            for (line in lines) {
                when (val msg = WireProtocol.decode(line)) {
                    is SdrMessage.Bearing -> emit(WireProtocol.toDetection(msg))
                    is SdrMessage.Spectrum -> SpectrumBus.publish(msg)
                    else -> {}
                }
                delay(stepMs)
            }
        } while (loop)
    }

    companion object {
        /** Build from a newline-delimited capture file. */
        fun fromFile(file: File, rateHz: Float = 10f, loop: Boolean = true): RecordingSource =
            RecordingSource(
                lines = file.takeIf { it.exists() }?.readLines().orEmpty(),
                rateHz = rateHz,
                loop = loop,
            )
    }
}
