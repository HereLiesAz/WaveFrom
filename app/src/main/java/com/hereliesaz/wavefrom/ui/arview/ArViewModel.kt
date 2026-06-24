package com.hereliesaz.wavefrom.ui.arview

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.wavefrom.BuildConfig
import com.hereliesaz.wavefrom.ar.frame.DeclinationProvider
import com.hereliesaz.wavefrom.ar.sensor.DeviceOrientation
import com.hereliesaz.wavefrom.ar.sensor.HeadingProvider
import com.hereliesaz.wavefrom.signal.localize.SyntheticApertureLocalizer
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

    /**
     * Tier-2 localizer. Fed device poses by the ARCore renderer (Phase 2); until
     * a pose stream with real translation exists it stays inert and tracks remain
     * RSSI-only. Held here so the same instance receives both poses and detections.
     */
    val localizer = SyntheticApertureLocalizer()

    private val repository = SignalRepository(
        sources = SignalRepository.defaultSources(app, includeSimulated = BuildConfig.DEBUG),
        scope = viewModelScope,
        localizer = localizer,
    )

    val tracks: StateFlow<List<Track>> = repository.tracks

    val orientation: StateFlow<DeviceOrientation> =
        HeadingProvider(app).orientation()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                DeviceOrientation(0f, 0f, 0f),
            )

    init {
        updateDeclination(app)
    }

    /**
     * Seed magnetic declination from the last known location so the sensor heading
     * converts magnetic→true. Best-effort: if location permission is missing or no
     * fix exists, declination stays at its manual default (the north-nudge slider).
     */
    private fun updateDeclination(ctx: Context) {
        val granted = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        val loc = try {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (e: SecurityException) {
            null
        } ?: return
        DeclinationProvider().update(loc.latitude, loc.longitude, loc.altitude, System.currentTimeMillis())
    }
}
