package com.hereliesaz.wavefrom.ui.arview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.ar.core.ArCoreApk
import com.hereliesaz.wavefrom.ar.RenderMode
import kotlinx.coroutines.delay

/**
 * Entry composable: picks the ARCore world-tracking view when supported, else
 * the sensor overlay. ARCore availability can take a few async checks to resolve
 * on cold start, so this starts on the sensor path and upgrades to ARCore once
 * confirmed.
 */
@Composable
fun WaveFromArScreen(viewModel: ArViewModel = viewModel()) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(RenderMode.SENSOR) }

    LaunchedEffect(Unit) {
        repeat(10) {
            val availability = runCatching {
                ArCoreApk.getInstance().checkAvailability(context)
            }.getOrNull()
            if (availability != null && !availability.isTransient) {
                if (availability.isSupported) mode = RenderMode.ARCORE
                return@LaunchedEffect
            }
            delay(200)
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (mode) {
            RenderMode.ARCORE -> ArCoreScreen(viewModel)
            RenderMode.SENSOR -> ArScreen(viewModel)
        }

        // Tapping a marker opens the 3D IQ-helix viewer over the camera view. The
        // track is looked up live so the helix keeps updating; if it expires while
        // open, the viewer closes.
        val selectedId by viewModel.selectedTrackId.collectAsStateWithLifecycle()
        val tracks by viewModel.tracks.collectAsStateWithLifecycle()
        val selected = selectedId?.let { id -> tracks.firstOrNull { it.id == id } }
        if (selected != null) {
            WaveformViewer3D(
                track = selected,
                onClose = viewModel::clearSelection,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
