package com.hereliesaz.wavefrom.ui.arview

import android.hardware.SensorManager
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hereliesaz.wavefrom.BuildConfig
import com.hereliesaz.wavefrom.ar.frame.BearingFrame
import com.hereliesaz.wavefrom.ar.frame.CalibrationConfig
import com.hereliesaz.wavefrom.ar.frame.FrameMath
import com.hereliesaz.wavefrom.ar.sensor.DeviceOrientation
import com.hereliesaz.wavefrom.signal.model.Track
import com.hereliesaz.wavefrom.signal.physics.PathLoss
import com.hereliesaz.wavefrom.signal.source.cellular.CellDiagnostics

/**
 * Shared overlay controls (spectrum waterfall toggle + calibration) drawn on top of
 * either the sensor or ARCore camera view. [orientation] (with its compass accuracy)
 * and [tracks] feed the bearing-frame calibration; [headingFrame] tells the panel
 * which frame the device heading is in.
 */
@Composable
fun HudControls(
    orientation: DeviceOrientation,
    tracks: List<Track>,
    headingFrame: BearingFrame,
    modifier: Modifier = Modifier,
    showArHelix: Boolean = false,
    onToggleArHelix: () -> Unit = {},
) {
    var showWaterfall by remember { mutableStateOf(false) }
    var showCalibrate by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        if (showWaterfall) {
            SpectrumWaterfall(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().height(120.dp),
            )
        }
        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(onClick = { showWaterfall = !showWaterfall }) {
                Text(if (showWaterfall) "Hide spectrum" else "Spectrum")
            }
            FilledTonalButton(onClick = onToggleArHelix) {
                Text(if (showArHelix) "Hide AR helix" else "AR helix")
            }
            FilledTonalButton(onClick = { showCalibrate = !showCalibrate }) {
                Text("Calibrate")
            }
        }
        if (showCalibrate) {
            CalibrationPanel(
                orientation = orientation,
                tracks = tracks,
                headingFrame = headingFrame,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            )
        }
        if (BuildConfig.DEBUG) {
            val lastResolve by CellDiagnostics.lastResolve.collectAsStateWithLifecycle()
            DiagnosticsOverlay(
                orientation = orientation,
                tracks = tracks,
                headingFrame = headingFrame,
                lastResolve = lastResolve,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            )
        }
    }
}

/**
 * Live calibration: path-loss exponent (RSSI→distance), the bearing-frame offsets
 * (SDR array, north nudge), a compass-reliability warning, and a one-tap align that
 * pins the centred SDR track to the crosshair.
 */
@Composable
@VisibleForTesting
internal fun CalibrationPanel(
    orientation: DeviceOrientation,
    tracks: List<Track>,
    headingFrame: BearingFrame,
    modifier: Modifier = Modifier,
) {
    var exponent by remember { mutableFloatStateOf(PathLoss.Config.exponent.toFloat()) }
    var sdrOffset by remember { mutableFloatStateOf(CalibrationConfig.sdrArrayOffsetDeg) }
    var northNudge by remember { mutableFloatStateOf(CalibrationConfig.manualNorthNudgeDeg) }

    val cfg = CalibrationConfig.state
    val headingTrue = FrameMath.headingToTrue(headingFrame, orientation.azimuthDeg, cfg)
    val centeredSdr = CalibrationActions.centeredSdrTrack(tracks, headingTrue, cfg.sdrArrayOffsetDeg)
    val compassPoor = orientation.accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW

    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(12.dp)
            .width(240.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (compassPoor) {
            Text(
                "Compass unreliable — wave the phone in a figure-8",
                color = Color(0xFFFF6E6E),
            )
        }

        Text("Path-loss exponent: ${"%.1f".format(exponent)}", color = Color.White)
        Slider(
            value = exponent,
            onValueChange = {
                exponent = it
                PathLoss.Config.exponent = it.toDouble()
            },
            valueRange = 2.0f..4.5f,
        )

        Text("SDR array offset: ${sdrOffset.toInt()}°", color = Color.White)
        Slider(
            value = sdrOffset,
            onValueChange = {
                sdrOffset = it
                CalibrationConfig.sdrArrayOffsetDeg = it
            },
            valueRange = -180f..180f,
        )

        val declSource = if (cfg.declinationFromLocation) "GPS" else "manual"
        Text(
            "North nudge: ${northNudge.toInt()}° (decl ${"%.1f".format(cfg.declinationDeg)}° $declSource)",
            color = Color.White,
        )
        Slider(
            value = northNudge,
            onValueChange = {
                northNudge = it
                CalibrationConfig.manualNorthNudgeDeg = it
            },
            valueRange = -30f..30f,
        )

        // One-tap: point the camera at where the centred SDR emitter really is and
        // tap to solve the array offset that pins it to the crosshair.
        FilledTonalButton(
            onClick = {
                CalibrationActions.solveAlignOffset(centeredSdr, headingTrue)?.let { solved ->
                    CalibrationConfig.sdrArrayOffsetDeg = solved
                    sdrOffset = solved
                }
            },
            enabled = centeredSdr != null,
        ) {
            Text(
                centeredSdr?.let { "Align “${it.first.identity.label}” to crosshair" }
                    ?: "Align SDR — center a marker",
            )
        }
    }
}
