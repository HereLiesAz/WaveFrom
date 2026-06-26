package com.hereliesaz.wavefrom.ui.arview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hereliesaz.wavefrom.ar.frame.BearingFrame

/**
 * Sensor-overlay AR view: CameraX preview + compass/gyro orientation. The
 * fallback path used when ARCore isn't available. The compass reads magnetic
 * north, so heading converts via the magnetic→true frame.
 */
@Composable
fun ArScreen(viewModel: ArViewModel) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val orientation by viewModel.orientation.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        CameraPreview(Modifier.fillMaxSize())
        SignalHud(
            tracks = tracks,
            orientation = orientation,
            headingFrame = BearingFrame.MAGNETIC_NORTH,
            targetFrame = BearingFrame.SDR_ARRAY,
            modifier = Modifier.fillMaxSize(),
            onSelectTrack = viewModel::selectTrack,
        )
        HudControls(
            orientation = orientation,
            tracks = tracks,
            headingFrame = BearingFrame.MAGNETIC_NORTH,
        )
    }
}
