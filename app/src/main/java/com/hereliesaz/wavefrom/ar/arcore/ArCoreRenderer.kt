package com.hereliesaz.wavefrom.ar.arcore

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * GLSurfaceView renderer that draws the ARCore camera background and, each
 * tracked frame, reports the camera pose as
 * [px, py, pz, qx, qy, qz, qw] via [onPose]. Marker drawing is handled by the
 * Compose overlay on top, not here.
 */
class ArCoreRenderer(
    private val sessionProvider: () -> Session?,
    private val onPose: (FloatArray) -> Unit,
) : GLSurfaceView.Renderer {

    private val background = BackgroundRenderer()
    private var width = 0
    private var height = 0
    private var displayRotation = 0
    private var viewportChanged = false

    fun setDisplayRotation(rotation: Int) {
        displayRotation = rotation
        viewportChanged = true
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        background.createOnGlThread()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        GLES20.glViewport(0, 0, w, h)
        width = w
        height = h
        viewportChanged = true
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val session = sessionProvider() ?: return

        session.setCameraTextureName(background.textureId)
        if (viewportChanged && width > 0 && height > 0) {
            session.setDisplayGeometry(displayRotation, width, height)
            viewportChanged = false
        }

        val frame = try {
            session.update()
        } catch (e: Exception) {
            return
        }

        background.draw(frame)

        val camera = frame.camera
        if (camera.trackingState == TrackingState.TRACKING) {
            val t = camera.pose.translation
            val q = camera.pose.rotationQuaternion
            onPose(floatArrayOf(t[0], t[1], t[2], q[0], q[1], q[2], q[3]))
        }
    }
}
