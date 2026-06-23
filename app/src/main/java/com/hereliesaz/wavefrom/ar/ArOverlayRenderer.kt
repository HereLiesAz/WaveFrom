package com.hereliesaz.wavefrom.ar

import android.content.Context

/**
 * How signals are anchored to the camera view. WaveFrom prefers full ARCore
 * world tracking and falls back to a sensor-based overlay (compass + gyro) on
 * devices without ARCore.
 */
enum class RenderMode { ARCORE, SENSOR }

/**
 * Chooses the rendering mode for the current device. Phase 1 ships only the
 * sensor overlay; Phase 2 adds [RenderMode.ARCORE] selected via
 * `com.google.ar.core.ArCoreApk.checkAvailability()`. The seam lives here so the
 * UI asks the factory instead of hard-coding a mode.
 */
object ArRendererFactory {
    fun choose(@Suppress("unused") context: Context): RenderMode {
        // TODO(phase2): return ARCORE when ArCoreApk reports SUPPORTED_INSTALLED.
        return RenderMode.SENSOR
    }
}
