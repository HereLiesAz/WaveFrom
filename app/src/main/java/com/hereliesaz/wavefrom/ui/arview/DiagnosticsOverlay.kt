package com.hereliesaz.wavefrom.ui.arview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hereliesaz.wavefrom.ar.frame.BearingFrame
import com.hereliesaz.wavefrom.ar.frame.CalibrationConfig
import com.hereliesaz.wavefrom.ar.frame.FrameMath
import com.hereliesaz.wavefrom.ar.sensor.DeviceOrientation
import com.hereliesaz.wavefrom.signal.model.Track
import com.hereliesaz.wavefrom.signal.source.cellular.CellResolveSnapshot

/**
 * Debug-only readout of the live calibration state, so the three hardware-bound
 * features (align, ARCore north seed, cellular bearing) can be verified on a device
 * at a glance. Gated behind `BuildConfig.DEBUG` by the caller; stripped from release.
 */
@Composable
internal fun DiagnosticsOverlay(
    orientation: DeviceOrientation,
    tracks: List<Track>,
    headingFrame: BearingFrame,
    lastResolve: CellResolveSnapshot?,
    modifier: Modifier = Modifier,
) {
    val cfg = CalibrationConfig.state
    val headingTrue = FrameMath.headingToTrue(headingFrame, orientation.azimuthDeg, cfg)
    val centered = CalibrationActions.centeredSdrTrack(tracks, headingTrue, cfg.sdrArrayOffsetDeg)
    val declSource = if (cfg.declinationFromLocation) "GPS" else "manual"

    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .width(230.dp),
    ) {
        listOf(
            "frame=$headingFrame  hdg ${orientation.azimuthDeg.fmt()}→true ${headingTrue.fmt()}",
            "compass acc ${orientation.accuracy}",
            "SDR off ${cfg.sdrArrayOffsetDeg.fmt()}  nudge ${cfg.manualNorthNudgeDeg.fmt()}  decl ${cfg.declinationDeg.fmt()} $declSource",
            "ARCore seeded ${cfg.arcoreSeeded}  sess→N ${cfg.sessionToTrueNorthDeg.fmt()}",
            "centered SDR: ${centered?.first?.identity?.label ?: "none"}",
            lastResolve?.let { "cell ${it.cellKey} → ${it.bearingDeg.fmt()}" } ?: "cell: no resolve",
        ).forEach { line ->
            Text(
                line,
                color = Color(0xFF8CE0A0),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun Float.fmt(): String = "%.0f°".format(this)
