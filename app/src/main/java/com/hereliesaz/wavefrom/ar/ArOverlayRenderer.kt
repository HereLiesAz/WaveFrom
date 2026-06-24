package com.hereliesaz.wavefrom.ar

import android.content.Context
import com.google.ar.core.ArCoreApk

/**
 * How signals are anchored to the camera view. WaveFrom prefers full ARCore
 * world tracking and falls back to a sensor-based overlay (compass + gyro) on
 * devices without ARCore.
 */
enum class RenderMode { ARCORE, SENSOR }

/**
 * Chooses the rendering mode for the current device via ArCoreApk. The check can
 * be transient on cold start (UNKNOWN_CHECKING) — the UI re-queries — so this
 * returns SENSOR until ARCore is positively known to be supported.
 */
object ArRendererFactory {
    fun choose(context: Context): RenderMode = try {
        if (ArCoreApk.getInstance().checkAvailability(context).isSupported) RenderMode.ARCORE
        else RenderMode.SENSOR
    } catch (e: Exception) {
        RenderMode.SENSOR
    }
}
