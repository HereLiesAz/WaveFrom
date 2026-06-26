package com.hereliesaz.wavefrom.ui.arview

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hereliesaz.wavefrom.signal.model.Track
import com.hereliesaz.wavefrom.signal.model.Vec3
import com.hereliesaz.wavefrom.signal.physics.BandColor
import com.hereliesaz.wavefrom.signal.waveform.HelixGeometry
import com.hereliesaz.wavefrom.signal.waveform.IqWindow
import com.hereliesaz.wavefrom.signal.waveform.OrbitCamera
import com.hereliesaz.wavefrom.signal.waveform.OrbitProjection
import com.hereliesaz.wavefrom.signal.waveform.WaveformSource

private const val FOV_DEG = 50f

/**
 * Full-screen interactive viewer for a single emitter's 3D IQ helix (I vs Q vs time).
 * The helix slowly auto-spins; drag orbits it, pinch zooms. When [iqWindow] is null the
 * helix is synthesized parametrically from the track's frequency + power; when a real
 * captured window is supplied (Step 4) it reflects actual modulation. The header makes
 * the data source explicit so the visual never overstates its fidelity.
 */
@Composable
fun WaveformViewer3D(
    track: Track,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    iqWindow: IqWindow? = null,
) {
    val window = remember(track.id, track.smoothedPowerDbm, track.frequencyHz, iqWindow) {
        iqWindow ?: HelixGeometry.parametric(track)
    }
    val points = remember(window) { HelixGeometry.fromIq(window, radiusScale = 1f) }
    val color = Color(BandColor.argb(track.band))

    // Continuous turntable spin, with user drag/zoom layered on top.
    val spin by rememberInfiniteTransition(label = "spin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing)),
        label = "azimuth",
    )
    var dragAz by remember { mutableFloatStateOf(0f) }
    var elevation by remember { mutableFloatStateOf(18f) }
    var distance by remember { mutableFloatStateOf(4.5f) }

    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xFF0A1014))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    dragAz += pan.x * 0.3f
                    elevation = (elevation - pan.y * 0.3f).coerceIn(-85f, 85f)
                    distance = (distance / zoom).coerceIn(1.5f, 12f)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cam = OrbitCamera(spin + dragAz, elevation, distance)
            val w = size.width
            val h = size.height
            val projected = points.map { OrbitProjection.project(it, cam, FOV_DEG, w, h) }

            // Faint axes (I / Q / time) for orientation.
            val axisLen = HelixGeometry.AXIS_LENGTH / 2f
            drawAxis(cam, w, h, Vec3(axisLen, 0f, 0f), Color(0x55FF6E6E))
            drawAxis(cam, w, h, Vec3(0f, axisLen, 0f), Color(0x5566D9FF))
            drawAxis(cam, w, h, Vec3(0f, 0f, axisLen), Color(0x55FFFFFF))

            for (n in 0 until projected.size - 1) {
                val a = projected[n] ?: continue
                val b = projected[n + 1] ?: continue
                // Brighten along the time axis so the corkscrew reads as 3D.
                val t = n.toFloat() / projected.size
                drawLine(
                    color = color.copy(alpha = 0.35f + 0.65f * t),
                    start = Offset(a.x, a.y),
                    end = Offset(b.x, b.y),
                    strokeWidth = 4f,
                )
            }
        }

        Header(track, window.source, Modifier.align(Alignment.TopStart).fillMaxWidth())

        FilledTonalButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
        ) { Text("Close") }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAxis(
    cam: OrbitCamera,
    w: Float,
    h: Float,
    end: Vec3,
    color: Color,
) {
    val o = OrbitProjection.project(Vec3.ZERO, cam, FOV_DEG, w, h) ?: return
    val e = OrbitProjection.project(end, cam, FOV_DEG, w, h) ?: return
    drawLine(color, Offset(o.x, o.y), Offset(e.x, e.y), strokeWidth = 2f)
}

@Composable
private fun Header(track: Track, source: WaveformSource, modifier: Modifier = Modifier) {
    val color = Color(BandColor.argb(track.band))
    val freq = track.band.frequencyHz(track.frequencyHz)
    val freqLabel = "%.3f GHz".format(freq / 1e9)
    Column(
        modifier
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(color))
            Text(track.identity.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            SourceBadge(source)
        }
        Text("${track.band.displayName} · $freqLabel · ${track.smoothedPowerDbm.toInt()} dBm", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        Text(
            if (source == WaveformSource.REAL) {
                "Real captured IQ — twist is true baseband modulation."
            } else {
                "Synthesized from frequency + power; twist is a perceptual map of the carrier, not literal Hz."
            },
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun SourceBadge(source: WaveformSource) {
    val (label, bg) = when (source) {
        WaveformSource.REAL -> "REAL" to Color(0xFF2E7D32)
        WaveformSource.PARAMETRIC -> "PARAMETRIC" to Color(0xFF455A64)
    }
    Text(
        label,
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
