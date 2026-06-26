package com.hereliesaz.wavefrom.ui.arview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hereliesaz.wavefrom.ar.frame.BearingFrame
import com.hereliesaz.wavefrom.ar.frame.CalibrationConfig
import com.hereliesaz.wavefrom.ar.frame.FrameMath
import com.hereliesaz.wavefrom.ar.sensor.DeviceOrientation
import com.hereliesaz.wavefrom.ar.sensor.ScreenPoint
import com.hereliesaz.wavefrom.ar.sensor.ScreenProjection
import com.hereliesaz.wavefrom.signal.model.Direction
import com.hereliesaz.wavefrom.signal.model.SourceType
import com.hereliesaz.wavefrom.signal.model.Track
import com.hereliesaz.wavefrom.signal.model.Vec3
import com.hereliesaz.wavefrom.signal.physics.BandColor
import com.hereliesaz.wavefrom.signal.waveform.AnchoredHelixProjection
import com.hereliesaz.wavefrom.signal.waveform.HelixGeometry
import kotlin.math.atan
import kotlin.math.roundToInt
import kotlin.math.tan

private const val HORIZONTAL_FOV_DEG = 63f

/**
 * The AR overlay. Bearing-bearing tracks (true / interferometric) are projected
 * onto the camera at their azimuth/elevation; RSSI-only tracks have no honest
 * direction, so they are listed in a bottom strip explicitly labeled
 * "direction unknown" rather than placed at a fabricated angle.
 */
@Composable
fun SignalHud(
    tracks: List<Track>,
    orientation: DeviceOrientation,
    headingFrame: BearingFrame,
    targetFrame: BearingFrame,
    modifier: Modifier = Modifier,
    onSelectTrack: (String) -> Unit = {},
    showArHelix: Boolean = false,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        // Derive vertical FOV from the horizontal FOV and the viewport aspect.
        val vFov = Math.toDegrees(
            2.0 * atan(tan(Math.toRadians(HORIZONTAL_FOV_DEG / 2.0)) * (heightPx / widthPx)),
        ).toFloat()

        // One atomic snapshot of the live calibration; both the heading and the SDR
        // targets convert against the same offsets. With the default (zero) config
        // every conversion is the identity, so behaviour matches the pre-calibration
        // build until the user calibrates.
        val cfg = CalibrationConfig.state
        val headingTrue = FrameMath.headingToTrue(headingFrame, orientation.azimuthDeg, cfg)

        // Project each bearing track once; reused by the optional helix overlay and the
        // markers (this path recomposes on every orientation update, so avoid re-projecting).
        val bearingPoints = tracks.mapNotNull { track ->
            val bearing = track.bearingOrNull() ?: return@mapNotNull null
            // Only an external SDR reports azimuth in its array frame; everything else
            // (e.g. interferometric) is already in the heading frame — don't double-offset.
            val targetAz =
                if (targetFrame == BearingFrame.SDR_ARRAY && track.sourceType == SourceType.EXTERNAL_SDR) {
                    FrameMath.sdrArrayToTrue(bearing.azimuth, cfg.sdrArrayOffsetDeg)
                } else {
                    bearing.azimuth
                }
            val p = ScreenProjection.project(
                targetAzimuthDeg = targetAz,
                targetElevationDeg = bearing.elevation,
                headingDeg = headingTrue,
                pitchDeg = orientation.pitchDeg,
                horizontalFovDeg = HORIZONTAL_FOV_DEG,
                verticalFovDeg = vFov,
                widthPx = widthPx,
                heightPx = heightPx,
            ) ?: return@mapNotNull null
            Triple(track, bearing, p)
        }

        // Unit helix geometry per bearing track. Its shape depends only on frequency
        // (stable per id; fromIq autoscales the power away), so cache it across the
        // orientation updates that drive recomposition. Only built while the toggle is on.
        val helixById = remember(showArHelix, bearingPoints.map { it.first.id }) {
            if (!showArHelix) {
                emptyMap()
            } else {
                bearingPoints.associate { (t, _, _) ->
                    t.id to HelixGeometry.fromIq(HelixGeometry.parametric(t, samples = 96))
                }
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(Color.White.copy(alpha = 0.5f), radius = 6f, center = c)
            drawCircle(Color.White.copy(alpha = 0.2f), radius = 48f, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            if (showArHelix) {
                bearingPoints.forEach { (track, _, p) ->
                    helixById[track.id]?.let { drawAnchoredHelix(track, p, c, it) }
                }
            }
        }

        val now = System.currentTimeMillis()
        bearingPoints.forEach { (track, bearing, p) ->
            BearingMarker(track, bearing.ambiguous, p.x, p.y, stalenessAlpha(track.ageMs(now)), onSelectTrack)
        }

        val rssiTracks = tracks.filter { it.direction is Direction.RssiOnly }
        if (rssiTracks.isNotEmpty()) {
            NearbyStrip(
                rssiTracks,
                now,
                Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                onSelectTrack,
            )
        }
    }
}

/**
 * Opacity for a track of the given age. Solid while fresh, then fading toward (not
 * to) zero as it nears the aggregator's ~15 s expiry, so the user sees a signal
 * going stale rather than vanishing without warning.
 */
private fun stalenessAlpha(ageMs: Long): Float {
    val fadeStart = 5_000f
    val fadeEnd = 13_000f
    return when {
        ageMs <= fadeStart -> 1f
        ageMs >= fadeEnd -> 0.25f
        else -> 1f - 0.75f * ((ageMs - fadeStart) / (fadeEnd - fadeStart))
    }
}

/**
 * An honest distance label: a confidence-widened range, not a single crisp number,
 * because RSSI→distance is fuzzy. High confidence collapses to "~X m"; low
 * confidence shows a wide "~lo–hi m" bracket.
 */
private fun fuzzyDistanceLabel(distanceM: Float, confidence: Float): String {
    val halfWidth = 1f - confidence.coerceIn(0.1f, 1f)
    val lo = (distanceM * (1f - halfWidth)).coerceAtLeast(0.1f).roundToInt()
    val hi = (distanceM * (1f + halfWidth)).roundToInt()
    return if (hi - lo <= 1) "~$hi m" else "~$lo–$hi m"
}

/**
 * Draw a track's IQ helix as a small foreshortened "spring" pinned at its marker. Its
 * time axis points from the marker toward the crosshair (the line of sight), giving the
 * AR overlay an at-a-glance 3D shape; the full interactive helix is the standalone viewer.
 */
private fun DrawScope.drawAnchoredHelix(track: Track, point: ScreenPoint, reticle: Offset, local: List<Vec3>) {
    val radiusPx = ((track.smoothedPowerDbm + 90f) / 60f).coerceIn(0f, 1f) * 10f + 10f
    val proj = AnchoredHelixProjection.project(
        points = local,
        center = point,
        axisX = reticle.x - point.x,
        axisY = reticle.y - point.y,
        radiusPx = radiusPx,
        lengthPx = 70f,
    )
    val color = Color(BandColor.argb(track.band))
    for (n in 0 until proj.size - 1) {
        val t = n.toFloat() / proj.size
        drawLine(
            color = color.copy(alpha = 0.25f + 0.55f * t),
            start = Offset(proj[n].x, proj[n].y),
            end = Offset(proj[n + 1].x, proj[n + 1].y),
            strokeWidth = 3f,
        )
    }
}

private data class Bearing(val azimuth: Float, val elevation: Float, val ambiguous: Boolean)

private fun Track.bearingOrNull(): Bearing? = when (val d = direction) {
    is Direction.TrueBearing -> Bearing(d.azimuthDeg, d.elevationDeg ?: 0f, ambiguous = false)
    is Direction.InterferometricBearing -> Bearing(d.azimuthDeg, 0f, ambiguous = d.ambiguousMirrorDeg != null)
    else -> null
}

@Composable
private fun BearingMarker(
    track: Track,
    ambiguous: Boolean,
    x: Float,
    y: Float,
    staleAlpha: Float,
    onSelectTrack: (String) -> Unit,
) {
    val color = Color(BandColor.argb(track.band))
    // Strong signals render larger.
    val radiusDp = ((track.smoothedPowerDbm + 90f) / 60f).coerceIn(0f, 1f) * 14f + 8f
    // Cache the clickable modifier so this frequently-recomposed path doesn't allocate
    // a new lambda each orientation update.
    val clickMod = remember(track.id, onSelectTrack) { Modifier.clickable { onSelectTrack(track.id) } }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        // Measure the marker and place it centered on (x, y) in pixels, converting
        // the dp dot radius to px so it stays correct at any screen density.
        modifier = clickMod
            .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val radiusPx = radiusDp.dp.toPx()
            layout(placeable.width, placeable.height) {
                placeable.placeRelative(
                    (x - placeable.width / 2f).roundToInt(),
                    (y - radiusPx / 2f).roundToInt(),
                )
            }
        },
    ) {
        Box(
            Modifier
                .size(radiusDp.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = (if (ambiguous) 0.5f else 0.9f) * staleAlpha)),
        )
        Text(
            text = track.identity.label,
            color = Color.White.copy(alpha = staleAlpha),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "${track.smoothedPowerDbm.roundToInt()} dBm",
            color = color.copy(alpha = staleAlpha),
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun NearbyStrip(
    tracks: List<Track>,
    now: Long,
    modifier: Modifier = Modifier,
    onSelectTrack: (String) -> Unit = {},
) {
    Column(
        modifier
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = "Nearby · direction unknown",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tracks.take(24).forEach { NearbyChip(it, stalenessAlpha(it.ageMs(now)), onSelectTrack) }
        }
    }
}

@Composable
private fun NearbyChip(track: Track, staleAlpha: Float, onSelectTrack: (String) -> Unit = {}) {
    val color = Color(BandColor.argb(track.band))
    val rssi = track.direction as? Direction.RssiOnly
    val label = rssi?.estimatedDistanceM?.let { fuzzyDistanceLabel(it, rssi.confidence) }
        ?: track.band.displayName
    val clickMod = remember(track.id, onSelectTrack) { Modifier.clickable { onSelectTrack(track.id) } }
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .then(clickMod)
            .background(Color.White.copy(alpha = 0.08f * staleAlpha))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color.copy(alpha = staleAlpha)))
        Column {
            Text(track.identity.label, color = Color.White.copy(alpha = staleAlpha), fontSize = 11.sp)
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.6f * staleAlpha),
                fontSize = 9.sp,
            )
        }
    }
}
