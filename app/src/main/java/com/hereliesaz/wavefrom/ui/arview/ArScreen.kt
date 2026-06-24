package com.hereliesaz.wavefrom.ui.arview

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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hereliesaz.wavefrom.signal.physics.PathLoss

/**
 * The main AR view: live camera background, the signal overlay, an optional
 * spectrum waterfall, and a path-loss calibration control. Collecting
 * [ArViewModel] state starts the scan pipeline (cold flows), so sensing only
 * runs while this screen is visible.
 */
@Composable
fun ArScreen(viewModel: ArViewModel = viewModel()) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val orientation by viewModel.orientation.collectAsStateWithLifecycle()
    var showWaterfall by remember { mutableStateOf(false) }
    var showCalibrate by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        CameraPreview(Modifier.fillMaxSize())
        SignalHud(tracks = tracks, orientation = orientation, modifier = Modifier.fillMaxSize())

        if (showWaterfall) {
            SpectrumWaterfall(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(120.dp),
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
            FilledTonalButton(onClick = { showCalibrate = !showCalibrate }) {
                Text("Calibrate")
            }
        }

        if (showCalibrate) {
            CalibrationPanel(Modifier.align(Alignment.BottomEnd).padding(8.dp))
        }
    }
}

/** Adjusts the live path-loss exponent used for RSSI→distance estimates. */
@Composable
private fun CalibrationPanel(modifier: Modifier = Modifier) {
    var exponent by remember { mutableFloatStateOf(PathLoss.Config.exponent.toFloat()) }
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(12.dp)
            .width(220.dp),
    ) {
        Text("Path-loss exponent: ${"%.1f".format(exponent)}", color = Color.White)
        Slider(
            value = exponent,
            onValueChange = {
                exponent = it
                PathLoss.Config.exponent = it.toDouble()
            },
            valueRange = 2.0f..4.5f,
        )
        Text(
            "2 = open space · 3 = indoor · 4 = obstructed",
            color = Color.White.copy(alpha = 0.6f),
        )
    }
}
