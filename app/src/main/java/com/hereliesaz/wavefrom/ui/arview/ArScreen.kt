package com.hereliesaz.wavefrom.ui.arview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Sensor-overlay AR view: CameraX preview + compass/gyro orientation. The
 * fallback path used when ARCore isn't available.
 */
@Composable
fun ArScreen(viewModel: ArViewModel) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val orientation by viewModel.orientation.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        CameraPreview(Modifier.fillMaxSize())
        SignalHud(tracks = tracks, orientation = orientation, modifier = Modifier.fillMaxSize())
        HudControls()
    }
}
