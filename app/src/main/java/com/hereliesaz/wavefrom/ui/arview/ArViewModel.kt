package com.hereliesaz.wavefrom.ui.arview

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.SensorManager
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
import com.hereliesaz.wavefrom.signal.record.ReplayController
import com.hereliesaz.wavefrom.signal.record.SessionFormat
import com.hereliesaz.wavefrom.signal.record.SessionStore
import com.hereliesaz.wavefrom.signal.repo.SignalRepository
import com.hereliesaz.wavefrom.signal.source.sdr.IqFrame
import com.hereliesaz.wavefrom.signal.source.sdr.WaveformBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    /**
     * Track the user tapped to inspect in the 3D waveform viewer, by [Track.id], or
     * null when the viewer is closed. The viewer looks the live track up from [tracks]
     * so its helix keeps updating as the signal changes.
     */
    private val _selectedTrackId = MutableStateFlow<String?>(null)
    val selectedTrackId: StateFlow<String?> = _selectedTrackId.asStateFlow()

    fun selectTrack(id: String) { _selectedTrackId.value = id }

    fun clearSelection() { _selectedTrackId.value = null }

    /** Latest real IQ window from an SDR (on-phone USB or networked), for the 3D viewer. */
    val liveWaveform: StateFlow<IqFrame?> = WaveformBus.latest

    // ---- Top-down map -----------------------------------------------------
    /**
     * The user's pose for the map, pushed by whichever camera screen is active:
     * world [eye] position, the [sessionToTrueDeg] that rotates the world frame to
     * true north (0 for the GPS/ENU frame), the true [headingDeg] the user faces,
     * and the user's geographic position when a GPS origin exists.
     */
    data class MapPose(
        val eye: com.hereliesaz.wavefrom.signal.model.Vec3 = com.hereliesaz.wavefrom.signal.model.Vec3.ZERO,
        val sessionToTrueDeg: Float = 0f,
        val headingDeg: Float = 0f,
        val userGeo: com.hereliesaz.wavefrom.signal.localize.GeoPoint? = null,
    )

    private val _mapPose = MutableStateFlow(MapPose())
    val mapPose: StateFlow<MapPose> = _mapPose.asStateFlow()

    /** Breadcrumb history for the map, held here so it survives map close + rotation. */
    val trackTrails = com.hereliesaz.wavefrom.ar.map.TrackTrails()

    fun updateMapPose(
        eye: com.hereliesaz.wavefrom.signal.model.Vec3,
        sessionToTrueDeg: Float,
        headingDeg: Float,
        userGeo: com.hereliesaz.wavefrom.signal.localize.GeoPoint? = null,
    ) {
        _mapPose.value = MapPose(eye, sessionToTrueDeg, headingDeg, userGeo)
    }

    // ---- Session record & replay -----------------------------------------
    private val sessionStore = SessionStore(app)
    private var recordingJob: Job? = null
    private var recordStartMs = 0L

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    /** True while a recorded session is driving the pipeline instead of live radios. */
    val isReplaying: StateFlow<Boolean> =
        ReplayController.active.map { it != null }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _hasRecordings = MutableStateFlow(false)
    /** True when at least one recording exists to replay. */
    val hasRecordings: StateFlow<Boolean> = _hasRecordings.asStateFlow()

    init {
        refreshRecordings()
    }

    private fun refreshRecordings() {
        viewModelScope.launch(Dispatchers.IO) {
            _hasRecordings.value = sessionStore.list().isNotEmpty()
        }
    }

    /** Start capturing the live detection stream to a new file, or stop and flush. */
    fun toggleRecording() {
        if (_isRecording.value) {
            recordingJob?.cancel() // the coroutine's finally closes the handle on its own thread
            recordingJob = null
            _isRecording.value = false
            refreshRecordings()
            return
        }
        recordStartMs = System.currentTimeMillis()
        _isRecording.value = true
        recordingJob = viewModelScope.launch(Dispatchers.IO) {
            // Open and close the file entirely on this coroutine's thread, so the
            // non-thread-safe writer is never touched from the main thread on cancel.
            val handle = runCatching { sessionStore.create("session-${System.currentTimeMillis()}") }.getOrNull()
            if (handle == null) {
                _isRecording.value = false
                return@launch
            }
            try {
                repository.detections.collect { det ->
                    val offset = System.currentTimeMillis() - recordStartMs
                    runCatching { handle.writer.appendLine(SessionFormat.encodeLine(offset, det)) }
                }
            } finally {
                handle.close()
            }
        }
    }

    /** Load the newest recording and replay it through the pipeline (loops by default). */
    fun replayLatest() {
        viewModelScope.launch(Dispatchers.IO) {
            val newest = sessionStore.list().firstOrNull() ?: return@launch
            val session = runCatching { sessionStore.load(newest) }.getOrNull() ?: return@launch
            ReplayController.play(session)
        }
    }

    fun stopReplay() = ReplayController.stop()

    override fun onCleared() {
        recordingJob?.cancel() // finally-block in the recording coroutine closes the handle
        super.onCleared()
    }

    val orientation: StateFlow<DeviceOrientation> =
        HeadingProvider(app).orientation()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                // Mark the placeholder UNRELIABLE so the ARCore north-seed guard
                // (which requires accuracy ≥ MEDIUM) ignores it until a real
                // compass sample arrives.
                DeviceOrientation(0f, 0f, 0f, SensorManager.SENSOR_STATUS_UNRELIABLE),
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
