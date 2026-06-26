package com.hereliesaz.wavefrom.ar.map

import com.hereliesaz.wavefrom.ar.arcore.ArcoreMath
import com.hereliesaz.wavefrom.ar.frame.FrameMath
import com.hereliesaz.wavefrom.signal.model.Direction
import com.hereliesaz.wavefrom.signal.model.SourceType
import com.hereliesaz.wavefrom.signal.model.Track
import com.hereliesaz.wavefrom.signal.model.Vec3
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A track's place on the top-down map, by honesty tier. The map is north-up and
 * centred on the user, so everything is expressed relative to the user's eye:
 *
 *  - [Located]  a motion-estimated emitter: a real bearing *and* range → a dot at
 *               (east, north) metres from the user.
 *  - [Ray]      a bearing-only emitter (true / interferometric): direction known,
 *               range unknown → a ray from the user toward [azimuthTrueDeg].
 *  - [Ring]     an RSSI-only emitter with a fuzzy distance: range known, direction
 *               unknown → a ring of that radius around the user.
 *
 * RSSI-only tracks without even a distance estimate are dropped (nothing honest to
 * draw). All angles are degrees clockwise from true north.
 */
sealed interface RadarBlip {
    val track: Track
    val confidence: Float

    data class Located(
        override val track: Track,
        /** Metres east / north of the user (true-north frame). */
        val east: Float,
        val north: Float,
        val rangeM: Float,
        val azimuthTrueDeg: Float,
        override val confidence: Float,
    ) : RadarBlip

    data class Ray(
        override val track: Track,
        val azimuthTrueDeg: Float,
        /** The front/back mirror a 2-element baseline can't resolve, if any. */
        val mirrorTrueDeg: Float?,
        override val confidence: Float,
    ) : RadarBlip

    data class Ring(
        override val track: Track,
        val rangeM: Float,
        override val confidence: Float,
    ) : RadarBlip
}

/**
 * Builds the [RadarBlip] list from live tracks. Pure: [eye] is the user's world
 * position, [sessionToTrueDeg] rotates the world (ARCore session) frame onto true
 * north (0 for the already-north-aligned GPS/ENU frame), and [sdrArrayOffsetDeg]
 * rotates a raw SDR-array bearing onto true north.
 */
object RadarModel {

    fun build(
        tracks: List<Track>,
        eye: Vec3,
        sessionToTrueDeg: Float,
        sdrArrayOffsetDeg: Float,
    ): List<RadarBlip> = tracks.mapNotNull { track ->
        when (val dir = track.direction) {
            is Direction.MotionEstimated -> {
                val d = dir.worldPos - eye
                val azSession = ArcoreMath.bearing(eye, dir.worldPos).first
                val azTrue = FrameMath.sessionToTrue(azSession, sessionToTrueDeg)
                val horiz = sqrt(d.x * d.x + d.z * d.z)
                val a = Math.toRadians(azTrue.toDouble())
                RadarBlip.Located(
                    track = track,
                    east = (horiz * sin(a)).toFloat(),
                    north = (horiz * cos(a)).toFloat(),
                    rangeM = horiz,
                    azimuthTrueDeg = azTrue,
                    confidence = dir.confidence,
                )
            }
            is Direction.TrueBearing -> {
                // Only an external SDR reports azimuth in its array frame; rotate that
                // onto true north. Everything else is already true.
                val azTrue = if (track.sourceType == SourceType.EXTERNAL_SDR)
                    FrameMath.sdrArrayToTrue(dir.azimuthDeg, sdrArrayOffsetDeg) else dir.azimuthDeg
                RadarBlip.Ray(track, FrameMath.wrap360(azTrue), null, dir.confidence)
            }
            is Direction.InterferometricBearing -> RadarBlip.Ray(
                track,
                FrameMath.wrap360(dir.azimuthDeg),
                dir.ambiguousMirrorDeg?.let { FrameMath.wrap360(it) },
                dir.confidence,
            )
            is Direction.RssiOnly -> dir.estimatedDistanceM?.let {
                RadarBlip.Ring(track, it, dir.confidence)
            }
        }
    }
}
