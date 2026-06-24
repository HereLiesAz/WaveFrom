package com.hereliesaz.wavefrom.ar.arcore

import com.hereliesaz.wavefrom.ar.sensor.DeviceOrientation
import com.hereliesaz.wavefrom.signal.model.Vec3
import kotlin.math.asin
import kotlin.math.atan2

/**
 * Pure-math helpers bridging ARCore poses and WaveFrom's overlay model. ARCore's
 * world frame is gravity-aligned (+Y up) with the camera initially looking down
 * -Z; yaw is session-relative (absolute-north alignment is a calibration TODO).
 * Because the camera orientation and emitter bearings are computed in the same
 * world frame here, they stay consistent with each other.
 */
object ArcoreMath {

    /** Rotate vector [v] by unit quaternion q = [x,y,z,w]. */
    fun rotate(q: FloatArray, v: Vec3): Vec3 {
        val x = q[0]; val y = q[1]; val z = q[2]; val w = q[3]
        // t = 2 * cross(q.xyz, v)
        val tx = 2f * (y * v.z - z * v.y)
        val ty = 2f * (z * v.x - x * v.z)
        val tz = 2f * (x * v.y - y * v.x)
        // v + w*t + cross(q.xyz, t)
        return Vec3(
            v.x + w * tx + (y * tz - z * ty),
            v.y + w * ty + (z * tx - x * tz),
            v.z + w * tz + (x * ty - y * tx),
        )
    }

    /** Camera look direction (world) from its rotation quaternion. */
    fun forward(q: FloatArray): Vec3 = rotate(q, Vec3(0f, 0f, -1f))

    /** Device orientation (session-relative heading/pitch) from a pose quaternion. */
    fun orientationOf(q: FloatArray): DeviceOrientation {
        val f = forward(q)
        return DeviceOrientation(
            azimuthDeg = azimuth(f),
            pitchDeg = Math.toDegrees(asin(f.y.coerceIn(-1f, 1f).toDouble())).toFloat(),
            rollDeg = 0f,
        )
    }

    /** Azimuth/elevation (deg) of [target] as seen from [eye], in the world frame. */
    fun bearing(eye: Vec3, target: Vec3): Pair<Float, Float> {
        val d = target - eye
        val len = d.length().coerceAtLeast(1e-3f)
        val az = azimuth(d)
        val el = Math.toDegrees(asin((d.y / len).coerceIn(-1f, 1f).toDouble())).toFloat()
        return az to el
    }

    /** Heading of a direction in the world's X/-Z plane, 0..360. */
    private fun azimuth(d: Vec3): Float {
        val a = Math.toDegrees(atan2(d.x.toDouble(), (-d.z).toDouble())).toFloat()
        return (a + 360f) % 360f
    }
}
