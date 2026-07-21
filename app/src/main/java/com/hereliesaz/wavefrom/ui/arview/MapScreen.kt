package com.hereliesaz.wavefrom.ui.arview

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hereliesaz.wavefrom.ar.frame.CalibrationConfig
import com.hereliesaz.wavefrom.ar.map.RadarBlip
import com.hereliesaz.wavefrom.ar.map.RadarModel
import com.hereliesaz.wavefrom.ar.map.RadarProjection
import com.hereliesaz.wavefrom.ar.map.TrackTrails
import com.hereliesaz.wavefrom.signal.physics.BandColor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Top-down "radar" map: the user at centre, north up, emitters placed by honesty
 * tier — located emitters as dots (with breadcrumb trails), bearing-only emitters
 * as rays, RSSI-only emitters as range rings. A geographic readout appears when a
 * GPS origin is known. It plots the same live tracks the AR overlay shows, from a
 * second vantage point, and never fabricates a position the tier can't support.
 */
@Composable
fun MapScreen(viewModel: ArViewModel, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val pose by viewModel.mapPose.collectAsStateWithLifecycle()

    val blips = remember(tracks, pose, CalibrationConfig.sdrArrayOffsetDeg) {
        RadarModel.build(tracks, pose.eye, pose.sessionToTrueDeg, CalibrationConfig.sdrArrayOffsetDeg)
    }
    // Trails live in the ViewModel so they survive map close + rotation.
    val trails = viewModel.trackTrails

    val labelPaint = remember { Paint().apply { isAntiAlias = true; textSize = 28f; color = android.graphics.Color.WHITE } }
    val ringPaint = remember { Paint().apply { isAntiAlias = true; textSize = 24f; color = 0x99FFFFFF.toInt() } }
    // Hoisted out of the draw scope to avoid per-frame allocations.
    val dash = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 10f)) }
    val userPath = remember {
        Path().apply { moveTo(0f, -18f); lineTo(-11f, 12f); lineTo(11f, 12f); close() }
    }

    val located = blips.filterIsInstance<RadarBlip.Located>()
    val rings = blips.filterIsInstance<RadarBlip.Ring>()
    val rays = blips.filterIsInstance<RadarBlip.Ray>()
    LaunchedEffect(located) {
        located.forEach { trails.add(it.track.id, it.east, it.north) }
    }
    val maxRange = maxOf(
        MIN_RANGE_M,
        located.maxOfOrNull { it.rangeM } ?: MIN_RANGE_M,
        rings.maxOfOrNull { it.rangeM } ?: MIN_RANGE_M,
    )

    Box(modifier.fillMaxSize().background(Color(0xFF0B0F14))) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radiusPx = min(cx, cy) * 0.9f
            val mpp = RadarProjection.autoScale(maxRange, radiusPx)

            drawRangeRings(cx, cy, radiusPx, maxRange, mpp, ringPaint)
            drawCardinalTicks(cx, cy, radiusPx)

            // RSSI-only: distance known, direction unknown → a faint ring around the user.
            rings.forEach { r ->
                val rp = r.rangeM / mpp
                drawCircle(
                    Color(BandColor.argb(r.track.band)).copy(alpha = 0.18f),
                    radius = rp, center = Offset(cx, cy), style = Stroke(width = 2f),
                )
            }

            // Bearing-only: direction known, range unknown → a ray to the map edge.
            rays.forEach { ray ->
                val color = Color(BandColor.argb(ray.track.band))
                drawRay(cx, cy, ray.azimuthTrueDeg, maxRange, mpp, color.copy(alpha = 0.7f), dash)
                ray.mirrorTrueDeg?.let { drawRay(cx, cy, it, maxRange, mpp, color.copy(alpha = 0.3f), dash) }
            }

            // Located: trails then dots.
            located.forEach { dot ->
                val color = Color(BandColor.argb(dot.track.band))
                val trail = trails.trail(dot.track.id)
                for (i in 0 until trail.size - 1) {
                    val a = RadarProjection.metricToScreen(trail[i].x, trail[i].y, cx, cy, mpp)
                    val b = RadarProjection.metricToScreen(trail[i + 1].x, trail[i + 1].y, cx, cy, mpp)
                    drawLine(color.copy(alpha = 0.25f), Offset(a.x, a.y), Offset(b.x, b.y), strokeWidth = 2f)
                }
                val p = RadarProjection.polarToScreen(dot.azimuthTrueDeg, dot.rangeM, cx, cy, mpp)
                drawCircle(color.copy(alpha = 0.35f + 0.5f * dot.confidence), radius = 9f, center = Offset(p.x, p.y))
                drawContext.canvas.nativeCanvas.drawText(
                    "${dot.track.identity.label}  ${dot.rangeM.roundToInt()}m", p.x + 12f, p.y, labelPaint,
                )
            }

            drawUser(cx, cy, pose.headingDeg, userPath)
        }

        Column(Modifier.align(Alignment.TopStart).padding(12.dp)) {
            Text("Map · north up", color = Color.White)
            Text(
                "${located.size} located · ${rays.size} bearings · ${rings.size} range-only",
                color = Color.White.copy(alpha = 0.7f),
            )
            Text("range ≈ ${maxRange.roundToInt()} m", color = Color.White.copy(alpha = 0.7f))
            pose.userGeo?.let {
                Text("you: %.5f, %.5f".format(it.latDeg, it.lonDeg), color = Color(0xFF8FD0FF))
            }
        }
        FilledTonalButton(onClick = onClose, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
            Text("Close map")
        }
    }
}

private fun DrawScope.drawRangeRings(
    cx: Float, cy: Float, radiusPx: Float, maxRange: Float, mpp: Float, paint: Paint,
) {
    for (i in 1..4) {
        val rPx = radiusPx * (i * 0.25f)
        drawCircle(Color.White.copy(alpha = 0.10f), radius = rPx, center = Offset(cx, cy), style = Stroke(2f))
        val metres = (rPx * mpp).roundToInt()
        drawContext.canvas.nativeCanvas.drawText("${metres}m", cx + 4f, cy - rPx + 22f, paint)
    }
}

private fun DrawScope.drawCardinalTicks(cx: Float, cy: Float, radiusPx: Float) {
    val faint = Color.White.copy(alpha = 0.15f)
    drawLine(faint, Offset(cx, cy - radiusPx), Offset(cx, cy + radiusPx))
    drawLine(faint, Offset(cx - radiusPx, cy), Offset(cx + radiusPx, cy))
}

private fun DrawScope.drawRay(
    cx: Float, cy: Float, azDeg: Float, maxRange: Float, mpp: Float, color: Color, dash: PathEffect,
) {
    val end = RadarProjection.polarToScreen(azDeg, maxRange, cx, cy, mpp)
    drawLine(color, Offset(cx, cy), Offset(end.x, end.y), strokeWidth = 3f, pathEffect = dash)
}

private fun DrawScope.drawUser(cx: Float, cy: Float, headingDeg: Float, userPath: Path) {
    // userPath is built once around (0,0); translate to the centre and rotate by heading.
    translate(cx, cy) {
        rotate(headingDeg, pivot = Offset.Zero) {
            drawPath(userPath, Color(0xFF4CC2FF))
        }
    }
    drawCircle(Color.White, radius = 3f, center = Offset(cx, cy))
}

private const val MIN_RANGE_M = 20f
