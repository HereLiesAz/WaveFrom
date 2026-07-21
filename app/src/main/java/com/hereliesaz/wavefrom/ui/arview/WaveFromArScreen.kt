package com.hereliesaz.wavefrom.ui.arview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    var showMap by remember { mutableStateOf(false) }

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

        // Tapping a marker (or the Live IQ button) opens the 3D IQ-helix viewer over the
        // camera view. The data is looked up live so the helix keeps updating.
        val selectedId by viewModel.selectedTrackId.collectAsStateWithLifecycle()
        val tracks by viewModel.tracks.collectAsStateWithLifecycle()
        val liveWaveform by viewModel.liveWaveform.collectAsStateWithLifecycle()

        val target = selectedId?.let { id ->
            val track = tracks.firstOrNull { it.id == id }
            when {
                // A tapped emitter: use its real IQ if a window is tagged for it, else parametric.
                track != null -> {
                    val real = liveWaveform?.takeIf { it.sourceId == track.id }?.window
                    WaveformTarget.fromTrack(track, real)
                }
                // A single-antenna SDR has no track; match its live window by source id.
                liveWaveform?.sourceId == id -> liveWaveform?.let { WaveformTarget.fromFrame(it) }
                else -> null
            }
        }
        if (target != null) {
            WaveformViewer3D(
                target = target,
                onClose = viewModel::clearSelection,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Top-down map overlay. The camera screen stays composed underneath so it keeps
        // pushing the user's live pose; the map covers it with an opaque background.
        if (showMap) {
            MapScreen(viewModel, onClose = { showMap = false }, Modifier.fillMaxSize())
        } else {
            FilledTonalButton(
                onClick = { showMap = true },
                modifier = Modifier.align(Alignment.CenterStart).padding(8.dp),
            ) {
                Text("Map")
            }
        }
    }
}
