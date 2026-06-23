package com.hereliesaz.wavefrom.signal.source.sdr

import com.hereliesaz.wavefrom.signal.model.Detection
import com.hereliesaz.wavefrom.signal.model.Direction
import com.hereliesaz.wavefrom.signal.model.Identity
import com.hereliesaz.wavefrom.signal.model.SignalBand
import com.hereliesaz.wavefrom.signal.model.SourceType
import org.json.JSONObject

/**
 * Codec for the WaveFrom SDR wire protocol (newline-delimited JSON). Uses the
 * platform `org.json` so no extra dependency is needed. The Raspberry Pi
 * companion pod and any vendor adapter (QuadRF/Kraken) target this exact shape:
 *
 *   {"type":"bearing","trackId":"krk-7","freqHz":5.8e9,"powerDbm":-61.2,
 *    "azimuthDeg":134.5,"elevationDeg":3.0,"confidence":0.82,"label":"5.8GHz FPV","ts":169...}
 *   {"type":"heartbeat","podId":"pi-roof","antennaCount":4,"ts":169...}
 */
object WireProtocol {

    /** Decode one JSON line into a message, or null if malformed/unknown. */
    fun decode(line: String): SdrMessage? {
        val json = try {
            JSONObject(line.trim())
        } catch (e: Exception) {
            return null
        }
        val ts = json.optLong("ts", System.currentTimeMillis())
        return when (json.optString("type")) {
            "bearing" -> SdrMessage.Bearing(
                trackId = json.optString("trackId", "sdr"),
                frequencyHz = json.optDouble("freqHz", 0.0).toLong(),
                powerDbm = json.optDouble("powerDbm", -100.0).toFloat(),
                azimuthDeg = json.optDouble("azimuthDeg", 0.0).toFloat(),
                elevationDeg = if (json.has("elevationDeg") && !json.isNull("elevationDeg"))
                    json.optDouble("elevationDeg").toFloat() else null,
                confidence = json.optDouble("confidence", 0.5).toFloat(),
                // optString returns the literal "null" for an explicit JSON
                // null, so guard with isNull to avoid showing "null" in the HUD.
                label = if (json.has("label") && !json.isNull("label"))
                    json.optString("label").takeIf { it.isNotBlank() } else null,
                timestampMs = ts,
            )
            "heartbeat" -> SdrMessage.Heartbeat(
                podId = json.optString("podId", "pod"),
                antennaCount = json.optInt("antennaCount", 1),
                timestampMs = ts,
            )
            else -> null
        }
    }

    /** Map a bearing message to the unified [Detection] model. */
    fun toDetection(b: SdrMessage.Bearing): Detection {
        val band = SignalBand.fromFrequencyHz(b.frequencyHz)
        return Detection(
            sourceType = SourceType.EXTERNAL_SDR,
            band = band,
            frequencyHz = b.frequencyHz.takeIf { it > 0 },
            powerDbm = b.powerDbm,
            direction = Direction.TrueBearing(
                azimuthDeg = b.azimuthDeg,
                elevationDeg = b.elevationDeg,
                confidence = b.confidence,
            ),
            identity = Identity(key = b.trackId, label = b.label ?: b.trackId),
            timestampMs = b.timestampMs,
        )
    }
}
