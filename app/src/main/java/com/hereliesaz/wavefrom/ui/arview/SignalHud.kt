package com.hereliesaz.wavefrom.ui.arview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hereliesaz.wavefrom.ar.sensor.DeviceOrientation
import com.hereliesaz.wavefrom.ar.sensor.ScreenProjection
import com.hereliesaz.wavefrom.signal.model.Direction
import com.hereliesaz.wavefrom.signal.model.Track
import com.hereliesaz.wavefrom.signal.physics.BandColor
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
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        // Derive vertical FOV from the horizontal FOV and the viewport aspect.
        val vFov = Math.toDegrees(
            2.0 * atan(tan(Math.toRadians(HORIZONTAL_FOV_DEG / 2.0)) * (heightPx / widthPx)),
        ).toFloat()

        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(Color.White.copy(alpha = 0.5f), radius = 6f, center = c)
            drawCircle(Color.White.copy(alpha = 0.2f), radius = 48f, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        }

        tracks.forEach { track ->
            val bearing = track.bearingOrNull() ?: return@forEach
            val point = ScreenProjection.project(
                targetAzimuthDeg = bearing.azimuth,
                targetElevationDeg = bearing.elevation,
                headingDeg = orientation.azimuthDeg,
                pitchDeg = orientation.pitchDeg,
                horizontalFovDeg = HORIZONTAL_FOV_DEG,
                verticalFovDeg = vFov,
                widthPx = widthPx,
                heightPx = heightPx,
            ) ?: return@forEach
            BearingMarker(track, bearing.ambiguous, point.x, point.y)
        }

        val rssiTracks = tracks.filter { it.direction is Direction.RssiOnly }
        if (rssiTracks.isNotEmpty()) {
            NearbyStrip(
                rssiTracks,
                Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            )
        }
    }
}

private data class Bearing(val azimuth: Float, val elevation: Float, val ambiguous: Boolean)

private fun Track.bearingOrNull(): Bearing? = when (val d = direction) {
    is Direction.TrueBearing -> Bearing(d.azimuthDeg, d.elevationDeg ?: 0f, ambiguous = false)
    is Direction.InterferometricBearing -> Bearing(d.azimuthDeg, 0f, ambiguous = d.ambiguousMirrorDeg != null)
    else -> null
}

@Composable
private fun BearingMarker(track: Track, ambiguous: Boolean, x: Float, y: Float) {
    val color = Color(BandColor.argb(track.band))
    // Strong signals render larger.
    val radiusDp = ((track.smoothedPowerDbm + 90f) / 60f).coerceIn(0f, 1f) * 14f + 8f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        // Measure the marker and place it centered on (x, y) in pixels, converting
        // the dp dot radius to px so it stays correct at any screen density.
        modifier = Modifier.layout { measurable, constraints ->
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
                .background(color.copy(alpha = if (ambiguous) 0.5f else 0.9f)),
        )
        Text(
            text = track.identity.label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "${track.smoothedPowerDbm.roundToInt()} dBm",
            color = color,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun NearbyStrip(tracks: List<Track>, modifier: Modifier = Modifier) {
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
            tracks.take(24).forEach { NearbyChip(it) }
        }
    }
}

@Composable
private fun NearbyChip(track: Track) {
    val color = Color(BandColor.argb(track.band))
    val distance = (track.direction as? Direction.RssiOnly)?.estimatedDistanceM
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Column {
            Text(track.identity.label, color = Color.White, fontSize = 11.sp)
            Text(
                text = distance?.let { "~${it.roundToInt()} m" } ?: track.band.displayName,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 9.sp,
            )
        }
    }
}
