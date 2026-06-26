package com.hereliesaz.wavefrom.signal.record

import com.hereliesaz.wavefrom.signal.model.Detection
import org.json.JSONObject

/** A detection paired with its arrival offset (ms) from the start of a recording. */
data class TimedDetection(val offsetMs: Long, val detection: Detection)

/**
 * The on-disk session format: newline-delimited JSON, one [TimedDetection] per
 * line — `{"t":<offsetMs>,"d":{…detection…}}`. Streamable (write as detections
 * arrive, read line-by-line) and diffable. The first line may be a header
 * `{"v":1,"kind":"wavefrom-session"}` which [parseLine] simply skips.
 *
 * Pure; the Android recorder/loader supplies the actual I/O.
 */
object SessionFormat {

    const val VERSION = 1
    fun header(): String =
        JSONObject().put("v", VERSION).put("kind", "wavefrom-session").toString()

    fun encodeLine(offsetMs: Long, detection: Detection): String =
        JSONObject().put("t", offsetMs).put("d", DetectionCodec.encode(detection)).toString()

    /** Parse one line into a [TimedDetection], or null for blanks, the header, or junk. */
    fun parseLine(line: String): TimedDetection? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        val o = runCatching { JSONObject(trimmed) }.getOrNull() ?: return null
        val det = o.optJSONObject("d") ?: return null // header / non-detection line
        return TimedDetection(o.optLong("t", 0L), DetectionCodec.decode(det))
    }

    /** Parse a whole recording (e.g. file lines) into ordered detections. */
    fun parse(lines: Sequence<String>): List<TimedDetection> =
        lines.mapNotNull { parseLine(it) }.toList()
}
