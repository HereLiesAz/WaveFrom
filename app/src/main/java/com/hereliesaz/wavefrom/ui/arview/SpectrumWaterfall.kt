package com.hereliesaz.wavefrom.ui.arview

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hereliesaz.wavefrom.signal.source.sdr.SpectrumBus

private const val MAX_ROWS = 96

/**
 * Scrolling spectrum waterfall fed by [SpectrumBus]: each incoming frame is a
 * new row at the top, power mapped to a blue→red heat color. Mirrors the
 * "render the spectrum" half of an RF camera; only active when an SDR/Pi pod is
 * streaming spectrum frames.
 */
@Composable
fun SpectrumWaterfall(modifier: Modifier = Modifier) {
    val latest by SpectrumBus.latest.collectAsStateWithLifecycle()
    val history = remember { mutableStateListOf<FloatArray>() }

    LaunchedEffect(latest) {
        latest?.let { frame ->
            history.add(0, frame.powersDbm)
            while (history.size > MAX_ROWS) history.removeAt(history.lastIndex)
        }
    }

    Canvas(modifier) {
        if (history.isEmpty()) return@Canvas
        val rowH = size.height / MAX_ROWS
        history.forEachIndexed { row, powers ->
            if (powers.isEmpty()) return@forEachIndexed
            val colW = size.width / powers.size
            for (i in powers.indices) {
                drawRect(
                    color = heatColor(powers[i]),
                    topLeft = Offset(i * colW, row * rowH),
                    size = Size(colW + 1f, rowH + 1f),
                )
            }
        }
    }
}

/** Map power in dBm (~-120 weak .. -20 strong) to a blue→cyan→yellow→red heat. */
private fun heatColor(dbm: Float): Color {
    val t = ((dbm + 120f) / 100f).coerceIn(0f, 1f)
    return when {
        t < 0.33f -> lerp(Color(0xFF06121F), Color(0xFF1565C0), t / 0.33f)
        t < 0.66f -> lerp(Color(0xFF1565C0), Color(0xFFFFEB3B), (t - 0.33f) / 0.33f)
        else -> lerp(Color(0xFFFFEB3B), Color(0xFFE53935), (t - 0.66f) / 0.34f)
    }
}

private fun lerp(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)
