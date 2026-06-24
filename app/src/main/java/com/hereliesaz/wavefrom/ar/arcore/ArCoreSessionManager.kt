package com.hereliesaz.wavefrom.ar.arcore

import android.app.Activity
import android.util.Log
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session

/**
 * Owns the ARCore [Session] lifecycle: install prompt, creation, configuration,
 * resume/pause/close. All ARCore failures degrade to a null session so the
 * caller can fall back to the sensor overlay rather than crash.
 */
class ArCoreSessionManager {

    var session: Session? = null
        private set

    private var installRequested = false

    /** Create the session if needed; returns null if unavailable or install pending. */
    fun ensureSession(activity: Activity): Session? {
        session?.let { return it }
        return try {
            when (ArCoreApk.getInstance().requestInstall(activity, !installRequested)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    installRequested = true
                    null
                }
                ArCoreApk.InstallStatus.INSTALLED -> {
                    Session(activity).also { s ->
                        s.configure(
                            Config(s).apply {
                                focusMode = Config.FocusMode.AUTO
                                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                            },
                        )
                        session = s
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "ARCore session unavailable", e)
            null
        }
    }

    fun resume() {
        try {
            session?.resume()
        } catch (e: Exception) {
            Log.w(TAG, "ARCore resume failed", e)
            session = null
        }
    }

    fun pause() {
        try {
            session?.pause()
        } catch (e: Exception) {
            Log.w(TAG, "ARCore pause failed", e)
        }
    }

    fun close() {
        session?.close()
        session = null
    }

    private companion object {
        const val TAG = "ArCoreSession"
    }
}
