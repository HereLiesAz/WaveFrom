package com.hereliesaz.wavefrom.signal.record

import com.hereliesaz.wavefrom.signal.model.Detection
import com.hereliesaz.wavefrom.signal.model.Direction
import com.hereliesaz.wavefrom.signal.model.Identity
import com.hereliesaz.wavefrom.signal.model.SignalBand
import com.hereliesaz.wavefrom.signal.model.SourceType
import com.hereliesaz.wavefrom.signal.model.Vec3
import org.json.JSONObject

/**
 * Round-trips a [Detection] (including its [Direction] sealed subtype) to compact
 * JSON, for recording a live session to disk and replaying it later. Uses the
 * platform `org.json`, so no extra dependency. Unknown enums decode to safe
 * fallbacks rather than throwing, so a recording from a newer build still loads.
 *
 * Pure — no Android types — and the exact inverse of [decode]∘[encode] for every
 * Direction tier (see DetectionCodecTest).
 */
object DetectionCodec {

    fun encode(d: Detection): JSONObject = JSONObject().apply {
        put("st", d.sourceType.name)
        put("band", d.band.name)
        d.frequencyHz?.let { put("freqHz", it) }
        put("pwr", d.powerDbm.toDouble())
        put("dir", encodeDir(d.direction))
        put("id", JSONObject().apply {
            put("key", d.identity.key)
            put("label", d.identity.label)
            d.identity.vendor?.let { put("vendor", it) }
        })
        put("ts", d.timestampMs)
    }

    fun decode(o: JSONObject): Detection {
        val idObj = o.optJSONObject("id") ?: JSONObject()
        return Detection(
            sourceType = enum(o.optString("st"), SourceType.SIMULATED),
            band = enum(o.optString("band"), SignalBand.UNKNOWN),
            frequencyHz = if (o.has("freqHz") && !o.isNull("freqHz")) o.optLong("freqHz") else null,
            powerDbm = o.optDouble("pwr", -120.0).toFloat(),
            direction = decodeDir(o.optJSONObject("dir") ?: JSONObject()),
            identity = Identity(
                key = idObj.optString("key", "?"),
                label = idObj.optString("label", "?"),
                vendor = if (idObj.has("vendor") && !idObj.isNull("vendor")) idObj.optString("vendor") else null,
            ),
            timestampMs = o.optLong("ts", 0L),
        )
    }

    private fun encodeDir(dir: Direction): JSONObject = JSONObject().apply {
        when (dir) {
            is Direction.TrueBearing -> {
                put("t", "true"); put("az", dir.azimuthDeg.toDouble())
                dir.elevationDeg?.let { put("el", it.toDouble()) }
                put("conf", dir.confidence.toDouble())
            }
            is Direction.InterferometricBearing -> {
                put("t", "interf"); put("az", dir.azimuthDeg.toDouble())
                dir.ambiguousMirrorDeg?.let { put("mirror", it.toDouble()) }
                put("conf", dir.confidence.toDouble())
            }
            is Direction.MotionEstimated -> {
                put("t", "motion")
                put("x", dir.worldPos.x.toDouble())
                put("y", dir.worldPos.y.toDouble())
                put("z", dir.worldPos.z.toDouble())
                put("conf", dir.confidence.toDouble())
            }
            is Direction.RssiOnly -> {
                put("t", "rssi")
                dir.estimatedDistanceM?.let { put("dist", it.toDouble()) }
                put("conf", dir.confidence.toDouble())
            }
        }
    }

    private fun decodeDir(o: JSONObject): Direction {
        val conf = o.optDouble("conf", 0.5).toFloat()
        return when (o.optString("t")) {
            "true" -> Direction.TrueBearing(
                azimuthDeg = o.optDouble("az", 0.0).toFloat(),
                elevationDeg = if (o.has("el") && !o.isNull("el")) o.optDouble("el").toFloat() else null,
                confidence = conf,
            )
            "interf" -> Direction.InterferometricBearing(
                azimuthDeg = o.optDouble("az", 0.0).toFloat(),
                ambiguousMirrorDeg = if (o.has("mirror") && !o.isNull("mirror")) o.optDouble("mirror").toFloat() else null,
                confidence = conf,
            )
            "motion" -> Direction.MotionEstimated(
                worldPos = Vec3(
                    o.optDouble("x", 0.0).toFloat(),
                    o.optDouble("y", 0.0).toFloat(),
                    o.optDouble("z", 0.0).toFloat(),
                ),
                confidence = conf,
            )
            // Default/unknown → RssiOnly: the most conservative tier (no fake direction).
            else -> Direction.RssiOnly(
                estimatedDistanceM = if (o.has("dist") && !o.isNull("dist")) o.optDouble("dist").toFloat() else null,
                confidence = conf,
            )
        }
    }

    private inline fun <reified T : Enum<T>> enum(name: String, fallback: T): T =
        runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)
}
