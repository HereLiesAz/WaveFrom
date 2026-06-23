package com.hereliesaz.wavefrom.signal.source.debug

import com.hereliesaz.wavefrom.signal.model.Detection
import com.hereliesaz.wavefrom.signal.model.Direction
import com.hereliesaz.wavefrom.signal.model.Identity
import com.hereliesaz.wavefrom.signal.model.SignalBand
import com.hereliesaz.wavefrom.signal.model.SourceType
import com.hereliesaz.wavefrom.signal.source.SignalSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.sin

/**
 * Development-only source that emits synthetic [Direction.TrueBearing] emitters
 * slowly orbiting the user. It validates the azimuth/elevation → screen
 * projection path on any device with no SDR hardware, de-risking the Phase 3
 * network-SDR work. Wire it up only in debug builds.
 */
class FakeBearingSource(
    private val emitterCount: Int = 3,
) : SignalSource {

    override val sourceType = SourceType.SIMULATED
    override fun isAvailable() = true

    override fun detections(): Flow<Detection> = flow {
        var tick = 0
        val bands = listOf(SignalBand.WIFI_5, SignalBand.WIFI_6, SignalBand.BLUETOOTH)
        while (true) {
            val now = System.currentTimeMillis()
            for (i in 0 until emitterCount) {
                val azimuth = ((tick * 1.5f) + i * (360f / emitterCount)) % 360f
                val elevation = 10f * sin(Math.toRadians((tick * 2 + i * 40).toDouble())).toFloat()
                val band = bands[i % bands.size]
                emit(
                    Detection(
                        sourceType = SourceType.SIMULATED,
                        band = band,
                        frequencyHz = band.approxCenterHz,
                        powerDbm = -55f + 5f * sin(Math.toRadians((tick * 3).toDouble())).toFloat(),
                        direction = Direction.TrueBearing(
                            azimuthDeg = azimuth,
                            elevationDeg = elevation,
                            confidence = 0.9f,
                        ),
                        identity = Identity(key = "sim-$i", label = "Sim emitter $i"),
                        timestampMs = now,
                    )
                )
            }
            tick++
            delay(EMIT_INTERVAL_MS)
        }
    }

    private companion object {
        const val EMIT_INTERVAL_MS = 100L
    }
}
