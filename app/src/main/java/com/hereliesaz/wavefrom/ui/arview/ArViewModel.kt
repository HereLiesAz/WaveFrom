package com.hereliesaz.wavefrom.ui.arview

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.wavefrom.BuildConfig
import com.hereliesaz.wavefrom.ar.sensor.DeviceOrientation
import com.hereliesaz.wavefrom.ar.sensor.HeadingProvider
import com.hereliesaz.wavefrom.signal.model.Track
import com.hereliesaz.wavefrom.signal.repo.SignalRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Owns the signal pipeline and the device-orientation stream for the AR screen.
 * Both are cold upstreams shared as [StateFlow]s, so scanning only runs while the
 * UI is on-screen (and, by gating behind the permission screen, only once
 * permissions are granted).
 */
class ArViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = SignalRepository(
        sources = SignalRepository.defaultSources(app, includeSimulated = BuildConfig.DEBUG),
        scope = viewModelScope,
    )

    val tracks: StateFlow<List<Track>> = repository.tracks

    val orientation: StateFlow<DeviceOrientation> =
        HeadingProvider(app).orientation()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                DeviceOrientation(0f, 0f, 0f),
            )
}
