package com.hereliesaz.wavefrom.ui.arview

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.opengl.GLSurfaceView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hereliesaz.wavefrom.ar.arcore.ArCoreRenderer
import com.hereliesaz.wavefrom.ar.arcore.ArCoreSessionManager
import com.hereliesaz.wavefrom.ar.arcore.ArcoreMath
import com.hereliesaz.wavefrom.ar.sensor.DeviceOrientation
import com.hereliesaz.wavefrom.signal.localize.Pose
import com.hereliesaz.wavefrom.signal.model.Direction
import com.hereliesaz.wavefrom.signal.model.Track
import com.hereliesaz.wavefrom.signal.model.Vec3

/**
 * ARCore AR view: GL camera background with world tracking. The camera pose
 * feeds the motion-aided localizer (lighting up Tier 2) and drives the overlay
 * orientation; motion-estimated emitters are projected from their 3D world
 * position. Falls back to the sensor screen if ARCore can't start.
 */
@Composable
fun ArCoreScreen(viewModel: ArViewModel) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context.findActivity()

    val sessionManager = remember { ArCoreSessionManager() }
    var orientation by remember { mutableStateOf(DeviceOrientation(0f, 0f, 0f)) }
    var camWorld by remember { mutableStateOf(Vec3.ZERO) }

    val glView = remember {
        GLSurfaceView(context).apply {
            preserveEGLContextOnPause = true
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setRenderer(
                ArCoreRenderer(
                    sessionProvider = { sessionManager.session },
                    onPose = { pose ->
                        val pos = Vec3(pose[0], pose[1], pose[2])
                        val q = floatArrayOf(pose[3], pose[4], pose[5], pose[6])
                        camWorld = pos
                        orientation = ArcoreMath.orientationOf(q)
                        viewModel.localizer.onPose(
                            Pose(pos, ArcoreMath.forward(q), System.currentTimeMillis()),
                        )
                    },
                ),
            )
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    activity?.let { sessionManager.ensureSession(it) }
                    sessionManager.resume()
                    glView.onResume()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    glView.onPause()
                    sessionManager.pause()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sessionManager.close()
        }
    }

    val arTracks = remember(tracks, camWorld) { tracks.map { projectForArcore(it, camWorld) } }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { glView }, modifier = Modifier.fillMaxSize())
        SignalHud(tracks = arTracks, orientation = orientation, modifier = Modifier.fillMaxSize())
        HudControls()
    }
}

/** Project a motion-estimated 3D emitter into a bearing relative to the camera. */
private fun projectForArcore(track: Track, eye: Vec3): Track {
    val dir = track.direction
    if (dir !is Direction.MotionEstimated) return track
    val (az, el) = ArcoreMath.bearing(eye, dir.worldPos)
    return track.copy(direction = Direction.TrueBearing(az, el, dir.confidence))
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
