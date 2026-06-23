package com.hereliesaz.wavefrom.ui.arview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * The main AR view: live camera background with the signal overlay on top.
 * Collecting [ArViewModel] state starts the scan pipeline (cold flows), so
 * sensing only runs while this screen is visible.
 */
@Composable
fun ArScreen(viewModel: ArViewModel = viewModel()) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val orientation by viewModel.orientation.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        CameraPreview(Modifier.fillMaxSize())
        SignalHud(tracks = tracks, orientation = orientation, modifier = Modifier.fillMaxSize())
    }
}
