package com.hereliesaz.wavefrom.ui.arview

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hereliesaz.wavefrom.ar.arcore.ArcoreMath
import com.hereliesaz.wavefrom.ar.frame.BearingFrame
import com.hereliesaz.wavefrom.ar.frame.CalibrationConfig
import com.hereliesaz.wavefrom.ar.frame.FrameMath
import com.hereliesaz.wavefrom.signal.localize.GpsPoseProvider
import com.hereliesaz.wavefrom.signal.localize.Pose
import com.hereliesaz.wavefrom.signal.model.Direction
import com.hereliesaz.wavefrom.signal.model.Track
import com.hereliesaz.wavefrom.signal.model.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sensor-overlay AR view: CameraX preview + compass/gyro orientation. The
 * fallback path used when ARCore isn't available. The compass reads magnetic
 * north, so heading converts via the magnetic→true frame.
 *
 * Without ARCore there is no VIO translation, so motion-aided localization runs
 * off GPS instead: each fix becomes a [Pose] in the local ENU frame, giving the
 * solver a coarse aperture. GPS noise caps these solves to low confidence (they
 * mostly surface only when backed by accurate Wi-Fi-RTT ranges), and they project
 * to bearings here just like the ARCore path does.
 */
@Composable
fun ArScreen(viewModel: ArViewModel) {
    val context = LocalContext.current
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val orientation by viewModel.orientation.collectAsStateWithLifecycle()
    val liveWaveform by viewModel.liveWaveform.collectAsStateWithLifecycle()
    var showArHelix by remember { mutableStateOf(false) }
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val isReplaying by viewModel.isReplaying.collectAsStateWithLifecycle()
    val hasRecordings by viewModel.hasRecordings.collectAsStateWithLifecycle()
    // Only offer the Live-IQ button while an SDR window is actually arriving.
    val freshWaveform = liveWaveform?.takeIf { System.currentTimeMillis() - it.timestampMs < 3_000 }

    // GPS → local-ENU pose feed for the motion-aided localizer (sensor-path aperture).
    val gps = remember { GpsPoseProvider() }
    var devicePos by remember { mutableStateOf<Vec3?>(null) }
    DisposableEffect(viewModel) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        var listener: LocationListener? = null
        if (granted && lm != null) {
            val l = LocationListener { loc: Location ->
                val pos = gps.toLocal(loc.latitude, loc.longitude, if (loc.hasAltitude()) loc.altitude else 0.0)
                devicePos = pos
                // forward is unused by the solver; derive a horizontal look vector from
                // the compass so the pose is well-formed. Accuracy gates/penalizes the solve.
                val azRad = Math.toRadians(orientation.azimuthDeg.toDouble())
                val forward = Vec3(sin(azRad).toFloat(), 0f, (-cos(azRad)).toFloat())
                val acc = if (loc.hasAccuracy()) loc.accuracy else DEFAULT_GPS_ACCURACY_M
                viewModel.localizer.onPose(Pose(pos, forward, loc.time, acc))
            }
            try {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, GPS_INTERVAL_MS, 0f, l, Looper.getMainLooper(),
                )
                listener = l
            } catch (e: SecurityException) {
                // Permission revoked between the check and the request: stay RSSI-only.
            } catch (e: IllegalArgumentException) {
                // No GPS provider on this device (or it's disabled): stay RSSI-only.
            }
        }
        onDispose { listener?.let { lm?.removeUpdates(it) } }
    }

    // Push the user's pose to the top-down map: GPS/ENU is already true north, so the
    // session→true rotation is 0; the user's lat/lon comes from the GPS origin.
    // snapshotFlow (not LaunchedEffect keys) so high-frequency orientation updates don't
    // churn the coroutine — one collector reacts to whichever state changes.
    LaunchedEffect(Unit) {
        snapshotFlow { Pair(devicePos, orientation) }.collect { (pos, orient) ->
            val eye = pos ?: return@collect
            val headingTrue = FrameMath.headingToTrue(
                BearingFrame.MAGNETIC_NORTH, orient.azimuthDeg, CalibrationConfig.state,
            )
            viewModel.updateMapPose(eye, 0f, headingTrue, gps.toGeo(eye))
        }
    }

    // Project motion-estimated emitters to bearings from the current GPS position, so
    // the HUD can place them; pass-through for every other tier. ENU is already true
    // north, so no session offset is needed (unlike the ARCore path).
    val projected = remember(tracks, devicePos) {
        devicePos?.let { eye -> tracks.map { projectForSensor(it, eye) } } ?: tracks
    }

    Box(Modifier.fillMaxSize()) {
        CameraPreview(Modifier.fillMaxSize())
        SignalHud(
            tracks = projected,
            orientation = orientation,
            headingFrame = BearingFrame.MAGNETIC_NORTH,
            targetFrame = BearingFrame.SDR_ARRAY,
            modifier = Modifier.fillMaxSize(),
            onSelectTrack = viewModel::selectTrack,
            showArHelix = showArHelix,
        )
        HudControls(
            orientation = orientation,
            tracks = projected,
            headingFrame = BearingFrame.MAGNETIC_NORTH,
            showArHelix = showArHelix,
            onToggleArHelix = { showArHelix = !showArHelix },
            liveWaveformLabel = freshWaveform?.label,
            onOpenLiveWaveform = { freshWaveform?.let { viewModel.selectTrack(it.sourceId) } },
            isRecording = isRecording,
            onToggleRecording = viewModel::toggleRecording,
            isReplaying = isReplaying,
            canReplay = hasRecordings,
            onToggleReplay = { if (isReplaying) viewModel.stopReplay() else viewModel.replayLatest() },
        )
    }
}

/**
 * Project a motion-estimated 3D emitter into a true-north bearing as seen from the
 * device's current ENU position. The GPS world frame is already aligned to true
 * north, so (unlike the ARCore session frame) the azimuth needs no offset.
 */
private fun projectForSensor(track: Track, eye: Vec3): Track {
    val dir = track.direction
    if (dir !is Direction.MotionEstimated) return track
    val (az, el) = ArcoreMath.bearing(eye, dir.worldPos)
    return track.copy(direction = Direction.TrueBearing(az, el, dir.confidence))
}

private const val GPS_INTERVAL_MS = 1_000L
private const val DEFAULT_GPS_ACCURACY_M = 10f
