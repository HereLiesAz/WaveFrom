package com.hereliesaz.wavefrom.ui.arview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hereliesaz.wavefrom.signal.source.sdr.OccupancyBus
import com.hereliesaz.wavefrom.signal.source.sdr.SdrMessage
import kotlin.math.roundToInt

/** Readings older than this are dropped from the panel rather than shown stale. */
private const val STALE_AFTER_MS = 10_000L

private val CautionAmber = Color(0xFFFFB060)
private val PresentGreen = Color(0xFF6FCF97)
private val AbsentGrey = Color(0xFF9AA0A6)

/**
 * Room-level presence/vitals readings from any CSI-capable companion pod
 * ([OccupancyBus]) — a wholly different kind of claim than the located-emitter
 * markers in [SignalHud], so it gets its own panel rather than a spot on the AR
 * overlay: RuView's own vitals work (the inspiration for this pipeline) is
 * explicit that these numbers are a research/prototype capability, not a
 * medical measurement, and that boundary has to be visible everywhere a BPM is.
 *
 * The safety line below is not optional decoration — it stays on screen for as
 * long as this panel is open, unconditionally, regardless of how confident any
 * individual reading is.
 */
@Composable
fun OccupancyPanel(modifier: Modifier = Modifier) {
    val readings by OccupancyBus.latest.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()
    val fresh = readings.values
        .filter { now - it.timestampMs < STALE_AFTER_MS }
        .sortedBy { it.zoneId }

    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(12.dp)
            .width(240.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Not a medical device — experimental, room-level RF estimate only",
            color = CautionAmber,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (fresh.isEmpty()) {
            Text(
                "No occupancy data — connect a CSI-capable companion pod",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
            )
        } else {
            fresh.forEach { OccupancyZoneRow(it) }
        }
    }
}

@Composable
private fun OccupancyZoneRow(occupancy: SdrMessage.Occupancy) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PresenceDot(occupancy.present)
            Text(occupancy.zoneId, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            if (occupancy.synthetic) {
                Text("SIM", color = CautionAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            presenceLabel(occupancy),
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 11.sp,
        )
        vitalsLabel("Breathing", occupancy.breathingBpm, occupancy.breathingConfidence)?.let {
            Text(it, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
        }
        vitalsLabel("Heart", occupancy.heartBpm, occupancy.heartConfidence)?.let {
            Text(it, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun PresenceDot(present: Boolean) {
    val color = if (present) PresentGreen else AbsentGrey
    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
}

private fun presenceLabel(occupancy: SdrMessage.Occupancy): String {
    val pct = (occupancy.presenceConfidence * 100).roundToInt()
    return if (occupancy.present) "Present · $pct% confidence" else "No one detected · $pct% confidence"
}

/** null when the extractor didn't have enough signal to report a rate at all. */
private fun vitalsLabel(name: String, bpm: Float?, confidence: Float): String? {
    if (bpm == null) return null
    val pct = (confidence * 100).roundToInt()
    return "$name: ${bpm.roundToInt()} BPM · $pct% confidence"
}
